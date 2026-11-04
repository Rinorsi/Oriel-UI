package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Event;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The icon library: every icon loads (a batch per frame), categories and search filter, a click shows the icon. */
class IconsPresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void theGridLoadsFiltersAndShowsTheChosenIcon() {
        IconsPreset icons = new IconsPreset();
        Document doc = host.open(OrielKit.ICONS, icons, 640, 360);
        assertEquals(202, icons.icons.size());
        assertTrue(doc.getElementById("grid").childCount() < 202, "the first batch only");
        for (int i = 0; i < 10; i++) frame(doc, clock, 16);
        assertEquals(202, doc.getElementById("grid").childCount(), "then the rest, a batch per frame");
        click(doc, "#cats [data-cat=combat]", 0, Modifiers.NONE, clock);
        for (int i = 0; i < 4; i++) frame(doc, clock, 16);
        assertEquals(22, doc.getElementById("grid").childCount());
        var q = doc.getElementById("q");
        q.setValue("剑");
        q.dispatchEvent(new Event("input", true, false));
        frame(doc, clock, 16);
        assertTrue(doc.getElementById("grid").childCount() >= 1);
        click(doc, "#grid .tile", 0, Modifiers.NONE, clock);
        assertTrue(doc.getElementById("d-code").textContent().contains(icons.chosen.id() + ".png"));
    }
}
