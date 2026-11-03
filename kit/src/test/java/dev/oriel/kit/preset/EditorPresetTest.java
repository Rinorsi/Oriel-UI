package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The component editor: placing, selecting, dragging with snapping, the history, and export. */
class EditorPresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};
    private Document doc;

    private EditorPreset open() {
        EditorPreset editor = new EditorPreset();
        // The editor is made for the common 640x360 GUI; small windows hide the side panes.
        doc = host.open(OrielKit.EDITOR, editor, 640, 360);
        return editor;
    }

    @Test
    void theLibraryAddsPartsAndHistoryUndoesThem() {
        EditorPreset editor = open();
        int before = editor.parts.size();
        click(doc, ".palette .part[data-kind=slot]", 0, Modifiers.NONE, clock);
        assertEquals(before + 1, editor.parts.size());
        assertEquals("slot", editor.selected().getFirst().kind);
        editor.undo();
        assertEquals(before, editor.parts.size());
        editor.redo();
        assertEquals(before + 1, editor.parts.size());
    }

    @Test
    void draggingMovesTheSelectionOnTheGrid() {
        EditorPreset editor = open();
        EditorPreset.Part buy = editor.parts.stream().filter(p -> p.name.equals("购买按钮")).findFirst().orElseThrow();
        int x = buy.x, y = buy.y;
        float[] r = doc.querySelector(".node[data-id='" + buy.id + "']").getBoundingClientRect();
        float px = r[0] + 10, py = r[1] + 8;
        doc.input().mouseMove(px, py, Modifiers.NONE);
        doc.input().mouseDown(px, py, 0, Modifiers.NONE);
        for (int i = 1; i <= 5; i++) {
            doc.input().mouseMove(px - i * 3, py - i * 3, Modifiers.NONE);
            frame(doc, clock, 16);
        }
        doc.input().mouseUp(px - 15, py - 15, 0, Modifiers.NONE);
        frame(doc, clock, 16);
        assertTrue(buy.x < x && buy.y < y, "moved up and left: " + buy.x + "," + buy.y);
        editor.undo();
        // Undo restores copies, so look the part up again.
        assertEquals(x, editor.find(buy.id).x);
        assertEquals(y, editor.find(buy.id).y);
    }

    @Test
    void multiSelectDuplicateDeleteAndAlign() {
        EditorPreset editor = open();
        int title = editor.parts.get(1).id, sub = editor.parts.get(2).id;
        click(doc, ".node[data-id='" + title + "']", 0, Modifiers.NONE, clock);
        click(doc, ".layer[data-id='" + sub + "']", 0, new Modifiers(false, true, false, false), clock);
        assertEquals(2, editor.selection.size());
        editor.align("top");
        assertEquals(editor.find(title).y, editor.find(sub).y);
        int n = editor.parts.size();
        editor.duplicate();
        assertEquals(n + 2, editor.parts.size());
        editor.delete();
        assertEquals(n, editor.parts.size());
        assertTrue(editor.selection.isEmpty());
    }

    @Test
    void exportWritesEveryVisiblePart() {
        EditorPreset editor = open();
        editor.parts.getFirst().visible = false;
        String html = editor.html();
        assertTrue(html.contains("商人的货架"));
        assertTrue(html.contains("<model block=\"minecraft:enchanting_table\">"));
        assertFalse(html.contains("背景面板"));
        click(doc, "#export", 0, Modifiers.NONE, clock);
        assertTrue(doc.getElementById("export-box").hasClass("on"));
        assertNotNull(doc.getElementById("code").textContent());
    }
}
