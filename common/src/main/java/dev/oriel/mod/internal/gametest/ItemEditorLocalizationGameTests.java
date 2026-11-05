package dev.oriel.mod.internal.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.Host;
import dev.oriel.kit.LocalizedMessage;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.net.MessagePayload;
import dev.oriel.mod.internal.net.OpenPayload;
import dev.oriel.mod.internal.net.PatchPayload;
import dev.oriel.mod.internal.server.ItemEditor;
import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.players.NameAndId;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

/** Exercise the real editor server handler and render its untranslated reply through the client's catalog path. */
public final class ItemEditorLocalizationGameTests {
    private ItemEditorLocalizationGameTests() {}

    public static void run(GameTestHelper helper) {
        List<CustomPacketPayload> sent = new ArrayList<>();
        OrielGameTests.withPlayer(helper, (player, packet) -> sent.add(packet), player -> {
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(Items.IRON_SWORD));
            var custom = new net.minecraft.nbt.CompoundTag();
            custom.putString("external_marker", "must-survive-editor");
            player.getMainHandItem().set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(custom));
            helper.assertFalse(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack()),
                    "test player must not have editor permission");
            ItemStack before = player.getMainHandItem().copy();
            helper.assertTrue(ItemEditor.open(player), "editor opens through the intercepted network");
            OpenPayload opened = (OpenPayload) sent.getLast();
            serverReply(helper, sent, opened.session(), player, "{}", "oriel.server.item_editor.no_permission",
                    "没有编辑物品的权限", "You don't have permission to edit items.");
            serverReply(helper, sent, opened.session(), player, "true", "oriel.server.item_editor.bad_data",
                    "数据格式不对", "Invalid data format.");
            helper.assertTrue(ItemStack.matches(before, player.getMainHandItem()), "rejections do not modify the item");
            var players = helper.getLevel().getServer().getPlayerList();
            NameAndId identity = new NameAndId(player.getGameProfile());
            helper.assertFalse(players.isOp(identity), "temporary test operator must not already exist");
            // GameTestServer's default operator level is not a gamemaster; request the fixture's level explicitly.
            players.op(identity, java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER),
                    java.util.Optional.of(false));
            try {
                helper.assertTrue(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack()),
                        "operator reaches real editor field validation");
                serverReply(helper, sent, opened.session(), player, "{\"rarity\":\"not-a-rarity\"}",
                        "oriel.server.item_editor.unknown_rarity", List.of("not-a-rarity"), null,
                        "未知稀有度 not-a-rarity", "Unknown rarity: not-a-rarity");
                JsonObject longName = new JsonObject();
                longName.addProperty("name", "x".repeat(ItemEditor.MAX_NAME + 1));
                serverReply(helper, sent, opened.session(), player, longName.toString(),
                        "oriel.server.item_editor.name_limit", List.of(), (long) ItemEditor.MAX_NAME,
                        "名称最多 50 个字", "Name may contain at most 50 characters.");
                helper.assertTrue(ItemStack.matches(before, player.getMainHandItem()), "argument and count rejections are atomic");
                for (String name : List.of("first-save", "second-save")) {
                    int packets = sent.size();
                    JsonObject edit = new JsonObject();
                    edit.addProperty("name", name);
                    dev.oriel.mod.server.OrielServer.handleMessage(player, new MessagePayload(opened.session(), "save", edit.toString()));
                    helper.assertValueEqual(sent.size(), packets + 1, "successful save sends fresh result");
                    PatchPayload patch = (PatchPayload) sent.getLast();
                    JsonObject result = JsonParser.parseString(patch.value()).getAsJsonObject();
                    helper.assertValueEqual(result.get("status").getAsString(), "saved", "consecutive saves update the server snapshot");
                    helper.assertValueEqual(result.getAsJsonObject("item").get("name").getAsString(), name, "result contains saved item name");
                    helper.assertValueEqual(player.getMainHandItem().getHoverName().getString(), name, "authoritative item matches success result");
                    helper.assertValueEqual(player.getMainHandItem().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
                            before.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA), "unmanaged custom component preserved");
                }
                int slot = player.getInventory().getSelectedSlot();
                player.getInventory().setSelectedSlot((slot + 1) % 9);
                serverReply(helper, sent, opened.session(), player, "{}", "oriel.server.item_editor.wrong_slot",
                        "你换了快捷栏格子，请重新打开编辑器", "Your selected hotbar slot changed. Reopen the editor.");
                player.getInventory().setSelectedSlot(slot);
                player.getMainHandItem().setDamageValue(1);
                ItemStack changed = player.getMainHandItem().copy();
                serverReply(helper, sent, opened.session(), player, "{}", "oriel.server.item_editor.item_changed",
                        "手里的物品已经变了，请重新打开编辑器", "The item in your hand changed. Reopen the editor.");
                helper.assertTrue(ItemStack.matches(changed, player.getMainHandItem()), "stale edit does not overwrite external changes");
                dev.oriel.mod.server.OrielServer.handleClosed(player,
                        new dev.oriel.mod.internal.net.ClosedPayload(opened.session()));
                int packets = sent.size();
                dev.oriel.mod.server.OrielServer.handleMessage(player, new MessagePayload(opened.session(), "save", "{}"));
                helper.assertValueEqual(sent.size(), packets, "closed editor ignores later saves");
                helper.assertTrue(ItemStack.matches(changed, player.getMainHandItem()), "cancel leaves the held item unchanged");
            } finally {
                players.deop(identity);
            }
            dev.oriel.mod.server.OrielServer.onPlayerLeft(player);
        });
        helper.succeed();
    }

    private static void serverReply(GameTestHelper helper, List<CustomPacketPayload> sent, int session,
                                         net.minecraft.server.level.ServerPlayer player, String input, String expectedKey,
                                         String chinese, String english) {
        serverReply(helper, sent, session, player, input, expectedKey, List.of(), null, chinese, english);
    }

    private static void serverReply(GameTestHelper helper, List<CustomPacketPayload> sent, int session,
                                         net.minecraft.server.level.ServerPlayer player, String input, String expectedKey,
                                         List<String> args, Long count, String chinese, String english) {
        int packets = sent.size();
        dev.oriel.mod.server.OrielServer.handleMessage(player, new MessagePayload(session, "save", input));
        helper.assertValueEqual(sent.size(), packets + 1, "each save produces a fresh rejection");
        PatchPayload patch = (PatchPayload) sent.getLast();
        var buffer = Unpooled.buffer();
        try {
            PatchPayload.STREAM_CODEC.encode(buffer, patch);
            patch = PatchPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(buffer.readableBytes(), 0, "rejection codec consumes all bytes");
        } finally { buffer.release(); }
        helper.assertValueEqual(patch.key(), "result", "editor sends result patch");
        JsonObject reply = JsonParser.parseString(patch.value()).getAsJsonObject();
        helper.assertValueEqual(reply.get("status").getAsString(), "rejected", "server rejection status");
        JsonObject reason = reply.getAsJsonObject("reason");
        helper.assertValueEqual(reason.get("key").getAsString(), expectedKey, "server carries key rather than translated text");
        LocalizedMessage message = LocalizedMessage.fromJson(reason);
        helper.assertValueEqual(message.args(), args, "wire arguments preserved");
        helper.assertTrue(java.util.Objects.equals(message.count(), count), "wire plural count preserved");
        helper.assertFalse(patch.value().contains(chinese) || patch.value().contains(english), "wire reply is language-neutral");
        for (String locale : List.of("zh_cn", "en_us")) {
            Host host = new Host() {
                @Override public FontMetrics fonts() { return (text, font) -> 0; }
                @Override public String loadText(String url) { return null; }
                @Override public String language() { return locale; }
            };
            Document document = Document.create(host, "oriel:test/item-editor-localization");
            try {
                String rendered = LocalizedMessage.fromJson(reason).render(document);
                helper.assertValueEqual(rendered, locale.equals("zh_cn") ? chinese : english, "client message in " + locale);
                Constants.LOG.info("ORIEL_ITEM_EDITOR_I18N PASS {} {}: {}", locale, expectedKey, rendered);
            } finally { document.close(); }
        }
    }
}
