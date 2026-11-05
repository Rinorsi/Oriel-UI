package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.GameEffects;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.AfterEach;
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
    void besideTheInventoryABigGroupIsChipsThatShowTheirCard() {
        Document doc = host.open(OrielKit.INVENTORY_EFFECTS, new InventoryEffectsPreset(), 640, 360);
        double[] clock = {0};
        for (int i = 0; i < 30; i++) frame(doc, clock, 16);
        Element beneficial = doc.querySelector(".group.beneficial"), harmful = doc.querySelector(".group.harmful");
        assertTrue(beneficial.hasClass("folded"), "more than " + PotionHudPreset.COMPACT_OVER + " effects start as chips");
        assertFalse(harmful.hasClass("folded"), "a small group keeps its rows");
        assertEquals(beneficial.querySelectorAll(".chip").size(), Integer.parseInt(beneficial.querySelector(".head .n").textContent()));
        assertTrue(doc.getElementById("side").hasClass("more-below"), "a cut-off panel says there is more below");

        // Hovering a chip shows that effect's card, as a row does.
        Element chip = beneficial.querySelector(".chip");
        float[] c = KitHost.centre(doc, ".group.beneficial .chip");
        doc.input().mouseMove(c[0], c[1], Modifiers.NONE);
        frame(doc, clock, 16);
        Element tip = doc.querySelector(".o-efftip");
        assertTrue(tip.hasClass("on"), "the chip's card shows");
        assertEquals(chip.querySelector("img").getAttribute("src"), tip.querySelector("img").getAttribute("src"));
        assertFalse(tip.querySelector(".left").textContent().isBlank(), "with its time left");

        // The header switches the group to rows, and the choice holds when the group changes.
        click(doc, ".group.beneficial .head", 0, Modifiers.NONE, clock);
        assertFalse(beneficial.hasClass("folded"));
        for (int i = 0; i < 700; i++) frame(doc, clock, 16); // arrivals and departures refresh the groups
        assertFalse(beneficial.hasClass("folded"), "the player's choice stays");
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

    /** U04: the card is placed by its measured size, so a long description near the window's edge never runs off it. */
    @Test
    void theCardStaysInsideTheWindowForEveryEffectAtEveryPosition() {
        // Long descriptions, as translated game text can be: the card grows to several lines.
        String longText = "Greatly increases the speed at which the player walks, sprints and swims, and widens the field of view while it lasts.";
        List<GameEffects.Entry> all = new java.util.ArrayList<>();
        String[] ids = {"speed", "haste", "strength", "regeneration", "resistance", "jump_boost", "night_vision", "fire_resistance", "water_breathing", "absorption", "luck", "slow_falling"};
        for (String id : ids) all.add(new GameEffects.Entry("minecraft:" + id, id, 2, GameEffects.Kind.BENEFICIAL, 600, "minecraft:textures/mob_effect/" + id + ".png", false, true, true, longText));
        GameEffects.provide(() -> new GameEffects.Snapshot(all));
        for (int[] size : new int[][] {{640, 360}, {427, 240}, {320, 200}}) {
            Document doc = host.open(OrielKit.INVENTORY_EFFECTS, new InventoryEffectsPreset(), size[0], size[1]);
            double[] clock = {0};
            for (int i = 0; i < 30; i++) frame(doc, clock, 16);
            float vw = doc.viewportWidth(), vh = doc.viewportHeight();
            int hovered = 0;
            for (Element target : doc.querySelectorAll(".o-effects .chip, .o-effects .row")) {
                float[] r = target.getBoundingClientRect();
                float cx = r[0] + r[2] / 2, cy = r[1] + r[3] / 2;
                if (r[2] <= 0 || cx < 0 || cx > vw || cy < 0 || cy > vh) continue; // scrolled out of the panel or window
                doc.input().mouseMove(cx, cy, Modifiers.NONE);
                frame(doc, clock, 16);
                Element tip = doc.querySelector(".o-efftip");
                if (!tip.hasClass("on")) continue;
                float[] t = tip.getBoundingClientRect();
                hovered++;
                String where = size[0] + "x" + size[1] + " " + tip.querySelector("b").textContent() + " card " + java.util.Arrays.toString(t);
                assertTrue(t[0] >= 0 && t[1] >= 0, "inside the window's top left: " + where);
                assertTrue(t[0] + t[2] <= vw + 0.5f && t[1] + t[3] <= vh + 0.5f, "inside the window's bottom right: " + where);
            }
            assertTrue(hovered > 3, "cards were shown at " + size[0] + "x" + size[1] + ": " + hovered);
        }
    }

    @AfterEach
    void fixturesAgain() {
        GameEffects.useFixtures();
    }

    private static GameEffects.Entry entry(String id, int level, GameEffects.Kind kind, int seconds) {
        return new GameEffects.Entry(id, id.substring(id.indexOf(':') + 1), level, kind, seconds,
                "minecraft:textures/mob_effect/" + id.substring(id.indexOf(':') + 1) + ".png", false, true, true);
    }

    @Test
    void inGameTheEffectsAreThePlayersOwn() {
        GameEffects.Snapshot[] game = {new GameEffects.Snapshot(List.of())};
        GameEffects.provide(() -> game[0]);
        PotionHudPreset hud = new PotionHudPreset();
        Document doc = host.open(OrielKit.POTIONS, hud);
        assertTrue(doc.getElementById("hud").hasClass("empty"), "nothing to show yet");
        assertEquals("身上没有药水效果", doc.querySelector(".hint").textContent());

        game[0] = new GameEffects.Snapshot(List.of(entry("minecraft:speed", 2, GameEffects.Kind.BENEFICIAL, 30),
                entry("minecraft:bad_omen", 7, GameEffects.Kind.NEUTRAL, -1)));
        frame(doc, clock, 50);
        assertFalse(doc.getElementById("hud").hasClass("empty"));
        assertEquals(2, doc.getElementById("tiles").childCount());
        assertTrue(doc.getElementById("toast").hasClass("on") || doc.getElementById("toast").childCount() > 0, "an arrival is announced");
        PotionHudPreset.Running speed = hud.groups.get(PotionHudPreset.Kind.BENEFICIAL).getFirst();
        assertEquals("speed II", speed.effect.name() + PotionHudPreset.level(speed.effect.level()));
        assertEquals(" VII", PotionHudPreset.level(7));
        assertEquals(" 11", PotionHudPreset.level(11));

        // A paused game reports nothing new: the countdown stops at the second it reported, and nothing expires.
        for (int i = 0; i < 100; i++) frame(doc, clock, 100);
        assertEquals("0:30", time(speed));
        assertEquals(2, doc.getElementById("tiles").childCount());

        // The effect the game no longer has fades out.
        game[0] = new GameEffects.Snapshot(List.of(entry("minecraft:bad_omen", 7, GameEffects.Kind.NEUTRAL, -1)));
        for (int i = 0; i < 10; i++) frame(doc, clock, 100);
        assertEquals(1, doc.getElementById("tiles").childCount());
        assertEquals("**:**", time(hud.groups.get(PotionHudPreset.Kind.NEUTRAL).getFirst()));
    }

    private static String time(PotionHudPreset.Running r) {
        return r.time.textContent();
    }
}
