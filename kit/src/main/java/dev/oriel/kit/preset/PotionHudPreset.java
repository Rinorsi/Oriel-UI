package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.GameEffects;
import dev.oriel.kit.Shell;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The potion HUD over fixture effects, in two forms that share one model:
 * <ul>
 *   <li>the HUD page ({@link #attach}): a compact bar of icon tiles at the top right, each draining like an item
 *   cooldown, with the level and time; a new effect is announced by a card that slides out under the bar and then
 *   folds away into its tile; the handle (or H) unfolds the full panel;</li>
 *   <li>the full panel ({@link #mountPanel}), also embedded in the inventory: effects grouped into beneficial,
 *   harmful and neutral, each with a countdown and a draining bar. A group shows three rows and a "另有 N 个" chip
 *   that expands it; its header folds it to a strip of icons.</li>
 * </ul>
 * Effects blink in their last ten seconds and fade out when they end. In game ({@link GameEffects} has a provider)
 * they are the player's own, as the game reports them; elsewhere (the previewer, tests) fixtures keep arriving from
 * a queue.
 */
public final class PotionHudPreset implements PageControllers.Controller {
    static final int SHOWN = 3, BAR_TILES = 5;
    static final double ARRIVAL_SECONDS = 9, FRESH_SECONDS = 4, ANNOUNCE_SECONDS = 2.6;

    enum Kind { BENEFICIAL, HARMFUL, NEUTRAL }

    /** {@code id} is a registry id ("minecraft:speed") or a vanilla one's path; {@code seconds} may be infinite. */
    record Effect(String id, String name, int level, Kind kind, double seconds) {}

    /** One running effect: when it ends (in page seconds), its panel row and its bar tile. */
    static final class Running {
        Effect effect;
        final double start;
        double end;
        /** The duration its bar drains over: the longest it has had (a new dose can extend it). */
        double total;
        /** The whole seconds the game last reported (game effects), which the countdown does not pass. */
        int reported = -1;
        Element row, time, tile, tileTime;
        Dom.Style bar, drain;
        int shownSeconds = -1;
        /** Until when (page seconds) it shows even past a group's row limit: arrivals stay in view a moment. */
        double freshUntil;
        boolean leaving;

        Running(Effect effect, double now) {
            this.effect = effect;
            this.start = now;
            this.end = now + effect.seconds();
            this.total = effect.seconds();
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

    private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final String[] GROUP_TITLES = {"增益", "减益", "中性"}, GROUP_CLASSES = {"beneficial", "harmful", "neutral"};

    final Map<Kind, List<Running>> groups = new LinkedHashMap<>();
    private final Map<Kind, Element> groupElements = new LinkedHashMap<>();
    private final Map<Kind, Boolean> expanded = new LinkedHashMap<>();
    private final Deque<Effect> queue = new ArrayDeque<>();
    private Document doc;
    private Element toast, tiles, more, hud;
    /** Every row of every group, with no "more" fold (the panel has a place of its own). */
    private boolean everyRow;
    private double now, nextArrival = ARRIVAL_SECONDS, toastUntil;
    /** The game's effects last seen, or null for fixtures. */
    private GameEffects.Snapshot game;
    private Element hint;

    /** The HUD page: bar, announcements and the folding full panel. */
    @Override
    public void attach(Document document) {
        doc = document;
        Shell.setup(doc);
        hud = Dom.byId(doc, "hud");
        toast = Dom.byId(doc, "toast");
        tiles = Dom.byId(doc, "tiles");
        more = Dom.byId(doc, "more-tiles");
        hint = Dom.query(hud, ".hint");
        Element handle = Dom.byId(doc, "handle");
        Shell.activate(handle, this::togglePanel);
        Dom.onKey(doc, e -> {
            if (e.key.equalsIgnoreCase("h")) togglePanel();
        });
        start(Dom.byId(doc, "panel"));
    }

    /**
     * The full panel alone, inside {@code where} of another page (the inventory's effects column); {@code folded}
     * starts every group folded to its strip of icons, else every row shows (the place scrolls when they overflow).
     */
    public void mountPanel(Document document, Element where, boolean folded) {
        doc = document;
        everyRow = !folded;
        start(where);
        if (folded) for (Element g : groupElements.values()) g.addClass("folded");
    }

    private void start(Element panel) {
        for (Kind k : Kind.values()) {
            groups.put(k, new ArrayList<>());
            expanded.put(k, false);
            Element g = Dom.html(doc, "<div class=\"group " + GROUP_CLASSES[k.ordinal()] + "\"><div class=\"head\">"
                    + "<img class=\"o-ico caret\" src=\"../textures/icons/down.png\"><i class=\"dot\"></i>"
                    + GROUP_TITLES[k.ordinal()] + "<span class=\"n\"></span></div><div class=\"list\"></div>"
                    + "<div class=\"strip\"></div><div class=\"more\"></div></div>");
            panel.appendChild(g);
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
        game = GameEffects.current();
        if (game != null) {
            sync(game, false);
        } else {
            for (Effect e : INITIAL) add(e, false);
            queue.addAll(ARRIVALS);
        }
        // Every group's counts and empty state, also those nothing has been added to yet.
        for (Kind k : Kind.values()) refresh(k);
        doc.scheduler().requestAnimationFrame(t -> frame(t, t));
    }

    private void togglePanel() {
        if (hud != null) hud.toggleClass("full");
    }

    private void frame(double t, double origin) {
        now = (t - origin) / 1000;
        tick();
        doc.scheduler().requestAnimationFrame(next -> frame(next, origin));
    }

    /** Advances to {@link #now}: arrivals, countdowns, bars and departures. */
    void tick() {
        if (game != null) {
            GameEffects.Snapshot next = GameEffects.current();
            if (next != null && next != game) {
                game = next;
                sync(next, true);
            }
        }
        if (now >= nextArrival && !queue.isEmpty()) {
            nextArrival = now + ARRIVAL_SECONDS;
            Effect e = queue.poll();
            add(e, true);
            queue.add(e);
            announce(e);
        }
        if (toast != null && now > toastUntil && toast.hasClass("on")) {
            toast.removeClass("on");
            toast.addClass("folding");
        }
        for (Kind k : Kind.values()) {
            for (Running r : List.copyOf(groups.get(k))) {
                double left = r.remaining(now);
                // Between the game's reports the countdown runs on, but not past the second it reported (paused games).
                if (r.reported > 0) left = Math.max(left, r.reported - 0.999);
                int secs = (int) Math.min(Integer.MAX_VALUE, Math.ceil(left));
                if (secs != r.shownSeconds) {
                    r.shownSeconds = secs;
                    String text = clock(secs);
                    Dom.text(r.time, text);
                    r.row.toggleClass("low", secs <= 10 && secs > 0);
                    if (r.tile != null) {
                        Dom.text(r.tileTime, shortClock(secs));
                        r.tile.toggleClass("low", secs <= 10 && secs > 0);
                    }
                }
                double fraction = Double.isInfinite(r.total) ? 1 : r.total <= 0 ? 0 : Math.min(1, left / r.total);
                r.bar.set("width", Math.round(1000 * fraction) / 10.0 + "%").apply();
                if (r.drain != null) r.drain.set("height", Math.round(1000 * (1 - fraction)) / 10.0 + "%").apply();
                if (game == null && left <= 0 && !r.leaving) leave(k, r);
            }
        }
    }

    void add(Effect e, boolean fresh) {
        List<Running> list = groups.get(e.kind());
        // A new dose replaces a running one of the same effect.
        for (Running r : List.copyOf(list)) if (r.effect.id().equals(e.id()) && !r.leaving) remove(e.kind(), r);
        Running r = new Running(e, now);
        r.freshUntil = fresh ? now + FRESH_SECONDS : 0;
        Element row = Dom.html(doc, "<div class=\"eff\"><i class=\"remain\"></i><img><span class=\"name\"></span>"
                + "<span class=\"time\"></span></div>");
        Dom.query(row, "img").setAttribute("src", icon(e));
        Dom.text(Dom.query(row, ".name"), e.name() + level(e.level()));
        r.row = row;
        r.time = Dom.query(row, ".time");
        r.bar = new Dom.Style(Dom.query(row, ".remain"));
        if (tiles != null) {
            Element tile = Dom.html(doc, "<div class=\"tile " + GROUP_CLASSES[e.kind().ordinal()] + "\"><img>"
                    + "<i class=\"drain\"></i><b class=\"lv\"></b><span class=\"t\"></span></div>");
            Dom.query(tile, "img").setAttribute("src", icon(e));
            Dom.text(Dom.query(tile, ".lv"), level(e.level()).trim());
            tile.toggleClass("leveled", !level(e.level()).isBlank());
            tile.setAttribute("title", e.name() + level(e.level()));
            r.tile = tile;
            r.tileTime = Dom.query(tile, ".t");
            r.drain = new Dom.Style(Dom.query(tile, ".drain"));
        }
        if (fresh) {
            row.addClass("fresh");
            if (r.tile != null) r.tile.addClass("fresh");
            doc.scheduler().setTimeout(() -> {
                row.removeClass("fresh");
                if (r.tile != null) r.tile.removeClass("fresh");
            }, 1600);
            doc.scheduler().setTimeout(() -> refresh(e.kind()), FRESH_SECONDS * 1000 + 50);
        }
        // Soonest to end first within a group, so countdowns about to run out are in view.
        int at = 0;
        while (at < list.size() && list.get(at).end <= r.end) at++;
        list.add(at, r);
        refresh(e.kind());
    }

    /**
     * Brings the effects in line with the game's: a new effect (or a new level of one) is added, announced when
     * {@code fresh}; one that is still running takes the game's time; one the game no longer has fades out.
     * Effects the game hides from the HUD ({@code showIcon} off) are left out.
     */
    private void sync(GameEffects.Snapshot snapshot, boolean fresh) {
        Map<String, GameEffects.Entry> wanted = new LinkedHashMap<>();
        for (GameEffects.Entry e : snapshot.effects()) if (e.showIcon()) wanted.put(e.id(), e);
        for (Kind k : Kind.values()) {
            boolean moved = false;
            for (Running r : List.copyOf(groups.get(k))) {
                if (r.leaving) continue;
                GameEffects.Entry e = wanted.get(r.effect.id());
                if (e == null) {
                    leave(k, r);
                } else if (e.level() == r.effect.level() && kind(e) == k) {
                    wanted.remove(e.id());
                    moved |= report(r, e);
                }
            }
            if (moved) {
                groups.get(k).sort(Comparator.comparingDouble(r -> r.end));
                refresh(k);
            }
        }
        for (GameEffects.Entry e : wanted.values()) {
            Effect effect = new Effect(e.id(), e.name(), e.level(), kind(e), e.seconds() < 0 ? Double.POSITIVE_INFINITY : e.seconds());
            add(effect, fresh);
            Running r = find(effect);
            if (r != null) r.reported = e.seconds();
            if (fresh) announce(effect);
        }
    }

    /** The game's time for a running effect; whether its place in the soonest-first order may have changed. */
    private boolean report(Running r, GameEffects.Entry e) {
        double end = e.seconds() < 0 ? Double.POSITIVE_INFINITY : now + e.seconds();
        boolean moved = Math.abs(end - r.end) > 1.5; // a new dose, not the clock
        r.end = end;
        r.reported = e.seconds();
        r.total = Math.max(r.total, e.seconds() < 0 ? Double.POSITIVE_INFINITY : e.seconds());
        if (!e.name().equals(r.effect.name())) { // the language changed
            r.effect = new Effect(r.effect.id(), e.name(), r.effect.level(), r.effect.kind(), r.effect.seconds());
            Dom.text(Dom.query(r.row, ".name"), e.name() + level(e.level()));
            if (r.tile != null) r.tile.setAttribute("title", e.name() + level(e.level()));
        }
        return moved;
    }

    private Running find(Effect e) {
        for (Running r : groups.get(e.kind())) if (r.effect == e) return r;
        return null;
    }

    private static Kind kind(GameEffects.Entry e) {
        return Kind.valueOf(e.kind().name());
    }

    private void leave(Kind k, Running r) {
        r.leaving = true;
        r.row.addClass("gone");
        if (r.tile != null) r.tile.addClass("gone");
        doc.scheduler().setTimeout(() -> remove(k, r), 620);
    }

    private void remove(Kind k, Running r) {
        groups.get(k).remove(r);
        r.row.remove();
        if (r.tile != null) r.tile.remove();
        refresh(k);
    }

    /** The card under the bar: the new effect's icon, name and duration, folding away after a moment. */
    private void announce(Effect e) {
        if (toast == null) return;
        toast.removeAllChildren();
        toast.setAttribute("class", "toast " + GROUP_CLASSES[e.kind().ordinal()]);
        Element img = Dom.el(doc, "img", null, null);
        img.setAttribute("src", icon(e));
        toast.appendChild(img);
        Element text = Dom.el(doc, "div", "text", null);
        text.appendChild(Dom.el(doc, "div", "name", (e.kind() == Kind.HARMFUL ? "受到 " : "获得 ") + e.name() + level(e.level())));
        text.appendChild(Dom.el(doc, "div", "sub", clock((int) e.seconds()) + " · " + GROUP_TITLES[e.kind().ordinal()]));
        toast.appendChild(text);
        doc.scheduler().requestAnimationFrame(t -> toast.addClass("on"));
        toastUntil = now + ANNOUNCE_SECONDS;
    }

    /** Rebuilds a group's visible rows, strip and counts, and the bar. */
    void refresh(Kind k) {
        Element g = groupElements.get(k), list = Dom.query(g, ".list"), strip = Dom.query(g, ".strip"), more = Dom.query(g, ".more");
        List<Running> effects = groups.get(k);
        int limit = everyRow || expanded.get(k) ? Integer.MAX_VALUE : SHOWN;
        // The first rows, plus any effect that has just started: an arrival always slides into view.
        List<Element> want = new ArrayList<>();
        for (int i = 0; i < effects.size(); i++) {
            Running r = effects.get(i);
            if (i < limit || now < r.freshUntil) want.add(r.row);
        }
        place(list, want);
        strip.removeAllChildren();
        for (Running r : effects) {
            Element img = Dom.el(doc, "img", null, null);
            img.setAttribute("src", icon(r.effect));
            strip.appendChild(img);
        }
        int hidden = effects.size() - want.size();
        more.setAttribute("style", everyRow || hidden == 0 && !expanded.get(k) ? "display: none" : "");
        Dom.text(more, expanded.get(k) ? "收起 ▴" : "另有 " + hidden + " 个效果 ▾");
        Dom.text(Dom.query(g, ".n"), Integer.toString(effects.size()));
        g.toggleClass("empty", effects.isEmpty());
        refreshBar();
    }

    /** The bar: beneficial, then neutral, then harmful tiles, soonest first in each, up to {@link #BAR_TILES}. */
    private void refreshBar() {
        if (tiles == null) return;
        List<Element> want = new ArrayList<>();
        int total = 0;
        for (Kind k : new Kind[] {Kind.BENEFICIAL, Kind.NEUTRAL, Kind.HARMFUL}) {
            for (Running r : groups.getOrDefault(k, List.of())) {
                total++;
                if (want.size() < BAR_TILES) want.add(r.tile);
            }
        }
        place(tiles, want);
        Dom.text(more, total > want.size() ? "+" + (total - want.size()) : "");
        more.toggleClass("none", total <= want.size());
        // In game, the HUD has nothing to show until the player has an effect; the hint says so.
        boolean empty = game != null && total == 0;
        if (hud != null && hud.hasClass("empty") != empty) {
            hud.toggleClass("empty", empty);
            if (hint != null) Dom.text(hint, empty ? "身上没有药水效果" : "打开聊天栏后可展开全部效果");
        }
    }

    /** Puts {@code want} into {@code parent} in order, moving only what must move (re-inserting replays slide-ins). */
    private static void place(Element parent, List<Element> want) {
        for (Element c : List.copyOf(parent.children())) if (!want.contains(c)) c.remove();
        for (int i = 0; i < want.size(); i++) {
            Element e = want.get(i);
            if (i >= parent.childCount() || parent.childAt(i) != e) parent.insertBefore(e, i < parent.childCount() ? parent.childAt(i) : null);
        }
    }

    static String icon(Effect e) {
        int colon = e.id().indexOf(':');
        String namespace = colon < 0 ? "minecraft" : e.id().substring(0, colon);
        return namespace + ":textures/mob_effect/" + e.id().substring(colon + 1) + ".png";
    }

    /** The level after a name: none for I, Roman numerals to X, digits beyond, as vanilla. */
    static String level(int level) {
        return level < ROMAN.length ? ROMAN[Math.max(0, level)] : " " + level;
    }

    static String clock(int seconds) {
        if (seconds >= 3600) return "**:**";
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    /** The bar's short form: seconds under a minute, minutes above. */
    static String shortClock(int seconds) {
        if (seconds >= 3600) return "∞";
        return seconds < 60 ? seconds + "s" : seconds / 60 + "m";
    }
}
