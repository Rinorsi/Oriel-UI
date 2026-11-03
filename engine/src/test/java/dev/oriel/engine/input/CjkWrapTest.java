package dev.oriel.engine.input;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.layout.CjkBreaks;
import dev.oriel.engine.layout.Fragment;
import dev.oriel.engine.layout.LineBox;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CJK line breaking: paragraphs and text areas break between ideographs, never put closing punctuation at the
 * start of a line or opening punctuation at its end, and agree with each other. TestFonts advances CJK 6px, so a
 * 60px box holds ten characters.
 */
class CjkWrapTest {
    static final String TEXT = "优易库让界面像网页一样书写。中文标点不会出现在行首，“引号”也不会留在行尾！";

    private static List<String> paragraphLines(Element p) {
        List<String> out = new ArrayList<>();
        for (LineBox line : p.box.lines) {
            StringBuilder sb = new StringBuilder();
            for (Fragment f : line.fragments) if (f instanceof Fragment.TextRun r) sb.append(r.text());
            out.add(sb.toString());
        }
        return out;
    }

    @Test
    void paragraphsBreakBetweenIdeographs() {
        Page page = new TestHost().load("<p id=p style='width: 60px; margin: 0'>" + TEXT + "</p>");
        List<String> lines = paragraphLines(page.byId("p"));
        assertTrue(lines.size() >= 4, () -> "wrapped into " + lines);
        assertEquals(TEXT, String.join("", lines));
        for (String line : lines) {
            assertFalse("。，！”".indexOf(line.charAt(0)) >= 0, () -> "line starts with closing punctuation: " + lines);
            assertFalse(line.endsWith("“"), () -> "line ends with an opening quote: " + lines);
            assertTrue(line.codePointCount(0, line.length()) <= 11, () -> "line too long: " + lines);
        }
    }

    @Test
    void textareasBreakTheSameWay() {
        Page page = new TestHost().load("<p id=p style='width: 60px; margin: 0'>" + TEXT + "</p>"
                + "<textarea id=ta style='width: 60px; height: 90px; padding: 0; border: 0'>" + TEXT + "</textarea>");
        Element ta = page.byId("ta");
        ta.focus();
        page.frame();
        TextLayout layout = TextField.of(ta).layout();
        List<String> areaLines = new ArrayList<>();
        for (int i = 0; i < layout.lineCount(); i++) areaLines.add(layout.line(i).display());
        assertEquals(paragraphLines(page.byId("p")), areaLines);
    }

    @Test
    void latinWordsStillBreakOnlyAtSpaces() {
        Page page = new TestHost().load("<p id=p style='width: 30px; margin: 0'>abcdefgh ijk</p>");
        assertEquals(List.of("abcdefgh", "ijk"), paragraphLines(page.byId("p")));
    }

    @Test
    void mixedScriptsBreakAtTheBoundary() {
        // "Ore" then ideographs: a break may fall between the Latin word and the first ideograph.
        assertTrue(CjkBreaks.breakBetween('e', '像'));
        assertTrue(CjkBreaks.breakBetween('像', '素'));
        assertFalse(CjkBreaks.breakBetween('a', 'b'));
        assertFalse(CjkBreaks.breakBetween('素', '。'));
        assertFalse(CjkBreaks.breakBetween('「', '像'));
        assertFalse(CjkBreaks.breakBetween('ジ', 'ュ'), "small kana stay with the character before");
    }
}
