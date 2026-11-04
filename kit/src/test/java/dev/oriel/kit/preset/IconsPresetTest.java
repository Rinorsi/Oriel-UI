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
        int all = icons.icons.size();
        assertEquals(202, icons.icons.stream().filter(i -> i.kind() == IconsPreset.Kind.ICON).count());
        assertTrue(doc.getElementById("grid").childCount() < all, "the first batch only");
        for (int i = 0; i < 10; i++) frame(doc, clock, 16);
        assertEquals(all, doc.getElementById("grid").childCount(), "then the rest, a batch per frame");
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

    @Test
    void vanillaSpritesAndAnyItemCanBeIcons() {
        IconsPreset icons = new IconsPreset();
        Document doc = host.open(OrielKit.ICONS, icons, 640, 360);
        doc.querySelector("#cats [data-cat=vanilla]").scrollIntoView(dev.oriel.engine.dom.Element.ScrollAlign.NEAREST,
                dev.oriel.engine.dom.Element.ScrollAlign.NEAREST, dev.oriel.engine.dom.Element.ScrollBehavior.INSTANT);
        frame(doc, clock, 16);
        click(doc, "#cats [data-cat=vanilla]", 0, Modifiers.NONE, clock);
        for (int i = 0; i < 4; i++) frame(doc, clock, 16);
        assertEquals("/ 原版图标", doc.getElementById("crumb").textContent(), "the category opens");
        assertEquals(IconsPreset.SPRITES.length, doc.getElementById("grid").childCount());
        click(doc, "#grid .tile", 0, Modifiers.NONE, clock);
        assertEquals("<img src=\"sprite:minecraft:hud/heart/full\">", doc.getElementById("d-code").textContent());
        assertTrue(doc.getElementById("detail").hasClass("native"), "sprites keep their colours: no tint swatches");

        doc.querySelector("#cats [data-cat=items]").scrollIntoView(dev.oriel.engine.dom.Element.ScrollAlign.NEAREST,
                dev.oriel.engine.dom.Element.ScrollAlign.NEAREST, dev.oriel.engine.dom.Element.ScrollBehavior.INSTANT);
        frame(doc, clock, 16);
        click(doc, "#cats [data-cat=items]", 0, Modifiers.NONE, clock);
        var q = doc.getElementById("q");
        q.setValue("minecraft:wither_skeleton_skull");
        q.dispatchEvent(new Event("input", true, false));
        frame(doc, clock, 16);
        assertEquals(1, doc.getElementById("grid").childCount(), "an item id typed in the search is that item");
        click(doc, "#grid .tile", 0, Modifiers.NONE, clock);
        assertEquals("<item id=\"minecraft:wither_skeleton_skull\"></item>", doc.getElementById("d-code").textContent());
        assertEquals(3, doc.getElementById("stage").querySelectorAll("item").size());
    }
}
