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
        assertTrue(doc.querySelector(".m-zoom") != null, "the tile grows into the page first");
        frame(doc, clock, 400);
        assertEquals(List.of(OrielKit.FORGE), host.navigations);
        // The HUD tile opens the potion HUD preview page; it asks the host for nothing and changes nothing in the game.
        click(doc, ".card[data-page=potions]", 0, Modifiers.NONE, clock);
        assertTrue(host.sent.isEmpty(), "the tile sends the host no message");
        assertEquals(OrielKit.POTIONS, host.navigations.getLast());
        click(doc, "#close", 0, Modifiers.NONE, clock);
        assertTrue(host.closed);
    }

    @Test
    void themeSwitchesAndSticks() {
        Document doc = host.open(OrielKit.GALLERY, new GalleryPreset());
        assertTrue(doc.querySelector("[data-action=theme] img").getAttribute("src").endsWith("sun.png"));
        click(doc, "[data-action=theme]", 0, Modifiers.NONE, clock);
        assertEquals("light", doc.documentElement().getAttribute("data-theme"));
        assertTrue(doc.querySelector("[data-action=theme]").hasClass("on"));
        assertTrue(doc.querySelector("[data-action=theme] img").getAttribute("src").endsWith("moon.png"), "the same button as every page");
        Document next = host.open(OrielKit.GALLERY, new GalleryPreset());
        assertEquals("light", next.documentElement().getAttribute("data-theme"));
        Shell.theme(next, "dark");
    }

    @Test
    void particlesFillTheirCanvases() {
        Document doc = host.open(OrielKit.GALLERY, new GalleryPreset());
        frame(doc, clock, 2000);
        var canvas = doc.querySelector(".stage canvas[data-fx=stars]");
        // The wall-clock callback budget may defer this RAF while the migration oracle is running.
        for (int i = 0; i < 10 && canvas.getAttribute("width") == null; i++) frame(doc, clock, 16);
        assertTrue(canvas.getAttribute("width") != null, "the queued particle callback eventually runs");
        assertTrue(Integer.parseInt(canvas.getAttribute("width")) > 100, "the canvas takes its box's width");
    }
}
