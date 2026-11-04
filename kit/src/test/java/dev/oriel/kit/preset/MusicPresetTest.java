package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The music player preset: playback, reordering by model and by dragging a row. */
class MusicPresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void playPauseAndTheClockAdvance() {
        MusicPreset music = new MusicPreset();
        Document doc = host.open(OrielKit.MUSIC, music);
        click(doc, "#play", 0, Modifiers.NONE, clock);
        assertTrue(music.playing);
        for (int i = 0; i < 30; i++) frame(doc, clock, 100);
        assertTrue(music.position > 2.5 && music.position < 3.5, "about three seconds: " + music.position);
        assertEquals("0:03", doc.getElementById("elapsed").textContent());
        click(doc, "#play", 0, Modifiers.NONE, clock);
        assertFalse(music.playing);
        click(doc, "#next", 0, Modifiers.NONE, clock);
        assertEquals(1, music.current);
        assertEquals(0, music.position, 1e-9);
    }

    @Test
    void reorderingKeepsTheCurrentTrack() {
        MusicPreset music = new MusicPreset();
        host.open(OrielKit.MUSIC, music);
        music.select(2, true);
        String playing = music.track().title();
        music.move(2, 0);
        assertEquals(playing, music.track().title());
        assertEquals(0, music.current);
        music.move(5, 1);
        assertEquals(playing, music.track().title());
        assertEquals(6, music.playlist.size());
    }

    @Test
    void draggingARowReordersThePlaylist() {
        MusicPreset music = new MusicPreset();
        Document doc = host.open(OrielKit.MUSIC, music);
        String first = music.playlist.get(0).title();
        // The queue shares its place with the lyrics: open its tab.
        KitHost.click(doc, "[data-show='#queue']", 0, Modifiers.NONE, clock);
        assertFalse(doc.getElementById("queue").hasClass("o-hidden"));
        assertTrue(doc.getElementById("lyrics").hasClass("o-hidden"));
        Element row = music.rows.get(0);
        float[] r = row.getBoundingClientRect();
        float x = r[0] + 30, y = r[1] + r[3] / 2;
        doc.input().mouseMove(x, y, Modifiers.NONE);
        doc.input().mouseDown(x, y, 0, Modifiers.NONE);
        frame(doc, clock, 16);
        for (int i = 1; i <= 6; i++) {
            doc.input().mouseMove(x, y + i * 8, Modifiers.NONE);
            frame(doc, clock, 16);
        }
        doc.input().mouseUp(x, y + 48, 0, Modifiers.NONE);
        frame(doc, clock, 16);
        assertEquals(first, music.playlist.get(2).title(), "48 px down is two rows of 21");
    }

    @Test
    void timesFormat() {
        assertEquals("3:05", MusicPreset.time(185));
        assertEquals("0:09", MusicPreset.time(9));
    }

    @Test
    void theModeButtonCyclesAndDecidesWhatFollows() {
        MusicPreset music = new MusicPreset();
        Document doc = host.open(OrielKit.MUSIC, music, 640, 360);
        assertEquals(MusicPreset.Mode.LOOP, music.mode);
        click(doc, "#mode", 0, Modifiers.NONE, clock);
        assertEquals(MusicPreset.Mode.ONE, music.mode);
        assertTrue(doc.getElementById("mode").hasClass("one"));
        assertEquals("单曲循环", doc.getElementById("mode-label").textContent());
        music.ended();
        assertEquals(0, music.current, "single repeat plays the same track again");
        click(doc, "#mode", 0, Modifiers.NONE, clock);
        click(doc, "#mode", 0, Modifiers.NONE, clock);
        assertEquals(MusicPreset.Mode.ORDER, music.mode, "shuffle, then in order");
        music.select(music.playlist.size() - 1, true);
        music.ended();
        assertEquals(music.playlist.size() - 1, music.current, "in order, the last track stops");
    }
}
