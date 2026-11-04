package dev.oriel.kit.preset;

import dev.oriel.kit.Lang;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.Notice;
import dev.oriel.kit.Lazy;
import dev.oriel.kit.Motion;
import dev.oriel.kit.Shell;
import dev.oriel.kit.fx.Particles;

import java.util.ArrayList;
import java.util.List;

/**
 * The component and motion catalog: the list is built from the page's sections (their {@code data-title} and
 * {@code data-icon}); picking one slides it in. The motion section's buttons play their motion on the target, the
 * accordion expands with {@link Motion#toggle}, and the particle boxes burst when clicked.
 */
public final class CatalogPreset implements PageControllers.Controller {
    final List<Element> sections = new ArrayList<>();
    private final List<Element> items = new ArrayList<>();
    int current = -1;
    private Document doc;
    private boolean accOpen;
    private double pinnedUntil;

    @Override
    public void attach(Document document) {
        doc = document;
        Particles fx = Shell.setup(doc);
        Element list = Dom.byId(doc, "list");
        for (Element sec : doc.querySelectorAll(".sec")) {
            int index = sections.size();
            sections.add(sec);
            Element item = Dom.el(doc, "div", "item m-press", null);
            item.setAttribute("tabindex", "0");
            item.setAttribute("data-sec", sec.getAttribute("data-sec"));
            Element icon = Dom.el(doc, "img", "o-ico", null);
            icon.setAttribute("src", "../textures/icons/" + sec.getAttribute("data-icon") + ".png");
            item.appendChild(icon);
            item.appendChild(Dom.el(doc, "span", null, sec.getAttribute("data-title")));
            list.appendChild(item);
            items.add(item);
            Shell.activate(item, () -> show(index));
        }
        Dom.text(Dom.byId(doc, "count"), Lang.plural(doc, "oriel.ui.catalog.sections", sections.size()));
        Dom.onKey(doc, e -> {
            if (!e.defaultPrevented() && (e.key.equals("ArrowDown") || e.key.equals("ArrowUp"))) {
                e.preventDefault();
                show(Math.floorMod(current + (e.key.equals("ArrowDown") ? 1 : -1), sections.size()));
                items.get(current).focus();
            }
        });

        Element target = Dom.byId(doc, "demo-target");
        for (Element b : doc.querySelectorAll("[data-demo]")) {
            Shell.activate(b, () -> demo(b.getAttribute("data-demo"), target));
        }
        Element accBody = Dom.byId(doc, "acc-body");
        Shell.activate(Dom.byId(doc, "acc-head"), () -> Motion.toggle(accBody, accOpen = !accOpen));
        Shell.activate(Dom.byId(doc, "modal-close"), () -> Dom.byId(doc, "modal").removeClass("on"));
        burstOnClick(fx, "sparks-box", "sparks-demo", "sparks");
        burstOnClick(fx, "hearts-box", "hearts-demo", "hearts");
        // All sections are one long page; scrolling marks the section being read.
        Element pane = Dom.byId(doc, "pane");
        pane.addEventListener("scroll", e -> {
            if (doc.scheduler().now() < pinnedUntil) return;
            mark(reading(pane));
        });
        mark(0);
    }

    /** Scrolls to section {@code index} and marks it. */
    void show(int index) {
        Element pane = Dom.byId(doc, "pane");
        // Load everything down to the target first, so nothing above it grows while the pane scrolls there.
        boolean loaded = false;
        for (int i = 0; i <= index; i++) {
            if (sections.get(i).hasClass("o-lazy")) {
                Lazy.reveal(sections.get(i));
                loaded = true;
            }
        }
        pinnedUntil = doc.scheduler().now() + 700; // the smooth scroll passes other sections on its way
        mark(index);
        Runnable go = () -> {
            float top = sections.get(index).getBoundingClientRect()[1] - pane.getBoundingClientRect()[1] + pane.scrollTop() - 8;
            pane.scrollTo(0, Math.max(0, top), Element.ScrollBehavior.SMOOTH);
        };
        if (loaded) doc.scheduler().requestAnimationFrame(t -> go.run());
        else go.run();
    }

    /** The section under the top of the pane (the last one when scrolled to the end). */
    private int reading(Element pane) {
        if (pane.scrollTop() + pane.clientHeight() >= pane.scrollHeight() - 2) return sections.size() - 1;
        float top = pane.getBoundingClientRect()[1] + 24;
        int index = 0;
        for (int i = 0; i < sections.size(); i++) if (sections.get(i).getBoundingClientRect()[1] <= top) index = i;
        return index;
    }

    private void mark(int index) {
        if (index == current) return;
        current = index;
        for (int i = 0; i < sections.size(); i++) {
            sections.get(i).toggleClass("on", i == index);
            items.get(i).toggleClass("on", i == index);
        }
        Dom.text(Dom.byId(doc, "crumb"), "/ " + sections.get(index).getAttribute("data-title"));
    }

    void demo(String name, Element target) {
        switch (name) {
            case "pop" -> Motion.play(target, "m-pop", 400);
            case "error" -> Motion.play(target, "m-error", 300);
            case "success" -> Motion.play(target, "m-success", 560);
            case "attention" -> target.toggleClass("m-attention");
            case "stagger" -> {
                Motion.stagger(Dom.byId(doc, "stagger"));
            }
            case "count" -> Motion.count(Dom.byId(doc, "count-demo"), 0, 2235, 1200, "");
            case "modal" -> Dom.byId(doc, "modal").addClass("on");
            case "lazy" -> {
                for (Element row : Dom.byId(doc, "lazy-demo").children()) Lazy.replay(row);
            }
            case "preload" -> {
                Element cover = Dom.byId(doc, "preload");
                cover.removeClass("gone");
                cover.removeClass("done");
                doc.scheduler().setTimeout(() -> Lazy.ready(doc), 1600);
            }
            case "toast" -> {
                Notice.toast(doc, "success", Lang.t(doc, "oriel.ui.catalog.saved"), Lang.t(doc, "oriel.ui.catalog.3_changes"), 2700);
            }
            default -> { }
        }
    }

    private void burstOnClick(Particles fx, String box, String canvas, String preset) {
        Element b = Dom.byId(doc, box);
        Particles.Emitter emitter = fx.emitter(canvas);
        Dom.onMouse(b, "mousedown", e -> {
            float[] r = b.getBoundingClientRect();
            emitter.burst(preset, e.clientX - r[0], e.clientY - r[1], 24);
        });
    }
}
