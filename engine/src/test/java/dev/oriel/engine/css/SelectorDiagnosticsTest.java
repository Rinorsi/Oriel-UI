package dev.oriel.engine.css;

import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SelectorDiagnosticsTest {
    @Test
    void explanationFindsTheFailedPredicateAndKeepsImportantAndInlineWinners() {
        var page = new TestHost().load("""
                <style>.on { color:red } #x { color:blue!important; opacity:.3 }
                #x { opacity:.5 } #x::before { content:'x'; color:green }</style>
                <p id=x style='color:yellow; width:12px'></p>
                """);
        var explanation = page.doc.styleEngine().explainSelectors(page.query("#x"), ".on[data-ready]");
        Map<?, ?> query = (Map<?, ?>) ((List<?>) explanation.get("queryResults")).getFirst();
        assertEquals(false, query.get("matched"));
        assertTrue(((List<?>) query.get("trace")).stream().anyMatch(row ->
                row instanceof Map<?, ?> test && Boolean.FALSE.equals(test.get("matched"))
                        && test.get("predicate").toString().contains("on")));
        Map<?, ?> winners = (Map<?, ?>) explanation.get("declarationWinners");
        Map<?, ?> normal = (Map<?, ?>) winners.get("NONE");
        assertEquals("blue", ((Map<?, ?>) normal.get("color")).get("value"));
        assertEquals(true, ((Map<?, ?>) normal.get("color")).get("important"));
        assertEquals("inline", ((Map<?, ?>) normal.get("width")).get("origin"));
        assertEquals(".5", ((Map<?, ?>) normal.get("opacity")).get("value"));
        assertEquals("green", ((Map<?, ?>) ((Map<?, ?>) winners.get("BEFORE")).get("color")).get("value"));
        assertEquals(0xFF0000FF, page.style("#x").color, "inspection must not change the style");
    }

    @Test
    void attributeBucketsAreReportedSeparatelyFromLegacyUniversalCandidates() {
        var page = new TestHost().load("<style>[data-a] { color:red } [data-b] { color:blue }</style><p id=x data-a></p>");
        var explanation = page.doc.styleEngine().explainSelectors(page.query("#x"), "[data-b]");
        Map<?, ?> missing = (Map<?, ?>) ((List<?>) explanation.get("rules")).stream().filter(row ->
                row instanceof Map<?, ?> rule && "[data-b]".equals(rule.get("selector"))).findFirst().orElseThrow();
        assertEquals(true, missing.get("candidateOld"));
        assertEquals(false, missing.get("candidateNew"));
        assertTrue((int) explanation.get("candidateCountOld") > (int) explanation.get("candidateCountNew"));
        assertThrows(IllegalArgumentException.class, () -> page.doc.styleEngine().explainSelectors(page.doc.createElement("p"), "p"));
        assertThrows(IllegalArgumentException.class, () -> page.doc.styleEngine().explainSelectors(page.query("#x"), "["));
    }
}
