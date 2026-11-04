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
 * when the host declines (the previewer, tests), and the theme switch (the segmented control, or T).
 */
public final class GalleryPreset implements PageControllers.Controller {
    @Override
    public void attach(Document doc) {
        Shell.setup(doc);
        for (Element e : doc.querySelectorAll("[data-page=potions]")) Shell.activate(e, () -> openHud(doc));
        for (Element pick : doc.querySelectorAll("[data-theme-pick]")) {
            Shell.activate(pick, () -> theme(doc, pick.getAttribute("data-theme-pick")));
        }
        Dom.onKey(doc, e -> {
            if (e.key.equalsIgnoreCase("t") && !e.defaultPrevented()) theme(doc, Shell.theme().equals("light") ? "dark" : "light");
        });
        theme(doc, Shell.theme());
    }

    private static void theme(Document doc, String name) {
        Shell.theme(doc, name);
        for (Element pick : doc.querySelectorAll("[data-theme-pick]")) {
            pick.toggleClass("on", pick.getAttribute("data-theme-pick").equals(Shell.theme()));
        }
    }

    private static void openHud(Document doc) {
        if (doc.host().send(OrielKit.CHANNEL, "\"hud\"")) return;
        doc.navigate(OrielKit.POTIONS);
    }
}
