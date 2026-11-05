package dev.oriel.kit.bind;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Event;
import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.Host;
import dev.oriel.engine.host.PageControllers;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Runnable, headless Java controller example; the fixture is never registered in the gallery. */
class BindingExampleTest {
    @Test void loadsBindsLaysOutAndHandlesCommand() throws IOException {
        ViewModel vm = new ViewModel(Map.of(
                "title", "Inventory", "visible", true, "active", true, "tip", "Local preview",
                "rows", List.of(Map.of("id", "iron", "label", "Iron")),
                "save", (ViewModel.Command) event -> {}));
        vm.set("save", (ViewModel.Command) event -> vm.set("title", "Saved locally"));
        String url = "example-bind:ui/minimal.html";
        PageControllers.register(url, () -> doc -> Bindings.attach(doc, vm));
        try (InputStream input = getClass().getResourceAsStream("/bind/minimal.html")) {
            assertNotNull(input);
            Host host = new Host() {
                @Override public FontMetrics fonts() { return (text, font) -> text.length() * 6; }
                @Override public String loadText(String location) { return null; }
            };
            Document doc = Document.parse(host, url, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            try {
                doc.setViewport(427, 240, 1);
                doc.frame(0);
                assertEquals("Inventory", doc.getElementsByTagName("h1").get(0).textContent());
                assertEquals("Iron", doc.getElementsByTagName("li").get(0).textContent());
                assertTrue(doc.getElementById("status").hasClass("active"));
                assertEquals("Local preview", doc.getElementById("status").getAttribute("title"));
                assertNotNull(doc.getElementsByTagName("h1").get(0).box);
                doc.getElementById("save").dispatchEvent(new Event("click"));
                doc.frame(16);
                assertEquals("Saved locally", doc.getElementsByTagName("h1").get(0).textContent());
                assertTrue(doc.diagnostics().all().isEmpty(), () -> doc.diagnostics().all().toString());
            } finally { doc.close(); }
        }
    }
}
