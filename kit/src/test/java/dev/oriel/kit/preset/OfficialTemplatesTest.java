package dev.oriel.kit.preset;

import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfficialTemplatesTest {
    @Test void metadataIsLocalizedWithoutGlobalTemplateMessageKeys() {
        for (String language : java.util.List.of("en_us", "fr_fr", "zh_cn")) {
            KitHost host = new KitHost();
            host.language = language;
            StylesPreset.startAt("official");
            var doc = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
            var first = doc.querySelector("#grid .card .meta b").textContent();
            assertEquals(language.equals("zh_cn") ? "提示与成就弹条" : "Achievements and notices", first);
            assertFalse(doc.querySelector("#grid").textContent().contains("oriel.ui.numbered."));
            doc.querySelector("#grid .card").click();
            assertFalse(doc.getElementById("d-desc").textContent().contains("oriel.ui."));
            assertFalse(doc.getElementById("d-spec").textContent().contains("oriel.ui.numbered."));
            doc.close();
        }
    }
    @Test void officialViewIsContinuousAndLegacyIsSeparate() {
        KitHost host = new KitHost();
        StylesPreset.startAt("official");
        var doc = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        var cards = doc.querySelectorAll("#grid .card");
        assertEquals(56, cards.size());
        for (int i = 0; i < cards.size(); i++) {
            assertFalse(Character.isDigit(cards.get(i).querySelector(".meta b").textContent().charAt(0)), "titles carry no number");
            assertEquals("云诺羲", cards.get(i).querySelector(".by span").textContent());
        }
        assertEquals(0, doc.querySelectorAll("#grid img.shot[src]").size());
        doc.frame(60);
        assertTrue(doc.querySelectorAll("#grid img.shot[src]").size() <= 3);
        cards.get(43).click();
        assertTrue(doc.getElementById("d-desc").textContent().contains("配方"));
        doc.getElementById("d-open").click();
        for (int i = 1; i <= 40; i++) doc.frame(60 + i * 16);
        assertEquals("oriel:ui/templates/45/index.html", host.navigations.getLast());
        doc.close();
        var back = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        assertTrue(back.querySelector("#side [data-view=official]").hasClass("on"));
        back.getElementById("d-close").click();
        back.querySelector("#side [data-view=legacy]").click();
        assertEquals(11, back.querySelectorAll("#grid .card").size());
        back.close();
    }

    @Test void aTemplateTheLibraryIsTooOldForIsMarkedAndCannotBeOpened() {
        KitHost host = new KitHost();
        host.libraryVersion = "0.10.0";
        host.gameVersion = "26.1.2";
        StylesPreset.startAt("official");
        var doc = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        var cards = doc.querySelectorAll("#grid .card");
        assertEquals(56, cards.size());
        assertTrue(cards.stream().allMatch(c -> c.hasClass("locked")), "every packaged template asks for 0.11");
        cards.get(0).click();
        assertTrue(doc.getElementById("d-note").textContent().contains(">=0.11.0"));
        assertTrue(doc.getElementById("d-note").textContent().contains("0.10.0"));
        assertTrue(doc.getElementById("d-open").hasClass("disabled"));
        int before = host.navigations.size();
        doc.getElementById("d-open").click();
        for (int i = 1; i <= 40; i++) doc.frame(60 + i * 16);
        assertEquals(before, host.navigations.size(), "a locked template does not open");
        doc.close();
    }

    @Test void aHostThatKnowsItsVersionOrNotLocksNothingItMeets() {
        for (String version : new String[] {null, "0.11.0", "0.11.5"}) {
            KitHost host = new KitHost();
            host.libraryVersion = version;
            host.gameVersion = version == null ? null : "26.1.2";
            StylesPreset.startAt("official");
            var doc = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
            assertTrue(doc.querySelectorAll("#grid .card").stream().noneMatch(c -> c.hasClass("locked")), String.valueOf(version));
            doc.close();
        }
    }
}
