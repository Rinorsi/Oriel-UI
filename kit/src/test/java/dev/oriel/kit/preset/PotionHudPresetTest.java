package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The potion HUD preset: countdowns, expiry, arrivals, and folding groups. */
class PotionHudPresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void effectsCountDownExpireAndArrive() {
        PotionHudPreset hud = new PotionHudPreset();
        Document doc = host.open(OrielKit.POTIONS, hud);
        int harmful = hud.groups.get(PotionHudPreset.Kind.HARMFUL).size();
        assertEquals(4, harmful);
        // Wither ends at 8 s and fades out over 0.6 s; water breathing arrives at 9 s.
        for (int i = 0; i < 100; i++) frame(doc, clock, 100);
        assertEquals(harmful - 1, hud.groups.get(PotionHudPreset.Kind.HARMFUL).size());
        assertTrue(hud.groups.get(PotionHudPreset.Kind.BENEFICIAL).stream().anyMatch(r -> r.effect.id().equals("water_breathing")));
        assertTrue(doc.getElementById("toast").hasClass("on"));
    }

    @Test
    void groupsShowThreeRowsAndExpandOrFold() {
        PotionHudPreset hud = new PotionHudPreset();
        Document doc = host.open(OrielKit.POTIONS, hud);
        click(doc, "#handle", 0, Modifiers.NONE, clock);
        assertTrue(doc.getElementById("hud").hasClass("full"), "the handle opens the full panel");
        Element buffs = doc.querySelectorAll(".group").getFirst();
        assertEquals(PotionHudPreset.SHOWN, buffs.querySelector(".list").childCount());
        assertEquals("另有 4 个效果 ▾", buffs.querySelector(".more").textContent());
        click(doc, ".group .more", 0, Modifiers.NONE, clock);
        assertEquals(7, buffs.querySelector(".list").childCount());
        click(doc, ".group .head", 0, Modifiers.NONE, clock);
        assertTrue(buffs.hasClass("folded"));
        assertEquals(7, buffs.querySelector(".strip").childCount());
    }

    @Test
    void rowsAreOrderedSoonestFirstAndBlinkAtTheEnd() {
        PotionHudPreset hud = new PotionHudPreset();
        Document doc = host.open(OrielKit.POTIONS, hud);
        List<PotionHudPreset.Running> harmful = hud.groups.get(PotionHudPreset.Kind.HARMFUL);
        assertEquals("wither", harmful.getFirst().effect.id());
        frame(doc, clock, 16);
        assertTrue(harmful.getFirst().row.hasClass("low"), "8 s left");
        assertEquals("0:08", harmful.getFirst().time.textContent());
        assertEquals("**:**", PotionHudPreset.clock(3600));
    }

    @Test
    void theBarShowsATileForEachEffectThatDrains() {
        PotionHudPreset hud = new PotionHudPreset();
        Document doc = host.open(OrielKit.POTIONS, hud);
        assertEquals(PotionHudPreset.BAR_TILES, doc.getElementById("tiles").childCount());
        assertEquals("+" + (PotionHudPreset.INITIAL.size() - PotionHudPreset.BAR_TILES), doc.getElementById("more-tiles").textContent());
        for (int i = 0; i < 40; i++) frame(doc, clock, 100);
        PotionHudPreset.Running regen = hud.groups.get(PotionHudPreset.Kind.BENEFICIAL).stream()
                .filter(r -> r.effect.id().equals("regeneration")).findFirst().orElseThrow();
        assertTrue(regen.tileTime.textContent().matches("1[45]s"), "about 14 s left: " + regen.tileTime.textContent());
        assertTrue(regen.tile.querySelector(".drain").getAttribute("style").contains("height"), "the tile drains");
    }

    @Test
    void theInventoryCarriesTheFullPanelInItsOwnTab() {
        Document doc = host.open(OrielKit.INVENTORY, new InventoryPreset(), 640, 360);
        var groups = doc.querySelectorAll("#effects .group");
        assertEquals(3, groups.size());
        assertFalse(groups.getFirst().hasClass("folded"), "the tab has the room for every row");
        assertTrue(doc.getElementById("effects").hasClass("off"));
        double[] clock = {0};
        KitHost.click(doc, "#info-tabs [data-pane=effects]", 0, dev.oriel.engine.event.Modifiers.NONE, clock);
        assertFalse(doc.getElementById("effects").hasClass("off"));
        assertTrue(doc.getElementById("details").hasClass("off"));
        assertTrue(doc.getElementById("effects").getBoundingClientRect()[3] > 200, "the panel takes the column's height");
    }
}
