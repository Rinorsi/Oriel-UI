package dev.oriel.engine.diag;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SilentFailuresTest {
    private static long count(Page page, String code) {
        return page.doc.diagnostics().all().stream().filter(d -> d.code().equals(code)).count();
    }

    @Test
    void invalidVariableSubstitutionReportsOriginalAndExpandedDeclarationOnce() {
        Page page = new TestHost().load(
                "<style>:root { --cyan: cyan } div { background: conic-gradient(var(--cyan), not-a-color) }</style>"
                        + "<div>A</div><div>B</div>");
        assertEquals(1, count(page, "invalid-after-var"));
        Diagnostic d = page.doc.diagnostics().all().getFirst();
        assertTrue(d.message().contains("background"));
        assertTrue(d.message().contains("var(--cyan)"));
        assertTrue(d.message().contains("conic-gradient(cyan, not-a-color)"));
        assertFalse(d.suggestion().isBlank());
        page.doc.body().setAttribute("class", "changed");
        page.frame();
        assertEquals(1, count(page, "invalid-after-var"));
    }

    @Test
    void undefinedVariablesWarnButValidFallbacksDoNot() {
        Page page = new TestHost().load(
                "<div style='color: var(--missing)'>A</div>"
                        + "<div style='color: var(--missing, red)'>B</div>");
        assertEquals(1, count(page, "undefined-var"));
        assertEquals(1, page.doc.diagnostics().all().size());
    }

    @Test
    void cachedUnsetStillInheritsEachElementsOwnParent() {
        Page page = new TestHost().load(
                "<style>.child { color: var(--missing) }</style>"
                        + "<div style='color:red'><span id=a class=child>A</span></div>"
                        + "<div style='color:blue'><span id=b class=child>B</span></div>");
        assertNotEquals(page.doc.getElementById("a").style.color, page.doc.getElementById("b").style.color);
        assertEquals(1, count(page, "undefined-var"));
    }

    @Test
    void unsupportedElementsIncludeSvgChildrenAndDedupeAcrossFragments() {
        Page page = new TestHost().load("<svg><circle/><circle/></svg><video/><audio/>"
                + "<iframe/><object/><embed/><math/><table><tr><td>T</td></tr></table><marquee>M</marquee>"
                + "<custom-widget/><canvas/><div/><button>B</button>");
        assertEquals(12, count(page, "unsupported-element"));
        page.doc.body().setInnerHTML("<svg><circle/></svg>");
        assertEquals(12, count(page, "unsupported-element"));
    }

    @Test
    void dataUrlsAndMissingImagesAreDocumentDiagnostics() {
        Page page = new TestHost().load("<div style='height:20px;background-image:url("
                + "\"data:image/png;base64,AAAA\""
                + ")'>A</div><img src='absent.png'><div style='height:20px;background:url(absent.png)'>B</div>");
        page.paint();
        page.frame().paint();
        assertEquals(1, count(page, "unsupported-url"));
        assertEquals(1, count(page, "missing-image"));
    }

    @Test
    void invalidLonghandAfterVarIsReported() {
        Page page = new TestHost().load("<div style='--width: wide; width: var(--width)'>A</div>");
        assertEquals(1, count(page, "invalid-after-var"));
    }

    @Test
    void unknownImageSizeIsNotALoadFailure() {
        TestHost host = new TestHost() {
            @Override
            public boolean imageFailed(String url) { return false; }
        };
        Page page = host.load("<img src='loading.png'>");
        page.paint();
        assertEquals(0, count(page, "missing-image"));
    }

    @Test
    void foreignChildrenAndProgrammaticElementsAreReported() {
        Page page = new TestHost().load("<svg><feGaussianBlur/><custom-shape/></svg>");
        page.doc.createElement("VIDEO");
        assertEquals(3, count(page, "unsupported-element"));
    }

    @Test
    void hostProvidedElementsAreNotReportedBeforeTheyAttach() {
        TestHost host = new TestHost();
        host.replaced.put("video", element -> new dev.oriel.engine.host.ReplacedContent() {
            @Override public float intrinsicWidth() { return 20; }
            @Override public float intrinsicHeight() { return 20; }
            @Override public void paint(dev.oriel.engine.paint.Canvas canvas, float x, float y, float w, float h) {}
        });
        Page page = host.load("<video></video>");
        assertEquals(0, count(page, "unsupported-element"));
        assertNotNull(page.doc.querySelector("video").replaced);
    }
}
