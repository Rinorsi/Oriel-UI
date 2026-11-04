package dev.oriel.kit.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.dom.Text;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Lang;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class LocalizationTest {
    private static final List<String> PAGES = List.of("gallery", "inventory", "forge", "music", "settings",
            "editor", "catalog", "icons", "item_editor");
    private static final List<Supplier<PageControllers.Controller>> CONTROLLERS = List.of(GalleryPreset::new,
            InventoryPreset::new, ForgePreset::new, MusicPreset::new, SettingsPreset::new,
            EditorPreset::new, CatalogPreset::new, IconsPreset::new, ItemEditorPreset::new);

    @Test void languageCatalogsHaveIdenticalNonemptyKeys() {
        KitHost host = new KitHost();
        JsonObject zh = JsonParser.parseString(host.loadText("oriel:lang/zh_cn.json")).getAsJsonObject();
        JsonObject en = JsonParser.parseString(host.loadText("oriel:lang/en_us.json")).getAsJsonObject();
        assertEquals(zh.keySet(), en.keySet());
        assertTrue(zh.size() > 100);
        for (String key : zh.keySet()) {
            assertFalse(zh.get(key).getAsString().isBlank(), key);
            assertFalse(en.get(key).getAsString().isBlank(), key);
            assertEquals(placeholders(zh.get(key).getAsString()), placeholders(en.get(key).getAsString()), key);
        }
    }

    @Test void libraryDefaultsAlsoContainTheSharedNativeUiMessages() {
        KitHost host = new KitHost();
        Document doc = host.open(OrielKit.GALLERY, new GalleryPreset());
        assertEquals("优易库", Lang.t(doc, "oriel.screen"));
        host.language = "en_us";
        assertEquals("Oriel", Lang.t(doc, "oriel.screen"));
        doc.close();
    }

    @Test void chineseBindingsPreserveTheEntireSourceTreeIncludingHiddenExamples() {
        OrielKit.register();
        KitHost host = new KitHost();
        for (String page : PAGES) {
            String html = host.loadText(OrielKit.page(page));
            String url = "oriel:ui/__translation_source_" + page + ".html";
            Document source = Document.create(host, url);
            source.removeAllChildren();
            dev.oriel.engine.html.HtmlParser.parseInto(source, html);
            Document translated = Document.parse(host, url, html);
            assertSameTree(source, translated);
            source.close(); translated.close();
        }
    }

    @Test void allPresetTextAndLabelsResolveInBothLanguages() {
        for (String language : List.of("zh_cn", "en_us")) {
            KitHost host = new KitHost(); host.language = language;
            for (int i = 0; i < PAGES.size(); i++) {
                Document doc = host.open(OrielKit.page(PAGES.get(i)), CONTROLLERS.get(i).get());
                for (int frame = 1; frame <= 60; frame++) doc.frame(frame * 16);
                assertEquals(0, doc.diagnostics().count(Diagnostic.Severity.WARNING),
                        language + " " + PAGES.get(i) + ": " + doc.diagnostics().all());
                assertNoKeys(doc.body());
                if (language.equals("en_us")) assertEnglishFixture(doc.body());
                doc.close();
            }
        }
    }

    @Test void documentLanguageAndReloadedControllerUseTheNewCatalog() {
        KitHost host = new KitHost();
        Document zh = host.open(OrielKit.SETTINGS, new SettingsPreset());
        String before = Lang.t(zh, "oriel.ui.settings.video");
        host.language = "en_us";
        Document en = host.open(OrielKit.SETTINGS, new SettingsPreset());
        assertNotEquals(before, Lang.t(en, "oriel.ui.settings.video"));
        assertNoKeys(en.body());
        zh.close(); en.close();
    }

    @Test void settingsCarryStableChoicesAcrossALanguageReloadAndPreserveUserText() {
        KitHost host = new KitHost();
        SettingsPreset before = new SettingsPreset();
        Document zh = host.open(OrielKit.SETTINGS, before);
        before.values.put("graphics", "极佳");
        before.values.put("name", "玩家自定义名字");
        before.values.put("render", 16f);
        before.show("外观", false);
        host.language = "en_us";
        Object saved = before.saveState();
        SettingsPreset after = new SettingsPreset();
        Document en = host.open(OrielKit.SETTINGS, after);
        Object defaultPrefix = after.values.get("prefix");
        after.restoreState(saved);
        assertNotEquals("极佳", after.values.get("graphics"));
        assertEquals("玩家自定义名字", after.values.get("name"));
        assertEquals(16f, after.values.get("render"));
        assertEquals(defaultPrefix, after.values.get("prefix"));
        assertNotEquals("外观", after.tab);
        zh.close(); en.close();
    }

    @Test void musicKeepsPlaybackWhileItsFixtureNamesChangeLanguage() {
        KitHost host = new KitHost();
        MusicPreset before = new MusicPreset();
        Document zh = host.open(OrielKit.MUSIC, before);
        before.current = 2; before.position = 12; before.volume = 0.5;
        Object saved = before.saveState();
        host.language = "en_us";
        MusicPreset after = new MusicPreset();
        Document en = host.open(OrielKit.MUSIC, after);
        after.restoreState(saved);
        assertEquals(2, after.current);
        assertEquals(12, after.position);
        assertEquals(0.5, after.volume);
        zh.close(); en.close();
    }

    @Test void inventoryFixturesRelocalizeWithoutLosingTheirSlotState() {
        KitHost host = new KitHost();
        InventoryPreset before = new InventoryPreset();
        Document zh = host.open(OrielKit.INVENTORY, before);
        before.slots[6] = before.slots[4]; before.slots[4] = null;
        Object saved = before.saveState();
        host.language = "en_us";
        InventoryPreset after = new InventoryPreset();
        Document en = host.open(OrielKit.INVENTORY, after);
        after.restoreState(saved);
        assertEquals("minecraft:torch", after.slots[6].id());
        assertTrue(en.body().textContent().contains(Lang.t(en, "oriel.ui.inventory.sharpness_v_diamond_sword")));
        zh.close(); en.close();
    }

    @Test void itemEditorKeepsUnsavedUserTextAcrossLanguageReloads() {
        KitHost host = new KitHost();
        ItemEditorPreset before = new ItemEditorPreset();
        Document zh = host.open(OrielKit.ITEM_EDITOR, before);
        before.edit("name", new com.google.gson.JsonPrimitive("我的试验剑"));
        Object saved = before.saveState();
        host.language = "en_us";
        ItemEditorPreset after = new ItemEditorPreset();
        Document en = host.open(OrielKit.ITEM_EDITOR, after);
        after.restoreState(saved);
        assertEquals("我的试验剑", after.work.get("name").getAsString());
        assertNotEquals("钻石剑", after.baseline.get("defaultName").getAsString());
        zh.close(); en.close();
    }

    private static void assertNoKeys(Node node) {
        if (node instanceof Text t) assertFalse(t.data().contains("oriel.ui."), t.data());
        if (node instanceof Element e) {
            for (String attr : List.of("title", "placeholder", "value", "alt", "aria-label")) {
                String value = e.getAttribute(attr);
                assertTrue(value == null || !value.contains("oriel.ui."), e + " " + attr + ": " + value);
            }
        }
        for (Node child : node.childNodes()) assertNoKeys(child);
    }

    private static void assertEnglishFixture(Node node) {
        if (node instanceof Element e && "effects".equals(e.id())) return; // Lead-owned potion component.
        if (node instanceof Text text) {
            assertFalse(text.data().codePoints().anyMatch(c -> c >= 0x3400 && c <= 0x9FFF), text.data());
        }
        for (Node child : node.childNodes()) assertEnglishFixture(child);
    }

    private static List<String> placeholders(String text) {
        return java.util.regex.Pattern.compile("%(?:[0-9]+[$])?s").matcher(text).results()
                .map(java.util.regex.MatchResult::group).sorted().toList();
    }

    private static String sourceValue(Node node, String text) {
        String key = text.strip();
        return key.startsWith("oriel.ui.") && !key.contains(" ")
                ? text.replace(key, node.ownerDocument().messages().text(key)) : text;
    }

    private static void assertSameTree(Node source, Node translated) {
        assertEquals(source.getClass(), translated.getClass());
        if (source instanceof Element a && translated instanceof Element b) {
            assertEquals(a.tagName(), b.tagName());
            assertEquals(a.attributes().keySet(), b.attributes().keySet());
            a.attributes().forEach((key, value) -> assertEquals(key.startsWith("data-i18n") ? value : sourceValue(a, value), b.getAttribute(key), key));
        }
        if (source instanceof Text a && translated instanceof Text b) assertEquals(sourceValue(source, a.data()), b.data());
        assertEquals(source.childCount(), translated.childCount(), source.toString());
        for (int i = 0; i < source.childCount(); i++) assertSameTree(source.childAt(i), translated.childAt(i));
    }
}
