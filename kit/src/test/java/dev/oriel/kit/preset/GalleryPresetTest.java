package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.oriel.kit.preset.KitHost.click;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gallery: cards open their presets. */
class GalleryPresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void galleryCardsOpenTheirPages() {
        Document doc = host.open(OrielKit.GALLERY, new GalleryPreset());
        click(doc, ".ore-card[data-page=forge]", 0, Modifiers.NONE, clock);
        assertEquals(List.of(OrielKit.FORGE), host.navigations);
        // The HUD card asks the host first; this host declines, so the page opens instead.
        click(doc, ".ore-card[data-page=potions]", 0, Modifiers.NONE, clock);
        assertEquals("oriel \"hud\"", host.sent.getLast());
        assertEquals(OrielKit.POTIONS, host.navigations.getLast());
        click(doc, "#close", 0, Modifiers.NONE, clock);
        assertTrue(host.closed);
    }
}
