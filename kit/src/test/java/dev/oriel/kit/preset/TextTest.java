package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.engine.input.TextSelection;
import dev.oriel.kit.McFormat;
import dev.oriel.kit.Notice;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.oriel.kit.preset.KitHost.click;
import static dev.oriel.kit.preset.KitHost.frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Text: Minecraft's formatting codes, the formatted field, the page font, notices. */
class TextTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @AfterEach
    void reset() {
        Shell.font(host.open(OrielKit.GALLERY, new GalleryPreset()), "mc");
    }

    @Test
    void codesSplitIntoRunsAsTheGameReadsThem() {
        List<McFormat.Run> runs = McFormat.parse("§6§l金§r白&a绿§n线§c红");
        assertEquals(List.of("金", "白", "绿", "线", "红"), runs.stream().map(McFormat.Run::text).toList());
        assertEquals(0xFFAA00, runs.get(0).color());
        assertTrue(runs.get(0).bold());
        assertEquals(-1, runs.get(1).color(), "§r resets the colour");
        assertFalse(runs.get(1).bold());
        assertTrue(runs.get(3).underlined());
        assertFalse(runs.get(4).underlined(), "a colour ends the styles before it, as in the game");
        assertEquals("金白绿线红", McFormat.strip("§6§l金§r白&a绿§n线§c红"));
        assertEquals("{\"text\":\"\",\"extra\":[{\"text\":\"金\",\"color\":\"gold\",\"bold\":true},{\"text\":\"x\\\"\",\"color\":\"#12abef\",\"obfuscated\":true}]}",
                McFormat.json("§6§l金§#12abef§kx\""));
        assertEquals("& 不是代码", McFormat.strip("& 不是代码"));
    }

    @Test
    void theFieldWrapsTheSelectionInACode() {
        Document doc = host.open(OrielKit.CATALOG, new CatalogPreset(), 960, 540);
        Element box = doc.querySelector(".o-mcfield");
        assertNotNull(box.querySelector(".o-mcbar"), "the bar is made from the markup");
        Element field = box.querySelector(".o-mcfield > input");
        field.setValue("你好世界");
        frame(doc, clock, 16);
        field.focus();
        TextSelection.select(field, 2, 4);
        box.querySelector(".sw[data-mc=c]").click();
        assertEquals("你好§c世界§r", field.value());
        assertEquals(4, TextSelection.start(field), "the styled text stays selected");
        box.querySelector("[data-mc=l]").click();
        assertEquals("你好§c§l世界§r§r", field.value());
        Element preview = box.querySelector(".o-mcpreview");
        assertEquals("你好世界", preview.textContent());
        assertTrue(box.getAttribute("data-json").contains("\"color\":\"red\""));
        assertTrue(doc.getElementById("mc-json").textContent().contains("\"bold\":true"), "and the JSON goes where data-json-out says");
    }

    @Test
    void theFontAppliesToEveryPage() {
        Document catalog = host.open(OrielKit.CATALOG, new CatalogPreset(), 960, 540);
        Element card = catalog.querySelector(".font-card[data-font-pick=kai]");
        card.click();
        assertEquals("kai", catalog.documentElement().getAttribute("data-font"));
        assertTrue(card.hasClass("on"));
        frame(catalog, clock, 16);
        assertEquals("oriel:kai", catalog.querySelector(".pane").computedStyle().fontFamily.getFirst());
        Document home = host.open(OrielKit.GALLERY, new GalleryPreset(), 960, 540);
        assertEquals("kai", home.documentElement().getAttribute("data-font"), "pages opened later keep it");
        Shell.font(home, "mc");
        assertNull(home.documentElement().getAttribute("data-font"));
    }

    @Test
    void noticesShowAndLeave() {
        Document doc = host.open(OrielKit.CATALOG, new CatalogPreset(), 960, 540);
        boolean[] ran = {false};
        Element bar = Notice.bar(doc, "warn", "需要重新加载", "重新加载", () -> ran[0] = true);
        assertTrue(bar.hasClass("warn"));
        bar.querySelector(".ore-btn").click();
        assertTrue(ran[0]);
        Notice.bar(doc, null, "第二条", null, null);
        assertEquals(1, doc.querySelectorAll("#o-notice").size(), "one bar at a time");
        Notice.toast(doc, "success", "已保存", "ui/a.html", 1000);
        assertEquals(1, doc.querySelectorAll(".o-toasts .o-toast").size());
        for (int i = 0; i < 90; i++) frame(doc, clock, 16);
        assertEquals(0, doc.querySelectorAll(".o-toasts .o-toast").size(), "a toast leaves by itself");
        doc.querySelector("[data-toast]").click();
        assertEquals(1, doc.querySelectorAll(".o-toasts .o-toast").size(), "data-toast needs no code");
        for (int i = 0; i < 20; i++) frame(doc, clock, 16); // the bar has risen into place
        click(doc, "#o-notice .x", 0, Modifiers.NONE, clock);
        assertNull(doc.getElementById("o-notice"));
    }
}
