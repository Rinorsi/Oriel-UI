package dev.oriel.engine.host;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Java controllers attach to the page registered for their URL, before DOMContentLoaded, once per load. */
class PageControllersTest {
    @Test
    void attachesToItsPageBeforeContentLoaded() {
        List<String> order = new ArrayList<>();
        PageControllers.register("test:controlled.html", () -> doc -> {
            order.add("attach");
            doc.addEventListener("DOMContentLoaded", e -> order.add("loaded"));
            Element b = doc.getElementById("b");
            b.addEventListener("click", e -> b.setTextContent("clicked"));
        });
        TestHost host = new TestHost();
        host.resource("test:controlled.html", "<button id=b>go</button>");
        Document doc = Document.parse(host, "test:controlled.html", host.resources.get("test:controlled.html"));
        assertEquals(List.of("attach", "loaded"), order);
        doc.getElementById("b").click();
        assertEquals("clicked", doc.getElementById("b").textContent());
    }

    @Test
    void otherPagesAndReloadsGetTheirOwn() {
        List<Document> attached = new ArrayList<>();
        PageControllers.register("test:counted.html", () -> attached::add);
        TestHost host = new TestHost();
        Document.parse(host, "test:other.html", "<p>x</p>");
        assertTrue(attached.isEmpty());
        Document first = Document.parse(host, "test:counted.html", "<p>x</p>");
        Document second = Document.parse(host, "test:counted.html", "<p>x</p>");
        assertEquals(List.of(first, second), attached);
        assertTrue(PageControllers.has("test:counted.html"));
    }

    @Test
    void aThrowingControllerStopsOnlyItsPage() {
        PageControllers.register("test:broken.html", () -> doc -> {
            throw new IllegalStateException("broken controller");
        });
        TestHost host = new TestHost().recordErrors();
        Document doc = Document.parse(host, "test:broken.html", "<p>x</p>");
        assertNotNull(doc.error());
    }
}
