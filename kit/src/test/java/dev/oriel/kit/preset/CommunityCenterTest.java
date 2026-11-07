package dev.oriel.kit.preset;

import dev.oriel.kit.OrielKit;
import dev.oriel.kit.TemplateCatalog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The community page: categories that cover every official template once, folding groups, the details with pinned actions, avatars. */
class CommunityCenterTest {
    @Test void everyOfficialTemplateIsInExactlyOneCategory() {
        Set<String> seen = new HashSet<>();
        for (TemplateCatalog.Category c : TemplateCatalog.categories()) {
            assertFalse(c.names().isEmpty(), c.key());
            assertTrue(c.name("zh_cn").length() <= 4, "short enough not to be cut in the sidebar: " + c.name("zh_cn"));
            for (String id : c.ids()) assertTrue(seen.add(id), id + " is listed twice");
        }
        List<String> official = new ArrayList<>();
        for (String id : TemplateCatalog.ids()) if (TemplateCatalog.numbered(id)) official.add(id);
        assertEquals(new HashSet<>(official), seen);
    }

    @Test void categoriesFilterTheGridAndShowTheirCount() {
        KitHost host = new KitHost();
        StylesPreset.startAt("cat:items");
        var doc = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        var cat = TemplateCatalog.categories().stream().filter(c -> c.key().equals("items")).findFirst().orElseThrow();
        assertEquals(cat.ids().size(), doc.querySelectorAll("#grid .card").size());
        assertEquals(Integer.toString(cat.ids().size()), doc.getElementById("count").textContent());
        assertTrue(doc.querySelector("#side [data-view=\"cat:items\"]").hasClass("on"));
        doc.close();
    }

    @Test void sidebarGroupsFoldAndTheOldLooksStartFolded() {
        KitHost host = new KitHost();
        var doc = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        assertTrue(doc.getElementById("set-old").hasClass("closed"), "older looks start folded");
        assertFalse(doc.getElementById("set-browse").hasClass("closed"));
        doc.querySelector("#side .grp[data-set=browse]").click();
        assertTrue(doc.getElementById("set-browse").hasClass("closed"));
        doc.querySelector("#side .grp[data-set=browse]").click();
        assertFalse(doc.getElementById("set-browse").hasClass("closed"));
        doc.close();
    }

    @Test void detailsKeepTheActionsPinnedAndHideInternalNotes() {
        KitHost host = new KitHost();
        var doc = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        doc.querySelector("#grid .card").click();
        assertTrue(doc.getElementById("drawer").hasClass("on"));
        assertNotNull(doc.querySelector("#drawer .dacts #d-open"), "the actions sit in the pinned footer, not in the scrolling part");
        assertNull(doc.querySelector("#drawer .dscroll #d-open"));
        String shown = doc.getElementById("drawer").textContent();
        assertFalse(shown.contains("待游戏验收") || shown.contains("ui/templates/") || shown.contains("minecraft:default"), shown);
        assertFalse(doc.getElementById("d-more").hasClass("on"), "numbered templates have no design notes to fold in");
        assertEquals(TemplateCatalog.avatar("云诺羲"), doc.getElementById("d-avatar").getAttribute("src"));
        doc.close();
    }

    @Test void templatesMadeOfKitPagesOpenThemAndCannotBeSavedAsCopies() {
        assertEquals("oriel:ui/inventory.html", TemplateCatalog.page("54"));
        assertFalse(TemplateCatalog.saveable("54"));
        assertTrue(TemplateCatalog.saveable("01"));
        KitHost host = new KitHost();
        StylesPreset.openTemplate("54");
        var doc = host.open(OrielKit.STYLES, new StylesPreset(true), 640, 360);
        assertTrue(doc.getElementById("drawer").hasClass("on"), "openTemplate opens that template's details");
        assertEquals("display: none", doc.getElementById("d-save").getAttribute("style"));
        doc.close();
    }

    @Test void authorsHaveAvatarsKeptWithTheTemplates() {
        assertEquals("oriel:ui/templates/avatars/rinorsi.png", TemplateCatalog.avatar("云诺羲"));
        assertEquals("oriel:textures/ui/emblem.png", TemplateCatalog.avatar("someone else"));
    }
}
