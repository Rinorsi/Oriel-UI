package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.Lang;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;

/**
 * About: the version, the author's links (copied, not opened: a page cannot open the browser by itself) and the
 * mods built on Oriel. The text is in the page; this fills in the version and makes the copy buttons work.
 */
public final class AboutPreset implements PageControllers.Controller {
    @Override
    public void attach(Document doc) {
        Shell.setup(doc);
        if (!dev.oriel.kit.ReferenceContent.available()) {
            for (Element image : doc.querySelectorAll("img")) {
                if (image.getAttribute("src").startsWith("templates/"))
                    image.setAttribute("src", "oriel:textures/ui/emblem.png");
            }
        }
        String version = doc.host().libraryVersion();
        if (version == null || version.isBlank()) version = OrielKit.class.getPackage().getImplementationVersion();
        Element ver = doc.getElementById("ver");
        if (ver != null) {
            if (version == null || version.isBlank()) ver.setAttribute("style", "display: none");
            else Dom.text(ver, "v" + version);
        }
        for (Element button : doc.querySelectorAll("[data-copy]")) {
            String label = button.textContent();
            Shell.activate(button, () -> {
                doc.host().setClipboard(button.getAttribute("data-copy"));
                Dom.text(button, Lang.t(doc, "oriel.ui.about.copied"));
                button.addClass("primary");
                doc.scheduler().setTimeout(() -> {
                    Dom.text(button, label);
                    button.removeClass("primary");
                }, 1500);
            });
        }
    }
}
