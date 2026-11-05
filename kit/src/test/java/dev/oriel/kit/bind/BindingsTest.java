package dev.oriel.kit.bind;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.event.Event;
import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.Host;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("try")
class BindingsTest {
    private static Document page(String body) {
        return Document.parse(new Host() {
            @Override public FontMetrics fonts() { return (text, font) -> text.length() * 6; }
            @Override public String loadText(String url) { return null; }
            @Override public void diagnostic(Diagnostic diagnostic) {}
        }, "test:ui/bind.html", "<!doctype html><html><head></head><body>\n" + body + "</body></html>");
    }

    @Test void textClassAttributeAndEventUpdateLocally() {
        AtomicInteger calls = new AtomicInteger();
        ViewModel vm = new ViewModel(Map.of("player", Map.of("name", "Alex"), "active", true,
                "tip", "Hello", "save", (ViewModel.Command) event -> calls.incrementAndGet()));
        Document doc = page("<p id='name' o-text='player.name'></p>"
                + "<button id='save' class='base' o-class:active='active' o-attr:title='tip' o-on:click='save'>Save</button>");
        try (Bindings bindings = Bindings.attach(doc, vm)) {
            Element name = doc.getElementById("name"), save = doc.getElementById("save");
            assertEquals("Alex", name.textContent());
            assertTrue(save.hasClass("active"));
            assertEquals("Hello", save.getAttribute("title"));
            Node originalText = name.firstChild();
            vm.set("tip", "Updated");
            assertSame(originalText, name.firstChild());
            vm.set("player.name", "Steve");
            vm.set("active", false);
            vm.set("tip", null);
            assertEquals("Steve", name.textContent());
            assertFalse(save.hasClass("active"));
            assertTrue(save.hasClass("base"));
            assertFalse(save.hasAttribute("title"));
            save.dispatchEvent(new Event("click"));
            assertEquals(1, calls.get());
            vm.set("save", (ViewModel.Command) event -> calls.addAndGet(10));
            save.dispatchEvent(new Event("click"));
            assertEquals(11, calls.get());
        }
        doc.getElementById("save").dispatchEvent(new Event("click"));
        assertEquals(11, calls.get());
        vm.set("player.name", "Closed");
        assertEquals("Steve", doc.getElementById("name").textContent());
    }

    @Test void conditionalRetainsNodeAndLatestState() {
        Document doc = page("<input id='before'><section id='panel' o-if='visible'><p o-text='name'></p><input id='draft'></section><p id='after'></p>");
        ViewModel vm = new ViewModel(Map.of("visible", true, "name", "First"));
        try (Bindings bindings = Bindings.attach(doc, vm)) {
            Element panel = doc.getElementById("panel"), draft = doc.getElementById("draft");
            draft.setValue("retained");
            vm.set("visible", false);
            assertNull(doc.getElementById("panel"));
            vm.set("name", "Latest");
            vm.set("visible", true);
            assertSame(panel, doc.getElementById("panel"));
            assertEquals("Latest", panel.textContent());
            assertEquals("retained", draft.value());
            assertTrue(panel.indexInParent() < doc.getElementById("after").indexInParent());
        }
    }

    private static Map<String, Object> item(String id, String label) {
        return Map.of("id", id, "label", label);
    }

    @Test void keyedLoopReordersReusesUpdatesAndDisposes() {
        AtomicInteger clicks = new AtomicInteger();
        Document doc = page("<main id='rows'><button o-for='row in rows' o-key='row.id' o-text='row.label' o-on:click='select'></button><p id='tail'>Tail</p></main>");
        ViewModel vm = new ViewModel(Map.of("rows", List.of(item("a", "A"), item("b", "B")),
                "select", (ViewModel.Command) e -> clicks.incrementAndGet()));
        try (Bindings bindings = Bindings.attach(doc, vm)) {
            List<Element> first = doc.getElementsByTagName("button");
            vm.set("rows", List.of(item("b", "B2"), item("a", "A"), item("c", "C")));
            List<Element> next = doc.getElementsByTagName("button");
            assertEquals(3, next.size());
            assertSame(first.get(1), next.get(0));
            assertSame(first.get(0), next.get(1));
            assertEquals("B2", next.get(0).textContent());
            assertEquals("Tail", doc.getElementById("tail").textContent());
            next.get(0).dispatchEvent(new Event("click"));
            assertEquals(1, clicks.get());
            vm.set("rows", List.of(item("a", "A")));
            next.get(0).dispatchEvent(new Event("click"));
            assertEquals(1, clicks.get());
            assertSame(first.get(0), doc.getElementsByTagName("button").get(0));
        }
    }

    @Test void nestedLoopsAndRootDependencies() {
        Document doc = page("<section o-for='group in groups' o-key='group.id'><p o-text='title'></p><span o-for='row in group.rows' o-key='row.id' o-text='row.label'></span></section>");
        ViewModel vm = new ViewModel(Map.of("title", "Root", "groups", List.of(Map.of("id", "g", "rows", List.of(item("a", "A"))))));
        try (Bindings bindings = Bindings.attach(doc, vm)) {
            Element span = doc.getElementsByTagName("span").get(0);
            vm.set("title", "New");
            assertEquals("New", doc.getElementsByTagName("p").get(0).textContent());
            assertSame(span, doc.getElementsByTagName("span").get(0));
            vm.set("groups", List.of(Map.of("id", "g", "rows", List.of(item("a", "A2")))));
            assertSame(span, doc.getElementsByTagName("span").get(0));
            assertEquals("A2", span.textContent());
        }
    }

    @Test void diagnosticsIdentifySourceAndPosition() {
        for (String attribute : List.of("o-text='missing'", "o-text='name + 1'", "o-class:x='name'",
                "o-on:click='name'", "o-unknown='name'", "o-attr:onclick='name'", "o-key='name'")) {
            Document doc = page("  <p " + attribute + "></p>");
            assertThrows(IllegalArgumentException.class, () -> Bindings.attach(doc, new ViewModel(Map.of("name", "Alex"))), attribute);
            Diagnostic d = doc.diagnostics().all().stream().filter(it -> it.code().startsWith("binding-")).findFirst().orElseThrow();
            assertEquals(Diagnostic.Severity.ERROR, d.severity());
            assertEquals("test:ui/bind.html", d.source());
            assertEquals(2, d.line());
            assertEquals(3, d.column());
        }
    }

    @Test void duplicateKeysRejectUpdateBeforeChangingRows() {
        Document doc = page("<p o-for='row in rows' o-key='row.id' o-text='row.label'></p>");
        ViewModel vm = new ViewModel(Map.of("rows", List.of(item("a", "A"))));
        try (Bindings bindings = Bindings.attach(doc, vm)) {
            Element original = doc.getElementsByTagName("p").get(0);
            assertThrows(IllegalArgumentException.class, () -> vm.set("rows", List.of(item("a", "X"), item("a", "Y"))));
            assertSame(original, doc.getElementsByTagName("p").get(0));
            assertEquals("A", original.textContent());
            assertTrue(doc.diagnostics().all().stream().anyMatch(d -> d.code().equals("binding-key") && d.line() == 2));
            vm.set("rows", List.of(item("a", "Recovered")));
            assertSame(original, doc.getElementsByTagName("p").get(0));
            assertEquals("Recovered", original.textContent());
        }
    }

    @Test void rejectsMissingKeyWrongCollectionAndInvalidStructure() {
        for (String html : List.of("<p o-for='row in rows'></p>",
                "<p o-for='row of rows' o-key='row.id'></p>",
                "<p o-for='row in name' o-key='row.id'></p>",
                "<p o-text='name'><span o-text='name'></span></p>")) {
            Document doc = page(html);
            assertThrows(IllegalArgumentException.class, () -> Bindings.attach(doc, new ViewModel(Map.of("rows", List.of(), "name", "X"))));
            assertTrue(doc.diagnostics().all().stream().anyMatch(d -> d.code().startsWith("binding-")));
        }
    }

    @Test void modelSnapshotsInputsAndEqualWritesDoNotMutateDom() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "First");
        List<Map<String, Object>> rows = new ArrayList<>(List.of(item("a", "A")));
        input.put("rows", rows);
        ViewModel vm = new ViewModel(input);
        Document doc = page("<p o-text='name'></p><span o-for='row in rows' o-key='row.id' o-text='row.label'></span>");
        try (Bindings bindings = Bindings.attach(doc, vm)) {
            int version = doc.domVersion();
            vm.set("name", "First");
            assertEquals(version, doc.domVersion());
            input.put("name", "External");
            rows.clear();
            assertEquals("First", vm.get("name"));
            assertEquals(1, ((List<?>) vm.get("rows")).size());
        }
    }

    @Test void documentUnloadStopsObservationAndListeners() {
        AtomicInteger calls = new AtomicInteger();
        Document doc = page("<button o-on:click='save' o-text='name'></button>");
        ViewModel vm = new ViewModel(Map.of("name", "Before", "save", (ViewModel.Command) e -> calls.incrementAndGet()));
        Bindings.attach(doc, vm);
        Element button = doc.getElementsByTagName("button").get(0);
        doc.close();
        vm.set("name", "After");
        button.dispatchEvent(new Event("click"));
        assertEquals("Before", button.textContent());
        assertEquals(0, calls.get());
    }

    @Test void loopConditionalRowsKeepIdentityAndReleaseHiddenListeners() {
        AtomicInteger calls = new AtomicInteger();
        Document doc = page("<button o-for='row in rows' o-key='row.id' o-if='row.visible' o-text='row.id' o-on:click='save'></button>");
        ViewModel vm = new ViewModel(Map.of("rows", List.of(Map.of("id", "a", "visible", true)),
                "save", (ViewModel.Command) e -> calls.incrementAndGet()));
        try (Bindings bindings = Bindings.attach(doc, vm)) {
            Element button = doc.getElementsByTagName("button").get(0);
            vm.set("rows", List.of(Map.of("id", "a", "visible", false)));
            assertTrue(doc.getElementsByTagName("button").isEmpty());
            vm.set("rows", List.of(Map.of("id", "a", "visible", true)));
            assertSame(button, doc.getElementsByTagName("button").get(0));
            vm.set("rows", List.of(Map.of("id", "a", "visible", false)));
            vm.set("rows", List.of());
            button.dispatchEvent(new Event("click"));
            assertEquals(0, calls.get());
        }
    }

    @Test void emptyLoopStillValidatesDirectiveGrammar() {
        Document doc = page("<p o-for='row in rows' o-key='row.id' o-text='row.name + 1'></p>");
        assertThrows(IllegalArgumentException.class, () -> Bindings.attach(doc, new ViewModel(Map.of("rows", List.of()))));
        assertTrue(doc.diagnostics().all().stream().anyMatch(d -> d.code().equals("binding-expression")));
    }

    @Test void failedAttachRemovesPreviouslyInstalledListeners() {
        AtomicInteger calls = new AtomicInteger();
        Document doc = page("<button o-on:click='save'></button><p o-text='missing'></p>");
        assertThrows(IllegalArgumentException.class, () -> Bindings.attach(doc, new ViewModel(
                Map.of("save", (ViewModel.Command) e -> calls.incrementAndGet()))));
        doc.getElementsByTagName("button").get(0).dispatchEvent(new Event("click"));
        assertEquals(0, calls.get());
    }

    @Test void booleanAttributeUsesPresenceAndNullTextIsEmpty() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disabled", true); data.put("name", null);
        Document doc = page("<button o-attr:disabled='disabled' o-text='name'></button>");
        ViewModel vm = new ViewModel(data);
        try (Bindings bindings = Bindings.attach(doc, vm)) {
            Element button = doc.getElementsByTagName("button").get(0);
            assertEquals("", button.getAttribute("disabled"));
            assertEquals("", button.textContent());
            vm.set("disabled", false);
            assertFalse(button.hasAttribute("disabled"));
        }
    }

    @Test void clonedRowErrorKeepsTemplatePosition() {
        Document doc = page("  <p o-for='row in rows' o-key='row.id' o-text='row.missing'></p>");
        assertThrows(IllegalArgumentException.class, () -> Bindings.attach(doc,
                new ViewModel(Map.of("rows", List.of(item("a", "A"))))));
        Diagnostic diagnostic = doc.diagnostics().all().stream().filter(d -> d.code().equals("binding-path")).findFirst().orElseThrow();
        assertEquals(2, diagnostic.line());
        assertEquals(3, diagnostic.column());
    }

    @Test void conditionalBlurCanUpdateModelDuringStructuralChange() {
        ViewModel vm = new ViewModel(Map.of("title", "Before", "visible", true, "blur", (ViewModel.Command) event -> {}));
        vm.set("blur", (ViewModel.Command) event -> vm.set("title", "Blurred"));
        Document doc = page("<p o-text='title'></p><section o-if='visible'><input id='input' o-on:blur='blur'></section>");
        try (Bindings bindings = Bindings.attach(doc, vm)) {
            doc.frame(0);
            doc.getElementById("input").focus();
            assertNotNull(doc.focusedElement());
            vm.set("visible", false);
            assertEquals("Blurred", doc.getElementsByTagName("p").get(0).textContent());
            assertNull(doc.focusedElement());
        }
    }

    @Test void reorderingRowsPreservesFocusAndLiveInputValue() {
        Document doc = page("<div o-for='row in rows' o-key='row.id'><input o-attr:data-id='row.id'><span o-text='row.label'></span></div>");
        ViewModel vm = new ViewModel(Map.of("rows", List.of(item("a", "A"), item("b", "B"))));
        try (Bindings bindings = Bindings.attach(doc, vm)) {
            doc.frame(0);
            Element input = doc.getElementsByTagName("input").get(0);
            input.setValue("Draft");
            input.focus();
            vm.set("rows", List.of(item("b", "B2"), item("a", "A")));
            doc.frame(16);
            assertSame(input, doc.getElementsByTagName("input").get(1));
            assertSame(input, doc.focusedElement());
            assertEquals("Draft", input.value());
        }
    }
}
