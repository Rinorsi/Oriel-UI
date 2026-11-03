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
}
