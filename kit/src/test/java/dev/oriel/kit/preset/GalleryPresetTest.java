package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The launcher: tiles open their presets, the theme switches, particles run. */
class GalleryPresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void tilesOpenTheirPages() {
        Document doc = host.open(OrielKit.GALLERY, new GalleryPreset());
        click(doc, ".card[data-go=forge]", 0, Modifiers.NONE, clock);
        assertEquals(List.of(OrielKit.FORGE), host.navigations);
        // The HUD tile asks the host first; this host declines, so the page opens instead.
        click(doc, ".card[data-page=potions]", 0, Modifiers.NONE, clock);
        assertEquals("oriel \"hud\"", host.sent.getLast());
        assertEquals(OrielKit.POTIONS, host.navigations.getLast());
        click(doc, "#close", 0, Modifiers.NONE, clock);
        assertTrue(host.closed);
    }

    @Test
    void themeSwitchesAndSticks() {
        Document doc = host.open(OrielKit.GALLERY, new GalleryPreset());
        click(doc, "[data-theme-pick=light]", 0, Modifiers.NONE, clock);
        assertEquals("light", doc.documentElement().getAttribute("data-theme"));
        assertTrue(doc.querySelector("[data-theme-pick=light]").hasClass("on"));
        Document next = host.open(OrielKit.GALLERY, new GalleryPreset());
        assertEquals("light", next.documentElement().getAttribute("data-theme"));
        Shell.theme(next, "dark");
    }

    @Test
    void particlesFillTheirCanvases() {
        Document doc = host.open(OrielKit.GALLERY, new GalleryPreset());
        frame(doc, clock, 2000);
        var canvas = doc.querySelector(".banner canvas[data-fx=stars]");
        assertTrue(Integer.parseInt(canvas.getAttribute("width")) > 100, "the canvas takes its box's width");
    }
}
