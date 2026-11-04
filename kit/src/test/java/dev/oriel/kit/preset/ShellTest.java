package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What every page gets from Shell: title bar tabs with directed transitions, the window switch, live demo controls. */
class ShellTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @AfterEach
    void reset() {
        Shell.window(host.open(OrielKit.GALLERY, new GalleryPreset()), "max");
    }

    @Test
    void tabsSwitchPagesWithADirection() {
        Document home = host.open(OrielKit.GALLERY, new GalleryPreset(), 640, 360);
        click(home, ".o-apptabs [data-go=catalog]", 0, Modifiers.NONE, clock);
        assertTrue(home.querySelector(".o-body").hasClass("m-page-leave-next"), "the content leaves to the left");
        frame(home, clock, 200);
        assertEquals(List.of(OrielKit.CATALOG), host.navigations);
        Document catalog = host.open(OrielKit.CATALOG, new CatalogPreset(), 640, 360);
        assertTrue(catalog.querySelector(".o-body").hasClass("m-page-next"), "and the new content comes in from the right");
        // The indicator starts under the old tab, then glides to this page's.
        frame(catalog, clock, 16);
        frame(catalog, clock, 16);
        frame(catalog, clock, 400);
        float[] bar = catalog.querySelector(".o-apptabs .m-indicator").getBoundingClientRect();
        float[] tab = catalog.querySelector(".o-apptabs .on").getBoundingClientRect();
        assertEquals(tab[0], bar[0], 1f);
        // Back to the home tab is the other way; the current tab does nothing.
        click(catalog, ".o-apptabs .on", 0, Modifiers.NONE, clock);
        assertFalse(catalog.querySelector(".o-body").hasClass("m-page-leave-next"));
        click(catalog, ".o-apptabs [data-go=gallery]", 0, Modifiers.NONE, clock);
        assertTrue(catalog.querySelector(".o-body").hasClass("m-page-leave-prev"));
    }

    @Test
    void windowSwitchIsAddedAndSticks() {
        Document home = host.open(OrielKit.GALLERY, new GalleryPreset(), 960, 540);
        assertNotNull(home.querySelector(".o-titlebar [data-action=window]"), "the switch sits in the title bar");
        float full = home.querySelector(".o-window").getBoundingClientRect()[2];
        click(home, "[data-action=window]", 0, Modifiers.NONE, clock);
        assertEquals("mini", home.documentElement().getAttribute("data-window"));
        frame(home, clock, 600);
        float mini = home.querySelector(".o-window").getBoundingClientRect()[2];
        assertTrue(mini < full - 100, "the window shrinks to the centred size: " + full + " -> " + mini);
        Document settings = host.open(OrielKit.SETTINGS, new SettingsPreset(), 960, 540);
        assertEquals("mini", settings.documentElement().getAttribute("data-window"), "every page opens in the same mode");
    }

    @Test
    void demoControlsWorkWithoutCode() {
        Document doc = host.open(OrielKit.CATALOG, new CatalogPreset(), 640, 360);
        click(doc, "[data-sec=tabs]", 0, Modifiers.NONE, clock); // not required: sections are in the DOM either way
        var tabs = doc.querySelectorAll(".sec .ore-tabs .ore-tab");
        tabs.get(2).click();
        assertTrue(tabs.get(2).hasClass("selected"));
        assertFalse(tabs.get(0).hasClass("selected"));
        var seg = doc.querySelectorAll("#seg-demo > span");
        seg.get(1).click();
        assertTrue(seg.get(1).hasClass("on"));
        assertFalse(seg.get(0).hasClass("on"));
        var select = doc.querySelector(".sec .ore-select");
        select.querySelector(".value").scrollIntoView(dev.oriel.engine.dom.Element.ScrollAlign.CENTER, dev.oriel.engine.dom.Element.ScrollAlign.NEAREST, dev.oriel.engine.dom.Element.ScrollBehavior.INSTANT);
        frame(doc, clock, 16);
        click(doc, ".sec .ore-select .value", 0, Modifiers.NONE, clock);
        assertTrue(select.hasClass("open"), "the select opens");
        click(doc, ".sec .ore-select .option:nth-child(3)", 0, Modifiers.NONE, clock);
        assertFalse(select.hasClass("open"));
        assertEquals("流畅", select.querySelector(".value").textContent());
        var toggle = doc.querySelector(".sec .ore-toggle");
        boolean was = toggle.hasClass("on");
        toggle.click();
        assertEquals(!was, toggle.hasClass("on"));
    }
}
