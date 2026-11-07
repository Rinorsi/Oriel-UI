package dev.oriel.engine.paint;

import dev.oriel.engine.testing.TestHost;
import dev.oriel.engine.testing.RecordingCanvas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DecorationCacheTest {
    @Test
    void animatedStyleSnapshotsDoNotAllocateSingleFrameCachePrograms() {
        var page = new TestHost().load("<style>@keyframes drift{from{transform:translateX(0px)}to{transform:translateX(20px)}}</style>"
                + "<div id='g' style='width:80px;height:40px;background:radial-gradient(red,blue);animation:drift 1s linear infinite'></div>");
        Painter painter = page.doc.painter();
        painter.mode(Painter.Mode.NEW);
        var element = page.doc.getElementById("g");
        for (int frame = 1; frame <= 3; frame++) {
            page.doc.frame(frame * 100);
            assertNotSame(element.baseStyle, element.style);
            page.paint(new RecordingCanvas());
            assertEquals(0L, painter.cacheStatistics().get("entries"));
            assertEquals(0L, painter.cacheStatistics().get("misses"));
        }
    }

    @Test
    void cachedProgramMatchesFreshPaintAcrossStyleLayoutAndScaleChanges() {
        var page = new TestHost().load("<div id='g' style='width:80px;height:40px;background:radial-gradient(red,blue);border-radius:8px'></div>");
        Painter painter = page.doc.painter();
        painter.mode(Painter.Mode.BOTH);
        painter.trace(true);
        page.paint(new RecordingCanvas());
        DisplayList first = painter.lastDisplayList();
        page.paint(new RecordingCanvas());
        first.assertEquivalent(painter.lastDisplayList(), page.doc.url());
        assertEquals(1L, painter.cacheStatistics().get("hits"));
        page.doc.getElementById("g").setAttribute("style", "width:90px;height:20px;background:linear-gradient(white,black)");
        page.frame();
        page.paint(new RecordingCanvas());
        assertEquals(0L, painter.cacheStatistics().get("hits"));
        RecordingCanvas scaled = new RecordingCanvas();
        scaled.devicePixel = .5f;
        page.paint(scaled);
        assertEquals(0L, painter.cacheStatistics().get("hits"));
        page.paint(scaled);
        assertEquals(1L, painter.cacheStatistics().get("hits"));
    }

    @Test
    void resourceAndCanvasImagesNeverEnterGeometryCache() {
        TestHost host = new TestHost();
        host.imageSizes.put("test:a.png", new float[] {4, 4});
        var page = host.load("<div style='width:80px;height:40px;background:radial-gradient(red,blue),url(a.png)'></div>");
        page.doc.painter().mode(Painter.Mode.NEW);
        page.paint(new RecordingCanvas());
        page.paint(new RecordingCanvas());
        assertEquals(0L, page.doc.painter().cacheStatistics().get("entries"));
    }
}
