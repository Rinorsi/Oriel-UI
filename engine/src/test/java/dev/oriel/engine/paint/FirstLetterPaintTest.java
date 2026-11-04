package dev.oriel.engine.paint;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.RecordingCanvas;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The ::first-letter is painted in its own style, not its element's live one. */
class FirstLetterPaintTest {
    @Test
    void theLetterKeepsItsColour() {
        Page page = new TestHost().load("<style>#p::first-letter { color: #f00 }</style><div id=p style='color: #00f'>ab</div>");
        List<RecordingCanvas.Call> texts = page.paint(new RecordingCanvas()).ops("drawText");
        assertEquals(List.of("a", "b"), texts.stream().map(RecordingCanvas.Call::text).toList());
        assertEquals(0xFFFF0000, texts.get(0).color());
        assertEquals(0xFF0000FF, texts.get(1).color());
    }
}
