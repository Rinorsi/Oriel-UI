package dev.oriel.engine.input;

import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.FontSpec;
import dev.oriel.engine.layout.TextMeasure;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputBackendTest {
    @Test
    void inertBodyDoesNotReceiveFallbackKeyboardEvents() {
        Page p = new TestHost().load("<body inert><button>Blocked</button></body>");
        p.listen(p.doc.body(), "keydown", "keyup");
        p.key("A");
        assertTrue(p.log.isEmpty(), "An inert body cannot be the keyboard fallback target");
    }

    @Test
    void makingAControlInertInItsClickListenerStopsItsDefaultAction() {
        Page p = new TestHost().load("<input id=t type=checkbox>");
        p.byId("t").addEventListener("click", e -> p.byId("t").setAttribute("inert", ""));
        p.byId("t").click();
        assertFalse(p.byId("t").checked());
    }

    @Test
    void inertDuringAnActiveTextDragReleasesCapture() {
        Page p = new TestHost().load("<input id=t value=abcdefghij style='width:150px;height:20px'>");
        p.down(4, 6);
        TextEditor editor = TextField.of(p.byId("t")).editor;
        int before = editor.caret();
        p.byId("t").setAttribute("inert", "");
        p.move(60, 6);
        assertEquals(before, editor.caret());
    }

    @Test
    void inertSubtreeCannotTakeFocusOrActivate() {
        Page p = new TestHost().load("<div inert><button id=b>Blocked</button><input id=t></div><button id=a>Available</button>");
        p.byId("b").focus();
        assertNull(p.doc.focusedElement());
        p.listen(p.byId("b"), "click");
        p.byId("b").click();
        assertTrue(p.log.isEmpty());
        p.key("Tab");
        assertSame(p.byId("a"), p.doc.focusedElement());
    }

    @Test
    void textWidthCacheHasABoundedCharacterBudget() {
        InputReplayTest.backend("new", () -> {
        int[] calls = {0};
        FontMetrics metrics = (text, font) -> { calls[0]++; return text.length(); };
        TextMeasure measure = new TextMeasure(metrics);
        FontSpec font = new FontSpec(java.util.List.of("test"), 8, false, false);
        String large = "x".repeat(600_000);
        measure.width(large, font);
        measure.width(large, font);
        assertEquals(2, calls[0], "Oversized strings must not be retained");
        return null;
        });
    }

    @Test
    void fontEpochInvalidatesWidthsGlyphsAndTextFieldLayouts() {
        InputReplayTest.backend("both", () -> {
            class MutableFonts implements FontMetrics {
                long epoch;
                float advance = 5;
                public long generation() { return epoch; }
                public float width(String text, FontSpec font) { return text.codePointCount(0, text.length()) * advance; }
            }
            MutableFonts fonts = new MutableFonts();
            TextMeasure measure = new TextMeasure(fonts);
            FontSpec font = new FontSpec(java.util.List.of("test"), 8, false, false);
            var style = new dev.oriel.engine.style.ComputedStyle();
            assertEquals(10, measure.width("ab", font));
            assertEquals(5, measure.advance('a', font, style));
            TextLayout before = new TextLayout("ab", false, 50, measure, font, style);
            fonts.advance = 9; fonts.epoch++;
            assertEquals(18, measure.width("ab", font));
            assertEquals(9, measure.advance('a', font, style));
            assertFalse(before.matches("ab", false, 50, font, style, measure.generation()));
            return null;
        });
    }

    @Test
    void spatialNavigationIsOptInAndTextEditingKeepsItsArrowKeys() {
        Page p = new TestHost().load("""
                <button id=a style='position:absolute;left:0;top:0'>A</button>
                <button id=b style='position:absolute;left:80px;top:0'>B</button>
                <input id=t value=abc style='position:absolute;left:80px;top:40px'>
                """);
        p.byId("a").focus();
        p.key("ArrowRight");
        assertSame(p.byId("a"), p.doc.focusedElement());
        p.input.setNavigationKeys(java.util.Map.of("ArrowRight", "right"));
        p.key("ArrowRight");
        assertSame(p.byId("b"), p.doc.focusedElement());
        assertTrue(p.input.navigate("down"));
        assertSame(p.byId("t"), p.doc.focusedElement());
        p.key("ArrowLeft");
        assertEquals(2, TextField.of(p.byId("t")).editor.caret());
        assertTrue(p.input.explainFocus(p.byId("t")).contains("navigation-action"));
    }

    @Test
    void traceAndQueriesExplainCancellationOcclusionAndFocusWithoutTextPayloads() {
        Page p = new TestHost().load("""
                <input id=t type=password style='width:100px;height:20px'>
                <button id=cover style='position:absolute;left:0;top:0;width:100px;height:20px'>Cover</button>
                """);
        assertTrue(p.input.whyNoClick(p.byId("t"), 5, 5).contains("occluded"));
        java.util.List<String> lines = new java.util.ArrayList<>();
        p.input.setTrace(lines::add);
        p.byId("t").focus();
        p.byId("t").addEventListener("beforeinput", dev.oriel.engine.event.Event::preventDefault);
        p.input.charTyped("private-password-123");
        p.input.preedit("secret-preedit-456", 2, 0, 2);
        p.byId("t").setAttribute("inert", "");
        assertNull(p.doc.focusedElement());
        assertEquals("", p.byId("t").value());
        assertFalse(lines.toString().contains("private-password"));
        assertFalse(lines.toString().contains("secret-preedit"));
        assertTrue(lines.stream().anyMatch(line -> line.contains("beforeinput") && line.contains("\"defaultPrevented\":true")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("composition")));
        p.input.setTrace(null);
        int count = lines.size();
        p.input.charTyped("ignored");
        assertEquals(count, lines.size());
    }
}
