package dev.oriel.engine.input;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input method composition: the preedit shows inline without changing the value, the caret rectangle (for the
 * candidate window) moves with it, commit arrives as typed text, and blur ends a composition.
 */
class PreeditTest {
    private final Page page = new TestHost().load("<input id=t value=ab style=\"position: absolute; left: 10px; top: 20px\">");
    private final Element t = page.byId("t");

    @Test
    void compositionShowsWithoutChangingTheValueThenCommits() {
        t.focus();
        page.frame();
        float[] before = page.input.textCaretRect();
        assertNotNull(before);
        assertTrue(page.input.preedit("zhong", 5, 0, 5));
        assertEquals("ab", t.value(), "composing is not typing");
        TextField field = TextField.of(t);
        assertEquals("zhong", field.preedit());
        float[] during = page.input.textCaretRect();
        assertTrue(during[0] > before[0], "the caret sits at the end of the composition");
        assertEquals(before[1], during[1], 0.01);

        // The input method converts and commits: the composition ends, the text comes as typed characters.
        assertTrue(page.input.preedit("中文", 2, 0, 2));
        assertTrue(page.input.preedit(null, 0, 0, 0));
        assertTrue(page.input.charTyped("中文"));
        assertEquals("ab中文", t.value());
        assertNull(field.preedit());
    }

    @Test
    void compositionReplacesTheSelectionOnlyVisually() {
        t.focus();
        TextField field = TextField.of(t);
        field.editor.selectAll();
        page.input.preedit("x", 1, 0, 1);
        assertEquals(0, field.composeAt());
        assertEquals("x", field.composedLayout().line(0).display());
        assertEquals("ab", t.value());
    }

    @Test
    void blurEndsTheComposition() {
        t.focus();
        page.input.preedit("ni", 2, 0, 2);
        t.blur();
        assertNull(TextField.of(t).preedit());
        assertFalse(page.input.preedit("ni", 2, 0, 2), "nothing focused takes it");
    }
}
