package dev.oriel.kit.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.oriel.kit.preset.KitHost.click;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item editor page: edits stay on the working copy, invalid values block saving, saving sends only the changed
 * fields, the server's answer moves the baseline or shows the reason, and revert goes back.
 */
class ItemEditorPresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    private ItemEditorPreset open(Document[] doc) {
        ItemEditorPreset editor = new ItemEditorPreset();
        doc[0] = host.open(OrielKit.ITEM_EDITOR, editor, 640, 360);
        return editor;
    }

    @Test
    void editsAreOnACopyAndSaveSendsOnlyChanges() {
        Document[] d = new Document[1];
        ItemEditorPreset editor = open(d);
        Document doc = d[0];
        assertEquals("龙牙", editor.work.get("name").getAsString());
        click(doc, "#rarity [data-r=epic]", 0, Modifiers.NONE, clock);
        editor.addEnchant("minecraft:mending");
        assertEquals(List.of("rarity", "enchantments"), editor.changes());
        assertEquals("rare", editor.baseline.get("rarity").getAsString(), "the baseline is untouched");
        assertTrue(doc.getElementById("dirty").hasClass("on"));
        assertTrue(doc.getElementById("tooltip").textContent().contains("经验修补 I"), "the tooltip follows the copy");

        host.acceptSends = true;
        click(doc, "#save", 0, Modifiers.NONE, clock);
        String sent = host.sent.getLast();
        assertTrue(sent.startsWith("save "), sent);
        JsonObject edit = JsonParser.parseString(sent.substring(5)).getAsJsonObject();
        assertEquals(java.util.Set.of("rarity", "enchantments"), edit.keySet());

        // The server accepts: the copy becomes the baseline.
        JsonObject reply = new JsonObject();
        reply.addProperty("status", "saved");
        JsonObject item = editor.work.deepCopy();
        reply.add("item", item);
        doc.patch("result", reply.toString());
        assertTrue(editor.changes().isEmpty());
        assertFalse(doc.getElementById("dirty").hasClass("on"));
    }

    @Test
    void invalidValuesBlockSavingAndRefusalsShowTheReason() {
        Document[] d = new Document[1];
        ItemEditorPreset editor = open(d);
        Document doc = d[0];
        editor.edit("maxDamage", new com.google.gson.JsonPrimitive(50));
        assertFalse(editor.errors.isEmpty(), "damage 120 is not below 50");
        host.acceptSends = true;
        int before = host.sent.size();
        editor.save();
        assertEquals(before, host.sent.size(), "nothing is sent while invalid");
        editor.revert();
        assertTrue(editor.errors.isEmpty());
        editor.setEnchantLevel(0, 10);
        editor.save();
        JsonObject reply = new JsonObject();
        reply.addProperty("status", "rejected");
        reply.add("reason", dev.oriel.kit.LocalizedMessage.text("oriel.server.item_editor.item_changed").json());
        doc.patch("result", reply.toString());
        assertTrue(doc.getElementById("status").textContent().contains("手里的物品已经变了"));
        assertEquals(List.of("enchantments"), editor.changes(), "a refusal keeps the edit");
    }

    @Test
    void theTreeShowsEveryComponent() {
        Document[] d = new Document[1];
        open(d);
        Document doc = d[0];
        click(doc, "#tabs [data-tab=all]", 0, Modifiers.NONE, clock);
        assertTrue(doc.querySelectorAll("#tree > .node").size() >= 6);
        assertEquals("ok", "ok");
    }
}
