package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;

import java.util.List;

/**
 * The style showcase: an index of eight full-screen looks (RPG, arcane, military, sci-fi, xianxia, fantasy, Terraria,
 * dungeon), each page plain HTML and CSS under {@code ui/styles/}. Escape goes back: from a look to the index, from
 * the index to the gallery; the pages' {@code data-go} chips do the same with the mouse.
 */
public final class StylesPreset implements PageControllers.Controller {
    /** The looks, in the index's order: their pages are {@code ui/styles/<name>.html}. */
    public static final List<String> PAGES = List.of("rpg", "arcane", "military", "scifi", "xianxia", "fantasy", "terraria", "dungeon");

    private final boolean index;

    /** {@code index}: the index page, rather than one of the looks. */
    public StylesPreset(boolean index) {
        this.index = index;
    }

    @Override
    public void attach(Document doc) {
        Shell.setup(doc);
        Dom.onKey(doc, e -> {
            if (!e.key.equals("Escape") || e.defaultPrevented()) return;
            e.preventDefault();
            if (index) Shell.back(doc);
            else Shell.go(doc, OrielKit.STYLES, "back");
        });
    }
}
