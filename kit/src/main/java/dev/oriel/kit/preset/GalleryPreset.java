package dev.oriel.kit.preset;

import com.google.gson.JsonObject;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;
import dev.oriel.kit.TemplateCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * The launcher. The shared markup ({@link Shell}) opens the presets and closes the screen; this adds the community
 * tile (the picks turning over, each opening that template's details) and T for the theme (the title bar button is
 * Shell's, as on every page).
 */
public final class GalleryPreset implements PageControllers.Controller {
    /** The templates the home page shows, in the order they turn over: the ones that read best at a glance. */
    static final List<String> PICKS = List.of("24", "14", "27", "37", "47", "17");
    private static final int TURN_MS = 4200;

    private Document doc;
    private final List<Element> slides = new ArrayList<>();
    private final List<Element> dots = new ArrayList<>();
    private int current;
    /** Bumped by hand-turning, so the pending automatic turn knows it has been replaced. */
    private int generation;

    @Override
    public void attach(Document document) {
        doc = document;
        counts();
        Shell.setup(doc);
        // The community button: the template centre, on All.
        for (Element e : doc.querySelectorAll("[data-community]")) Shell.activate(e, () -> {
            StylesPreset.startAt("all");
            Shell.go(doc, OrielKit.STYLES, "forward");
        });
        picks();
        Dom.onKey(doc, e -> {
            if (e.key.equalsIgnoreCase("t") && !e.defaultPrevented()) Shell.theme(doc, Shell.theme().equals("light") ? "dark" : "light");
        });
    }

    /** The figures on the picture are the real ones: how many templates the centre lists. */
    private void counts() {
        Element templates = doc.getElementById("stat-templates");
        if (templates != null) templates.setAttribute("data-count", Integer.toString(TemplateCatalog.ids().size()));
    }

    private void picks() {
        Element stage = doc.getElementById("picks");
        if (stage == null) return;
        Element anchor = Dom.byId(doc, "pick-prev");
        Element dotBar = Dom.byId(doc, "pick-dots");
        for (String id : PICKS) {
            JsonObject meta;
            try {
                meta = TemplateCatalog.metadata(id, doc.host()::loadText);
            } catch (RuntimeException e) {
                continue; // a template that is not packaged is simply not shown
            }
            TemplateCatalog.Text text = TemplateCatalog.localized(meta, doc.host().language());
            String author = meta.has("author") ? meta.get("author").getAsString() : "";
            Element slide = Dom.html(doc, "<div class=\"slide\"><img><div class=\"cap\"><b></b><span></span></div></div>");
            slide.querySelector("img").setAttribute("src", TemplateCatalog.cover(id));
            Dom.text(slide.querySelector("b"), id + " · " + text.name());
            Element by = slide.querySelector("span");
            by.setAttribute("lang", "zh");
            Dom.text(by, author);
            stage.insertBefore(slide, anchor);
            Element dot = Dom.el(doc, "i", null, null);
            dotBar.appendChild(dot);
            slides.add(slide);
            dots.add(dot);
        }
        if (slides.isEmpty()) return;
        show(0);
        Shell.activate(Dom.byId(doc, "pick-prev"), () -> turn(-1));
        Shell.activate(Dom.byId(doc, "pick-next"), () -> turn(1));
        Dom.onClick(Dom.byId(doc, "pick-prev"), ev -> ev.stopPropagation());
        Dom.onClick(Dom.byId(doc, "pick-next"), ev -> ev.stopPropagation());
        // Enter on the focused tile opens the picture on show.
        Shell.activate(Dom.byId(doc, "pick"), () -> open(PICKS.get(Math.min(current, PICKS.size() - 1))));
        arm();
    }

    private void open(String id) {
        StylesPreset.openTemplate(id);
        Shell.go(doc, OrielKit.STYLES, "forward");
    }

    private void show(int index) {
        current = Math.floorMod(index, slides.size());
        for (int i = 0; i < slides.size(); i++) {
            slides.get(i).toggleClass("on", i == current);
            dots.get(i).toggleClass("on", i == current);
        }
    }

    private void turn(int by) {
        generation++;
        show(current + by);
        arm();
    }

    /** The next automatic turn; none under reduced motion, and none once the page is gone. */
    private void arm() {
        if (doc.host().prefersReducedMotion() || slides.size() < 2) return;
        int mine = generation;
        doc.scheduler().setTimeout(() -> {
            if (!doc.isConnected() || mine != generation) return;
            show(current + 1);
            arm();
        }, TURN_MS);
    }
}
