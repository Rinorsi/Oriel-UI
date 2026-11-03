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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The settings preset: toggles, sliders, dropdowns and text fields, by pointer and keyboard. */
class SettingsPresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void togglesSlidersAndCountsChanges() {
        SettingsPreset settings = new SettingsPreset();
        Document doc = host.open(OrielKit.SETTINGS, settings);
        click(doc, "[data-key=vsync] .ore-toggle", 0, Modifiers.NONE, clock);
        assertEquals(false, settings.values.get("vsync"));
        assertTrue(doc.querySelector("[data-key=vsync]").hasClass("changed"));
        Element slider = doc.querySelector("[data-key=render] .ore-slider");
        slider.focus();
        doc.input().keyDown("ArrowRight", "ArrowRight", Modifiers.NONE);
        frame(doc, clock, 16);
        assertEquals(13f, settings.values.get("render"));
        assertEquals("已修改 2 项", doc.getElementById("status").textContent());
        settings.resetTab();
        frame(doc, clock, 16);
        assertEquals(0, settings.changes());
    }

    @Test
    void dropdownsWorkFromTheKeyboard() {
        SettingsPreset settings = new SettingsPreset();
        Document doc = host.open(OrielKit.SETTINGS, settings);
        Element select = doc.querySelector("[data-key=graphics] .ore-select");
        select.focus();
        doc.input().keyDown("Enter", "Enter", Modifiers.NONE);
        frame(doc, clock, 16);
        assertTrue(select.hasClass("open"));
        doc.input().keyDown("ArrowDown", "ArrowDown", Modifiers.NONE);
        doc.input().keyDown("Enter", "Enter", Modifiers.NONE);
        frame(doc, clock, 16);
        assertFalse(select.hasClass("open"));
        assertEquals("极佳", settings.values.get("graphics"));
        // Escape closes an open menu without choosing.
        doc.input().keyDown(" ", "Space", Modifiers.NONE);
        doc.input().keyDown("Escape", "Escape", Modifiers.NONE);
        frame(doc, clock, 16);
        assertFalse(select.hasClass("open"));
        assertEquals("极佳", settings.values.get("graphics"));
    }

    @Test
    void tabsSwitchWithClicksAndArrows() {
        SettingsPreset settings = new SettingsPreset();
        Document doc = host.open(OrielKit.SETTINGS, settings);
        click(doc, ".ore-tab:nth-child(3)", 0, Modifiers.NONE, clock);
        assertEquals("控制", settings.tab);
        assertNotNull(doc.querySelector("[data-key=name] input"));
        doc.input().keyDown("ArrowRight", "ArrowRight", Modifiers.NONE);
        frame(doc, clock, 16);
        assertEquals("辅助功能", settings.tab);
        // Tab moves the focus through the page's controls.
        doc.input().keyDown("Tab", "Tab", Modifiers.NONE);
        frame(doc, clock, 16);
        assertNotNull(doc.focusedElement());
    }

    @Test
    void textFieldsStoreWhatIsTyped() {
        SettingsPreset settings = new SettingsPreset();
        Document doc = host.open(OrielKit.SETTINGS, settings);
        settings.show("控制", false);
        frame(doc, clock, 16);
        Element input = doc.querySelector("[data-key=name] input");
        input.focus();
        doc.input().keyDown("End", "End", Modifiers.NONE);
        doc.input().charTyped("优易");
        frame(doc, clock, 16);
        assertEquals("Steve优易", settings.values.get("name"));
    }
}
