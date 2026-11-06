package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;

/**
 * The launcher. The shared markup ({@link Shell}) opens the presets and closes the screen; this adds the potion HUD
 * tile, which asks the host to show the HUD over the game ({@code oriel} message {@code "hud"}) and opens its page
 * when the host declines (the previewer, tests), and T for the theme (the title bar button is Shell's, as on every page).
 */
public final class GalleryPreset implements PageControllers.Controller {
    @Override
    public void attach(Document doc) {
        Shell.setup(doc);
        for (Element e : doc.querySelectorAll("[data-page=potions]")) Shell.activate(e, () -> openHud(doc));
        // The home page's Demos button: the template centre, on its Demos view.
        for (Element e : doc.querySelectorAll("[data-demos]")) Shell.activate(e, () -> {
            StylesPreset.startAt("demos");
            Shell.go(doc, OrielKit.STYLES, "forward");
        });
        Dom.onKey(doc, e -> {
            if (e.key.equalsIgnoreCase("t") && !e.defaultPrevented()) Shell.theme(doc, Shell.theme().equals("light") ? "dark" : "light");
        });
    }

    private static void openHud(Document doc) {
        if (doc.host().send(OrielKit.CHANNEL, "\"hud\"")) return;
        doc.navigate(OrielKit.POTIONS);
    }
}
