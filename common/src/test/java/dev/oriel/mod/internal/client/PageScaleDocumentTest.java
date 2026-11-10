package dev.oriel.mod.internal.client;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.Host;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real layout/input, without a Minecraft window. Native GPU/IME acceptance is separate. */
class PageScaleDocumentTest {
    @Test void resizeRetainsDocumentStateAndMappedInputTargets() {
        Host host = new Host() {
            public FontMetrics fonts() { return (text, font) -> text.length() * 6; }
            public String loadText(String url) { return null; }
        };
        for (int[] window : new int[][] {{320, 180}, {960, 540}}) {
            var doc = Document.parse(host, "scale:test.html", """
                    <style>body { margin:0 } button,input { position:absolute; left:8px; width:60px; height:20px }
                    button {top:8px; -mc-tooltip-delay:0ms} input {top:36px}</style>
                    <button id=target title=tooltip>test</button><input id=draft value=unchanged>
                    """, null, new Viewport(window[0], window[1], 2));
            try {
                var target = doc.querySelector("#target");
                var draft = doc.querySelector("#draft");
                var clicks = new AtomicInteger();
                var wheels = new AtomicInteger();
                target.addEventListener("click", e -> clicks.incrementAndGet());
                target.addEventListener("wheel", e -> wheels.incrementAndGet());
                for (int requested = 0; requested <= 4; requested++) {
                    var s = PageScale.resolve(requested, 2);
                    var focused = doc.focusedElement();
                    doc.setViewport(s.extent(window[0]), s.extent(window[1]), s.devicePixelRatio());
                    doc.frame(1000L + requested * 1000);
                    assertSame(focused, doc.focusedElement(), "viewport change keeps input focus");
                    double gx = s.toGui(20), gy = s.toGui(16);
                    float x = (float) s.toPage(gx), y = (float) s.toPage(gy);
                    assertSame(target, doc.hitTest(x, y).element());
                    doc.input().mouseMove(x, y, Modifiers.NONE);
                    var tooltip = doc.input().tooltip();
                    assertNotNull(tooltip);
                    assertEquals(20, tooltip.x());
                    assertEquals(gx, s.toGui(tooltip.x()), 0.000001);
                    doc.input().mouseDown(x, y, 0, Modifiers.NONE);
                    doc.input().mouseUp(x, y, 0, Modifiers.NONE);
                    assertEquals(requested + 1, clicks.get());
                    doc.input().wheel(x, y, 0, 24, Modifiers.NONE);
                    assertEquals(requested + 1, wheels.get());
                    doc.input().mouseDown(x, y, 0, Modifiers.NONE);
                    doc.input().mouseMove((float) s.toPage(gx + s.toGui(7)), y, Modifiers.NONE);
                    assertEquals(27, doc.input().pointer()[0]);
                    doc.input().mouseUp(27, y, 0, Modifiers.NONE);
                    // Second click from the drag is allowed by the engine; start next iteration fresh.
                    clicks.set(requested + 1);
                    assertSame(draft, doc.querySelector("#draft"));
                    assertEquals("unchanged", draft.value());
                    assertEquals(36, s.toPage(s.toGui(36)), 0.000001);
                    // A focused field and its IME caret are owned by the same document across viewport changes.
                    doc.input().mouseDown(20, 44, 0, Modifiers.NONE);
                    doc.input().mouseUp(20, 44, 0, Modifiers.NONE);
                    doc.input().preedit("中", 1, 0, 1);
                    doc.frame(1500L + requested * 1000);
                    var caret = doc.input().textCaretRect();
                    assertNotNull(caret);
                    for (float coordinate : caret) assertEquals(coordinate, s.toPage(s.toGui(coordinate)), 0.000001);
                    doc.input().preedit(null, 0, 0, 0);
                }
            } finally {
                doc.close();
            }
        }
    }
}
