package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;

import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * The motion library's Java half (the CSS half is the {@code m-*} classes in ore.css; rules in docs/zh/motion.md).
 * Everything here drives CSS: it measures, sets inline styles and classes, and lets transitions and keyframes do
 * the moving, so the curves and durations stay in one place.
 */
public final class Motion {
    /** Durations (ms) and the curves, as in ore.css's --m-* tokens. */
    public static final int FAST = 120, BASE = 220, SLOW = 360;

    private Motion() {}

    /**
     * Restarts a one-shot animation class on {@code e} (removing it, then adding it on the next frame), e.g. the
     * press ripple {@code m-rippling} or {@code fx-shake}; it comes off again after {@code ms}.
     */
    public static void play(Element e, String cls, int ms) {
        Document doc = e.ownerDocument();
        e.removeClass(cls);
        // Two frames: a frame's callbacks run before its style, so the removal must be styled in a frame of its own
        // for the animation to start again.
        doc.scheduler().requestAnimationFrame(t -> doc.scheduler().requestAnimationFrame(t2 -> {
            e.addClass(cls);
            doc.scheduler().setTimeout(() -> e.removeClass(cls), ms);
        }));
    }

    /**
     * Replays a {@code .m-stagger} list's entrance: the class comes off for a styled frame (so the children's
     * animation stops) and goes back on, and the children rise again one after another.
     */
    public static void stagger(Element list) {
        list.removeClass("m-stagger");
        var scheduler = list.ownerDocument().scheduler();
        scheduler.requestAnimationFrame(t -> scheduler.requestAnimationFrame(t2 -> list.addClass("m-stagger")));
    }

    /**
     * Makes every {@code .m-ripple} element of {@code doc} answer a press with the pixel ripple (a square ring
     * growing from the press point) and a little spring.
     */
    public static void ripples(Document doc) {
        for (Element e : doc.querySelectorAll(".m-ripple")) {
            Dom.onMouse(e, "mousedown", ev -> {
                float[] r = e.getBoundingClientRect();
                new Dom.Style(e).px("--rx", ev.clientX - r[0]).px("--ry", ev.clientY - r[1]).apply();
                play(e, "m-rippling", 420);
            });
        }
    }

    /**
     * Expands or collapses {@code body} by animating its height from where it is to its content's height (or 0),
     * then leaves it at {@code auto} when open; {@code m-open} on {@code body}'s parent turns carets and the like.
     */
    public static void toggle(Element body, boolean open) {
        Document doc = body.ownerDocument();
        Element parent = body.parentElement();
        if (parent != null) parent.toggleClass("m-open", open);
        float from = body.getBoundingClientRect()[3];
        Dom.Style st = new Dom.Style(body);
        st.set("overflow", "hidden").set("height", "auto").apply();
        float to = open ? body.scrollHeight() : 0;
        st.px("height", from).set("transition", "height " + BASE + "ms var(--m-out), opacity " + BASE + "ms")
                .set("opacity", open ? "1" : "0").apply();
        doc.scheduler().requestAnimationFrame(t -> st.px("height", to).apply());
        doc.scheduler().setTimeout(() -> {
            if (open) st.set("height", "auto").set("overflow", "visible").apply();
        }, BASE + 20);
    }

    /** Counts {@code e}'s text from {@code from} to {@code to} over {@code ms}, easing out, with {@code suffix}. */
    public static void count(Element e, double from, double to, int ms, String suffix) {
        tween(e.ownerDocument(), ms, k -> Dom.text(e, Math.round(from + (to - from) * k) + suffix));
    }

    /** Calls {@code step} with an eased 0..1 every frame for {@code ms}, ending on exactly 1. */
    public static void tween(Document doc, int ms, DoubleConsumer step) {
        double[] start = {Double.NaN};
        doc.scheduler().requestAnimationFrame(new java.util.function.DoubleConsumer() {
            @Override
            public void accept(double now) {
                if (Double.isNaN(start[0])) start[0] = now;
                double t = Math.min(1, (now - start[0]) / ms);
                step.accept(1 - Math.pow(1 - t, 3));
                if (t < 1) doc.scheduler().requestAnimationFrame(this);
            }
        });
    }

    /**
     * Slides {@code indicator} (absolutely positioned in the tabs' container) under {@code active}: its left and
     * width transition, so one bar glides between tabs.
     */
    public static void indicator(Element indicator, Element active) {
        Element box = indicator.parentElement();
        float[] c = box.getBoundingClientRect(), a = active.getBoundingClientRect();
        new Dom.Style(indicator).px("left", a[0] - c[0]).px("width", a[2]).apply();
    }

    /**
     * Reveals the children of {@code scroller} as they scroll into view: each gets {@code m-in} (which plays the
     * {@code .m-reveal} entrance) the first time its top comes within the scroller's view.
     */
    public static void reveal(Element scroller) {
        Runnable check = () -> {
            float[] v = scroller.getBoundingClientRect();
            for (Element c : scroller.querySelectorAll(".m-reveal")) {
                if (c.hasClass("m-in")) continue;
                float[] r = c.getBoundingClientRect();
                if (r[1] < v[1] + v[3] - 4) c.addClass("m-in");
            }
        };
        scroller.addEventListener("scroll", e -> check.run());
        scroller.ownerDocument().scheduler().requestAnimationFrame(t -> check.run());
    }

    /**
     * The spotlight: a soft light under the pointer on {@code .m-spot} elements (--mx/--my in px), the modern
     * stand-in for a 3D tilt the engine does not draw.
     */
    public static void spotlights(Document doc) {
        for (Element e : doc.querySelectorAll(".m-spot")) {
            Dom.onMouse(e, "mousemove", ev -> {
                float[] r = e.getBoundingClientRect();
                new Dom.Style(e).px("--mx", ev.clientX - r[0]).px("--my", ev.clientY - r[1]).apply();
            });
        }
    }

    /**
     * The zoom transition: a panel grows from {@code from}'s box to the window's while the page fades, then
     * {@code then} runs (usually a navigation, whose window then zooms in). The card seems to open into the page.
     */
    public static void zoomInto(Element from, Runnable then) {
        Document doc = from.ownerDocument();
        Element window = doc.querySelector(".o-window");
        if (window == null) window = doc.querySelector(".home");
        Element app = doc.querySelector(".o-app");
        if (window == null || app == null) {
            then.run();
            return;
        }
        float[] f = from.getBoundingClientRect(), w = window.getBoundingClientRect();
        Element ghost = Dom.el(doc, "div", "m-zoom", null);
        Dom.Style st = new Dom.Style(ghost);
        st.px("left", f[0]).px("top", f[1]).px("width", f[2]).px("height", f[3]).apply();
        app.appendChild(ghost);
        Element leaving = window;
        leaving.addClass("m-leaving");
        doc.scheduler().requestAnimationFrame(t -> st.px("left", w[0]).px("top", w[1]).px("width", w[2]).px("height", w[3]).apply());
        doc.scheduler().setTimeout(then, SLOW);
    }

    /** Wires the declarative parts of the library: ripples, spotlights, staggered lists and counters. */
    public static void attach(Document doc) {
        ripples(doc);
        spotlights(doc);
        for (Element e : doc.querySelectorAll("[data-count]")) {
            try {
                count(e, 0, Double.parseDouble(e.getAttribute("data-count")), 1200, "");
            } catch (NumberFormatException ignored) {
                // not a number: left as written
            }
        }
        List<Element> stagger = doc.querySelectorAll(".m-stagger");
        for (Element list : stagger) {
            int i = 0;
            for (Element c : list.children()) new Dom.Style(c).set("animation-delay", (i++ * 60) + "ms").apply();
        }
    }
}
