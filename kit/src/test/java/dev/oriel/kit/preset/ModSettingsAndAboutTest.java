package dev.oriel.kit.preset;

import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.*;

/** The mod's settings page (what the game reports, what it is told) and the about page (copy buttons, tabs). */
class ModSettingsAndAboutTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test void settingsShowTheDefaultsWithoutAGameAndSendChangesToOne() {
        host.acceptSends = true;
        var doc = host.open(OrielKit.MODSETTINGS, new ModSettingsPreset());
        assertEquals(8, doc.querySelectorAll("#groups .item").size());
        assertEquals(3, doc.querySelectorAll("#groups .group").size());
        assertTrue(doc.querySelector(".item[data-key=\"client.uiSounds\"] .ore-toggle").hasClass("on"));
        click(doc, ".item[data-key=\"client.uiSounds\"] .ore-toggle", 0, Modifiers.NONE, clock);
        assertFalse(doc.querySelector(".item[data-key=\"client.uiSounds\"] .ore-toggle").hasClass("on"));
        assertEquals("oriel {\"op\":\"set\",\"key\":\"client.uiSounds\",\"value\":\"false\"}", host.sent.getLast());
        assertTrue(doc.getElementById("saved").hasClass("on"), "an accepted change says so");
        assertTrue(doc.querySelector(".item[data-key=\"client.nestedNotice\"] .ore-toggle").hasClass("on"));
        doc.querySelector(".item[data-key=\"client.nestedNotice\"] .ore-toggle").click();
        assertEquals("oriel {\"op\":\"set\",\"key\":\"client.nestedNotice\",\"value\":\"OFF\"}", host.sent.getLast());
        doc.querySelector(".item[data-key=\"client.webLinks\"] .o-seg span:nth-child(2)").click();
        assertEquals("oriel {\"op\":\"set\",\"key\":\"client.webLinks\",\"value\":\"BLOCK\"}", host.sent.getLast());
        assertTrue(doc.querySelector(".item[data-key=\"client.webLinks\"] .o-seg span:nth-child(2)").hasClass("on"));
        doc.close();
    }

    @Test void restartOnlySettingsSayTheyNeedARestart() {
        var doc = host.open(OrielKit.MODSETTINGS, new ModSettingsPreset(), 640, 360,
                "{\"settings\":{\"items\":[{\"key\":\"client.shapeShader\",\"type\":\"bool\",\"value\":true,\"default\":true,\"live\":false},"
                        + "{\"key\":\"client.uiSounds\",\"type\":\"bool\",\"value\":true,\"default\":true,\"live\":true}]}}");
        assertEquals(1, doc.querySelectorAll(".item .n em").size());
        assertNotNull(doc.querySelector(".item[data-key=\"client.shapeShader\"] .n em"));
        doc.close();
    }

    @Test void theGameDecidesWhatTheSettingsSay() {
        var doc = host.open(OrielKit.MODSETTINGS, new ModSettingsPreset(), 640, 360,
                "{\"settings\":{\"items\":[{\"key\":\"client.uiSounds\",\"type\":\"bool\",\"value\":false,\"default\":true,\"live\":true}]}}");
        assertEquals(1, doc.querySelectorAll("#groups .item").size());
        assertFalse(doc.querySelector(".item[data-key=\"client.uiSounds\"] .ore-toggle").hasClass("on"));
        doc.close();
    }

    @Test void aboutCopiesLinksAndLinksNothingElse() {
        var doc = host.open(OrielKit.ABOUT, new AboutPreset());
        doc.querySelector(".row .copy").click();
        assertEquals("https://b23.tv/hjUvs0c", host.clipboard);
        assertTrue(host.navigations.isEmpty());
        frame(doc, clock, 2000);
        assertEquals(3, doc.querySelectorAll(".row .copy").size());
        doc.close();
    }

    @Test void everyMainPageHasTheSameSixTabs() {
        for (String page : new String[]{"gallery", "styles", "catalog", "icons", "modsettings", "about"}) {
            String html = new String(readPage(page), java.nio.charset.StandardCharsets.UTF_8);
            for (String go : new String[]{"gallery", "styles", "catalog", "icons", "modsettings", "about"}) {
                assertTrue(html.contains("data-go=\"" + go + "\""), page + " lacks the " + go + " tab");
            }
            assertFalse(html.contains("oriel.ui.nav.editor"), page + " still has the editor tab");
        }
    }

    private static byte[] readPage(String name) {
        try (var in = ModSettingsAndAboutTest.class.getResourceAsStream("/assets/oriel/ui/" + name + ".html")) {
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
