package dev.oriel.mod.internal.gametest;


import dev.oriel.mod.internal.net.ClosedPayload;
import dev.oriel.mod.internal.net.MessagePayload;
import dev.oriel.mod.internal.net.OpenPayload;
import dev.oriel.mod.internal.net.OrielNetwork;
import dev.oriel.mod.server.ForgeMenu;
import dev.oriel.mod.server.OrielServer;
import dev.oriel.mod.server.OrielSession;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Server-only regressions for fallback, per-player routing and atomic forge inventory changes. */
public final class ServerFeaturesGameTests {
    public static void init() {
        GameTests.add("protocol_compatibility", h -> {
            h.assertFalse(OrielNetwork.compatible(type -> false), "missing client rejected");
            h.assertFalse(OrielNetwork.compatible(type -> type != dev.oriel.mod.internal.net.ProtocolPayload.TYPE), "old or mismatched protocol rejected");
            h.assertTrue(OrielNetwork.compatible(type -> true), "matching complete capabilities accepted");
            h.assertFalse(OrielNetwork.compatible(type -> type != dev.oriel.mod.internal.net.PatchPayload.TYPE), "incomplete protocol rejected");
            for (boolean installed : new boolean[] {false, true}) {
                String reason = dev.oriel.mod.internal.net.ProtocolMessages.rejection(installed);
                h.assertTrue(reason.contains("Oriel UI") && reason.contains("优易库") && reason.contains("1"),
                        "language resources provide bilingual missing/version rejection");
            }
            h.succeed();
        });
        GameTests.add("unsupported_client", ServerFeaturesGameTests::unsupported);
        GameTests.add("player_isolation", ServerFeaturesGameTests::isolation);
        GameTests.add("forge_menu", ServerFeaturesGameTests::forge);
    }

    private static void unsupported(GameTestHelper h) {
        ServerPlayer player = OrielGameTests.player(h);
        try {
            h.assertFalse(OrielNetwork.canReceive(player, OpenPayload.TYPE), "mock has no Oriel channel");
            OrielSession session = OrielServer.open(player, "oriel:oriel/demo/templates.html", null);
            h.assertFalse(session.isOpen(), "unsupported client returns closed session");
            h.assertValueEqual(session.openStatus(), OrielSession.OpenStatus.CLIENT_UNSUPPORTED, "explicit failure result");
            int[] calls = {0};
            session.onOpenFailure(reason -> {
                h.assertValueEqual(reason, OrielSession.OpenStatus.CLIENT_UNSUPPORTED, "late callback reason");
                calls[0]++;
            });
            h.assertValueEqual(calls[0], 1, "callback attached after return is not lost");
            h.assertTrue(OrielServer.session(player, session.id()) == null, "failed session removed");
            h.assertFalse(ForgeMenu.open(player), "forge refuses unsupported client before a custom menu packet");
        } finally { OrielGameTests.leave(h, player); }
        h.succeed();
    }

    private static void isolation(GameTestHelper h) {
        List<ServerPlayer> recipients = new ArrayList<>();
        OrielGameTests.withPlayers(h, (p, packet) -> recipients.add(p), (a, b) -> {
            OrielSession session = OrielServer.open(a, "oriel:oriel/demo/templates.html", null);
            h.assertValueEqual(session.openStatus(), OrielSession.OpenStatus.SENT, "negotiated delivery");
            session.onOpenFailure(reason -> h.fail("successful send called failure callback"));
            List<ServerPlayer> messages = new ArrayList<>();
            session.onMessage("reply", (p, value) -> messages.add(p));
            OrielServer.handleMessage(b, new MessagePayload(session.id(), "reply", "true"));
            OrielServer.handleClosed(b, new ClosedPayload(session.id()));
            h.assertTrue(session.isOpen(), "other player cannot close session");
            OrielServer.handleMessage(a, new MessagePayload(session.id(), "reply", "true"));
            h.assertValueEqual(messages, List.of(a), "only target player can reply");
            h.assertValueEqual(recipients, List.of(a), "only target received open packet");
            session.close();
        });
        h.succeed();
    }

    private static ItemStack sword() {
        ItemStack item = new ItemStack(Items.IRON_SWORD);
        item.setDamageValue(100);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved"));
        return item;
    }

    private static void forge(GameTestHelper h) {
        ServerPlayer player = OrielGameTests.player(h);
        ForgeMenu menu = new ForgeMenu(1, player.getInventory());
        try {
            h.assertValueEqual(menu.slots.size(), 39, "3 forge + 36 player slots");
            h.assertFalse(menu.getSlot(ForgeMenu.OUTPUT).mayPlace(sword()), "output refuses insertion");
            h.assertFalse(menu.getSlot(ForgeMenu.MATERIAL).mayPlace(new ItemStack(Items.DIRT)), "material rejects unrelated items");
            menu.getSlot(ForgeMenu.INPUT).set(sword());
            menu.getSlot(ForgeMenu.MATERIAL).set(new ItemStack(Items.IRON_INGOT, 2));
            menu.broadcastChanges();
            menu.broadcastChanges();
            h.assertValueEqual(menu.pageData().get("progress").getAsInt(), 1, "extra broadcasts in one tick cannot accelerate crafting");
        } catch (RuntimeException failure) {
            menu.removed(player);
            OrielGameTests.leave(h, player);
            throw failure;
        }
        h.onEachTick(() -> { menu.broadcastChanges(); menu.broadcastChanges(); });
        h.startSequence().thenIdle(5).thenExecute(() -> menu.getSlot(ForgeMenu.MATERIAL).set(ItemStack.EMPTY))
                .thenIdle(2).thenExecute(() -> {
            h.assertValueEqual(menu.pageData().get("progress").getAsInt(), 0, "removing ingredient resets progress");
            h.assertTrue(menu.getSlot(ForgeMenu.OUTPUT).getItem().isEmpty(), "no result without material");
            menu.getSlot(ForgeMenu.MATERIAL).set(new ItemStack(Items.IRON_INGOT, 2));
        }).thenIdle(ForgeMenu.WORK_TICKS + 2).thenExecute(() -> {
            ItemStack result = menu.getSlot(ForgeMenu.OUTPUT).getItem();
            h.assertTrue(result.is(Items.IRON_SWORD) && result.getDamageValue() < 100, "server repaired the sword");
            h.assertValueEqual(result.get(DataComponents.CUSTOM_NAME).getString(), "Preserved", "components preserved");
            h.assertTrue(menu.getSlot(ForgeMenu.INPUT).getItem().isEmpty(), "one input consumed");
            h.assertValueEqual(menu.getSlot(ForgeMenu.MATERIAL).getItem().getCount(), 1, "one material consumed");
            menu.getSlot(ForgeMenu.INPUT).set(sword());
        }).thenIdle(45).thenExecute(() -> {
            h.assertValueEqual(menu.getSlot(ForgeMenu.MATERIAL).getItem().getCount(), 1, "occupied output does not consume more");
            h.assertFalse(menu.quickMoveStack(player, ForgeMenu.OUTPUT).isEmpty(), "shift take returns output");
            h.assertTrue(menu.quickMoveStack(player, ForgeMenu.OUTPUT).isEmpty(), "repeat shift take cannot duplicate");
            menu.removed(player);
            int count = player.getInventory().countItem(Items.IRON_INGOT);
            menu.removed(player);
            h.assertValueEqual(player.getInventory().countItem(Items.IRON_INGOT), count, "repeated close cannot return ingredients twice");
            h.assertTrue(menu.getSlot(ForgeMenu.INPUT).getItem().isEmpty(), "close returns stored items");
            OrielGameTests.leave(h, player);
        }).thenSucceed();
    }
}
