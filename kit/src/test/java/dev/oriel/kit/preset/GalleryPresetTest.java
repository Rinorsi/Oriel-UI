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
        click(doc, ".stage .actions [data-go=inventory]", 0, Modifiers.NONE, clock);
        assertTrue(doc.querySelector(".m-zoom") != null, "the start button grows into the page first");
        frame(doc, clock, 400);
        assertEquals(List.of(OrielKit.INVENTORY), host.navigations);
        click(doc, ".card[data-go=catalog]", 0, Modifiers.NONE, clock);
        frame(doc, clock, 400);
        assertEquals(OrielKit.CATALOG, host.navigations.getLast());
        click(doc, ".card[data-go=modsettings]", 0, Modifiers.NONE, clock);
        frame(doc, clock, 400);
        assertEquals(OrielKit.MODSETTINGS, host.navigations.getLast());
        click(doc, ".card[data-go=about]", 0, Modifiers.NONE, clock);
        frame(doc, clock, 400);
        assertEquals(OrielKit.ABOUT, host.navigations.getLast());
        assertTrue(host.sent.isEmpty(), "the tiles send the host no message");
        click(doc, "#close", 0, Modifiers.NONE, clock);
        assertTrue(host.closed);
    }

    @Test
    void communityButtonOpensTheCentreOnAllTemplates() {
        Document doc = host.open(OrielKit.GALLERY, new GalleryPreset());
        click(doc, "[data-community]", 0, Modifiers.NONE, clock);
        frame(doc, clock, 400);
        assertEquals(OrielKit.STYLES, host.navigations.getLast());
        assertEquals(0, doc.querySelectorAll("[data-demos]").size(), "the demos button is gone");
    }

    @Test
    void communityPicksTurnOverAndOpenTheirTemplate() {
        Document doc = host.open(OrielKit.GALLERY, new GalleryPreset());
        var slides = doc.querySelectorAll("#picks .slide");
        assertEquals(GalleryPreset.PICKS.size(), slides.size());
        assertTrue(slides.get(0).hasClass("on"));
        assertTrue(slides.get(0).querySelector(".cap b").textContent().contains("·"), "the title shows");
        assertEquals("云诺羲", slides.get(0).querySelector(".cap span").textContent(), "the author shows");
        click(doc, "#pick-next", 0, Modifiers.NONE, clock);
        assertTrue(slides.get(1).hasClass("on"));
        assertTrue(!slides.get(0).hasClass("on"));
        click(doc, "#pick-prev", 0, Modifiers.NONE, clock);
        assertTrue(slides.get(0).hasClass("on"));
        assertTrue(host.navigations.isEmpty(), "the arrows turn the picks, they do not open one");
        frame(doc, clock, 5000);
        assertTrue(slides.get(1).hasClass("on"), "the picks turn on their own");
        click(doc, "#picks", 0, Modifiers.NONE, clock);
        frame(doc, clock, 400);
        assertEquals(OrielKit.STYLES, host.navigations.getLast());
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
