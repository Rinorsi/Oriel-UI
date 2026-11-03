package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.drag;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The inventory preset driven through the page: drags, splits, carrying, quick moves, search and sort. */
class InventoryPresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void dragMovesAStackToAnEmptySlot() {
        InventoryPreset inv = new InventoryPreset();
        Document doc = host.open(OrielKit.INVENTORY, inv);
        assertNull(inv.slots[6]);
        drag(doc, "[data-i='3']", "[data-i='6']", 0, clock);
        assertNull(inv.slots[3]);
        assertEquals("minecraft:oak_log", inv.slots[6].id());
        assertEquals(64, inv.slots[6].count());
        assertEquals("minecraft:oak_log", doc.querySelector("[data-i='6'] item").getAttribute("id"));
    }

    @Test
    void dragOntoTheSameItemMergesAndOntoAnotherSwaps() {
        InventoryPreset inv = new InventoryPreset();
        Document doc = host.open(OrielKit.INVENTORY, inv);
        drag(doc, "[data-i='24']", "[data-i='10']", 0, clock); // bread 7 onto bread 12
        assertEquals(19, inv.slots[10].count());
        assertNull(inv.slots[24]);
        drag(doc, "[data-i='4']", "[data-i='5']", 0, clock); // torches onto ender pearls: swap
        assertEquals("minecraft:ender_pearl", inv.slots[4].id());
        assertEquals("minecraft:torch", inv.slots[5].id());
    }

    @Test
    void rightPressTakesHalfAndClickCarries() {
        InventoryPreset inv = new InventoryPreset();
        Document doc = host.open(OrielKit.INVENTORY, inv);
        drag(doc, "[data-i='11']", "[data-i='8']", 2, clock); // arrows 48: half to slot 8
        assertEquals(24, inv.slots[11].count());
        assertEquals(24, inv.slots[8].count());
        // A click without moving picks the stack up; the next click puts it down.
        click(doc, "[data-i='16']", 0, Modifiers.NONE, clock);
        assertNull(inv.slots[16]);
        assertTrue(doc.getElementById("ghost").hasClass("on"));
        click(doc, "[data-i='17']", 0, Modifiers.NONE, clock);
        assertEquals("minecraft:emerald", inv.slots[17].id());
        assertFalse(doc.getElementById("ghost").hasClass("on"));
    }

    @Test
    void shiftClickMovesBetweenHotbarAndGrid() {
        InventoryPreset inv = new InventoryPreset();
        Document doc = host.open(OrielKit.INVENTORY, inv);
        click(doc, "[data-i='" + (InventoryPreset.FIRST_HOTBAR + 2) + "']", 0, new Modifiers(true, false, false, false), clock);
        assertNull(inv.slots[InventoryPreset.FIRST_HOTBAR + 2]);
        assertEquals("minecraft:water_bucket", inv.slots[6].id(), "first empty main slot");
    }

    @Test
    void equipmentSlotsOnlyTakeTheirPiece() {
        InventoryPreset inv = new InventoryPreset();
        Document doc = host.open(OrielKit.INVENTORY, inv);
        int legs = InventoryPreset.FIRST_EQUIP + 2;
        drag(doc, "[data-i='3']", "[data-i='" + legs + "']", 0, clock);
        assertNull(inv.slots[legs], "logs are not leggings");
        assertEquals("minecraft:oak_log", inv.slots[3].id(), "and went back");
    }

    @Test
    void searchDimsTheRestAndBringsMatchesFirst() {
        InventoryPreset inv = new InventoryPreset();
        Document doc = host.open(OrielKit.INVENTORY, inv);
        inv.filter("面包");
        frame(doc, clock, 16);
        Element bread = doc.querySelector("[data-i='10']"), logs = doc.querySelector("[data-i='3']");
        assertTrue(bread.hasClass("match"));
        assertTrue(logs.hasClass("dim"));
        assertTrue(bread.getAttribute("style").contains("order: 0"), bread.getAttribute("style"));
        inv.filter("");
        frame(doc, clock, 16);
        assertFalse(logs.hasClass("dim"));
    }

    @Test
    void sortMergesAndOrdersByRarity() {
        InventoryPreset inv = new InventoryPreset();
        Document doc = host.open(OrielKit.INVENTORY, inv);
        click(doc, "#sort", 0, Modifiers.NONE, clock);
        assertEquals("epic", inv.slots[0].rarity());
        long breads = java.util.Arrays.stream(inv.slots, 0, InventoryPreset.MAIN)
                .filter(s -> s != null && s.id().equals("minecraft:bread")).count();
        assertEquals(1, breads, "the two bread stacks merged");
        for (int i = 0; i < InventoryPreset.MAIN; i++) {
            Element item = doc.querySelector("[data-i='" + i + "'] item");
            assertEquals(inv.slots[i] == null, item == null, "slot " + i + " shows its stack");
        }
    }
}
