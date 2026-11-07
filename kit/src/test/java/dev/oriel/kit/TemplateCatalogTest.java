package dev.oriel.kit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TemplateCatalogTest {
    @TempDir Path dir;

    private byte[] read(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/assets/oriel/" + name)) {
            if (in == null) throw new IOException(name);
            return in.readAllBytes();
        }
    }

    @Test void everyListedTemplateHasPackagedContent() throws Exception {
        assertEquals(IntStream.rangeClosed(1, 57).filter(i -> i != 44).mapToObj(i -> String.format("%02d", i)).toList(),
                TemplateCatalog.ids().subList(0, 56));
        assertEquals(67, TemplateCatalog.ids().size());
        for (String id : TemplateCatalog.ids()) assertTrue(read(TemplateCatalog.page(id).substring(6)).length > 100);
        assertFalse(TemplateCatalog.numbered("xianxia"));
        assertEquals("oriel:ui/templates/45/index.html", TemplateCatalog.page("45"));
        assertThrows(IllegalArgumentException.class, () -> TemplateCatalog.page("44"), "the background reference is not in the library");
        assertThrows(IllegalArgumentException.class, () -> TemplateCatalog.page("../45"));
    }

    @Test void saveCopiesEveryFileAndPreservesUserEdits() throws Exception {
        TemplateCatalog.saveNumbered(dir, "09", this::read);
        Path page = dir.resolve(TemplateCatalog.savedPath("09"));
        assertTrue(Files.isRegularFile(page.getParent().resolve("page.js")));
        assertTrue(Files.isDirectory(page.getParent().resolve("art")));
        assertTrue(Files.readString(page.getParent().resolve("template.json")).contains("locales"));
        assertArrayEquals(read("ui/templates/09/cover.png"), Files.readAllBytes(page.getParent().resolve("cover.png")));
        Files.writeString(page, "user edited");
        TemplateCatalog.saveNumbered(dir, "09", name -> { throw new IOException("must not reread"); });
        assertEquals("user edited", Files.readString(page));
    }

    @Test void localTextUsesCurrentThenEnglishChineseAndOtherLanguages() {
        var meta = new com.google.gson.JsonObject();
        var locales = new com.google.gson.JsonObject();
        meta.add("locales", locales);
        for (String locale : List.of("ja_jp", "zh_cn", "en_us")) {
            var text = new com.google.gson.JsonObject();
            text.addProperty("name", locale + " name");
            text.addProperty("description", locale + " description");
            text.addProperty("category", locale + " category");
            locales.add(locale, text);
        }
        assertEquals("ja_jp name", TemplateCatalog.localized(meta, "ja-JP").name());
        assertEquals("en_us name", TemplateCatalog.localized(meta, "fr_fr").name());
        assertEquals("en_us name", TemplateCatalog.localized(meta, "EN-US").name());
        locales.getAsJsonObject("en_us").addProperty("description", " ");
        assertEquals("zh_cn description", TemplateCatalog.localized(meta, "fr_fr").description());
        assertEquals(List.of("en_us category"), TemplateCatalog.localized(meta, "fr_fr").tags());
        locales.remove("en_us");
        assertEquals("zh_cn name", TemplateCatalog.localized(meta, "fr_fr").name());
        locales.remove("zh_cn");
        assertEquals("ja_jp name", TemplateCatalog.localized(meta, "fr_fr").name());
    }

    @Test void languageVariantsAndBlankFieldsAlsoFallBack() {
        var meta = com.google.gson.JsonParser.parseString("""
                {"id":"sample","locales":{
                  "fr_fr":{"name":"Nom","description":"","category":null},
                  "en_gb":{"name":"Name","description":"Description","category":"Category"},
                  "zh_tw":{"name":"名稱","description":"說明","category":"分類"}
                }}
                """).getAsJsonObject();
        assertEquals("Nom", TemplateCatalog.localized(meta, "fr_fr").name());
        assertEquals("Description", TemplateCatalog.localized(meta, "fr_fr").description());
        assertEquals("Category", TemplateCatalog.localized(meta, "fr_fr").category());
        assertEquals("Name", TemplateCatalog.localized(meta, "ko_kr").name());
        meta.getAsJsonObject("locales").remove("en_gb");
        assertEquals("名稱", TemplateCatalog.localized(meta, "ko_kr").name());
    }

    @Test void copiedLegacyTemplateKeepsItsLanguagesAndLocalCover() throws Exception {
        TemplateCatalog.saveCopy(dir, "xianxia", this::read);
        Path folder = dir.resolve("templates/legacy/xianxia");
        assertTrue(Files.isRegularFile(folder.resolve("template.json")));
        assertTrue(Files.isRegularFile(folder.resolve("cover.png")));
        var english = LocalPage.read(dir, folder.resolve("index.html"), "en_us");
        var chinese = LocalPage.read(dir, folder.resolve("index.html"), "zh_cn");
        assertFalse(english.title().contains("oriel.ui."));
        assertNotEquals(english.title(), chinese.title());
        assertEquals(english.title(), LocalPage.read(dir, folder.resolve("index.html"), "fr_fr").title());
        assertTrue(english.subtitle().contains("Legacy"));
        assertEquals("templates/legacy/xianxia/cover.png", english.cover());
    }

    @Test void copyFailureLeavesNoHalfPage() {
        assertThrows(IOException.class, () -> TemplateCatalog.saveNumbered(dir, "09", name -> {
            if (name.endsWith("page.js")) throw new IOException("unavailable resource");
            return read(name);
        }));
        assertFalse(Files.exists(dir.resolve("templates/09")));
        assertDoesNotThrow(() -> { try (var list = Files.list(dir)) {
            assertTrue(list.noneMatch(p -> p.getFileName().toString().startsWith(".template-")));
        }});
    }

    @Test void untrustedResourceManifestCannotEscapeTheTemplate() {
        for (String bad : List.of("../outside.html", "/absolute.html", "C:/outside.html")) {
            assertThrows(IOException.class, () -> TemplateCatalog.saveNumbered(dir, "09", name ->
                    ("[\"" + bad + "\"]").getBytes(StandardCharsets.UTF_8)));
        }
    }
}
