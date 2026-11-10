package dev.oriel.engine.input;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.oriel.engine.testing.Page.*;
import static org.junit.jupiter.api.Assertions.*;

class TextHighlightsTest {
    private final Page page = new TestHost().load("<textarea id=t wrap=off style='width:160px;height:70px;font-size:12px;line-height:16px'>a:1\nb:2</textarea>");
    private final Element t = page.byId("t");

    @Test void paintsNativeOffsetSpansAndNumbersWithoutReplacingTheModel() {
        var model = TextField.of(t).editor;
        assertTrue(TextHighlights.set(t, t.value(), List.of(new TextHighlights.Range(2,3,0xFFFF0000), new TextHighlights.Range(6,7,0xFF00FF00)), true));
        page.frame();
        var calls = page.paint().ops("drawText");
        assertTrue(calls.stream().anyMatch(c -> "1".equals(c.text()) && c.color() == 0xFFFF0000));
        assertTrue(calls.stream().anyMatch(c -> "2".equals(c.text()) && c.color() == 0xFF00FF00));
        assertTrue(TextField.of(t).gutter() > 0);
        assertSame(model, TextField.of(t).editor);
        assertEquals(2, TextField.of(t).layout().lineCount());
        var red = calls.stream().filter(c -> c.color() == 0xFFFF0000).findFirst().orElseThrow();
        assertEquals(TextField.of(t).textX() + TextField.of(t).layout().line(0).x(2), red.x(), 1);
        assertNotNull(red.clip(), "native text/gutter clip");
    }

    @Test void nativeMultilineSelectionPasteUndoRedoFontAndImeSurviveDecorations() {
        t.focus(); page.key("a", SHORTCUT);
        TextHighlights.set(t,t.value(),List.of(),true);
        assertEquals(t.value(), TextField.of(t).editor.selectedText());
        page.host.clipboard="{\n  text: \"中文\",\n  value: 2L\n}";
        page.key("v",SHORTCUT);
        String pasted=t.value();
        TextHighlights.set(t,pasted,List.of(new TextHighlights.Range(0,1,0xFFFF0000)),true);
        page.key("Home",SHORTCUT); page.key("ArrowDown",SHIFT);
        String selected=TextField.of(t).editor.selectedText();
        t.setAttribute("style","width:160px;height:70px;font-size:18px;line-height:20px"); page.frame();
        assertEquals(selected,TextField.of(t).editor.selectedText());
        page.key("z",SHORTCUT); assertEquals("a:1\nb:2",t.value());
        page.key("z",SHORTCUT_SHIFT); assertEquals(pasted,t.value());
        TextField field=TextField.of(t); field.preedit("拼音",1,0,2);
        page.paint(); assertEquals(pasted,t.value()); assertEquals("拼音",field.preedit());
        field.preedit(null,0,0,0);
        assertSame(field,TextField.of(t));
    }

    @Test void staleInvalidOverlappingAndSurrogateRangesCannotCorruptText() {
        assertFalse(TextHighlights.set(t,"old",List.of(),true));
        assertThrows(IllegalArgumentException.class,()->TextHighlights.set(t,t.value(),List.of(new TextHighlights.Range(0,3,1),new TextHighlights.Range(2,4,2)),true));
        t.setValue("a😀b");
        assertThrows(IllegalArgumentException.class,()->TextHighlights.set(t,t.value(),List.of(new TextHighlights.Range(1,2,1)),false));
        assertEquals("a😀b",t.value());
    }

    @Test void rhinoBindingAndHorizontalCaretScrollingUseTheSameField() {
        page.run("var ta=document.getElementById('t'); ta.setTextHighlights(ta.value,[{start:2,end:3,color:4294901760}],true);");
        assertEquals("false",page.eval("ta.setTextHighlights('stale',[],true)"));
        t.focus();page.key("a",SHORTCUT);page.type("abcdefghijklmnopqrstuvxyzabcdefghijklmnopqrstuvxyz\nend");
        page.frame();
        page.key("Home",SHORTCUT);page.key("End");page.frame();
        assertTrue(t.scrollLeft()>0);
        assertEquals(2,TextField.of(t).layout().lineCount());
    }

    @Test void scriptDiagnosticSelectionUsesNativeCaretAndKeepsUndo() {
        t.focus(); page.type("x");
        String edited = t.value();
        page.run("var ta=document.getElementById('t'); ta.setSelectionRange(2,4);");
        assertEquals("2", page.eval("ta.selectionStart"));
        assertEquals("4", page.eval("ta.selectionEnd"));
        assertEquals(edited, t.value());
        page.key("z",SHORTCUT); assertEquals("a:1\nb:2",t.value());
    }

    @Test void staleOrRejectedUpdatesKeepCurrentDecorationAndTextEditsSuppressOldColors() {
        var field = TextField.of(t);
        var ranges = List.of(new TextHighlights.Range(0, 1, 0xFFFF0000));
        assertTrue(TextHighlights.set(t, t.value(), ranges, true));
        var accepted = field.highlights;
        assertFalse(TextHighlights.set(t, "stale", List.of(), false));
        assertSame(accepted, field.highlights);
        assertThrows(IllegalArgumentException.class, () -> TextHighlights.set(t, t.value(),
                List.of(new TextHighlights.Range(-1, 1, 0)), false));
        assertThrows(IllegalArgumentException.class, () -> TextHighlights.set(t, t.value(),
                java.util.Collections.nCopies(100_001, ranges.getFirst()), false));
        assertSame(accepted, field.highlights);
        t.setValue("changed");
        assertTrue(page.paint().ops("drawText").stream().noneMatch(c -> c.color() == 0xFFFF0000));
        assertEquals("changed", t.value());
    }

    @Test void scriptRejectsNonIntegralOffsetsAndInvalidArgbWithoutMutatingTheField() {
        page.run("var ta=document.getElementById('t'); ta.setTextHighlights(ta.value,[],true);");
        var accepted = TextField.of(t).highlights;
        for (String span : new String[] {
                "{start:0.5,end:1,color:1}", "{start:0,end:Infinity,color:1}",
                "{start:0,end:1,color:NaN}", "{start:0,end:1,color:-1}",
                "{start:0,end:1,color:4294967296}", "{start:0,end:1,color:1.5}"}) {
            assertEquals("TypeError", page.eval("(function(){try {ta.setTextHighlights(ta.value,[" + span
                    + "],false);return 'accepted';}catch(e){return e.name;}})()"), span);
            assertSame(accepted, TextField.of(t).highlights);
            assertEquals("a:1\nb:2", t.value());
        }
    }

    @Test void unicodeSpansAcrossHardLinesAndDisablingNumbersPreserveSelectionAndHistory() {
        t.focus();
        page.key("a", SHORTCUT);
        page.host.clipboard = "a😀\nb中";
        page.key("v", SHORTCUT);
        var field = TextField.of(t);
        assertTrue(TextHighlights.set(t, t.value(), List.of(new TextHighlights.Range(1, 6, 0xFF00FF00)), true));
        page.frame();
        var colored = page.paint().ops("drawText").stream().filter(c -> c.color() == 0xFF00FF00).toList();
        assertEquals(List.of("😀", "b中"), colored.stream().map(c -> c.text()).toList());
        TextSelection.select(t, 1, 6);
        assertTrue(TextHighlights.set(t, t.value(), List.of(), false));
        page.frame();
        assertEquals(0, field.gutter());
        assertEquals(1, TextSelection.start(t));
        assertEquals(6, TextSelection.end(t));
        assertTrue(t.isFocused());
        page.key("z", SHORTCUT);
        assertEquals("a:1\nb:2", t.value());
    }

    @Test void paintedKeyStringAndNumberAreDirectlyClickableAndEditable() {
        String text = "key:\"hi\",n:2\nnext:3";
        t.setValue(text);
        t.setAttribute("style", "position:absolute;left:0;top:0;width:160px;height:70px;font-size:12px;line-height:16px");
        var spans = List.of(new TextHighlights.Range(0,3,0xFF7FC8FF),
                new TextHighlights.Range(4,8,0xFFE6B673),new TextHighlights.Range(11,12,0xFFB5E07A));
        var model = TextField.of(t).editor;
        for (int offset : new int[]{1,5,11}) {
            TextHighlights.set(t,text,spans,true);page.frame(1000);page.paint();
            var field = TextField.of(t);var line = field.layout().line(0);
            page.click(field.textX()+line.x(offset)+0.1f,field.glyphY(0)+2);
            assertTrue(t.isFocused());assertEquals(offset,model.caret(),"colored glyph hit offset");
            page.key("Delete");assertEquals(text.substring(0,offset)+text.substring(offset+1),t.value());
            page.key("z",SHORTCUT);assertEquals(text,t.value());assertSame(model,TextField.of(t).editor);
        }
    }
}
