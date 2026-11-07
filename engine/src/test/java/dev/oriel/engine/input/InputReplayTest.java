package dev.oriel.engine.input;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.Event;
import dev.oriel.engine.event.EventListener;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class InputReplayTest {
    @Test
    void overflowClipCannotUseTheUnclippedHitGrid() {
        backend("both", () -> {
            Page p = new TestHost().load("<div style='height:10px;overflow:clip'>"
                    + "<div style='height:2px;width:120px'></div>".repeat(200) + "</div>");
            assertDoesNotThrow(() -> p.input.hit(10, 100));
            return null;
        });
    }

    static <T> T backend(String mode, Supplier<T> operation) {
        String before = System.getProperty("oriel.inputBackend");
        System.setProperty("oriel.inputBackend", mode);
        try { return operation.get(); }
        finally {
            if (before == null) System.clearProperty("oriel.inputBackend");
            else System.setProperty("oriel.inputBackend", before);
        }
    }

    @Test
    void recordedPointerKeyboardCompositionWheelAndCancellationHaveNoStepDifferences() {
        List<String> old = backend("old", InputReplayTest::replay);
        assertEquals(old, backend("new", InputReplayTest::replay));
        assertEquals(old, backend("both", InputReplayTest::replay));
    }

    private static List<String> replay() {
        Page p = new TestHost().load("""
                <div id=outer style='height:100px;width:220px;overflow:auto'>
                  <input id=t value='ab中文' style='width:140px;height:20px'>
                  <textarea id=area style='width:130px;height:35px'>中文（测试） abc</textarea>
                  <div id=inner style='height:30px;overflow:auto'><div style='height:150px'>Nested</div></div>
                  <button id=cancel>Cancel</button><input id=check type=checkbox>
                  <div style='height:250px'></div>
                </div><button id=end tabindex=2>End</button>
                """);
        p.byId("cancel").addEventListener("mousedown", Event::preventDefault);
        p.byId("check").addEventListener("click", Event::preventDefault);
        List<String> events = new ArrayList<>(), snapshots = new ArrayList<>();
        for (String name : List.of("mousedown", "mouseup", "click", "keydown", "keyup", "beforeinput", "input",
                "focus", "blur", "focusin", "focusout", "wheel", "change", "mousemove", "scroll")) {
            p.doc.addEventListener(name, e -> events.add(e.type + ":capture:" + InputDiagnostics.id(e.target()) + ":" + e.defaultPrevented()), true, false);
            p.doc.addEventListener(name, e -> events.add(e.type + ":bubble:" + InputDiagnostics.id(e.target()) + ":" + e.defaultPrevented()));
        }
        List<Consumer<Page>> actions = List.of(
                q -> q.move(8, 8), q -> q.click(q.byId("t")),
                q -> q.key("End"), q -> q.input.charTyped("é👍🏽👩‍👩‍👧‍👦🇨🇳1️⃣"),
                q -> q.key("ArrowLeft"), q -> q.key("ArrowLeft", Page.SHIFT),
                q -> q.input.preedit("zhongwen", 5, 0, 8), q -> q.key("Backspace"),
                q -> q.input.preedit("中文", 2, 0, 2), q -> q.key("Escape"),
                q -> q.input.preedit("é👩‍👧", 2, 0, 2), q -> q.input.preedit(null, 0, 0, 0),
                q -> q.input.charTyped("中"), q -> q.key("z", Page.SHORTCUT), q -> q.key("y", Page.SHORTCUT),
                q -> q.down(8, 8), q -> q.move(80, 8), q -> q.up(80, 8),
                q -> q.key("Tab"), q -> q.input.charTyped("中文（（测试））结束"),
                q -> q.key("Home"), q -> q.key("ArrowDown", Page.SHIFT), q -> q.key("Delete"),
                q -> q.input.preedit("ni", 2, 0, 2), q -> q.byId("end").focus(),
                q -> q.key("Tab", Page.SHIFT), q -> q.click(q.byId("cancel")),
                q -> q.click(q.byId("check")), q -> q.wheel(12, 80, 0, 220),
                q -> q.frame(1500), q -> q.wheel(12, 80, 0, 220), q -> q.frame(2500),
                q -> q.input.mouseLeave());
        int step = 0;
        for (Consumer<Page> action : actions) {
            action.accept(p);
            p.frame();
            assertNull(p.doc.error(), "step " + step);
            StringBuilder state = new StringBuilder("step=" + step++ + ":" + events);
            events.clear();
            state.append(" focus=").append(InputDiagnostics.id(p.doc.focusedElement()))
                    .append(" visible=").append(p.input.focusVisible()).append(" caret=").append(Arrays.toString(p.input.textCaretRect()));
            for (String id : List.of("t", "area")) {
                TextField field = TextField.of(p.byId(id));
                state.append(id).append('=').append(field.editor.text()).append(':').append(field.editor.anchor())
                        .append(':').append(field.editor.caret()).append(':').append(field.preedit());
            }
            state.append(" scroll=").append(p.byId("outer").scrollTop()).append(':').append(p.byId("inner").scrollTop())
                    .append(" checked=").append(p.byId("check").checked());
            var hit = p.input.hit(12, 8);
            state.append(" hit=").append(hit == null ? null : InputDiagnostics.id(hit.element()));
            snapshots.add(state.toString());
        }
        return snapshots;
    }

    @Test
    void routeSnapshotsSurviveReparentingOnceRemovalAndNestedDispatch() {
        List<String> old = backend("old", InputReplayTest::mutatingDispatch);
        assertEquals(old, backend("new", InputReplayTest::mutatingDispatch));
        assertEquals(old, backend("both", InputReplayTest::mutatingDispatch));
    }

    private static List<String> mutatingDispatch() {
        Page p = new TestHost().load("<div id=p><button id=t>Target</button></div><div id=other></div>");
        List<String> log = new ArrayList<>();
        Element target = p.byId("t"), parent = p.byId("p");
        EventListener removed = e -> log.add("removed");
        parent.addEventListener("probe", e -> {
            log.add("capture-parent");
            parent.removeEventListener("probe", removed, false);
            p.byId("other").appendChild(target);
            target.dispatchEvent(new Event("nested"));
        }, true, true);
        parent.addEventListener("probe", removed);
        parent.addEventListener("probe", e -> log.add("bubble-original-parent"));
        target.addEventListener("probe", e -> { log.add("target"); e.preventDefault(); });
        p.doc.addEventListener("nested", e -> log.add("nested"));
        assertFalse(target.dispatchEvent(new Event("probe")));
        assertFalse(target.dispatchEvent(new Event("probe")));
        return log;
    }

    @Test
    void hitIndexAndFallbackMatchAfterStyleLayoutAndTreeChanges() {
        backend("both", () -> {
            Page p = new TestHost().load("<style>div{height:2px;width:120px}</style>" + "<div></div>".repeat(5000));
            HitGrid grid = new HitGrid(p.doc);
            grid.hit(10, 10);
            assertTrue(grid.indexed(), "The large plain-block fixture must actually exercise the index");
            for (int i = 0; i < 700; i++) p.input.hit(i % 160, i * 13 % 10020);
            Element first = p.doc.querySelector("div");
            first.setAttribute("style", "transform:translateX(20px);border-radius:10px");
            p.frame();
            grid.hit(10, 10);
            assertFalse(grid.indexed(), "Transforms must use shared paint geometry");
            for (int i = 0; i < 50; i++) p.input.hit(i, 1);
            first.setAttribute("style", "height:40px");
            first.setTextContent("中文");
            p.frame();
            p.input.hit(10, 10);
            assertNull(p.doc.error());
            return null;
        });
    }

    @Test
    void caretBinarySearchMatchesLinearIncludingNegativeSpacingAndGraphemes() {
        backend("both", () -> {
            for (String spacing : List.of("0", "-10px", "2px")) {
                Page p = new TestHost().load("<textarea id=t style='width:70px;letter-spacing:" + spacing
                        + "'>ab é👍🏽中文（（测试））结束\n👩‍👩‍👧‍👦🇨🇳1️⃣</textarea>");
                TextLayout layout = TextField.of(p.byId("t")).layout();
                for (int line = -1; line <= layout.lineCount(); line++)
                    for (float x = -40; x < 180; x += 0.75f) layout.offsetAt(line, x);
                for (int offset = -1; offset < 100; offset++) layout.lineOf(offset);
            }
            return null;
        });
    }

    @Test
    void hoverStyleFlushInvalidatesHitIndexWithoutAFrame() {
        backend("both", () -> {
            Page p = new TestHost().load("<style>div{height:2px;width:120px}div:hover{pointer-events:none}</style>" + "<div></div>".repeat(200));
            p.input.hit(10, 1);
            p.move(10, 1);
            p.doc.flushStyle();
            p.input.hit(10, 1);
            assertNull(p.doc.error());
            return null;
        });
    }
}
