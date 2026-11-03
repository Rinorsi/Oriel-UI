package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.drag;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hot reload hands the old controller's state to the new one (DocumentDriver and the previewer do this when a page
 * is saved): what the player had done survives an edit of the page.
 */
class HotReloadStateTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void theInventoryKeepsItsMovesAndFilter() {
        InventoryPreset before = new InventoryPreset();
        Document doc = host.open(OrielKit.INVENTORY, before);
        drag(doc, "[data-i='4']", "[data-i='6']", 0, clock);
        click(doc, "[data-cat=food]", 0, Modifiers.NONE, clock);
        Object state = before.saveState();

        InventoryPreset after = new InventoryPreset();
        Document reloaded = host.open(OrielKit.INVENTORY, after);
        after.restoreState(state);
        assertEquals("minecraft:torch", after.slots[6].id());
        assertTrue(reloaded.querySelector("[data-cat=food]").hasClass("on"));
        assertTrue(reloaded.querySelector("[data-i='3']").hasClass("dim"), "logs are not food");
    }

    @Test
    void settingsKeepTheirValuesAndTab() {
        SettingsPreset before = new SettingsPreset();
        Document doc = host.open(OrielKit.SETTINGS, before);
        click(doc, "[data-key=vsync] .ore-toggle", 0, Modifiers.NONE, clock);
        before.show("声音", false);
        Object state = before.saveState();

        SettingsPreset after = new SettingsPreset();
        host.open(OrielKit.SETTINGS, after);
        after.restoreState(state);
        assertEquals(false, after.values.get("vsync"));
        assertEquals("声音", after.tab);
        assertEquals(1, after.changes());
    }

    @Test
    void theEditorKeepsItsPartsAndHistory() {
        EditorPreset before = new EditorPreset();
        host.open(OrielKit.EDITOR, before, 640, 360);
        before.place("slot", 40, 40);
        int parts = before.parts.size();
        Object state = before.saveState();

        EditorPreset after = new EditorPreset();
        host.open(OrielKit.EDITOR, after, 640, 360);
        after.restoreState(state);
        assertEquals(parts, after.parts.size());
        after.undo();
        assertEquals(parts - 1, after.parts.size(), "the history came along");
    }
}
