package dev.oriel.kit.preset;

import dev.oriel.kit.Dom;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;
import dev.oriel.kit.fx.Particles;
import dev.oriel.kit.Slider;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.MouseEvent;
import dev.oriel.engine.host.PageControllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The music player preset over a fixture playlist: play and pause, scrub the progress bar, skip, set the volume,
 * and drag rows to reorder the playlist (the other rows slide out of the way). The equalizer bars dance while it
 * plays and settle when it pauses. There is no audio: the page models a player whose sound a host would provide.
 */
public final class MusicPreset implements PageControllers.Controller {
    static final float ROW = 21;
    static final int BARS = 24;

    record Track(String title, String artist, String disc, int seconds) {}

    final List<Track> playlist = new ArrayList<>(List.of(
            new Track("晨光矿道", "Oriel 合奏团", "minecraft:music_disc_cat", 185),
            new Track("深板岩回响", "地下乐队", "minecraft:music_disc_13", 214),
            new Track("下界之风", "烈焰三重奏", "minecraft:music_disc_pigstep", 148),
            new Track("林间小憩", "苔藓与蕨", "minecraft:music_disc_chirp", 196),
            new Track("海底神殿", "潮涌", "minecraft:music_disc_mellohi", 233),
            new Track("末地星尘", "紫颂", "minecraft:music_disc_otherside", 171)));
    final List<Element> rows = new ArrayList<>();
    int current;
    double position;
    boolean playing;
    double volume = 0.7;

    private Document doc;
    private Slider seek, volumeSlider;
    private Element title, meta, state, elapsed, length, disc, play, rowsBox;
    private final float[] levels = new float[BARS], caps = new float[BARS], targets = new float[BARS];
    private final List<Dom.Style> levelStyles = new ArrayList<>(), capStyles = new ArrayList<>(), mirrorStyles = new ArrayList<>();
    private Element screen, playIcon, miniPlayIcon, miniTitle;
    private Dom.Style miniLine;
    private Particles.Emitter notes;
    private boolean shuffle;
    private final Random random = new Random(3);
    private double last = -1, retarget;
    // Row dragging.
    private Element dragRow;
    private int dragFrom, dragTo;
    private float dragStartY, dragOffset;
    private boolean dragMoved;

    @Override
    public void attach(Document document) {
        doc = document;
        notes = Shell.setup(doc).emitter("notes");
        screen = Dom.byId(doc, "screen");
        playIcon = Dom.byId(doc, "play-icon");
        miniPlayIcon = Dom.byId(doc, "mini-play-icon");
        miniTitle = Dom.byId(doc, "mini-title");
        miniLine = new Dom.Style(Dom.byId(doc, "mini-line"));
        title = Dom.byId(doc, "title");
        meta = Dom.byId(doc, "meta");
        state = Dom.byId(doc, "state");
        elapsed = Dom.byId(doc, "elapsed");
        length = Dom.byId(doc, "length");
        disc = Dom.query(doc, ".disc");
        play = Dom.byId(doc, "play");
        rowsBox = Dom.byId(doc, "rows");
        Element eq = Dom.byId(doc, "eq"), mirror = Dom.byId(doc, "mirror");
        for (int i = 0; i < BARS; i++) {
            String colour = "--c: " + bandColour(i / (float) (BARS - 1));
            Element bar = Dom.el(doc, "div", "bar", null), lvl = Dom.el(doc, "div", "lvl", null), cap = Dom.el(doc, "div", "cap", null);
            bar.setAttribute("style", colour);
            bar.appendChild(lvl);
            bar.appendChild(cap);
            eq.appendChild(bar);
            levelStyles.add(new Dom.Style(lvl));
            capStyles.add(new Dom.Style(cap));
            Element mb = Dom.el(doc, "div", "bar", null), ml = Dom.el(doc, "div", "lvl", null);
            mb.setAttribute("style", colour);
            mb.appendChild(ml);
            mirror.appendChild(mb);
            mirrorStyles.add(new Dom.Style(ml));
        }
        seek = new Slider(doc, Dom.byId(doc, "seek"), 0).onInput(v -> {
            position = v * track().seconds();
            renderTime();
        });
        volumeSlider = new Slider(doc, Dom.byId(doc, "volume"), 20).onInput(v -> {
            volume = v;
            Dom.text(Dom.byId(doc, "vol-label"), volume == 0 ? "静音" : "音量 " + Math.round(volume * 100) + "%");
            Dom.attr(Dom.byId(doc, "vol-icon"), "src", "../textures/icons/" + (volume == 0 ? "mute" : "volume") + ".png");
        });
        volumeSlider.set(volume);
        for (int i = 0; i < playlist.size(); i++) rows.add(row(playlist.get(i)));
        layoutRows();
        Dom.text(Dom.byId(doc, "count"), playlist.size() + " 首");

        Dom.onClick(play, e -> toggle());
        Dom.onClick(Dom.byId(doc, "prev"), e -> skip(-1));
        Dom.onClick(Dom.byId(doc, "next"), e -> skip(1));
        Dom.onClick(Dom.byId(doc, "close"), e -> doc.host().close());
        Dom.onClick(Dom.byId(doc, "back"), e -> doc.navigate(OrielKit.GALLERY));
        Shell.activate(Dom.byId(doc, "mini-play"), this::toggle);
        Shell.activate(Dom.byId(doc, "mini"), () -> screen.addClass("mini"));
        Shell.activate(Dom.byId(doc, "expand"), () -> screen.removeClass("mini"));
        Element shuffleButton = Dom.byId(doc, "shuffle"), repeat = Dom.byId(doc, "repeat");
        Shell.activate(shuffleButton, () -> shuffleButton.toggleClass("on", shuffle = !shuffle));
        Shell.activate(repeat, () -> repeat.toggleClass("on"));
        Dom.onKey(doc, e -> {
            if (doc.focusedElement() != null && doc.focusedElement().isTextControl()) return;
            if (e.code.equals("Space")) {
                e.preventDefault();
                toggle();
            } else if (e.key.equals("ArrowRight")) {
                skip(1);
            } else if (e.key.equals("ArrowLeft")) {
                skip(-1);
            }
        });
        Dom.onMouse(doc, "mousemove", this::dragMove);
        Dom.onMouse(doc, "mouseup", this::dragEnd);
        select(0, false);
        doc.scheduler().requestAnimationFrame(this::frame);
    }

    Track track() {
        return playlist.get(current);
    }

    void toggle() {
        playing = !playing;
        String icon = "../textures/icons/" + (playing ? "pause" : "play") + ".png";
        Dom.attr(playIcon, "src", icon);
        Dom.attr(miniPlayIcon, "src", icon);
        screen.toggleClass("playing", playing);
        notes.rate(playing ? 2.5f : 0);
        Dom.text(Dom.byId(doc, "state-text"), playing ? "正在播放" : "已暂停");
    }

    void skip(int delta) {
        int next = shuffle && playlist.size() > 1 ? Math.floorMod(current + 1 + random.nextInt(playlist.size() - 1), playlist.size())
                : Math.floorMod(current + delta, playlist.size());
        select(next, true);
    }

    void select(int index, boolean restart) {
        current = index;
        if (restart) position = 0;
        Track t = track();
        Dom.text(title, t.title());
        Dom.text(meta, t.artist());
        Dom.attr(disc, "id", t.disc());
        Dom.text(miniTitle, t.title() + " · " + t.artist());
        new Dom.Style(screen).set("--disc", discColour(t.disc())).apply();
        Dom.text(length, time(t.seconds()));
        for (int i = 0; i < rows.size(); i++) rows.get(i).toggleClass("current", i == current);
        renderTime();
    }

    private void frame(double now) {
        double dt = last < 0 ? 0 : Math.min(0.1, (now - last) / 1000);
        last = now;
        if (playing && !seek.dragging()) {
            position += dt;
            if (position >= track().seconds()) skip(1);
            renderTime();
        }
        // Equalizer: while playing, each bar eases towards a target that changes a few times a second; the caps
        // fall slowly behind the bars; paused, everything settles to the floor.
        retarget -= dt;
        if (retarget <= 0) {
            retarget = 0.11;
            for (int i = 0; i < BARS; i++) {
                double shape = 0.55 + 0.45 * Math.sin(i * 0.7 + now / 900);
                targets[i] = playing ? (float) (volume * shape * (0.35 + random.nextFloat() * 0.65)) : 0;
            }
        }
        for (int i = 0; i < BARS; i++) {
            float k = (float) (1 - Math.pow(playing ? 0.0005 : 0.02, dt));
            levels[i] += (targets[i] - levels[i]) * k;
            caps[i] = Math.max(levels[i], caps[i] - (float) dt * 0.35f);
            levelStyles.get(i).set("height", Math.round(levels[i] * 1000) / 10f + "%").apply();
            capStyles.get(i).set("bottom", Math.round(caps[i] * 1000) / 10f + "%").apply();
            mirrorStyles.get(i).set("height", Math.round(levels[i] * 1000) / 10f + "%").apply();
        }
        doc.scheduler().requestAnimationFrame(this::frame);
    }

    private void renderTime() {
        Dom.text(elapsed, time((int) position));
        if (!seek.dragging()) seek.set(position / track().seconds());
        miniLine.set("width", Math.round(1000 * position / track().seconds()) / 10f + "%").apply();
    }

    /** The spectrum's colour across the bands: cyan, violet, pink. */
    static String bandColour(float t) {
        int[][] stops = {{85, 200, 255}, {196, 106, 255}, {255, 90, 154}};
        float f = t * 2;
        int i = Math.min(1, (int) f);
        float k = f - i;
        int[] a = stops[i], b = stops[i + 1];
        return String.format("#%02x%02x%02x", Math.round(a[0] + (b[0] - a[0]) * k), Math.round(a[1] + (b[1] - a[1]) * k),
                Math.round(a[2] + (b[2] - a[2]) * k));
    }

    /** Each disc's label colour, after its item. */
    static String discColour(String disc) {
        return switch (disc.substring(disc.lastIndexOf('_') + 1)) {
            case "cat" -> "#6cc349";
            case "13" -> "#ffd36b";
            case "pigstep" -> "#e25a5a";
            case "chirp" -> "#ff8a3a";
            case "mellohi" -> "#c46aff";
            case "otherside" -> "#55c8ff";
            default -> "#c46aff";
        };
    }

    static String time(int seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    // ---- Playlist rows ----

    private Element row(Track t) {
        Element row = Dom.el(doc, "div", "row", null);
        row.setAttribute("tabindex", "0");
        Element grip = Dom.el(doc, "img", "o-ico grip", null);
        grip.setAttribute("src", "../textures/icons/drag.png");
        row.appendChild(grip);
        Element icon = Dom.el(doc, "item", null, null);
        icon.setAttribute("id", t.disc());
        row.appendChild(icon);
        row.appendChild(Dom.el(doc, "span", "name", t.title()));
        Element eq = Dom.el(doc, "span", "mini-eq", null);
        for (int i = 0; i < 3; i++) eq.appendChild(Dom.el(doc, "i", null, null));
        row.appendChild(eq);
        row.appendChild(Dom.el(doc, "span", "len", time(t.seconds())));
        rowsBox.appendChild(row);
        Dom.onMouse(row, "mousedown", e -> dragStart(row, e));
        Dom.onKey(row, e -> {
            if (e.key.equals("Enter")) {
                select(rows.indexOf(row), true);
                if (!playing) toggle();
            }
        });
        return row;
    }

    /** Puts each row at its slot; the dragged one follows the pointer and the rest make room for it. */
    private void layoutRows() {
        for (int i = 0; i < rows.size(); i++) {
            Element row = rows.get(i);
            float y = i * ROW;
            if (row == dragRow) {
                y = dragFrom * ROW + dragOffset;
            } else if (dragRow != null) {
                if (dragFrom < dragTo && i > dragFrom && i <= dragTo) y -= ROW;
                if (dragFrom > dragTo && i < dragFrom && i >= dragTo) y += ROW;
            }
            new Dom.Style(row).set("transform", "translateY(" + Math.round(y) + "px)").apply();
        }
    }

    private void dragStart(Element row, MouseEvent e) {
        if (e.button != 0) return;
        dragRow = row;
        dragFrom = dragTo = rows.indexOf(row);
        dragStartY = e.clientY;
        dragOffset = 0;
        dragMoved = false;
    }

    private void dragMove(MouseEvent e) {
        if (dragRow == null) return;
        dragOffset = e.clientY - dragStartY;
        if (!dragMoved && Math.abs(dragOffset) < 3) return;
        if (!dragMoved) {
            dragMoved = true;
            dragRow.addClass("dragging");
        }
        dragOffset = Math.max(-dragFrom * ROW, Math.min((rows.size() - 1 - dragFrom) * ROW, dragOffset));
        dragTo = Math.max(0, Math.min(rows.size() - 1, Math.round(dragFrom + dragOffset / ROW)));
        layoutRows();
    }

    private void dragEnd(MouseEvent e) {
        if (dragRow == null) return;
        Element row = dragRow;
        dragRow = null;
        row.removeClass("dragging");
        if (!dragMoved) {
            select(rows.indexOf(row), true);
            if (!playing) toggle();
            layoutRows();
            return;
        }
        move(dragFrom, dragTo);
    }

    /** Moves a track in the playlist, keeping the current one playing. */
    void move(int from, int to) {
        Track playingTrack = track();
        Track t = playlist.remove(from);
        playlist.add(to, t);
        Element row = rows.remove(from);
        rows.add(to, row);
        current = playlist.indexOf(playingTrack);
        layoutRows();
        for (int i = 0; i < rows.size(); i++) rows.get(i).toggleClass("current", i == current);
    }
}
