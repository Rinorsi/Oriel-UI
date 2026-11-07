package dev.oriel.engine.css;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CompiledSelectorTest {
    @Test
    void ordinaryAndTracedProgramsVisitTheSamePredicatesAndUseTheSameBudget() {
        Document document = Document.parse(new TestHost(), "test:compiled-budget.html#pick",
                "<main><p id=pick class=x data-v=on></p><p></p><div class=x></div></main>");
        document.setHovered(document.querySelector("#pick"), true);
        for (String text : List.of("p.x", "[data-v=on]:hover", ":scope > p", ".x + p",
                "p:not(.off)", "main:has(> .x)", "p:nth-child(2 of .x)",
                "#pick:target", "p.x[data-v=on]:hover", ":only-child", ":only-of-type",
                ":is(p, div):not(.off):nth-child(2n of .x)")) {
            Selector selector = SelectorParser.parse(text).getFirst();
            CompiledSelector plain = CompiledSelector.compile(selector);
            CompiledSelector traced = CompiledSelector.compile(selector, (s, c, p, e, matched) -> {});
            for (Element element : document.querySelectorAll("*")) {
                for (int limit = 0; limit <= 20; limit++) {
                    var a = new Selector.MatchContext(document.documentElement(), SelectorAudit.Mode.NEW, limit);
                    var b = new Selector.MatchContext(document.documentElement(), SelectorAudit.Mode.NEW, limit);
                    Boolean expected = null;
                    try { expected = traced.matches(element, b); }
                    catch (IllegalArgumentException exhausted) { assertTrue(exhausted.getMessage().contains("work budget")); }
                    if (expected == null) assertThrows(IllegalArgumentException.class, () -> plain.matches(element, a));
                    else assertEquals(expected, plain.matches(element, a), text);
                    assertEquals(b.workUsed(), a.workUsed(), text);
                    assertEquals(b.interactionRead, a.interactionRead, text);
                }
            }
        }
        document.close();
    }

    @Test
    void targetOnlyDecodesAsciiHexadecimalEscapes() {
        for (String fragment : List.of("%４１", "%4", "%GG")) {
            Document document = Document.parse(new TestHost(), "test:target.html#" + fragment,
                    "<p id='" + fragment + "'></p><p id=A></p>");
            assertEquals(fragment, document.querySelector(":target").id());
        }
    }

    @Test
    void targetUsesTheDecodedDocumentFragmentAndTracksIdChanges() {
        Document d = Document.parse(new TestHost(), "test:target.html#%E4%B8%AD+",
                "<p id='中+'></p><p id='other'></p>");
        for (SelectorAudit.Mode mode : SelectorAudit.Mode.values()) {
            Selector selector = SelectorParser.parse(":target").getFirst();
            assertTrue(selector.matches(d.getElementById("中+"), new Selector.MatchContext(d.documentElement(), mode)));
            assertFalse(selector.matches(d.getElementById("other"), new Selector.MatchContext(d.documentElement(), mode)));
        }
        Element target = d.getElementById("中+");
        target.setAttribute("id", "changed");
        assertFalse(target.matches(":target"));
        assertNull(Document.parse(new TestHost(), "test:no-fragment.html", "<p id=''></p>").querySelector(":target"));
    }

    @Test
    void filteredNthBuildsSiblingRanksOncePerMatchingPass() {
        Document d = Document.parse(new TestHost(), "test:nth-linear.html",
                "<main>" + "<p class=x></p>".repeat(1000) + "</main>");
        CompiledSelector program = SelectorParser.parse("p:nth-child(2n of .x)").get(0).compiled();
        Selector.MatchContext context = new Selector.MatchContext(d.documentElement(), SelectorAudit.Mode.NEW, 10_000);
        List<Element> siblings = d.querySelectorAll("p");
        for (int i = 0; i < siblings.size(); i++) assertEquals(i % 2 == 1, program.matches(siblings.get(i), context));
        siblings.getFirst().removeAttribute("class");
        Selector.MatchContext next = new Selector.MatchContext(d.documentElement(), SelectorAudit.Mode.NEW, 10_000);
        assertFalse(program.matches(siblings.get(1), next), "a new pass must see the changed filter");
    }

    @Test
    void relativeHasAndFilteredNthRestyleAfterMutationInEveryMode() {
        String saved = System.getProperty("oriel.selector");
        try {
            for (String mode : List.of("old", "new", "both")) {
                System.setProperty("oriel.selector", mode);
                var page = new TestHost().load("""
                        <style>section:has(> .on) { color:red }
                        p:has(+ p.on) { color:blue } p:nth-child(2 of .on) { color:green }</style>
                        <section id=s><p id=a class=on></p><p id=b></p><p id=c class=on></p></section>
                        """);
                assertEquals(0xFFFF0000, page.style("#s").color, mode);
                assertEquals(0xFF008000, page.style("#c").color, mode);
                page.query("#b").setAttribute("class", "on");
                page.frame();
                assertEquals(0xFF0000FF, page.style("#a").color, mode);
                assertEquals(0xFF008000, page.style("#b").color, mode);
                page.query("#a").removeAttribute("class");
                page.query("#b").removeAttribute("class");
                page.query("#c").removeAttribute("class");
                page.frame();
                assertNotEquals(0xFFFF0000, page.style("#s").color, mode);
                assertNotEquals(0xFF0000FF, page.style("#a").color, mode);
            }
        } finally {
            if (saved == null) System.clearProperty("oriel.selector");
            else System.setProperty("oriel.selector", saved);
        }
    }

    @Test
    void nestedNthFiltersCannotExceedMatchingWorkBudget() {
        Document d = Document.parse(new TestHost(), "test:budget.html",
                "<main>" + "<p></p>".repeat(8) + "</main>");
        String selector = "p";
        for (int i = 0; i < 5; i++) selector = ":nth-child(n of " + selector + ")";
        CompiledSelector program = SelectorParser.parse(selector).get(0).compiled();
        Selector.MatchContext context = new Selector.MatchContext(d.documentElement(), SelectorAudit.Mode.NEW, 20);
        Element last = d.querySelectorAll("p").get(7);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> program.matches(last, context));
        assertTrue(ex.getMessage().contains("Selector work budget"));
        assertTrue(d.diagnostics().all().stream().anyMatch(issue -> issue.code().equals("selector-budget")));
    }

    @Test
    void cachedFilteredRanksReplayInteractionDependencies() {
        Document d = Document.parse(new TestHost(), "test:nth-hover.html", "<p></p><p></p><p></p>");
        List<Element> elements = d.querySelectorAll("p");
        d.setHovered(elements.get(0), true);
        d.setHovered(elements.get(1), true);
        CompiledSelector program = SelectorParser.parse("p:nth-child(2 of :hover)").getFirst().compiled();
        Selector.MatchContext context = new Selector.MatchContext(d.documentElement(), SelectorAudit.Mode.NEW);
        assertFalse(program.matches(elements.get(0), context));
        context.interactionRead = false;
        assertTrue(program.matches(elements.get(1), context));
        assertTrue(context.interactionRead, "cached ranks must still subscribe to hover invalidation");
        context.interactionRead = false;
        assertFalse(program.matches(elements.get(2), context));
        assertTrue(context.interactionRead, "currently excluded siblings can enter the filter after hover");
    }

    @Test
    void defaultStaysLegacyAndModesPreserveScopeOrderAndMutation() {
        String saved = System.getProperty("oriel.selector");
        try {
            System.clearProperty("oriel.selector");
            assertEquals(SelectorAudit.Mode.OLD, SelectorAudit.mode());
            System.setProperty("oriel.selector", "invalid");
            assertThrows(IllegalArgumentException.class, SelectorAudit::mode);
            for (String mode : List.of("old", "new", "both")) {
                System.setProperty("oriel.selector", mode);
                Document d = Document.parse(new TestHost(), "test:modes.html",
                        "<main id=root><p id=a class=x></p>text<p id=b></p><p id=c class=x></p></main>");
                Element root = d.getElementById("root");
                assertEquals(List.of("a", "c"), root.querySelectorAll(":scope > .x, .x").stream().map(Element::id).toList());
                assertEquals("c", root.querySelector(":scope > :nth-child(2 of .x)").id());
                d.getElementById("a").setAttribute("class", "off");
                assertEquals(List.of("c"), root.querySelectorAll(".x").stream().map(Element::id).toList());
                assertNull(root.querySelector(":scope"));
                assertTrue(root.matches(":scope:has(> p + p.x)"));
            }
        } finally {
            if (saved == null) System.clearProperty("oriel.selector");
            else System.setProperty("oriel.selector", saved);
        }
    }

    @Test
    void seededSelectorCorpusHasNoSemanticDifferences() {
        Random random = new Random(3603);
        StringBuilder html = new StringBuilder("<main id=scope>");
        for (int i = 0; i < 12; i++) {
            html.append("<section class='a b'><div class=a><p class=x></p><b></b></div>")
                    .append("<p class=b></p><p class=x></p></section>");
        }
        Document d = Document.parse(new TestHost(), "test:corpus.html", html + "</main>");
        List<Element> elements = d.descendants(e -> true);
        String[] atoms = {"*", "div", "section", "p", "b", ".a", ".x", ":scope",
                ":is(.a, p)", ":where(#scope, .x)", ":not(.b)", ":has(> p.x)",
                ":has(+ p)", ":has(~ p.x)", ":has(+ div > b)", ":nth-child(2n+1 of p, .a)"};
        String[] relations = {" ", " > ", " + ", " ~ "};
        for (int sample = 0; sample < 1200; sample++) {
            StringBuilder text = new StringBuilder(atoms[random.nextInt(atoms.length)]);
            for (int i = random.nextInt(4); i > 0; i--) {
                text.append(relations[random.nextInt(4)]).append(atoms[random.nextInt(atoms.length)]);
            }
            Selector selector = SelectorParser.parse(text.toString()).get(0);
            CompiledSelector compiled = selector.compiled();
            assertEquals(selector.specificity, compiled.specificity, text.toString());
            Selector.MatchContext oldContext = new Selector.MatchContext(d.getElementById("scope"), SelectorAudit.Mode.OLD);
            Selector.MatchContext newContext = new Selector.MatchContext(d.getElementById("scope"), SelectorAudit.Mode.NEW);
            for (Element e : elements) assertEquals(selector.matches(e, oldContext), compiled.matches(e, newContext), text.toString());
        }
    }

    @Test
    void ruleIndexRetainsMultiplicityPseudoElementsAndCascadeOrder() {
        TestHost host = new TestHost();
        Document d = Document.parse(host, "test:index.html", "<main><p id=x class=a data-k=v></p><p></p></main>");
        Stylesheet sheet = Stylesheet.parse("""
                *, [data-k] { color:red }
                #x, .a, p, [data-k] { color:blue }
                p::before, p { content:'x' }
                @media (min-width:10px) { :is(#x, p) { width:10px } }
                """, "test:index.css", host, diagnostic -> fail(diagnostic.toString()));
        CompiledRuleIndex compiled = new CompiledRuleIndex();
        int order = 0;
        for (Stylesheet.Rule rule : sheet.rules) if (rule instanceof Stylesheet.StyleRule style) {
            for (Selector selector : style.selectors()) compiled.add(selector, style.decls(), false, order);
            order++;
        }
        Element x = d.getElementById("x");
        List<RuleIndex.Entry> matches = new ArrayList<>();
        compiled.match(x, new Selector.MatchContext(x, SelectorAudit.Mode.NEW), matches);
        assertEquals(8, matches.size());
        assertEquals(1, matches.stream().filter(e -> e.selector().pseudoElement == dev.oriel.engine.dom.PseudoElement.BEFORE).count());
        for (int i = 1; i < matches.size(); i++) assertTrue(matches.get(i - 1).key() <= matches.get(i).key());
    }

    @Test
    void compiledProgramAgreesOnRelativeLogicalNthAndBacktracking() {
        Document d = Document.parse(new TestHost(), "test:compiled.html", """
                <main id=scope><div class=a><div class=a><p class=x></p></div></div>
                <section><i></i><p class=x></p><b></b><p></p></section></main>
                """);
        List<String> selectors = List.of(".a > .a .x", "main > .a .a .x", "div + section > p",
                "p:has(+ b)", "div:has(+ section > p.x)", "main:has(> div .x)",
                "p:nth-child(2 of :is(.x, b))", "p:nth-last-child(1 of p)",
                ":where(#scope) :is(p, b):not(.x)", ":scope > div", ":has(~ p)",
                "p::before", "div:hover p");
        Element scope = d.getElementById("scope");
        for (String text : selectors) for (Selector s : SelectorParser.parse(text)) {
            CompiledSelector compiled = CompiledSelector.compile(s);
            assertEquals(s.specificity, compiled.specificity);
            for (Element e : d.descendants(x -> true)) {
                Selector.MatchContext oldContext = new Selector.MatchContext(scope, SelectorAudit.Mode.OLD);
                Selector.MatchContext newContext = new Selector.MatchContext(scope, SelectorAudit.Mode.NEW);
                assertEquals(s.matches(e, oldContext), compiled.matches(e, newContext), text + " / " + e.tagName());
                if (oldContext.interactionRead) assertTrue(newContext.interactionRead, text);
            }
        }
    }

    @Test
    void specificityAndBudgetRemainBounded() {
        assertEquals(Selector.ID + Selector.CLASS + 1,
                CompiledSelector.compile(SelectorParser.parse("p:is(#x, .a):nth-child(2 of :where(#y))").get(0)).specificity);
        assertThrows(IllegalArgumentException.class, () -> SelectorParser.parse(".x".repeat(257)));
        assertThrows(IllegalArgumentException.class, () -> SelectorParser.parse(":has(:has(p))"));
        assertEquals(255 * Selector.CLASS, SelectorParser.parse(".x".repeat(255)).get(0).compiled().specificity);
        Document d = Document.parse(new TestHost(), "test:deep.html",
                "<div>".repeat(200) + "<b id=end></b>" + "</div>".repeat(200));
        CompiledSelector program = SelectorParser.parse(".missing " + "div ".repeat(100) + "b").get(0).compiled();
        assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                assertFalse(program.matches(d.getElementById("end"), new Selector.MatchContext(d.documentElement(), SelectorAudit.Mode.NEW))));
    }
}
