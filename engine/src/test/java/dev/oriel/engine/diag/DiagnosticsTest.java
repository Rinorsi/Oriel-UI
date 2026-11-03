package dev.oriel.engine.diag;

import dev.oriel.engine.diag.Diagnostic.Severity;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nothing in a stylesheet is dropped or approximated without a diagnostic saying so, with a hint where possible. */
class DiagnosticsTest {
    private static List<Diagnostic> diagnose(String css) {
        Page page = new TestHost().load("<style>" + css + "</style><div id=a></div>");
        return page.doc.diagnostics().all();
    }

    private static Diagnostic only(String css, String code) {
        List<Diagnostic> all = diagnose(css);
        List<Diagnostic> matching = all.stream().filter(d -> d.code().equals(code)).toList();
        assertEquals(1, matching.size(), () -> code + " in " + all);
        return matching.getFirst();
    }

    @Test
    void unknownPropertiesSuggestTheNearestName() {
        Diagnostic d = only("#a { bakground-color: red }", "unknown-property");
        assertEquals(Severity.WARNING, d.severity());
        assertEquals("did you mean 'background-color'?", d.suggestion());
        assertEquals("test:page.html", d.source());
        assertEquals(1, d.line());
    }

    @Test
    void invalidValuesSayWhy() {
        assertEquals("lengths need a unit, e.g. 10px", only("#a { width: 10 }", "invalid-value").suggestion());
        assertTrue(only("#a { width: 10pt }", "invalid-value").suggestion().startsWith("unit 'pt' is not supported"));
    }

    @Test
    void nestedRulesAreReportedNotSilentlyDropped() {
        Diagnostic d = only("#a { color: red; &:hover { color: blue } }", "not-a-declaration");
        assertTrue(d.suggestion().contains("CSS nesting is not supported"), d::toString);
    }

    @Test
    void approximationsAreReported() {
        only("#a { position: sticky; top: 0 }", "approximated");
        assertEquals(Severity.INFO, only("@layer base { #a { color: red } }", "layer-flattened").severity());
        assertEquals(Severity.INFO, only("@font-face { font-family: x }", "ignored-at-rule").severity());
        only("@container (width > 1px) { #a { color: red } }", "unknown-at-rule");
        only("#a { animation: x 1s } @keyframes x { from { color: red !important } }", "ignored-important");
    }

    @Test
    void validStylesheetsAreQuietAndRepeatsAreReportedOnce() {
        assertEquals(List.of(), diagnose("#a { color: red; border-image: url(a.png) 2 fill; width: calc(100% - 4px) }"));
        Page page = new TestHost().load("<style>#a { colr: red }</style><div id=a style='colr: red'></div><div style='colr: red'></div>");
        assertEquals(2, page.doc.diagnostics().all().size(), () -> page.doc.diagnostics().all().toString());
        assertEquals(2, page.doc.diagnostics().count(Severity.WARNING));
    }

    @Test
    void formatReadsLikeACompilerMessage() {
        Diagnostic d = new Diagnostic(Severity.WARNING, "invalid-value", "ns:ui/a.css", 3, "Invalid declaration 'width: 10'",
                "lengths need a unit, e.g. 10px");
        assertEquals("ns:ui/a.css:3: warning [invalid-value] Invalid declaration 'width: 10' (lengths need a unit, e.g. 10px)",
                d.format());
    }
}
