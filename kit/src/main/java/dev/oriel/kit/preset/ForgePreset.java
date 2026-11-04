package dev.oriel.kit.preset;

import dev.oriel.kit.Dom;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;
import dev.oriel.kit.fx.Particles;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.KeyboardEvent;
import dev.oriel.engine.host.PageControllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The forge preset: keep the furnace in the working range with the bellows, then strike while the marker crosses
 * the green zone (gold is perfect). Every strike swings the hammer, shakes the stage, flashes and throws sparks
 * (pixel particles, {@link Particles}); embers rise faster as the furnace heats; hits raise the quality, misses and cold or overheated metal lower it. The ingot's
 * colour and glow follow its temperature and it lengthens as it is worked. After eight strikes, quench to finish.
 */
public final class ForgePreset implements PageControllers.Controller {
    static final float MIN_TEMP = 300, MAX_TEMP = 1600, IDEAL_LOW = 1050, IDEAL_HIGH = 1350;
    static final int STRIKES = 8;
    /** Degrees a second: the bellows heat, the furnace cools. */
    static final float HEAT_RATE = 380, COOL_RATE = 30;

    /** The forge's state, separate from the page so tests can drive it. */
    static final class Model {
        float temp = 1180, quality, phase, zoneCenter = 0.5f;
        int strikes;
        boolean bellows, done;
        final List<String> verdicts = new ArrayList<>();

        void advance(float dt) {
            if (done) return;
            temp += (bellows ? HEAT_RATE : -COOL_RATE) * dt;
            temp = Math.max(MIN_TEMP, Math.min(MAX_TEMP, temp));
            phase += dt * (2.2f + strikes * 0.18f);
        }

        float marker() {
            return (float) (Math.sin(phase) + 1) / 2;
        }

        boolean ideal() {
            return temp >= IDEAL_LOW && temp <= IDEAL_HIGH;
        }

        float zoneHalf() {
            return ideal() ? 0.12f : 0.07f;
        }

        static final float CORE_HALF = 0.025f;

        /** Judges a strike at the current marker; returns the verdict key. */
        String strike(Random random) {
            if (done || strikes >= STRIKES) return null;
            strikes++;
            float d = Math.abs(marker() - zoneCenter);
            String verdict;
            if (temp < 800) {
                verdict = "cold";
                quality += 1;
            } else if (temp > 1480) {
                verdict = "hot";
                quality -= 4;
            } else if (d <= CORE_HALF) {
                verdict = "perfect";
                quality += 14;
            } else if (d <= zoneHalf()) {
                verdict = "hit";
                quality += 8;
            } else {
                verdict = "miss";
                quality -= 5;
            }
            quality = Math.max(0, Math.min(100, quality));
            temp -= 55;
            zoneCenter = 0.25f + random.nextFloat() * 0.5f;
            verdicts.add(verdict);
            return verdict;
        }

        String grade() {
            if (quality >= 90) return "传说";
            if (quality >= 70) return "史诗";
            if (quality >= 50) return "精良";
            if (quality >= 25) return "普通";
            return "粗糙";
        }
    }

    final Model model = new Model();
    private final Random random = new Random(7);
    private Particles.Emitter embers, sparks;
    private Document doc;
    private Dom.Style level, ideal, ingot, glow, haze, zone, core, marker, qualityBar;
    private Element temp, quality, grade, pips, log, stage, hammer, flash, verdict, result, resultCard, strikesLeft, bellowsButton;
    private double last = -1;
    private boolean flip;

    @Override
    public void attach(Document document) {
        doc = document;
        Particles fx = Shell.setup(doc);
        embers = fx.emitter("embers");
        sparks = fx.emitter("sparks");
        level = new Dom.Style(Dom.byId(doc, "level"));
        ideal = new Dom.Style(Dom.byId(doc, "ideal"));
        ingot = new Dom.Style(Dom.byId(doc, "ingot"));
        glow = new Dom.Style(Dom.byId(doc, "glow"));
        haze = new Dom.Style(Dom.byId(doc, "haze"));
        zone = new Dom.Style(Dom.byId(doc, "zone"));
        core = new Dom.Style(Dom.byId(doc, "core"));
        marker = new Dom.Style(Dom.byId(doc, "marker"));
        qualityBar = new Dom.Style(Dom.byId(doc, "quality-bar"));
        temp = Dom.byId(doc, "temp");
        quality = Dom.byId(doc, "quality");
        grade = Dom.byId(doc, "grade");
        pips = Dom.byId(doc, "pips");
        log = Dom.byId(doc, "log");
        stage = Dom.byId(doc, "stage");
        hammer = Dom.query(doc, ".hammer");
        flash = Dom.byId(doc, "flash");
        verdict = Dom.byId(doc, "verdict");
        result = Dom.byId(doc, "result");
        resultCard = Dom.byId(doc, "result-card");
        strikesLeft = Dom.byId(doc, "strikes-left");
        for (int i = 0; i < STRIKES; i++) pips.appendChild(Dom.el(doc, "div", "pip", null));
        ideal.set("bottom", pct(frac(IDEAL_LOW))).set("height", pct(frac(IDEAL_HIGH) - frac(IDEAL_LOW))).apply();

        Element bellows = Dom.byId(doc, "bellows");
        bellowsButton = bellows;
        Dom.onMouse(bellows, "mousedown", e -> model.bellows = true);
        Dom.onMouse(doc, "mouseup", e -> model.bellows = false);
        bellows.addEventListener("mouseleave", e -> model.bellows = false);
        Dom.onClick(Dom.byId(doc, "strike"), e -> strike());
        Dom.onClick(Dom.byId(doc, "quench"), e -> quench());
        Dom.onClick(Dom.byId(doc, "again"), e -> reset());
        Dom.onClick(Dom.byId(doc, "close"), e -> doc.host().close());
        Dom.onClick(Dom.byId(doc, "back"), e -> Shell.back(doc));
        Dom.onKey(doc, this::key);
        doc.addEventListener("keyup", e -> {
            if (((KeyboardEvent) e).code.equals("KeyB")) model.bellows = false;
        });
        doc.scheduler().requestAnimationFrame(this::frame);
        render();
    }

    private record State(float temp, float quality, int strikes, List<String> verdicts) {}

    @Override
    public Object saveState() {
        return model.done ? null : new State(model.temp, model.quality, model.strikes, List.copyOf(model.verdicts));
    }

    @Override
    public void restoreState(Object state) {
        if (!(state instanceof State s)) return;
        model.temp = s.temp();
        model.quality = s.quality();
        model.strikes = s.strikes();
        model.verdicts.addAll(s.verdicts());
        for (int i = 0; i < s.verdicts().size() && i < pips.childCount(); i++) {
            String v = s.verdicts().get(i);
            pips.children().get(i).addClass(v.equals("perfect") ? "perfect" : v.equals("hit") ? "hit" : "miss");
        }
        render();
    }

    private void key(KeyboardEvent e) {
        if (e.code.equals("Space") && !e.repeat) {
            e.preventDefault();
            strike();
        } else if (e.code.equals("KeyB")) {
            model.bellows = true;
        }
    }

    private void frame(double now) {
        float dt = last < 0 ? 0 : (float) Math.min(0.1, (now - last) / 1000);
        last = now;
        model.advance(dt);
        render();
        doc.scheduler().requestAnimationFrame(this::frame);
    }

    private static float frac(float t) {
        return (t - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
    }

    void render() {
        float f = frac(model.temp);
        level.set("height", pct(f)).apply();
        float heat = Math.max(0, (model.temp - 700) / 900f);
        embers.rate(model.done ? 2 : 4 + 26 * heat);
        haze.set("opacity", Float.toString(round(Math.min(0.3f, heat * 0.4f)))).apply();
        bellowsButton.toggleClass("on", model.bellows);
        Dom.text(temp, Math.round(model.temp) + "°");
        temp.toggleClass("cold", model.temp < 800);
        temp.toggleClass("hot", model.temp > 1480);
        // The ingot glows from dark red to white-yellow as it heats, and stretches as it is worked.
        int[] c = heatColour(model.temp);
        float width = 30 + model.strikes * 3.5f;
        ingot.px("left", 75 - width / 2).px("width", width)
                .set("background-color", rgb(c))
                .set("box-shadow", "0 0 " + Math.round(2 + 8 * f) + "px rgba(" + c[0] + ", " + c[1] + ", " + c[2] + ", " + round(0.25f + 0.6f * f) + ")")
                .apply();
        glow.set("opacity", Float.toString(round(Math.max(0, (model.temp - 700) / 900f)))).apply();
        float half = model.zoneHalf();
        zone.set("left", pct(model.zoneCenter - half)).set("width", pct(half * 2)).apply();
        core.set("left", pct(model.zoneCenter - Model.CORE_HALF)).set("width", pct(Model.CORE_HALF * 2)).apply();
        marker.set("left", pct(model.marker())).apply();
        Dom.text(quality, Math.round(model.quality) + "%");
        qualityBar.set("--value", Float.toString(round(model.quality / 100))).apply();
        Dom.text(grade, model.grade());
        grade.setAttribute("class", "o-chip grade " + tier(model.grade()));
        Dom.text(strikesLeft, "剩 " + (STRIKES - model.strikes));
    }

    void strike() {
        String v = model.strike(random);
        if (v == null) {
            pulse(Dom.byId(doc, "quench"));
            return;
        }
        flip = !flip;
        String a = flip ? "-a" : "-b", b = flip ? "-b" : "-a";
        restart(hammer, "swing" + a, "swing" + b);
        if (!v.equals("miss") && !v.equals("cold")) restart(stage, "shake" + a, "shake" + b);
        if (v.equals("perfect") || v.equals("hit")) restart(flash, "on" + a, "on" + b);
        String[] text = switch (v) {
            case "perfect" -> new String[] {"完美!", "#f8af2b"};
            case "hit" -> new String[] {"命中", "#6cc349"};
            case "cold" -> new String[] {"太冷了", "#6ad9f5"};
            case "hot" -> new String[] {"过热!", "#e25a5a"};
            default -> new String[] {"偏了", "#e25a5a"};
        };
        Dom.text(verdict, text[0]);
        verdict.setAttribute("style", "color: " + text[1]);
        restart(verdict, "pop" + a, "pop" + b);
        Element pip = pips.children().get(model.strikes - 1);
        pip.addClass(v.equals("perfect") ? "perfect" : v.equals("hit") ? "hit" : "miss");
        Element line = Dom.el(doc, "div", null, model.strikes + ". " + text[0]);
        line.setAttribute("style", "color: " + text[1]);
        log.insertBefore(line, log.firstChild());
        while (log.childCount() > 4) log.lastChild().remove();
        burst(v.equals("perfect") ? 22 : v.equals("hit") ? 14 : 5, v.equals("perfect"));
    }

    private void restart(Element e, String on, String off) {
        e.removeClass(off);
        e.removeClass(on);
        e.addClass(on);
    }

    private void pulse(Element e) {
        e.addClass("hero");
        doc.scheduler().setTimeout(() -> e.removeClass("hero"), 600);
    }

    // ---- Sparks: a burst on the stage's particle canvas, from the ingot ----

    private void burst(int count, boolean gold) {
        float[] st = stage.getBoundingClientRect(), in = Dom.byId(doc, "ingot").getBoundingClientRect();
        float x = in[0] - st[0] + in[2] / 2, y = in[1] - st[1];
        sparks.burst("sparks", x, y, count);
        if (gold) sparks.burst("stars", x, y - 6, count / 3);
    }

    /** Live spark particles (for tests). */
    int sparkCount() {
        return sparks.count();
    }

    /** The rarity class of a grade. */
    static String tier(String grade) {
        return switch (grade) {
            case "传说" -> "r-legendary";
            case "史诗" -> "r-epic";
            case "精良" -> "r-rare";
            case "普通" -> "r-uncommon";
            default -> "r-common";
        };
    }

    // ---- Finish ----

    void quench() {
        if (model.done) return;
        model.done = true;
        Dom.text(Dom.byId(doc, "result-title"), model.grade() + " 铁剑");
        int perfect = (int) model.verdicts.stream().filter("perfect"::equals).count();
        Dom.text(Dom.byId(doc, "result-text"), "品质 " + Math.round(model.quality) + "% · 完美 " + perfect + " 次 · 击打 " + model.strikes + " 次");
        resultCard.setAttribute("class", "card o-tile o-frame fx-sweep " + tier(model.grade()));
        result.addClass("on");
        burst(26, true);
    }

    private void reset() {
        Model fresh = new Model();
        model.temp = fresh.temp;
        model.quality = 0;
        model.strikes = 0;
        model.done = false;
        model.verdicts.clear();
        for (Element pip : pips.children()) pip.setAttribute("class", "pip");
        log.removeAllChildren();
        result.removeClass("on");
    }

    // ---- Colour ----

    /** Black-body-ish ramp: dark red at 600°, orange at 1000°, yellow at 1300°, near white at 1600°. */
    static int[] heatColour(float t) {
        float[][] stops = {{300, 58, 52, 50}, {650, 110, 26, 18}, {900, 196, 46, 22}, {1100, 230, 112, 30},
                {1300, 250, 186, 70}, {1600, 255, 246, 214}};
        for (int i = 1; i < stops.length; i++) {
            if (t <= stops[i][0]) {
                float k = (t - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
                int[] c = new int[3];
                for (int j = 0; j < 3; j++) c[j] = Math.round(stops[i - 1][j + 1] + (stops[i][j + 1] - stops[i - 1][j + 1]) * k);
                return c;
            }
        }
        return new int[] {255, 246, 214};
    }

    private static String rgb(int[] c) {
        return "rgb(" + c[0] + ", " + c[1] + ", " + c[2] + ")";
    }

    private static String pct(float f) {
        return round(Math.max(0, Math.min(1, f)) * 100) + "%";
    }

    private static float round(float v) {
        return Math.round(v * 100) / 100f;
    }
}
