package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Loading, so a page with a lot in it opens at once and fills in: {@code .o-lazy} boxes show a skeleton until they
 * come near the view (of their scroller or the window), then get their content one per {@link #STEP} ms, top to
 * bottom (their children are not laid out or drawn before that: 3D models, particles and long lists cost nothing
 * while waiting). A page's {@code .o-preload} cover (a loading screen) lifts after {@code data-min} ms, or when the
 * controller calls {@link #ready}.
 */
public final class Lazy {
    /** Time between two boxes revealed. */
    public static final int STEP = 70;
    /** How far outside the view a box is still revealed, in px. */
    static final float MARGIN = 40;

    private Lazy() {}

    public static void attach(Document doc) {
        List<Element> pending = new ArrayList<>(doc.querySelectorAll(".o-lazy"));
        if (!pending.isEmpty()) {
            double[] last = {-STEP};
            doc.scheduler().requestAnimationFrame(new java.util.function.DoubleConsumer() {
                @Override
                public void accept(double now) {
                    if (now - last[0] >= STEP) {
                        for (Element e : pending) {
                            if (!e.isConnected() || near(e)) {
                                reveal(e);
                                pending.remove(e);
                                last[0] = now;
                                break;
                            }
                        }
                    }
                    if (!pending.isEmpty()) doc.scheduler().requestAnimationFrame(this);
                }
            });
        }
        for (Element cover : doc.querySelectorAll(".o-preload")) {
            int min = (int) cover.numberAttribute("data-min", 500);
            if (min >= 0) doc.scheduler().setTimeout(() -> lift(cover), min);
        }
    }

    /** Lifts the page's loading cover now. */
    public static void ready(Document doc) {
        for (Element cover : doc.querySelectorAll(".o-preload")) lift(cover);
    }

    /** Sets the loading cover's progress (0..1) and line of text. */
    public static void progress(Document doc, double fraction, String text) {
        for (Element cover : doc.querySelectorAll(".o-preload")) {
            Element fill = cover.querySelector(".o-preload-bar i");
            if (fill != null) new Dom.Style(fill).set("width", Math.round(Math.max(0, Math.min(1, fraction)) * 1000) / 10.0 + "%").apply();
            Element what = cover.querySelector(".o-preload-what");
            if (what != null && text != null) Dom.text(what, text);
        }
    }

    /** Shows {@code box}'s content now (its children rise in). */
    public static void reveal(Element box) {
        box.removeClass("o-lazy");
        box.addClass("o-loaded");
    }

    /** Puts {@code box} back to its skeleton and reveals it again (a replay, for demos). */
    public static void replay(Element box) {
        box.removeClass("o-loaded");
        box.addClass("o-lazy");
        attach(box.ownerDocument());
    }

    private static void lift(Element cover) {
        if (cover.hasClass("done")) return;
        cover.addClass("done");
        cover.ownerDocument().scheduler().setTimeout(() -> cover.addClass("gone"), 360);
    }

    private static boolean near(Element e) {
        float[] r = e.getBoundingClientRect();
        float top = 0, bottom = e.ownerDocument().viewportHeight();
        for (Element p = e.parentElement(); p != null; p = p.parentElement()) {
            var s = p.computedStyle();
            if (s != null && (s.overflowY == dev.oriel.engine.style.Overflow.AUTO || s.overflowY == dev.oriel.engine.style.Overflow.SCROLL)) {
                float[] v = p.getBoundingClientRect();
                top = v[1];
                bottom = v[1] + v[3];
                break;
            }
        }
        return r[1] < bottom + MARGIN && r[1] + r[3] > top - MARGIN;
    }
}
