package dev.oriel.engine.host;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HotReloadTest {
    @TempDir Path dir;

    @Test void cssReloadKeepsNodesFocusInputScrollAndListeners() {
        TestHost host = new TestHost().resource("test:main.css", "@import 'colors.css';");
        host.resource("test:colors.css", "input { color:red }");
        var page = host.load("<link rel=stylesheet href='main.css'><div id=s style='height:30px;overflow:auto'><input id=i><p style='height:200px'>text</p></div>");
        Document doc = page.doc;
        Element input = page.byId("i"), scroll = page.byId("s");
        input.setValue("edited 中文");
        input.focus();
        page.key("Home");
        var shift = new dev.oriel.engine.event.Modifiers(true, false, false, false);
        page.key("ArrowRight", shift);
        page.key("ArrowRight", shift);
        Object control = input.controlState;
        page.run("var closureCount = 0; document.getElementById('i').addEventListener('change', () => closureCount++);");
        scroll.scrollTo(0, 20);
        int[] events = {0};
        input.addEventListener("change", e -> events[0]++);
        int version = doc.domVersion();
        host.resource("test:colors.css", "input { color:blue }");
        HotReload.apply(doc, List.of(Path.of("colors.css")), () -> fail("CSS rebuilt the DOM"));
        page.frame();
        assertSame(input, page.byId("i"));
        assertEquals(version, doc.domVersion());
        assertTrue(input.isFocused());
        assertEquals("edited 中文", input.value());
        assertSame(control, input.controlState);
        assertEquals(20f, scroll.scrollTop());
        assertEquals(0xFF0000FF, input.style.color);
        input.dispatchEvent(new dev.oriel.engine.event.Event("change", false, false));
        assertEquals(1, events[0]);
        assertEquals("1", page.eval("String(closureCount)"));
        page.type("X");
        assertEquals("Xited 中文", input.value(), "the selected first two characters survive CSS reload");
        assertNull(doc.error());
    }

    @Test void nonCssAndMixedChangesRequestTheHostsFullReload() {
        Document document = new TestHost().load("<p>old</p>").doc;
        for (var changed : List.of(
                List.of(Path.of("page.html")), List.of(Path.of("page.js")),
                List.of(Path.of("fixture.json")), List.of(Path.of("style.css"), Path.of("page.html")))) {
            int[] reloads = {0};
            HotReload.apply(document, changed, () -> reloads[0]++);
            assertEquals(1, reloads[0], changed.toString());
        }
        assertFalse(HotReload.cssOnly(List.of()));
    }

    @Test void watcherReturnsCreatedDeletedAndModifiedPaths() throws Exception {
        Path css = dir.resolve("main.css"), html = dir.resolve("page.html");
        Files.writeString(css, "a{}"); Files.writeString(html, "<p>");
        FileStamps stamps = new FileStamps();
        assertTrue(stamps.changedPaths(List.of(css, html)).isEmpty());
        Files.setLastModifiedTime(css, FileTime.fromMillis(Files.getLastModifiedTime(css).toMillis()+2000));
        assertEquals(java.util.Set.of(css), stamps.changedPaths(List.of(css, html)));
        Files.delete(css);
        assertEquals(java.util.Set.of(css), stamps.changedPaths(List.of(css, html)));
        Files.writeString(css, "b{}");
        assertEquals(java.util.Set.of(css), stamps.changedPaths(List.of(css, html)));
        assertTrue(stamps.changedPaths(List.of(css, html)).isEmpty());
    }
}
