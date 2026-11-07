package dev.oriel.engine.paint;

import dev.oriel.engine.testing.RecordingCanvas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisplayListTest {
    @Test
    void textExportDoesNotTraverseResolvedHostFontObjects() {
        var font = new dev.oriel.engine.host.FontSpec(java.util.List.of("test:font"), 12, true, false);
        font.setHostFont(java.util.Optional.of("host-only-font-cache"));
        var recorder = new DisplayList.Recorder(new RecordingCanvas());
        recorder.drawText("hello", 1, 2, font, -1, 0, false);
        DisplayList list = recorder.finish();
        var exported = com.google.gson.JsonParser.parseString(list.json(null)).getAsJsonArray();
        var spec = exported.get(0).getAsJsonObject().getAsJsonArray("arguments").get(3).getAsJsonObject();
        assertEquals(12, spec.get("size").getAsInt());
        assertEquals("test:font", spec.getAsJsonArray("families").get(0).getAsString());
        assertTrue(spec.get("bold").getAsBoolean());
        assertFalse(spec.get("italic").getAsBoolean());
        assertFalse(spec.has("hostFont"));
        assertFalse(list.jsonLines(null).contains("host-only-font-cache"));
        assertFalse(list.text(null).contains("host-only-font-cache"));
    }

    @Test
    void batchingNeverCrossesStateOrElementBoundaries() {
        var recorder = new DisplayList.Recorder(new RecordingCanvas());
        float[] quad = {0, 0, 0, 2, 2, 2, 2, 0};
        int[] colors = {-1, -1, -1, -1};
        recorder.fillQuads(quad, colors, 1);
        recorder.fillQuads(quad, colors, 1);
        recorder.multiplyAlpha(.5f);
        recorder.fillQuads(quad, colors, 1);
        recorder.element("other");
        recorder.fillQuads(quad, colors, 1);
        DisplayList raw = recorder.finish();
        assertEquals(4, raw.batched().size());
        raw.assertEquivalent(raw.batched(), "test:batch");
    }
    @Test
    void snapshotsReusedArraysAndPreservesShapeBackendDispatch() {
        RecordingCanvas direct = new RecordingCanvas();
        DisplayList.Recorder recorder = new DisplayList.Recorder(direct);
        float[] radii = {2, 2, 2, 2, 2, 2, 2, 2};
        recorder.fillRoundedRect(1, 2, 20, 10, radii, 0x8080ff00);
        radii[0] = 99;
        RecordingCanvas replay = new RecordingCanvas();
        recorder.finish().replay(replay);
        assertArrayEquals(direct.calls.getFirst().args(), replay.calls.getFirst().args());
        assertEquals("fillRoundedRect", replay.calls.getFirst().op());
    }

    @Test
    void groupsRetainBackendAcceptanceAndRejectChangedCapabilities() {
        RecordingCanvas direct = new RecordingCanvas();
        direct.groups = true;
        DisplayList.Recorder recorder = new DisplayList.Recorder(direct);
        assertTrue(recorder.beginGroup(1, 2, 3, 4));
        recorder.endGroup(1, java.util.List.of(), dev.oriel.engine.style.BlendMode.NORMAL);
        DisplayList list = recorder.finish();
        assertThrows(IllegalStateException.class, () -> list.replay(new RecordingCanvas()));
    }

    @Test
    void nativeContentGetsOriginalCanvasExactlyOnceAndKeepsOrdering() {
        RecordingCanvas direct = new RecordingCanvas();
        DisplayList.Recorder recorder = new DisplayList.Recorder(direct);
        int[] calls = {0};
        dev.oriel.engine.host.ReplacedContent content = new dev.oriel.engine.host.ReplacedContent() {
            public float intrinsicWidth() { return 4; }
            public float intrinsicHeight() { return 4; }
            public void paint(Canvas canvas, float x, float y, float w, float h) {
                assertInstanceOf(RecordingCanvas.class, canvas);
                calls[0]++;
                canvas.fillRect(x, y, w, h, -1);
            }
        };
        recorder.nativeContent(content, 0, 0, 4, 4);
        assertEquals(1, calls[0]);
        recorder.finish().replay(new RecordingCanvas());
        assertEquals(2, calls[0]);
    }

    @Test
    void differencesNameElementAndCommandAndExportValidJson() {
        DisplayList.Recorder a = new DisplayList.Recorder(new RecordingCanvas());
        a.element("div#reward");
        a.fillRect(0, 0, 4, 4, -1);
        DisplayList.Recorder b = new DisplayList.Recorder(new RecordingCanvas());
        b.element("div#reward");
        b.fillRect(0, 0, 4, 4, 0xff000000);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> a.finish().assertEquivalent(b.finish(), "test:page"));
        assertTrue(error.getMessage().contains("div#reward"));
        assertTrue(error.getMessage().contains("fillRect"));
        assertEquals(1, com.google.gson.JsonParser.parseString(a.finish().json("div#reward")).getAsJsonArray().size());
        assertEquals(0, com.google.gson.JsonParser.parseString(a.finish().json("#absent")).getAsJsonArray().size());
    }
}
