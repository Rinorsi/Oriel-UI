package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.KeyboardEvent;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.engine.event.MouseEvent;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;
import dev.oriel.kit.Sounds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoundsTest {
    private static Document page(KitHost host, String html) {
        OrielKit.register();
        return Document.parse(host, "test:sounds.html", "<html data-oriel-shell><body>" + html + "</body></html>");
    }

    @Test void buttonsTabsAndTogglesUseOneSemanticSound() {
        KitHost host = new KitHost();
        Document doc = page(host, "<button id='button' class='ore-btn'>Click</button>"
                + "<div class='ore-tabs'><div class='ore-tab selected'>A</div><div id='tab' class='ore-tab'>B</div></div>"
                + "<div class='ore-toggle'></div><input id='check' type='checkbox'>");
        doc.getElementById("button").click();
        doc.getElementById("tab").click();
        doc.querySelector(".ore-toggle").click();
        doc.getElementById("check").click();
        assertEquals(List.of("click", "tab", "toggle", "toggle"), host.sounds);
        assertTrue(doc.getElementById("check").checked(), "replacing sound must preserve checkbox activation");
        assertTrue(doc.querySelector(".ore-toggle").hasClass("on"));
        assertTrue(doc.getElementById("tab").hasClass("selected"));
        assertEquals(4, host.soundIds.size());
    }

    @Test void explicitOffAndDisabledAreSilentWithoutBreakingControls() {
        KitHost host = new KitHost();
        Document doc = page(host, "<button id='off' data-oriel-sound='off'>Mute</button>"
                + "<input id='check' type='checkbox' data-oriel-sound='off'>"
                + "<button id='disabled' disabled>Disabled</button><div id='custom' class='ore-btn' data-oriel-sound='off'></div>");
        for (String id : List.of("off", "check", "disabled", "custom")) doc.getElementById(id).click();
        assertEquals(List.of(), host.sounds);
        assertTrue(doc.getElementById("check").checked());
    }

    @Test void plainTextInputsStaySilentUnlessExplicitlyOptedIn() {
        KitHost host = new KitHost();
        Document doc = page(host, "<input id='plain'><input id='explicit' data-oriel-sound='click'>");
        doc.getElementById("plain").click();
        assertTrue(host.sounds.isEmpty());
        doc.getElementById("explicit").click();
        assertEquals(List.of("click"), host.sounds);
    }

    @Test void explicitTokensAndDynamicallyInsertedControlsWork() {
        KitHost host = new KitHost();
        Document doc = page(host, "<button id='tab' data-oriel-sound='tab'><span id='child'>Tab</span></button>");
        doc.getElementById("child").click();
        Element fresh = doc.createElement("div");
        fresh.setAttribute("class", "ore-btn");
        doc.body().appendChild(fresh);
        fresh.click();
        assertEquals(List.of("tab", "click"), host.sounds);
    }

    @Test void nativeControlsNestedInsideSoundContainersDoNotPlayTwice() {
        KitHost host = new KitHost();
        Document doc = page(host, "<div class='ore-btn'><input id='nested' type='checkbox'></div>");
        doc.getElementById("nested").click();
        assertEquals(List.of("toggle"), host.sounds);
    }

    @Test void keyboardActivationUsesTheSameToken() {
        KitHost host = new KitHost();
        Document doc = page(host, "<div id='button' class='ore-btn' tabindex='0'>Key</div><button id='native'>Key</button>");
        for (String id : List.of("button", "native")) {
            Element button = doc.getElementById(id);
            Shell.activate(button, () -> {});
            button.dispatchEvent(new KeyboardEvent("keydown", "Enter", "Enter", false, Modifiers.NONE));
        }
        assertEquals(List.of("click", "click"), host.sounds);
    }

    @Test void hoverRequiresExplicitOptInAndDoesNotPlayAgainOnClick() {
        KitHost host = new KitHost();
        Document doc = page(host, "<div id='normal' class='ore-btn'>Normal</div><div id='hover' data-oriel-sound='hover'><span id='child'>Hover</span></div>");
        for (String id : List.of("normal", "hover")) doc.getElementById(id).dispatchEvent(new MouseEvent("mouseover", true, true, 0, 0, 0, 0, Modifiers.NONE, 0, null));
        doc.getElementById("child").dispatchEvent(new MouseEvent("mouseover", true, true, 0, 0, 0, 0, Modifiers.NONE, 0, doc.getElementById("hover")));
        doc.getElementById("hover").click();
        assertEquals(List.of("hover"), host.sounds);
    }

    @Test void modalChangesAreObservedOnceAndOffIsRespected() {
        KitHost host = new KitHost();
        Document doc = page(host, "<div id='modal' class='m-modal'></div>");
        doc.frame(0);
        Element modal = doc.getElementById("modal");
        modal.addClass("on");
        doc.frame(16);
        doc.frame(32);
        modal.removeClass("on");
        doc.frame(48);
        modal.setAttribute("data-oriel-sound", "off");
        modal.addClass("on");
        doc.frame(64);
        assertEquals(List.of("open", "close"), host.sounds);
    }

    @Test void hostMuteAppliesToDefaultsAndExplicitScripts() {
        KitHost host = new KitHost();
        host.scriptsEnabled = true;
        host.uiSounds = false;
        Document doc = page(host, "<button id='button'>Click</button><script>oriel.playSound('success'); oriel.playSound('minecraft:ui.button.click');</script>");
        doc.getElementById("button").click();
        assertEquals(List.of(), host.sounds);
    }

    @Test void scriptsCanUseTokensWithoutOptingIntoShell() {
        KitHost host = new KitHost();
        host.scriptsEnabled = true;
        Document doc = Document.parse(host, "test:script-sounds.html", "<script>oriel.playSound('success'); oriel.playSound('minecraft:ui.button.click');</script>");
        assertNull(doc.error());
        assertEquals(List.of("success", "minecraft:ui.button.click"), host.sounds);
        assertEquals(List.of("minecraft:entity.experience_orb.pickup", "minecraft:ui.button.click"), host.soundIds);
    }

    @Test void tokenMappingKeepsRawIdsAndRejectsUnknownTokens() {
        assertEquals(8, Sounds.tokens().size());
        assertEquals(new Sounds.Sound("minecraft:custom", 0.5f, 1.5f), Sounds.resolve("minecraft:custom", 0.5f, 1.5f));
        assertEquals("minecraft:ui.button.click", Sounds.resolve("tab", 1, 1).id());
        assertEquals(0.2f, Sounds.resolve("tab", 0.5f, 1).volume());
        assertEquals(0.65f, Sounds.resolve("tab", 1, 0.5f).pitch());
        assertThrows(IllegalArgumentException.class, () -> Sounds.resolve("typo", 1, 1));
    }

    @Test void invalidDeclarationsAreDiagnosedBeforeInteraction() {
        KitHost host = new KitHost();
        Document doc = page(host, "<div data-oriel-sound='typo'>Invalid</div>");
        assertTrue(doc.diagnostics().all().stream().anyMatch(d -> d.code().equals("ui-sound-token") && d.line() == 1));
    }
}
