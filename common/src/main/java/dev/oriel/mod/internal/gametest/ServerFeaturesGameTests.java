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
        GameTests.add("item_editor_localization", ItemEditorLocalizationGameTests::run);
        GameTests.add("runtime_version", h -> {
            String version = dev.oriel.mod.internal.platform.Services.PLATFORM.modVersion("oriel");
            h.assertFalse(version.isBlank(), "loader supplies the running library version");
            try {
                int result = h.getLevel().getServer().getCommands().getDispatcher().execute("oriel version",
                        h.getLevel().getServer().createCommandSourceStack());
                h.assertValueEqual(result, 1, "version command executes");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
            h.succeed();
        });
        GameTests.add("protocol_compatibility", h -> {
            h.assertFalse(OrielNetwork.compatible(type -> false), "missing client rejected");
            h.assertFalse(OrielNetwork.compatible(type -> type != dev.oriel.mod.internal.net.ProtocolPayload.TYPE), "old or mismatched protocol rejected");
            h.assertTrue(OrielNetwork.compatible(type -> true), "matching complete capabilities accepted");
            h.assertFalse(OrielNetwork.compatible(type -> type != dev.oriel.mod.internal.net.PatchPayload.TYPE), "incomplete protocol rejected");
            for (boolean installed : new boolean[] {false, true}) {
                String reason = dev.oriel.mod.internal.net.ProtocolMessages.rejection(installed);
                h.assertTrue(reason.contains("Oriel UI") && reason.contains("优易库") && reason.contains(OrielNetwork.PROTOCOL_VERSION),
                        "language resources provide bilingual missing/version rejection");
            }
            h.succeed();
        });
        GameTests.add("unsupported_client", ServerFeaturesGameTests::unsupported);
        GameTests.add("player_isolation", ServerFeaturesGameTests::isolation);
        GameTests.add("forge_menu", ServerFeaturesGameTests::forge);
        ItemEditorComponentsGameTests.init();
        GameTests.add("forge_workstation", ServerFeaturesGameTests::workstation);
    }

    private static void workstation(GameTestHelper h) {
        OrielGameTests.withPlayers(h, (p, packet) -> {}, (a, b) -> {
            var pos = a.blockPosition().below();
            var block = net.minecraft.world.level.block.Blocks.SMITHING_TABLE;
            var previousBlock = h.getLevel().getBlockState(pos);
            h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
            var station = new ForgeMenu.Workstation(h.getLevel(), pos, block, 4);
            ForgeMenu first = new ForgeMenu(10, a.getInventory(), station);
            ForgeMenu second = new ForgeMenu(11, b.getInventory(), station);
            try {
                h.assertTrue(first.stillValid(a), "owner next to live workstation is valid");
                h.assertFalse(first.stillValid(b), "another player cannot use this menu");
                first.removed(b);
                h.assertTrue(first.stillValid(a), "another player cannot close this menu");
                var otherLevel = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
                if (otherLevel != null)
                    h.assertFalse(new ForgeMenu.Workstation(otherLevel, pos, block, 4).stillValid(a), "another dimension is invalid");
                for (double distance : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
                    h.assertTrue(OrielGameTests.throwsOn(() -> new ForgeMenu.Workstation(h.getLevel(), pos, block, distance),
                            IllegalArgumentException.class), "invalid configured distance rejected");
                }
                first.getSlot(ForgeMenu.INPUT).set(sword());
                first.getSlot(ForgeMenu.MATERIAL).set(new ItemStack(Items.IRON_INGOT, 2));
                second.getSlot(ForgeMenu.MATERIAL).set(new ItemStack(Items.DIAMOND, 3));
                first.broadcastChanges();
                h.assertValueEqual(second.pageData().get("progress").getAsInt(), 0, "progress belongs to one player");
                h.assertValueEqual(second.getSlot(ForgeMenu.MATERIAL).getItem().getCount(), 3, "inventory belongs to one player");
                a.setPos(pos.getX() + 20, pos.getY() + 1, pos.getZ());
                h.assertFalse(first.stillValid(a), "walking away invalidates station");
                h.assertTrue(first.quickMoveStack(a, ForgeMenu.INPUT).isEmpty(), "invalid menu cannot transfer items");
                a.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                h.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                h.assertFalse(first.stillValid(a), "removed workstation invalidates menu");
                h.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                h.assertFalse(first.stillValid(a), "replacement must have the expected block type");
                h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
                h.assertTrue(first.stillValid(a), "same block type is valid again");
                a.setHealth(0);
                h.assertFalse(first.stillValid(a), "dead owner cannot use the menu");
                a.setHealth(a.getMaxHealth());
                first.removed(a);
                h.assertFalse(first.stillValid(a), "closed menu stays invalid");
                h.assertTrue(second.stillValid(b), "closing one player leaves another menu alive");
                h.assertValueEqual(second.getSlot(ForgeMenu.MATERIAL).getItem().getCount(), 3, "close cannot drain another player");
                dev.oriel.mod.internal.server.OrielDemos.openChest(a, station);
                dev.oriel.mod.internal.server.OrielDemos.openChest(b, station);
                var chestA = a.containerMenu;
                var chestB = b.containerMenu;
                h.assertFalse(chestA.stillValid(b), "sample chest checks its owner");
                chestA.getSlot(0).set(ItemStack.EMPTY);
                h.assertFalse(chestB.getSlot(0).getItem().isEmpty(), "sample chests have separate inventories");
                a.setPos(pos.getX() + 20, pos.getY() + 1, pos.getZ());
                h.assertFalse(chestA.stillValid(a), "sample chest checks configured distance");
                a.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                h.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                h.assertFalse(chestA.stillValid(a), "sample chest checks block survival");
                a.closeContainer();
                b.closeContainer();
                h.assertFalse(chestA.stillValid(a), "closed sample chest stays invalid");
            } finally {
                first.removed(a);
                second.removed(b);
                a.closeContainer();
                b.closeContainer();
                h.getLevel().setBlockAndUpdate(pos, previousBlock);
            }
        });
        h.succeed();
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
