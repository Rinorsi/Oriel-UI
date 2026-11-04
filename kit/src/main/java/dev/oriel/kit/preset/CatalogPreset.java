package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
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
        Dom.text(Dom.byId(doc, "count"), sections.size() + " 节 · 每一项都是活的");
        Dom.onKey(doc, e -> {
            if (e.key.equals("ArrowDown") || e.key.equals("ArrowUp")) {
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
        show(0);
    }

    void show(int index) {
        if (index == current) return;
        current = index;
        for (int i = 0; i < sections.size(); i++) {
            sections.get(i).toggleClass("on", i == index);
            items.get(i).toggleClass("on", i == index);
        }
        Dom.text(Dom.byId(doc, "crumb"), "/ " + sections.get(index).getAttribute("data-title"));
        Element pane = Dom.byId(doc, "pane");
        pane.scrollTo(0, 0, Element.ScrollBehavior.INSTANT);
    }

    void demo(String name, Element target) {
        switch (name) {
            case "pop" -> Motion.play(target, "m-pop", 400);
            case "error" -> Motion.play(target, "m-error", 300);
            case "success" -> Motion.play(target, "m-success", 560);
            case "attention" -> target.toggleClass("m-attention");
            case "stagger" -> {
                Element box = Dom.byId(doc, "stagger");
                for (Element c : box.children()) Motion.play(c, "m-rise", 600);
            }
            case "count" -> Motion.count(Dom.byId(doc, "count-demo"), 0, 2235, 1200, "");
            case "modal" -> Dom.byId(doc, "modal").addClass("on");
            case "toast" -> {
                Element toast = Dom.el(doc, "div", "o-toast", "已保存 · 3 项修改");
                Dom.byId(doc, "app").appendChild(toast);
                doc.scheduler().setTimeout(toast::remove, 2700);
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
