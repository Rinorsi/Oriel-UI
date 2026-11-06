package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The home page's Demos button and the demos key open the template centre on its Demos view, once. */
class StylesStartViewTest {
    private static boolean onDemos(Document doc) {
        var item = doc.querySelector("#side .item[data-view=demos]");
        return item != null && item.hasClass("on") && !doc.querySelector("#side .item[data-view=all]").hasClass("on");
    }

    @Test void startAtOpensTheDemosViewThenFallsBackToAll() {
        KitHost host = new KitHost();
        StylesPreset.startAt("demos");
        Document first = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        assertTrue(onDemos(first), "the demos item is selected");
        assertEquals(12, first.querySelectorAll("#grid .card").size(), "the twelve demo cards are shown");
        first.close();
        Document second = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        assertFalse(onDemos(second), "a later open starts on All again");
        second.close();
    }
}
