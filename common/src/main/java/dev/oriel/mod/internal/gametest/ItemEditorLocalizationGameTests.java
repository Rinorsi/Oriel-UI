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

import java.util.ArrayList;
import java.util.List;

/** Exercise the real editor server handler and render its untranslated reply through the client's catalog path. */
public final class ItemEditorLocalizationGameTests {
    private ItemEditorLocalizationGameTests() {}

    public static void run(GameTestHelper helper) {
        List<CustomPacketPayload> sent = new ArrayList<>();
        OrielGameTests.withPlayer(helper, (player, packet) -> sent.add(packet), player -> {
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(Items.IRON_SWORD));
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
            dev.oriel.mod.server.OrielServer.onPlayerLeft(player);
        });
        helper.succeed();
    }

    private static void serverReply(GameTestHelper helper, List<CustomPacketPayload> sent, int session,
                                         net.minecraft.server.level.ServerPlayer player, String input, String expectedKey,
                                         String chinese, String english) {
        dev.oriel.mod.server.OrielServer.handleMessage(player, new MessagePayload(session, "save", input));
        PatchPayload patch = (PatchPayload) sent.getLast();
        helper.assertValueEqual(patch.key(), "result", "editor sends result patch");
        JsonObject reply = JsonParser.parseString(patch.value()).getAsJsonObject();
        helper.assertValueEqual(reply.get("status").getAsString(), "rejected", "server rejection status");
        JsonObject reason = reply.getAsJsonObject("reason");
        helper.assertValueEqual(reason.get("key").getAsString(), expectedKey, "server carries key rather than translated text");
        helper.assertTrue(reason.getAsJsonArray("args").isEmpty(), "no unnecessary arguments");
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
