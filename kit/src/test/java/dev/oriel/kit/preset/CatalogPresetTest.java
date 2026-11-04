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
        click(doc, "#list .item[data-sec=motion]", 0, Modifiers.NONE, clock);
        assertTrue(doc.querySelector(".sec[data-sec=motion]").hasClass("on"));
        click(doc, "[data-demo=modal]", 0, Modifiers.NONE, clock);
        assertTrue(doc.getElementById("modal").hasClass("on"));
        click(doc, "#modal-close", 0, Modifiers.NONE, clock);
        click(doc, "[data-demo=count]", 0, Modifiers.NONE, clock);
        for (int i = 0; i < 90; i++) frame(doc, clock, 16);
        assertEquals("2235", doc.getElementById("count-demo").textContent());
        click(doc, "#acc-head", 0, Modifiers.NONE, clock);
        for (int i = 0; i < 20; i++) frame(doc, clock, 16);
        assertTrue(doc.getElementById("acc").hasClass("m-open"));
        assertTrue(doc.getElementById("acc-body").getBoundingClientRect()[3] > 10, "the accordion opened");
    }
}
