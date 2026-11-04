package dev.oriel.engine.paint;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.RecordingCanvas;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code backdrop-filter} on a canvas that can only read back groups (as in game): the page becomes the group. */
class BackdropTest {
    private static final String PAGE = "<div style='width: 40px; height: 20px; backdrop-filter: blur(2px)'></div>";

    private static RecordingCanvas canvas() {
        RecordingCanvas c = new RecordingCanvas();
        c.groups = true;
        c.backdropsNeedGroups = true;
        return c;
    }

    private static List<String> ops(RecordingCanvas c) {
        return c.calls.stream().map(RecordingCanvas.Call::op)
                .filter(op -> op.contains("Group") || op.contains("Backdrop")).toList();
    }

    @Test
    void aBackdropOutsideAnyGroupMakesThePageAGroupFromTheNextFrame() {
        Page page = new TestHost().load(PAGE);
        assertEquals(List.of(), ops(page.paint(canvas())), "the first frame finds the backdrop outside any group");
        assertEquals(List.of("beginPageGroup", "beginBackdrop", "endGroup"), ops(page.paint(canvas())));
        assertTrue(page.doc.diagnostics().all().stream().noneMatch(d -> d.code().equals("effects-unsupported")),
                () -> page.doc.diagnostics().all().toString());
    }

    @Test
    void aCanvasThatReadsItsTargetDrawsBackdropsWithoutAPageGroup() {
        Page page = new TestHost().load(PAGE);
        RecordingCanvas c = new RecordingCanvas();
        c.groups = true;
        assertEquals(List.of("beginBackdrop"), ops(page.paint(c)));
    }

    @Test
    void aCanvasWithoutGroupsReportsTheBackdrop() {
        Page page = new TestHost().load(PAGE);
        page.paint(new RecordingCanvas());
        assertTrue(page.doc.diagnostics().all().stream().anyMatch(d -> d.code().equals("effects-unsupported")));
    }
}
