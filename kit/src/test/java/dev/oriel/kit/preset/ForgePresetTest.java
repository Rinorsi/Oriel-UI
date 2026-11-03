package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The forge preset: the strike judgement, the furnace model and a full forging through the page. */
class ForgePresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void strikesAreJudgedByTheMarker() {
        ForgePreset.Model m = new ForgePreset.Model();
        m.temp = 1200;
        m.zoneCenter = 0.5f;
        m.phase = 0; // sin(0) = 0: the marker sits at the middle
        assertEquals("perfect", m.strike(new Random(1)));
        m.zoneCenter = 0.5f;
        m.phase = (float) Math.asin(0.15); // marker at 0.575: inside the 0.12 zone, outside the core
        assertEquals("hit", m.strike(new Random(1)));
        m.zoneCenter = 0.5f;
        m.phase = (float) (Math.PI / 2); // marker at 1
        assertEquals("miss", m.strike(new Random(1)));
        m.temp = 600;
        assertEquals("cold", m.strike(new Random(1)));
        assertEquals(14 + 8 - 5 + 1, Math.round(m.quality));
    }

    @Test
    void theFurnaceHeatsWithTheBellowsAndCools() {
        ForgePreset.Model m = new ForgePreset.Model();
        float start = m.temp;
        m.bellows = true;
        m.advance(1);
        assertEquals(Math.min(ForgePreset.MAX_TEMP, start + ForgePreset.HEAT_RATE), m.temp, 0.01);
        m.bellows = false;
        m.advance(1);
        assertEquals(Math.min(ForgePreset.MAX_TEMP, start + ForgePreset.HEAT_RATE) - ForgePreset.COOL_RATE, m.temp, 0.01);
        m.advance(100);
        assertEquals(ForgePreset.MIN_TEMP, m.temp, 0.01);
    }

    @Test
    void eightStrikesThenQuench() {
        ForgePreset forge = new ForgePreset();
        Document doc = host.open(OrielKit.FORGE, forge);
        for (int i = 0; i < ForgePreset.STRIKES + 2; i++) {
            click(doc, "#strike", 0, Modifiers.NONE, clock);
            frame(doc, clock, 100);
        }
        assertEquals(ForgePreset.STRIKES, forge.model.strikes);
        assertEquals(ForgePreset.STRIKES, doc.querySelectorAll(".pip.perfect, .pip.hit, .pip.miss").size());
        assertTrue(forge.sparkCount() > 0, "strikes throw sparks");
        click(doc, "#quench", 0, Modifiers.NONE, clock);
        assertTrue(doc.getElementById("result").hasClass("on"));
        click(doc, "#again", 0, Modifiers.NONE, clock);
        assertEquals(0, forge.model.strikes);
    }

    @Test
    void heatColourRamp() {
        assertEquals(255, ForgePreset.heatColour(1600)[0]);
        assertTrue(ForgePreset.heatColour(700)[0] < ForgePreset.heatColour(1100)[0]);
        assertTrue(ForgePreset.heatColour(1100)[1] < ForgePreset.heatColour(1400)[1]);
    }
}
