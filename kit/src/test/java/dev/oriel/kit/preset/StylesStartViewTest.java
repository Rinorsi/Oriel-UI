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

    @Test void comingBackFromAPageReturnsToTheSameView() {
        KitHost host = new KitHost();
        Document first = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        first.querySelector("#side .item[data-view=demos]").click();
        first.querySelector("#grid .card").click();   // a demo page opens from here
        assertEquals(1, host.navigations.size());
        first.close();
        Document back = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        assertTrue(onDemos(back), "Escape on the page returns to the Demos view, not All");
        back.close();
        Document later = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        assertFalse(onDemos(later), "the place is used once");
        later.close();
    }

    @Test void theLibraryTheGameOpensThePageWithIsShownRightAway() {
        // The game hands it over wrapped as {"library": {...}}; coming back from a page rebuilds the centre from only this.
        String data = "{\"library\":{\"pages\":[{\"path\":\"p1/index.html\",\"title\":\"First\"}],\"folder\":\"config/oriel/my-pages\",\"namespace\":\"oriel_mine\"}}";
        KitHost host = new KitHost();
        StylesPreset.startAt("pages");
        Document doc = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360, data);
        assertEquals(1, doc.querySelectorAll("#grid .card").size(), "the page in the folder is listed without waiting for another message");
        doc.close();
    }
}
