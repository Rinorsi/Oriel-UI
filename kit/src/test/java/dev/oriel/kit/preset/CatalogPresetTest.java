package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The catalog: the list mirrors the sections, picking one shows it, the motion demos play. */
class CatalogPresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void theListPicksSectionsAndDemosPlay() {
        CatalogPreset catalog = new CatalogPreset();
        Document doc = host.open(OrielKit.CATALOG, catalog, 640, 360);
        assertEquals(catalog.sections.size(), doc.querySelectorAll("#list .item").size());
        assertTrue(catalog.sections.size() >= 10);
        doc.querySelector("#list .item[data-sec=motion]").scrollIntoView(dev.oriel.engine.dom.Element.ScrollAlign.NEAREST,
                dev.oriel.engine.dom.Element.ScrollAlign.NEAREST, dev.oriel.engine.dom.Element.ScrollBehavior.INSTANT);
        frame(doc, clock, 16);
        click(doc, "#list .item[data-sec=motion]", 0, Modifiers.NONE, clock);
        assertTrue(doc.querySelector(".sec[data-sec=motion]").hasClass("on"));
        for (int i = 0; i < 40; i++) frame(doc, clock, 16);
        float paneTop = doc.getElementById("pane").getBoundingClientRect()[1];
        assertEquals(paneTop + 8, doc.querySelector(".sec[data-sec=motion]").getBoundingClientRect()[1], 2f, "scrolled to the section");
        doc.querySelector("[data-demo=modal]").scrollIntoView(dev.oriel.engine.dom.Element.ScrollAlign.CENTER,
                dev.oriel.engine.dom.Element.ScrollAlign.NEAREST, dev.oriel.engine.dom.Element.ScrollBehavior.INSTANT);
        frame(doc, clock, 16);
        click(doc, "[data-demo=modal]", 0, Modifiers.NONE, clock);
        assertTrue(doc.getElementById("modal").hasClass("on"));
        click(doc, "#modal-close", 0, Modifiers.NONE, clock);
        doc.querySelector("[data-demo=count]").scrollIntoView(dev.oriel.engine.dom.Element.ScrollAlign.CENTER,
                dev.oriel.engine.dom.Element.ScrollAlign.NEAREST, dev.oriel.engine.dom.Element.ScrollBehavior.INSTANT);
        frame(doc, clock, 16);
        click(doc, "[data-demo=count]", 0, Modifiers.NONE, clock);
        for (int i = 0; i < 90; i++) frame(doc, clock, 16);
        assertEquals("2235", doc.getElementById("count-demo").textContent());
        doc.getElementById("acc-head").scrollIntoView(dev.oriel.engine.dom.Element.ScrollAlign.CENTER,
                dev.oriel.engine.dom.Element.ScrollAlign.NEAREST, dev.oriel.engine.dom.Element.ScrollBehavior.INSTANT);
        frame(doc, clock, 16);
        click(doc, "#acc-head", 0, Modifiers.NONE, clock);
        for (int i = 0; i < 20; i++) frame(doc, clock, 16);
        assertTrue(doc.getElementById("acc").hasClass("m-open"));
        assertTrue(doc.getElementById("acc-body").getBoundingClientRect()[3] > 10, "the accordion opened");
        // Scrolling back up marks the section being read.
        doc.getElementById("pane").scrollTo(0, 0, dev.oriel.engine.dom.Element.ScrollBehavior.INSTANT);
        frame(doc, clock, 700);
        frame(doc, clock, 16);
        assertTrue(doc.querySelector(".sec[data-sec=buttons]").hasClass("on"));
    }

    @Test
    void theStaggerDemoReplays() {
        Document doc = host.open(OrielKit.CATALOG, new CatalogPreset(), 640, 360);
        for (int i = 0; i < 60; i++) frame(doc, clock, 16);
        var last = doc.getElementById("stagger").children().getLast();
        assertEquals(1f, last.computedStyle().opacity, 0.01f, "the entrance has finished");
        doc.querySelector("[data-demo=stagger]").click();
        for (int i = 0; i < 4; i++) frame(doc, clock, 16);
        assertTrue(last.computedStyle().opacity < 0.9f, "the boxes rise again: " + last.computedStyle().opacity);
        for (int i = 0; i < 60; i++) frame(doc, clock, 16);
        assertEquals(1f, last.computedStyle().opacity, 0.01f);
    }
}
