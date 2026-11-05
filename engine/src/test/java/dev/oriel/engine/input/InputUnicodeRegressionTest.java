package dev.oriel.engine.input;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InputUnicodeRegressionTest {
    @Test
    void preeditScrollsToItsCaretAndCancellationRestoresTheCommittedExtent() {
        Page page = new TestHost().load("<input id=t value=ab style='width:30px;padding:0;border:0'>");
        Element t = page.byId("t");
        t.focus();
        page.frame();
        page.input.preedit("中文输入预编辑", 7, 0, 7);
        float[] caret = page.input.textCaretRect();
        float[] rect = t.getBoundingClientRect();
        assertTrue(t.scrollLeft() > 0);
        assertTrue(caret[0] <= rect[0] + rect[2]);
        assertEquals("ab", t.value());
        page.input.preedit(null, 0, 0, 0);
        assertEquals(0, t.scrollLeft());
    }

    @Test
    void hitTestingDoesNotReturnOffsetsInsideAGrapheme() {
        Page page = new TestHost().load("<input id=t value='é👩‍👩‍👧‍👦🇨🇳'>");
        TextLayout layout = TextField.of(page.byId("t")).layout();
        for (int x = 0; x < 130; x++) {
            assertTrue(List.of(0, 2, 13, 17).contains(layout.offsetAt(0, x)));
        }
    }

    @Test
    void deletingMovingAndSelectingKeepExtendedGraphemesWhole() {
        for (String cluster : List.of("e\u0301", "👍🏽", "👩‍👩‍👧‍👦", "🇨🇳", "1️⃣")) {
            TextEditor editor = new TextEditor(false);
            editor.reset("a" + cluster + "b");
            editor.moveTo(1, false);
            editor.moveHorizontally(true, false, true);
            assertEquals(cluster, editor.selectedText(), cluster);
            editor.moveTo(1 + cluster.length(), false);
            editor.apply(editor.planDelete(false, false));
            assertEquals("ab", editor.text(), cluster);
            editor.undo();
            editor.moveTo(1, false);
            editor.apply(editor.planDelete(true, false));
            assertEquals("ab", editor.text(), cluster);
            editor.undo();
            editor.moveTo(2, false);
            assertEquals(1, editor.caret(), cluster);
        }
    }

    @Test
    void compositionKeysCannotDeleteTheCommittedSelectionOrSubmit() {
        Page page = new TestHost().load("<input id=t value=中文abc>");
        Element t = page.byId("t");
        t.focus();
        TextField field = TextField.of(t);
        field.select(0, 2);
        page.input.preedit("ni", 2, 0, 2);
        for (String key : List.of("Backspace", "Delete", "ArrowLeft", "Enter")) {
            page.input.keyDown(key, key, Modifiers.NONE);
            assertEquals("中文abc", t.value(), key);
            assertEquals("中文", field.editor.selectedText(), key);
        }
        assertTrue(page.input.keyDown("Escape", "Escape", Modifiers.NONE));
        assertNull(field.preedit());
        assertEquals("中文abc", t.value());
        assertFalse(field.editor.canUndo());
    }

    @Test
    void cancellationBlurAndCommitPreserveSelectionAndUndoBoundaries() {
        Page page = new TestHost().load("<input id=t value=ab>");
        Element t = page.byId("t");
        t.focus();
        page.input.charTyped("c");
        page.input.preedit("zhong", 5, 0, 5);
        page.input.preedit(null, 0, 0, 0);
        page.input.charTyped("中");
        TextField field = TextField.of(t);
        field.keyDown("z", new Modifiers(false, true, false, false));
        assertEquals("abc", t.value());
        field.select(0, 2);
        page.input.preedit("ni", 2, 0, 2);
        t.blur();
        assertNull(field.preedit());
        assertEquals("ab", field.editor.selectedText());
        assertEquals("abc", t.value());
    }

    @Test
    void candidateRectangleFollowsCssScale() {
        Page page = new TestHost().load("<input id=t value=ab style='transform:scale(2); transform-origin:0 0'>");
        Element t = page.byId("t");
        t.focus();
        page.frame();
        float[] before = page.input.textCaretRect();
        page.input.preedit("中", 1, 0, 1);
        float[] after = page.input.textCaretRect();
        TextField field = TextField.of(t);
        float advance = field.composedLayout().x(3) - field.layout().x(2);
        assertEquals(advance * 2, after[0] - before[0], 0.01);
        assertEquals(field.glyphHeight() * 2, after[2], 0.01);
    }

    @Test
    void narrowTextareasRespectTheSameCjkPunctuationRunsAsParagraphs() {
        for (String text : List.of("中。。文", "中文（（测试））结束", "中文abc中文，结束")) {
            for (int width : List.of(6, 12, 18, 30, 60)) {
                Page page = new TestHost().load("<p id=p style='width:" + width + "px;margin:0'>" + text
                        + "</p><textarea id=t style='width:" + width + "px;padding:0;border:0'>" + text + "</textarea>");
                TextLayout layout = TextField.of(page.byId("t")).layout();
                var expected = page.byId("p").box.lines.stream().map(line -> line.fragments.stream()
                        .filter(f -> f instanceof dev.oriel.engine.layout.Fragment.TextRun)
                        .map(f -> ((dev.oriel.engine.layout.Fragment.TextRun) f).text())
                        .collect(java.util.stream.Collectors.joining())).toList();
                var actual = java.util.stream.IntStream.range(0, layout.lineCount()).mapToObj(i -> layout.line(i).display()).toList();
                assertEquals(expected, actual, text + " / " + width);
            }
        }
    }
}
