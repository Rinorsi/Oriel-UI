package dev.oriel.kit.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.engine.dom.Document;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Lang;
import dev.oriel.kit.LocalizedMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native/server message keys use the same catalogs as pages; server replies remain language-neutral. */
class BackendLocalizationTest {
    @Test void everyBackendMessageReferenceHasCatalogEntries() throws java.io.IOException {
        KitHost host = new KitHost();
        JsonObject zh = JsonParser.parseString(host.loadText("oriel:lang/zh_cn.json")).getAsJsonObject();
        JsonObject en = JsonParser.parseString(host.loadText("oriel:lang/en_us.json")).getAsJsonObject();
        var pattern = java.util.regex.Pattern.compile("oriel[.](?:server[.]item_editor|dev[.](?:reload|acceptance))[.][a-z0-9_.]+");
        int found = 0;
        for (String source : List.of("server/ItemEditor.java", "client/DevReload.java", "client/DevAutopilot.java")) {
            String sourceText = java.nio.file.Files.readString(java.nio.file.Path.of("../common/src/main/java/dev/oriel/mod/internal/" + source));
            var matcher = pattern.matcher(sourceText);
            while (matcher.find()) {
                String key = matcher.group(); found++;
                if (zh.has(key)) assertTrue(en.has(key), key);
                else for (String suffix : List.of(".one", ".other")) {
                    assertTrue(zh.has(key + suffix), key + suffix);
                    assertTrue(en.has(key + suffix), key + suffix);
                }
            }
        }
        assertTrue(found >= 60, "scan must cover the real backend sources");
    }

    @Test void backendKeysExistInBothCatalogs() {
        KitHost host = new KitHost();
        for (String locale : List.of("zh_cn", "en_us")) {
            JsonObject messages = JsonParser.parseString(host.loadText("oriel:lang/" + locale + ".json")).getAsJsonObject();
            for (String key : List.of("oriel.server.item_editor.no_permission", "oriel.server.item_editor.empty_hand",
                    "oriel.server.item_editor.name_limit.other", "oriel.dev.reload.compiling", "oriel.dev.acceptance.ready"))
                assertTrue(messages.has(key), locale + ": " + key);
        }
    }

    @Test void editorResolvesTheSameRejectionInTheReadersLanguage() {
        String reply = """
                {"status":"rejected","reason":{"key":"oriel.server.item_editor.no_permission","args":[]}}
                """;
        for (String locale : List.of("zh_cn", "en_us")) {
            KitHost host = new KitHost();
            host.language = locale;
            Document doc = host.open(OrielKit.ITEM_EDITOR, new ItemEditorPreset(), 640, 360);
            doc.patch("result", reply);
            assertEquals(locale.equals("zh_cn") ? "服务器拒绝了：没有编辑物品的权限"
                    : "Server rejected it: You don't have permission to edit items.", doc.getElementById("status").textContent());
            doc.close();
        }
    }

    @Test void rejectionCountsAndArgumentsStayUntranslatedUntilRead() {
        KitHost host = new KitHost();
        Document doc = host.open(OrielKit.ITEM_EDITOR, new ItemEditorPreset(), 640, 360);
        LocalizedMessage limit = LocalizedMessage.plural("oriel.server.item_editor.name_limit", 50);
        assertEquals(limit, LocalizedMessage.fromJson(limit.json()));
        assertEquals("名称最多 50 个字", limit.render(doc));
        host.language = "en_us";
        assertEquals("Name may contain at most 50 characters.", limit.render(doc));
        assertEquals("Name may contain at most 1 character.", LocalizedMessage.plural(limit.key(), 1).render(doc));
        assertEquals("Unknown rarity: 自定义", LocalizedMessage.text("oriel.server.item_editor.unknown_rarity", "自定义").render(doc));
        assertFalse(limit.json().toString().contains("名称"));
        doc.close();
    }

    @Test void codecFailureTranslationDoesNotRewriteUserComponentStrings() {
        KitHost host = new KitHost(); host.language = "en_us";
        Document doc = host.open(OrielKit.ITEM_EDITOR, new ItemEditorPreset());
        JsonObject item = new JsonObject();
        var value = new com.google.gson.JsonPrimitive("oriel.server.item_editor.unencodable");
        assertSame(value, LocalizedMessage.componentValue(doc, item, "test:user", value));
        var failed = new com.google.gson.JsonArray();
        failed.add("test:failed");
        item.add("unencodableComponents", failed);
        assertSame(value, LocalizedMessage.componentValue(doc, item, "test:user", value));
        assertEquals("(Cannot encode)", LocalizedMessage.componentValue(doc, item, "test:failed", value).getAsString());
        doc.close();
    }

    @Test void reloadMessageGrammarPreservesChineseAndSupportsEnglishCounts() {
        KitHost host = new KitHost();
        Document doc = host.open(OrielKit.ITEM_EDITOR, new ItemEditorPreset());
        assertEquals("mod.toml 等 3 个文件 改了：模组信息只在启动时读取，需要重启游戏",
                Lang.plural(doc, "oriel.dev.reload.restart_files", 3, "mod.toml"));
        assertEquals("已热替换 2 个类，新增 1 个类，页面已重新打开", Lang.t(doc, "oriel.dev.reload.done_added",
                Lang.plural(doc, "oriel.dev.reload.replaced_classes", 2), Lang.plural(doc, "oriel.dev.reload.added_classes", 1)));
        host.language = "en_us";
        assertEquals("Hot-swapped 2 classes; Added 1 class; pages reopened.", Lang.t(doc, "oriel.dev.reload.done_added",
                Lang.plural(doc, "oriel.dev.reload.replaced_classes", 2), Lang.plural(doc, "oriel.dev.reload.added_classes", 1)));
        assertEquals("1 resource file (icons)", Lang.plural(doc, "oriel.dev.reload.resources", 1, "icons"));
        assertEquals("2 resource files (icons)", Lang.plural(doc, "oriel.dev.reload.resources", 2, "icons"));
        doc.close();
    }
}
