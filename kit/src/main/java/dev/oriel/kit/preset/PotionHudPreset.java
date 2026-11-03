package dev.oriel.kit.preset;

import dev.oriel.kit.Dom;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The potion HUD preset over fixture effects: effects grouped into beneficial, harmful and neutral, each with a
 * countdown and a draining bar. A group shows three rows and a "另有 N 个" chip that expands it; its header folds it
 * to a strip of icons (the HUD takes the pointer while chat is open). Effects slide in when they start, blink in
 * their last ten seconds and fade out when they end; new ones keep arriving from a queue.
 */
public final class PotionHudPreset implements PageControllers.Controller {
    static final int SHOWN = 3;
    static final double ARRIVAL_SECONDS = 9, FRESH_SECONDS = 4;

    enum Kind { BENEFICIAL, HARMFUL, NEUTRAL }

    record Effect(String id, String name, int level, Kind kind, double seconds) {}

    /** One running effect: when it ends (in page seconds) and its row. */
    static final class Running {
        final Effect effect;
        final double start, end;
        Element row, time, left;
        Dom.Style bar;
        int shownSeconds = -1;
        /** Until when (page seconds) it shows even past a group's row limit: arrivals stay in view a moment. */
        double freshUntil;
        boolean leaving;

        Running(Effect effect, double now) {
            this.effect = effect;
            this.start = now;
            this.end = now + effect.seconds();
        }

        double remaining(double now) {
            return Math.max(0, end - now);
        }
    }

    static final List<Effect> INITIAL = List.of(
            new Effect("speed", "速度", 2, Kind.BENEFICIAL, 42),
            new Effect("strength", "力量", 1, Kind.BENEFICIAL, 190),
            new Effect("regeneration", "生命恢复", 2, Kind.BENEFICIAL, 18),
            new Effect("fire_resistance", "抗火", 1, Kind.BENEFICIAL, 480),
            new Effect("night_vision", "夜视", 1, Kind.BENEFICIAL, 330),
            new Effect("haste", "急迫", 2, Kind.BENEFICIAL, 105),
            new Effect("jump_boost", "跳跃提升", 1, Kind.BENEFICIAL, 55),
            new Effect("wither", "凋零", 2, Kind.HARMFUL, 8),
            new Effect("poison", "中毒", 1, Kind.HARMFUL, 24),
            new Effect("slowness", "缓慢", 1, Kind.HARMFUL, 72),
            new Effect("weakness", "虚弱", 1, Kind.HARMFUL, 35),
            new Effect("glowing", "发光", 1, Kind.NEUTRAL, 50),
            new Effect("bad_omen", "不祥之兆", 3, Kind.NEUTRAL, 3600));
    static final List<Effect> ARRIVALS = List.of(
            new Effect("water_breathing", "水下呼吸", 1, Kind.BENEFICIAL, 90),
            new Effect("resistance", "抗性提升", 2, Kind.BENEFICIAL, 30),
            new Effect("hunger", "饥饿", 1, Kind.HARMFUL, 20),
            new Effect("slow_falling", "缓降", 1, Kind.BENEFICIAL, 45),
            new Effect("invisibility", "隐身", 1, Kind.BENEFICIAL, 120),
            new Effect("mining_fatigue", "挖掘疲劳", 3, Kind.HARMFUL, 26),
            new Effect("luck", "幸运", 1, Kind.NEUTRAL, 300),
            new Effect("absorption", "伤害吸收", 2, Kind.BENEFICIAL, 60));

    private static final String[] ROMAN = {"", "", " II", " III", " IV", " V"};

    final Map<Kind, List<Running>> groups = new LinkedHashMap<>();
    private final Map<Kind, Element> groupElements = new LinkedHashMap<>();
    private final Map<Kind, Boolean> expanded = new LinkedHashMap<>();
    private final Deque<Effect> queue = new ArrayDeque<>();
    private Document doc;
    private Element toast;
    private double now, nextArrival = ARRIVAL_SECONDS, toastUntil;

    @Override
    public void attach(Document document) {
        doc = document;
        toast = Dom.byId(doc, "toast");
        Element hud = Dom.byId(doc, "hud");
        String[] titles = {"增益", "减益", "中性"};
        for (Kind k : Kind.values()) {
            groups.put(k, new ArrayList<>());
            expanded.put(k, false);
            Element g = Dom.html(doc, "<div class=\"group\"><div class=\"head\"><span class=\"caret\">▾</span>"
                    + titles[k.ordinal()] + "<span class=\"n\"></span></div><div class=\"list\"></div>"
                    + "<div class=\"strip\"></div><div class=\"more\"></div></div>");
            hud.appendChild(g);
            groupElements.put(k, g);
            Dom.onClick(Dom.query(g, ".head"), e -> {
                g.toggleClass("folded");
                refresh(k);
            });
            Dom.onClick(Dom.query(g, ".more"), e -> {
                expanded.put(k, !expanded.get(k));
                refresh(k);
            });
        }
        for (Effect e : INITIAL) add(e, false);
        queue.addAll(ARRIVALS);
        doc.scheduler().requestAnimationFrame(t -> frame(t, t));
    }

    private void frame(double t, double origin) {
        now = (t - origin) / 1000;
        tick();
        doc.scheduler().requestAnimationFrame(next -> frame(next, origin));
    }

    /** Advances to {@link #now}: arrivals, countdowns, bars and departures. */
    void tick() {
        if (now >= nextArrival && !queue.isEmpty()) {
            nextArrival = now + ARRIVAL_SECONDS;
            Effect e = queue.poll();
            add(e, true);
            queue.add(e);
            say("获得效果：" + e.name() + ROMAN[e.level()]);
        }
        if (now > toastUntil) toast.removeClass("on");
        for (Kind k : Kind.values()) {
            for (Running r : List.copyOf(groups.get(k))) {
                double left = r.remaining(now);
                int secs = (int) Math.ceil(left);
                if (secs != r.shownSeconds) {
                    r.shownSeconds = secs;
                    Dom.text(r.time, clock(secs));
                    r.row.toggleClass("low", secs <= 10 && secs > 0);
                }
                r.bar.set("width", Math.round(1000 * left / r.effect.seconds()) / 10.0 + "%").apply();
                if (left <= 0 && !r.leaving) leave(k, r);
            }
        }
    }

    void add(Effect e, boolean fresh) {
        List<Running> list = groups.get(e.kind());
        // A new dose replaces a running one of the same effect.
        for (Running r : List.copyOf(list)) if (r.effect.id().equals(e.id()) && !r.leaving) remove(e.kind(), r);
        Running r = new Running(e, now);
        r.freshUntil = fresh ? now + FRESH_SECONDS : 0;
        Element row = Dom.html(doc, "<div class=\"fx\"><img><span class=\"name\"></span><span class=\"time\"></span>"
                + "<div class=\"bar\"><div class=\"left\"></div></div></div>");
        row.toggleClass("harmful", e.kind() == Kind.HARMFUL);
        row.toggleClass("neutral", e.kind() == Kind.NEUTRAL);
        Dom.query(row, "img").setAttribute("src", icon(e));
        Dom.text(Dom.query(row, ".name"), e.name() + ROMAN[e.level()]);
        r.row = row;
        r.time = Dom.query(row, ".time");
        r.left = Dom.query(row, ".left");
        r.bar = new Dom.Style(r.left);
        if (fresh) {
            row.addClass("fresh");
            doc.scheduler().setTimeout(() -> row.removeClass("fresh"), 1600);
            doc.scheduler().setTimeout(() -> refresh(e.kind()), FRESH_SECONDS * 1000 + 50);
        }
        // Soonest to end first within a group, so countdowns about to run out are in view.
        int at = 0;
        while (at < list.size() && list.get(at).end <= r.end) at++;
        list.add(at, r);
        refresh(e.kind());
    }

    private void leave(Kind k, Running r) {
        r.leaving = true;
        r.row.addClass("gone");
        say(r.effect.name() + " 已结束");
        doc.scheduler().setTimeout(() -> remove(k, r), 620);
    }

    private void remove(Kind k, Running r) {
        groups.get(k).remove(r);
        r.row.remove();
        refresh(k);
    }

    /** Rebuilds a group's visible rows, strip and counts. */
    void refresh(Kind k) {
        Element g = groupElements.get(k), list = Dom.query(g, ".list"), strip = Dom.query(g, ".strip"), more = Dom.query(g, ".more");
        List<Running> effects = groups.get(k);
        int limit = expanded.get(k) ? Integer.MAX_VALUE : SHOWN;
        // The first rows, plus any effect that has just started: an arrival always slides into view.
        List<Element> want = new ArrayList<>();
        for (int i = 0; i < effects.size(); i++) {
            Running r = effects.get(i);
            if (i < limit || now < r.freshUntil) want.add(r.row);
        }
        // Rows only move when they must: re-inserting one would replay its slide-in.
        for (Element c : List.copyOf(list.children())) if (!want.contains(c)) c.remove();
        for (int i = 0; i < want.size(); i++) {
            Element row = want.get(i);
            if (i >= list.childCount() || list.childAt(i) != row) list.insertBefore(row, i < list.childCount() ? list.childAt(i) : null);
        }
        strip.removeAllChildren();
        for (Running r : effects) {
            Element img = Dom.el(doc, "img", null, null);
            img.setAttribute("src", icon(r.effect));
            strip.appendChild(img);
        }
        int hidden = effects.size() - want.size();
        more.setAttribute("style", hidden == 0 && !expanded.get(k) ? "display: none" : "");
        Dom.text(more, expanded.get(k) ? "收起 ▴" : "另有 " + hidden + " 个效果 ▾");
        Dom.text(Dom.query(g, ".n"), Integer.toString(effects.size()));
        g.toggleClass("empty", effects.isEmpty());
    }

    private void say(String text) {
        Dom.text(toast, text);
        toast.addClass("on");
        toastUntil = now + 2.2;
    }

    static String icon(Effect e) {
        return "minecraft:textures/mob_effect/" + e.id() + ".png";
    }

    static String clock(int seconds) {
        if (seconds >= 3600) return "**:**";
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
