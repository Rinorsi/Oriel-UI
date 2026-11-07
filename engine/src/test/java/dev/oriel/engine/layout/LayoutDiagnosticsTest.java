package dev.oriel.engine.layout;

import com.google.gson.JsonParser;
import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.FontSpec;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LayoutDiagnosticsTest {
    @Test
    void recordsActualConstraintsAndMinHeightClamp() {
        String previous = System.getProperty("oriel.layoutTrace");
        try {
            System.setProperty("oriel.layoutTrace", "true");
            Page page = new TestHost().load("<div id=t style='box-sizing:content-box;width:50%;height:10px;min-height:30px;padding:2px'></div>");
            var why = page.doc.layoutEngine().whySize(page.byId("t"));
            assertEquals(Float.toString(page.byId("t").box.width), why.get("borderBoxWidth"));
            assertEquals("34.0", why.get("borderBoxHeight"));
            assertEquals("34.0", why.get("minBorderBoxHeight"));
            assertEquals(page.doc.layoutEngine().snapshot().boxes().stream()
                    .filter(box -> box.element().contains("#t")).findFirst().orElseThrow().id(), why.get("boxId"));
            assertThrows(UnsupportedOperationException.class, () -> why.put("width", "wrong"));
            assertTrue(JsonParser.parseString(page.doc.layoutEngine().snapshot().toJson()).isJsonObject());
        } finally {
            if (previous == null) System.clearProperty("oriel.layoutTrace");
            else System.setProperty("oriel.layoutTrace", previous);
        }
    }

    @Test
    void fontReloadInvalidatesCachedWidthsAndRequestsLayout() {
        float[] advance = {6};
        FontMetrics fonts = new FontMetrics() {
            @Override public float width(String text, FontSpec font) { return advance[0] * text.length(); }
        };
        Page page = new TestHost() {
            @Override public FontMetrics fonts() { return fonts; }
        }.load("<div id=t style='display:inline-block'>word</div>");
        float width = page.byId("t").box.width;
        long generation = page.doc.layoutEngine().textMeasure().generation();
        advance[0] = 12;
        page.doc.layoutEngine().fontsChanged();
        assertTrue(page.doc.needsLayout());
        page.doc.flushLayout();
        assertEquals(width * 2, page.byId("t").box.width);
        assertEquals(generation + 1, page.doc.layoutEngine().textMeasure().generation());
    }

    @Test
    void fontReloadCanReplaceTheMetricsProviderWithoutReplacingTheSharedMeasure() {
        FontMetrics[] metrics = {(text, font) -> text.length() * 6};
        Page page = new TestHost() {
            @Override public FontMetrics fonts() { return metrics[0]; }
        }.load("<div id=t style='display:inline-block'>word</div>");
        TextMeasure shared = page.doc.layoutEngine().textMeasure();
        float width = page.byId("t").box.width;
        metrics[0] = (text, font) -> text.length() * 12;
        page.doc.layoutEngine().fontsChanged();
        page.doc.flushLayout();
        assertSame(shared, page.doc.layoutEngine().textMeasure(), "paint and controls retain the shared measurement object");
        assertEquals(width * 2, page.byId("t").box.width);
    }
}
