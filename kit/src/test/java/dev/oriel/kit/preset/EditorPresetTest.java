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
        doc.querySelector(".layer[data-id='" + sub + "']").scrollIntoView(dev.oriel.engine.dom.Element.ScrollAlign.CENTER,
                dev.oriel.engine.dom.Element.ScrollAlign.NEAREST, dev.oriel.engine.dom.Element.ScrollBehavior.INSTANT);
        frame(doc, clock, 16);
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

    @Test
    void aNewCanvasHasItsSizeAndAnEmptyHistory() {
        EditorPreset editor = open();
        click(doc, "#menus .o-menu:nth-child(1) .title", 0, Modifiers.NONE, clock);
        click(doc, "#new", 0, Modifiers.NONE, clock);
        assertTrue(doc.getElementById("new-box").hasClass("on"));
        click(doc, "#new-sizes .size[data-w='176']", 0, Modifiers.NONE, clock);
        assertEquals("166", doc.getElementById("new-h").value());
        click(doc, "#new-ok", 0, Modifiers.NONE, clock);
        assertFalse(doc.getElementById("new-box").hasClass("on"));
        assertEquals(0, editor.parts.size(), "blank");
        assertTrue(editor.undo.isEmpty());
        frame(doc, clock, 16);
        float[] board = doc.getElementById("board").getBoundingClientRect();
        assertEquals(176, board[2], 0.5f);
        assertEquals(166, board[3], 0.5f);
        assertTrue(doc.getElementById("board-label").textContent().contains("176 × 166"));
    }

    @Test
    void copyCutAndPaste() {
        EditorPreset editor = open();
        int before = editor.parts.size();
        editor.selection.add(editor.parts.getLast().id);
        doc.input().keyDown("c", "KeyC", new Modifiers(false, true, false, false));
        doc.input().keyDown("v", "KeyV", new Modifiers(false, true, false, false));
        assertEquals(before + 1, editor.parts.size());
        doc.input().keyDown("x", "KeyX", new Modifiers(false, true, false, false));
        assertEquals(before, editor.parts.size(), "the pasted copy was cut");
        doc.input().keyDown("v", "KeyV", new Modifiers(false, true, false, false));
        assertEquals(before + 1, editor.parts.size());
    }

    @Test
    void thePickerListsItemsByName() {
        EditorPreset editor = open();
        EditorPreset.Part slot = editor.parts.stream().filter(p -> p.kind.equals("slot")).findFirst().orElseThrow();
        editor.selection.clear();
        editor.selection.add(slot.id);
        editor.render();
        frame(doc, clock, 16);
        assertTrue(doc.getElementById("picked").textContent().contains("钻石剑"), "the game's name, not only the id");
        click(doc, "#picked", 0, Modifiers.NONE, clock);
        assertTrue(doc.getElementById("pick-box").hasClass("on"));
        var q = doc.getElementById("pick-q");
        q.setValue("绿宝石");
        q.dispatchEvent(new dev.oriel.engine.event.Event("input", true, false));
        assertEquals(1, doc.querySelectorAll("#pick-grid .pick").size());
        click(doc, "#pick-grid .pick", 0, Modifiers.NONE, clock);
        assertEquals("minecraft:emerald", slot.text);
        assertFalse(doc.getElementById("pick-box").hasClass("on"));
    }

    @Test
    void menusAndTheRightClickMenuRunCommands() {
        EditorPreset editor = open();
        click(doc, "#menus .o-menu:nth-child(2) .title", 0, Modifiers.NONE, clock);
        assertTrue(doc.querySelector("#menus .o-menu:nth-child(2)").hasClass("open"), "the 编辑 menu opens");
        click(doc, "#menus .o-menu:nth-child(2) [data-cmd=all]", 0, Modifiers.NONE, clock);
        assertFalse(doc.querySelector("#menus .o-menu:nth-child(2)").hasClass("open"), "choosing closes it");
        assertEquals(editor.parts.size(), editor.selection.size());
        // A right press on a part selects it and opens the menu there.
        int before = editor.parts.size();
        editor.selection.clear();
        editor.render();
        click(doc, ".node.k-slot", 2, Modifiers.NONE, clock);
        assertTrue(doc.getElementById("ctx").hasClass("open"));
        assertEquals(1, editor.selection.size());
        click(doc, "#ctx [data-cmd=dup]", 0, Modifiers.NONE, clock);
        assertFalse(doc.getElementById("ctx").hasClass("open"));
        assertEquals(before + 1, editor.parts.size());
    }
}
