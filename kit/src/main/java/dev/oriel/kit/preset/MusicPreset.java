package dev.oriel.kit.preset;

import dev.oriel.kit.Lang;

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

    /** A track; {@code lyrics} spread evenly over its length, none for an instrumental. */
    record Track(String title, String artist, String disc, int seconds, List<String> lyrics) {}

    final List<Track> playlist = new ArrayList<>(List.of(
            new Track("oriel.ui.music.dawnlight_mine", "oriel.ui.music.oriel_ensemble", "minecraft:music_disc_cat", 185, List.of("oriel.ui.music.the_mine_lamps_sway_against_the_rock", "oriel.ui.music.echoes_rise_layer_by_layer_from_the", "oriel.ui.music.we_stick_torches_around_the_corners", "oriel.ui.music.so_the_next_person_won_t_get", "oriel.ui.music.the_rhythm_of_falling_pickaxes", "oriel.ui.music.slowly_falls_in_step_with_the_dripping", "oriel.ui.music.dawn_shines_in_through_the_cave_entrance", "oriel.ui.music.like_a_map_freshly_spread_open", "oriel.ui.music.our_packs_are_filled_with_today_s", "oriel.ui.music.and_one_diamond_we_can_t_bear", "oriel.ui.music.the_road_home_is_a_little_long", "oriel.ui.music.but_every_step_glows")),
            new Track("oriel.ui.music.deepslate_echoes", "oriel.ui.music.underground_band", "minecraft:music_disc_13", 214, List.of("oriel.ui.music.down_just_a_little_farther_down", "oriel.ui.music.the_stone_grows_darker_and_darker", "oriel.ui.music.a_low_murmur_drifts_from_afar", "oriel.ui.music.we_hold_our_breath", "oriel.ui.music.where_the_light_can_t_reach", "oriel.ui.music.there_is_still_a_path")),
            new Track("oriel.ui.music.nether_winds", "oriel.ui.music.blazing_trio", "minecraft:music_disc_pigstep", 148, List.of("oriel.ui.music.hot_wind_whips_up_the_ash", "oriel.ui.music.there_are_no_stars_in_the_red", "oriel.ui.music.a_bridge_stretches_over_the_lava", "oriel.ui.music.every_block_must_be_placed_securely", "oriel.ui.music.we_look_back_at_the_portal_s", "oriel.ui.music.it_is_always_waiting_for_us")),
            new Track("oriel.ui.music.a_rest_among_the_trees", "oriel.ui.music.moss_and_ferns", "minecraft:music_disc_chirp", 196, List.of("oriel.ui.music.lie_down_beneath_the_shade_for_a", "oriel.ui.music.bees_circle_the_flowers", "oriel.ui.music.wolves_howl_in_the_distance", "oriel.ui.music.no_hurry_the_sun_hasn_t_set", "oriel.ui.music.break_the_bread_in_half", "oriel.ui.music.for_the_cat_passing_by")),
            new Track("oriel.ui.music.ocean_monument", "oriel.ui.music.tidal_surge", "minecraft:music_disc_mellohi", 233, List.of()),
            new Track("oriel.ui.music.end_stardust", "oriel.ui.music.chorus", "minecraft:music_disc_otherside", 171, List.of())));
    final List<Element> rows = new ArrayList<>();
    int current;
    double position;
    boolean playing;
    double volume = 0.7;

    private Document doc;
    private Slider seek, volumeSlider;
    private Element title, meta, state, elapsed, length, disc, play, rowsBox;
    /** The lyric lines (spread evenly over the track) and the one being sung. */
    private Element lyricsBox;
    private List<Element> lyrics = List.of();
    private int lyric = -1;
    private final float[] levels = new float[BARS], caps = new float[BARS], targets = new float[BARS];
    private final List<Dom.Style> levelStyles = new ArrayList<>(), capStyles = new ArrayList<>(), mirrorStyles = new ArrayList<>();
    private Element screen, playIcon, miniPlayIcon, miniTitle;
    private Dom.Style miniLine;
    private Particles.Emitter notes;
    /** The play mode, one button cycling through them as desktop players do. */
    enum Mode {
        ORDER("list", "oriel.ui.music.play_in_order"), LOOP("repeat", "oriel.ui.music.loop_playlist"), ONE("repeat", "oriel.ui.music.repeat_one"), SHUFFLE("shuffle", "oriel.ui.music.shuffle");

        final String icon, label;

        Mode(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }
    }

    Mode mode = Mode.LOOP;
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
        lyricsBox = Dom.byId(doc, "lyrics");
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
            Dom.text(Dom.byId(doc, "vol-label"), volume == 0 ? Lang.t(doc, "oriel.ui.music.mute") : Lang.t(doc, "oriel.ui.music.volume") + Math.round(volume * 100) + "%");
            Dom.attr(Dom.byId(doc, "vol-icon"), "src", "../textures/icons/" + (volume == 0 ? "mute" : "volume") + ".png");
        });
        volumeSlider.set(volume);
        for (int i = 0; i < playlist.size(); i++) rows.add(row(playlist.get(i)));
        layoutRows();
        Dom.text(Dom.byId(doc, "count"), playlist.size() + Lang.t(doc, "oriel.ui.music.tracks"));

        Dom.onClick(play, e -> toggle());
        Dom.onClick(Dom.byId(doc, "prev"), e -> skip(-1));
        Dom.onClick(Dom.byId(doc, "next"), e -> skip(1));
        Dom.onClick(Dom.byId(doc, "close"), e -> doc.host().close());
        Dom.onClick(Dom.byId(doc, "back"), e -> Shell.back(doc));
        Shell.activate(Dom.byId(doc, "mini-play"), this::toggle);
        Shell.activate(Dom.byId(doc, "mini"), () -> screen.addClass("mini"));
        Shell.activate(Dom.byId(doc, "expand"), () -> screen.removeClass("mini"));
        Element modeButton = Dom.byId(doc, "mode");
        Shell.activate(modeButton, () -> setMode(Mode.values()[(mode.ordinal() + 1) % Mode.values().length]));
        setMode(mode);
        Shell.activate(Dom.byId(doc, "queue-btn"), () -> Dom.query(doc, "[data-show='#queue']").click());
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
        renderLyric();
        doc.scheduler().requestAnimationFrame(this::frame);
    }

    private record State(List<Track> playlist, int current, double position, boolean playing, double volume) {}

    @Override
    public Object saveState() {
        return new State(List.copyOf(playlist), current, position, playing, volume);
    }

    @Override
    public void restoreState(Object state) {
        if (!(state instanceof State s) || !s.playlist().containsAll(playlist)) return;
        for (int i = 0; i < s.playlist().size(); i++) move(playlist.indexOf(s.playlist().get(i)), i);
        volumeSlider.set(volume = s.volume());
        select(s.current(), false);
        position = s.position();
        if (s.playing() != playing) toggle();
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
        Dom.text(Dom.byId(doc, "state-text"), playing ? Lang.t(doc, "oriel.ui.music.now_playing") : Lang.t(doc, "oriel.ui.music.paused"));
    }

    /** Marks the line for the current position and keeps it a third of the way down the lyrics. */
    private void renderLyric() {
        if (lyrics.isEmpty()) return;
        int at = (int) Math.min(lyrics.size() - 1, Math.floor(position / track().seconds() * lyrics.size()));
        if (at == lyric) return;
        lyric = at;
        for (int i = 0; i < lyrics.size(); i++) {
            lyrics.get(i).toggleClass("now", i == at);
            lyrics.get(i).toggleClass("near", Math.abs(i - at) == 1);
        }
        float[] box = lyricsBox.getBoundingClientRect(), line = lyrics.get(at).getBoundingClientRect();
        float top = line[1] - box[1] + lyricsBox.scrollTop() - box[3] / 3;
        lyricsBox.scrollTo(0, Math.max(0, top), Element.ScrollBehavior.SMOOTH);
    }

    void setMode(Mode m) {
        mode = m;
        Element button = Dom.byId(doc, "mode");
        Dom.attr(Dom.byId(doc, "mode-icon"), "src", "../textures/icons/" + m.icon + ".png");
        button.setAttribute("title", Lang.value(doc, m.label));
        button.toggleClass("one", m == Mode.ONE);
        Dom.text(Dom.byId(doc, "mode-label"), Lang.value(doc, m.label));
    }

    /** The previous or next track (by hand): random in shuffle, else round the list. */
    void skip(int delta) {
        int next = mode == Mode.SHUFFLE && playlist.size() > 1
                ? Math.floorMod(current + 1 + random.nextInt(playlist.size() - 1), playlist.size())
                : Math.floorMod(current + delta, playlist.size());
        select(next, true);
    }

    /** A track played to its end: the mode decides what follows (in order, the last one stops). */
    void ended() {
        switch (mode) {
            case ONE -> select(current, true);
            case ORDER -> {
                if (current + 1 < playlist.size()) select(current + 1, true);
                else {
                    position = track().seconds();
                    if (playing) toggle();
                }
            }
            default -> skip(1);
        }
    }

    void select(int index, boolean restart) {
        current = index;
        if (restart) position = 0;
        Track t = track();
        Dom.text(title, Lang.value(doc, t.title()));
        Dom.text(meta, Lang.value(doc, t.artist()));
        Dom.attr(disc, "id", t.disc());
        Dom.text(miniTitle, Lang.value(doc, t.title()) + " · " + Lang.value(doc, t.artist()));
        new Dom.Style(screen).set("--disc", discColour(t.disc())).apply();
        Dom.text(length, time(t.seconds()));
        for (int i = 0; i < rows.size(); i++) rows.get(i).toggleClass("current", i == current);
        showLyrics(t);
        renderTime();
    }

    /** The track's lyrics, or for an instrumental a quiet "纯音乐" with a small equalizer. */
    private void showLyrics(Track t) {
        lyricsBox.removeAllChildren();
        lyricsBox.toggleClass("pure", t.lyrics().isEmpty());
        lyric = -1;
        if (t.lyrics().isEmpty()) {
            lyricsBox.appendChild(Dom.html(doc, "<div class=\"pure-note\"><img class=\"o-ico x2\" src=\"../textures/icons/music.png\">"
                    + ("<span class=\"o-h1\">" + Lang.html(doc, "oriel.ui.music.instrumental") + "</span><span class=\"o-dim\">" + Lang.html(doc, "oriel.ui.music.no_lyrics_just_enjoy") + "</span>")
                    + "<div class=\"pure-eq\"><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div></div>"));
            lyrics = List.of();
        } else {
            List<Element> lines = new ArrayList<>();
            for (String line : t.lyrics()) {
                Element e = Dom.el(doc, "div", "ly", Lang.value(doc, line));
                lyricsBox.appendChild(e);
                lines.add(e);
            }
            lyrics = lines;
        }
        lyricsBox.scrollTo(0, 0, Element.ScrollBehavior.INSTANT);
    }

    private void frame(double now) {
        double dt = last < 0 ? 0 : Math.min(0.1, (now - last) / 1000);
        last = now;
        if (playing && !seek.dragging()) {
            position += dt;
            if (position >= track().seconds()) ended();
            renderTime();
        }
        // Equalizer: while playing, each bar eases towards a target that changes a few times a second; the caps
        // fall slowly behind the bars; paused, everything settles to the floor.
        retarget -= dt;
        if (retarget <= 0) {
            retarget = 0.11;
            for (int i = 0; i < BARS; i++) {
                double shape = 0.55 + 0.45 * Math.sin(i * 0.7 + now / 900);
                // The volume scales the bars only a little, so the spectrum uses its height at any volume.
                targets[i] = playing ? (float) Math.min(1, (0.45 + 0.55 * volume) * shape * (0.45 + random.nextFloat() * 0.6)) : 0;
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
        row.appendChild(Dom.el(doc, "span", "name", Lang.value(doc, t.title())));
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
