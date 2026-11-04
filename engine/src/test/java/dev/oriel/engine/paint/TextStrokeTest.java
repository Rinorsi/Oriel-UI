package dev.oriel.engine.paint;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.RecordingCanvas;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code -webkit-text-stroke}: the glyphs traced all round in the stroke colour, under the fill. */
class TextStrokeTest {
    @Test
    void theStrokeTracesTheGlyphsUnderTheFill() {
        Page page = new TestHost().load("<div id=t style='color: #fff; -webkit-text-stroke: 2px #f00'>A</div>");
        List<RecordingCanvas.Call> texts = page.paint(new RecordingCanvas()).ops("drawText");
        assertEquals(9, texts.size(), texts::toString);
        RecordingCanvas.Call fill = texts.get(8);
        assertEquals(0xFFFFFFFF, fill.color(), "the fill comes last");
        float far = 0;
        for (RecordingCanvas.Call c : texts.subList(0, 8)) {
            assertEquals(0xFFFF0000, c.color());
            far = Math.max(far, Math.max(Math.abs(c.x() - fill.x()), Math.abs(c.y() - fill.y())));
        }
        assertEquals(1, far, 0.01, "half the stroke width out (snapped to pixels)");
        assertEquals("2px", page.computed("#t", "-webkit-text-stroke-width"));
        assertEquals("rgb(255, 0, 0)", page.computed("#t", "-webkit-text-stroke-color"));
    }

    @Test
    void theStrokeColourDefaultsToTheTextColour() {
        Page page = new TestHost().load("<div id=t style='color: #0f0; -webkit-text-stroke-width: 1px'>A</div>");
        assertEquals("rgb(0, 255, 0)", page.computed("#t", "-webkit-text-stroke-color"));
    }
}
