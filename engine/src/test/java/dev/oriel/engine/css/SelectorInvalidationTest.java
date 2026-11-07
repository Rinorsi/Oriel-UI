package dev.oriel.engine.css;

import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SelectorInvalidationTest {
    @Test
    void mutationSequenceKeepsEveryComputedFieldEqualToLegacy() {
        String saved = System.getProperty("oriel.selector");
        try {
            String html = """
                    <style>.on { color:red } p[data-v] { opacity:.4 }
                    section:has(> .on) { color:blue } section:has([data-v]) { font-size:12px }
                    p:has(+ .on) { border-width:2px } p:has(~ [data-v]) { padding:3px }
                    p:nth-child(2 of :is(.on,[data-v])) { color:green }
                    p:not(.off) { margin:1px } p:hover { opacity:.8 }
                    p.on::before { content:'x'; color:yellow }</style>
                    <main><section><p></p><p></p><p></p><p></p></section>
                    <section><p></p><p></p><p></p><p></p></section></main>
                    """;
            System.setProperty("oriel.selector", "old");
            var oldPage = new TestHost().load(html);
            System.setProperty("oriel.selector", "new");
            var newPage = new TestHost().load(html);
            var oldElements = oldPage.doc.querySelectorAll("*");
            var newElements = newPage.doc.querySelectorAll("*");
            var oldTargets = oldPage.doc.querySelectorAll("p");
            var newTargets = newPage.doc.querySelectorAll("p");
            var random = new java.util.Random(360307);
            for (int step = 0; step < 160; step++) {
                int target = random.nextInt(oldTargets.size());
                String attribute = step % 3 == 0 ? "data-v" : "class";
                String value = switch (step % 4) { case 0 -> null; case 1 -> "on"; case 2 -> "off"; default -> "on off"; };
                for (String mode : java.util.List.of("old", "new")) {
                    System.setProperty("oriel.selector", mode);
                    var page = mode.equals("old") ? oldPage : newPage;
                    var element = (mode.equals("old") ? oldTargets : newTargets).get(target);
                    if (value == null) element.removeAttribute(attribute); else element.setAttribute(attribute, value);
                    page.doc.setHovered(element, step % 2 == 0);
                    page.doc.flushStyle();
                }
                for (int i = 0; i < oldElements.size(); i++) {
                    var oldElement = oldElements.get(i);
                    var newElement = newElements.get(i);
                    assertTrue(oldElement.baseStyle.sameAs(newElement.baseStyle), "step " + step + " element " + i);
                    if (oldElement.beforeBaseStyle == null) assertNull(newElement.beforeBaseStyle);
                    else assertTrue(oldElement.beforeBaseStyle.sameAs(newElement.beforeBaseStyle));
                }
            }
        } finally { restore(saved); }
    }

    @Test
    void localClassChangesAndDirectHasOnlyRematchAffectedElements() {
        String saved = System.getProperty("oriel.selector");
        try {
            System.setProperty("oriel.selector", "new");
            var page = new TestHost().load("<style>.card:has(> .on) { color:red }</style>"
                    + "<section class=card><p id=x></p></section>" + "<section class=card><p></p></section>".repeat(50));
            page.query("#x").setAttribute("class", "on");
            page.doc.flushStyle();
            assertEquals(0xFFFF0000, page.query("#x").parentElement().baseStyle.color);
            assertEquals(2, page.doc.styleEngine().rematched);
            page.query("#x").removeAttribute("class");
            page.doc.flushStyle();
            assertNotEquals(0xFFFF0000, page.query("#x").parentElement().baseStyle.color);
            assertEquals(2, page.doc.styleEngine().rematched);
        } finally { restore(saved); }
    }

    @Test
    void siblingHasAndFilteredNthInvalidateTheirReverseDependencies() {
        String saved = System.getProperty("oriel.selector");
        try {
            System.setProperty("oriel.selector", "new");
            var page = new TestHost().load("""
                    <style>p:has(+ .on) { color:red } p:has(~ [data-on]) { opacity:.5 }
                    p:nth-child(2 of .on) { color:blue }</style>
                    <main><p id=a class=on></p><p id=b></p><p id=c class=on></p></main><aside></aside>
                    """);
            page.query("#b").setAttribute("class", "on");
            page.query("#c").setAttribute("data-on", "");
            page.doc.flushStyle();
            assertEquals(0xFFFF0000, page.style("#a").color);
            assertEquals(0xFF0000FF, page.style("#b").color);
            assertEquals(.5f, page.style("#a").opacity);
            assertTrue(page.doc.styleEngine().rematched <= 3);
            page.query("#b").removeAttribute("class");
            page.query("#c").removeAttribute("data-on");
            page.doc.flushStyle();
            assertNotEquals(0xFFFF0000, page.style("#a").color);
            assertEquals(1f, page.style("#a").opacity);
        } finally { restore(saved); }
    }

    @Test
    void complexRelationsAndStructuralMutationsKeepTheConservativePath() {
        String saved = System.getProperty("oriel.selector");
        try {
            System.setProperty("oriel.selector", "new");
            var page = new TestHost().load("<style>.on p { color:red } section:has(> div > p.on) { opacity:.5 }</style>"
                    + "<section><div id=d><p id=p></p></div></section><aside></aside>");
            page.query("#d").setAttribute("class", "on");
            page.doc.flushStyle();
            assertEquals(0xFFFF0000, page.style("#p").color);
            assertEquals(page.doc.querySelectorAll("*").size(), page.doc.styleEngine().rematched);
            page.query("#p").setAttribute("class", "on");
            page.query("section").appendChild(page.doc.createElement("p"));
            page.doc.flushStyle();
            assertEquals(.5f, page.style("section").opacity);
            assertEquals(page.doc.querySelectorAll("*").size(), page.doc.styleEngine().rematched);
        } finally { restore(saved); }
    }

    private static void restore(String saved) {
        if (saved == null) System.clearProperty("oriel.selector");
        else System.setProperty("oriel.selector", saved);
    }
}
