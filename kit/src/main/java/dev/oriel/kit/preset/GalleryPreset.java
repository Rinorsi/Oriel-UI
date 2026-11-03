package dev.oriel.kit.preset;

import dev.oriel.kit.Dom;
import dev.oriel.kit.OrielKit;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;

/**
 * The gallery: a card per preset. Clicking a card (or Enter / Space on a focused one) opens its page in the same
 * screen; the HUD card asks the host to show the potion HUD over the game instead ({@code oriel} message
 * {@code "hud"}), and opens its page when the host declines (the previewer, tests).
 */
public final class GalleryPreset implements PageControllers.Controller {
    @Override
    public void attach(Document doc) {
        for (Element card : doc.querySelectorAll(".ore-card[data-page]")) {
            String page = card.getAttribute("data-page");
            Runnable open = () -> open(doc, page);
            Dom.onClick(card, e -> open.run());
            Dom.onKey(card, e -> {
                if (e.key.equals("Enter") || e.key.equals(" ")) {
                    e.preventDefault();
                    open.run();
                }
            });
        }
        Element close = Dom.byId(doc, "close");
        Dom.onClick(close, e -> doc.host().close());
        Dom.onKey(close, e -> {
            if (e.key.equals("Enter") || e.key.equals(" ")) doc.host().close();
        });
    }

    private static void open(Document doc, String page) {
        if (page.equals("potions") && doc.host().send(OrielKit.CHANNEL, "\"hud\"")) return;
        doc.navigate(OrielKit.page(page));
    }
}
