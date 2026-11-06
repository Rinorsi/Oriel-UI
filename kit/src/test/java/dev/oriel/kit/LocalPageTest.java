package dev.oriel.kit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalPageTest {
    @TempDir Path root;

    private Path write(String file, String text) throws IOException {
        Path p = root.resolve(file);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
        return p;
    }

    @Test void titleComesFromTheTitleTagElseTheFolderElseTheFileName() throws IOException {
        assertEquals("Item Editor", LocalPage.read(root, write("a/index.html", "<html><head><title> Item <b>Editor</b>\n</title>")).title());
        assertEquals("item-editor", LocalPage.read(root, write("item-editor/index.html", "<html>")).title());
        assertEquals("shop", LocalPage.read(root, write("shop.html", "<html>")).title());
        assertEquals("index", LocalPage.read(root, write("index.html", "<html>")).title());
    }

    @Test void coverIsTheMetaImageElseCoverPngElseTheSameNamePng() throws IOException {
        write("p/img/c.png", "x");
        assertEquals("p/img/c.png", LocalPage.read(root, write("p/index.html", "<meta name=\"oriel-cover\" content=\"img/c.png\">")).cover());
        write("q/cover.png", "x");
        assertEquals("q/cover.png", LocalPage.read(root, write("q/index.html", "<html>")).cover());
        write("r/hud.png", "x");
        assertEquals("r/hud.png", LocalPage.read(root, write("r/hud.html", "<html>")).cover());
        assertEquals("", LocalPage.read(root, write("s/index.html", "<html>")).cover());
    }

    @Test void subtitleIsTheOrielMetaElseTheDescriptionAndTextIsCut() throws IOException {
        assertEquals("Mine", LocalPage.read(root, write("a.html", "<meta name=\"description\" content=\"Plain\"><meta name=\"oriel-subtitle\" content=\"Mine\">")).subtitle());
        assertEquals("Plain", LocalPage.read(root, write("b.html", "<meta name=\"description\" content=\"Plain\">")).subtitle());
        assertEquals("", LocalPage.read(root, write("c.html", "<html>")).subtitle());
        String longText = "x".repeat(200);
        LocalPage.Info info = LocalPage.read(root, write("d.html", "<title>" + longText + "</title><meta name=\"oriel-subtitle\" content=\"" + longText + "\">"));
        assertEquals(LocalPage.TITLE_MAX, info.title().length());
        assertEquals(LocalPage.SUBTITLE_MAX, info.subtitle().length());
        assertEquals("…", info.title().substring(info.title().length() - 1));
    }

    @Test void coverMustBeAPngInsideTheFolder() throws IOException {
        write("outside.png", "x");
        Path inner = root.resolve("in");
        Files.createDirectories(inner);
        write("in/index.html", "<meta name='oriel-cover' content='../outside.png'>");
        assertEquals("", LocalPage.read(inner, inner.resolve("index.html")).cover());
        write("in/pic.jpg", "x");
        write("in/two.html", "<meta name=\"oriel-cover\" content=\"pic.jpg\">");
        assertEquals("", LocalPage.read(root, inner.resolve("two.html")).cover());
    }
}
