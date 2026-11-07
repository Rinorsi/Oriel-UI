package dev.oriel.engine.css;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import dev.oriel.engine.style.Length;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compatibility and opt-in behavior must be verified independently. */
class CascadeMigrationTest {
    private static void mode(String mode, Runnable test) {
        String previous = System.getProperty("oriel.cascade");
        String layers = System.getProperty("oriel.css.layers");
        try {
            System.setProperty("oriel.cascade", mode);
            System.setProperty("oriel.css.layers", "true");
            test.run();
        } finally {
            restore("oriel.cascade", previous);
            restore("oriel.css.layers", layers);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }

    @Test void normalLayersPrecedeSpecificityAndUnlayeredWins() {
        mode("new", () -> {
            Page p = new TestHost().load("""
                    <style>@layer base, theme;
                    @layer theme { div { color: blue; width: 20px } }
                    @layer base { #a { color: red; width: 10px } }
                    div { width: 30px }
                    </style><div id=a></div>""");
            assertEquals(0xff0000ff, p.style("#a").color);
            assertEquals(Length.px(30), p.style("#a").width);
        });
    }

    @Test void importantReversesLayerOrderButInlineStillWins() {
        mode("new", () -> {
            Page p = new TestHost().load("""
                    <style>@layer base, theme;
                    @layer base { div { color: red !important; width: 10px !important } }
                    @layer theme { #a { color: blue !important; width: 20px !important } }
                    #a { color: green !important; width: 30px !important }
                    </style><div id=a style='width:40px !important'></div>""");
            assertEquals(0xffff0000, p.style("#a").color);
            assertEquals(Length.px(40), p.style("#a").width);
        });
    }

    @Test void nestedLayersStayInsideTheirParentAcrossStyleSheets() {
        mode("new", () -> {
            Page p = new TestHost().load("""
                    <style>@layer a, b; @layer a { @layer x, y; @layer y { div { color: red } } }
                    @layer b { div { color: blue } }</style>
                    <style>@layer a.x { #a { color: green } }</style><div id=a></div>""");
            assertEquals(0xff0000ff, p.style("#a").color);
        });
    }

    @Test void dualRunPreservesInheritanceVariablesPseudoAndUnits() {
        mode("both", () -> {
            Page p = new TestHost().load("""
                    <style>:root { --unit: 6px; font-size: 10px }
                    #p { color:red; width:40px; --a:var(--b); --b:var(--a) }
                    #a { width:inherit; padding:calc(var(--unit) + 1px); border:1px solid currentColor }
                    #a::before { content:'x'; color:inherit; width:var(--a, 12px) }
                    </style><div id=p><div id=a></div></div>""");
            assertEquals(Length.px(40), p.style("#a").width);
            assertEquals(Length.px(7), p.style("#a").paddingTop);
            assertEquals(0xffff0000, p.query("#a").beforeBaseStyle.color);
            assertEquals(Length.px(12), p.query("#a").beforeBaseStyle.width);
            p.query("#p").setAttribute("style", "--unit:9px; color:blue");
            p.frame();
            assertEquals(Length.px(10), p.style("#a").paddingTop);
            assertEquals(0xff0000ff, p.query("#a").beforeBaseStyle.color);
        });
    }

    @Test void localHoverDoesNotRematchOtherHoverSubjects() {
        mode("new", () -> {
            Page p = new TestHost().load("<style>.item:hover { opacity:.5 }</style>"
                    + "<div class=item></div>".repeat(1000));
            p.doc.setHovered(p.query(".item"), true);
            p.frame();
            assertEquals(.5f, p.style(".item").opacity);
            assertTrue(p.doc.styleEngine().rematched <= 2, "only the changed hover subject rematches");
            assertTrue(p.doc.styleEngine().visited < 10, "unchanged sibling subtrees are not visited");
        });
    }

    @Test void relationalMutationsAndBatchesAgreeWithFullReference() {
        mode("both", () -> {
            Page p = new TestHost().load("""
                    <style>
                    .on > .item { color:red } .item.on ~ .item span { color:blue }
                    section:has(> .item.on) > .item { --size:7px }
                    .item:has(+ .item.on) span { opacity:.4 }
                    .item:nth-child(2n of .on) { width:13px }
                    section:focus-within .item { padding:2px }
                    .item:hover + .item span { color:green }
                    .item span { width:var(--size, 3px) }
                    </style><section id=parent>
                    <div class=item tabindex=0><span></span></div>
                    <div class=item tabindex=0><span></span></div>
                    <div class=item tabindex=0><span></span></div>
                    <div class=item tabindex=0><span></span></div>
                    </section>""");
            var items = p.doc.querySelectorAll(".item");
            for (int i = 0; i < 40; i++) {
                var item = items.get(i % items.size());
                item.setAttribute("class", i % 3 == 0 ? "item on" : "item");
                p.query("#parent").setAttribute("class", i % 2 == 0 ? "on" : "");
                p.doc.setHovered(item, i % 2 == 0);
                p.doc.setFocus(i % 5 == 0 ? null : item);
                if (i % 3 == 0) item.setAttribute("style", "--size:" + (i + 1) + "px");
                p.frame(); // both mode independently recomputes every element, including skipped subtrees
            }
        });
    }

    @Test void layerReopeningUsesTheOriginalNestedOrder() {
        mode("new", () -> {
            Page p = new TestHost().load("""
                    <style>@layer a { @layer x, y; @layer y { div { color:blue } } }
                    @layer a.x { #a { color:red } }</style><div id=a></div>""");
            assertEquals(0xff0000ff, p.style("#a").color);
        });
    }

    @Test void schemaDescribesInstalledLonghandsWithoutChangingExtensionRegistration() {
        assertEquals(Properties.all().size(), PropertySchema.properties().size());
        assertTrue(PropertySchema.named("color").inherited());
        assertTrue(PropertySchema.named("width").affectsLayout());
        assertEquals("currentColor".toLowerCase(java.util.Locale.ROOT),
                PropertySchema.named("border-top-color").initialValue().toLowerCase(java.util.Locale.ROOT));
    }

    @Test void propertyExplanationNamesTheWinningLayerAndVariableSource() {
        mode("new", () -> {
            Page p = new TestHost().load("""
                    <style>@layer base, theme; :root { --size:12px }
                    @layer theme { div { width:var(--size) } }
                    @layer base { #a { width:4px } }</style><div id=a></div>""");
            var trace = p.doc.styleEngine().explainProperty(p.query("#a"), "width");
            assertEquals("cascade-explanation", trace.get("kind"));
            assertEquals("12px", trace.get("baseValue"));
            assertTrue(trace.get("declarations").toString().contains("reason=layer-order"));
            assertTrue(trace.get("variableExpansion").toString().contains("name=--size"));
        });
    }

    @Test void inactiveConditionalLayersDoNotReserveOrder() {
        mode("new", () -> {
            Page p = new TestHost().load("""
                    <style>@media(min-width:9999px) { @layer first; }
                    @layer second { div { color:blue } }
                    @layer first { div { color:red } }</style><div id=a></div>""");
            assertEquals(0xffff0000, p.style("#a").color);
        });
    }

    @Test void propertyExplanationFollowsInvalidAndKeywordVariableFallbacks() {
        mode("new", () -> {
            Page p = new TestHost().load("""
                    <style>#p { color:red; --gone:blue }
                    #a { --bad:12px; color:var(--bad); --gone:var(--missing) }
                    #b { --word:inherit; width:var(--missing, inherit) }
                    </style><div id=p><div id=a></div><div id=b></div></div>""");
            var invalid = p.doc.styleEngine().explainProperty(p.query("#a"), "color");
            assertEquals("invalid-after-variable-unset", invalid.get("variableResolution"));
            assertEquals(p.query("#p").toString(), invalid.get("inheritedFrom"));
            var keyword = p.doc.styleEngine().explainProperty(p.query("#b"), "width");
            assertEquals(p.query("#p").toString(), keyword.get("inheritedFrom"));
            var custom = p.doc.styleEngine().explainProperty(p.query("#a"), "--gone");
            assertEquals(null, custom.get("inheritedFrom"));
        });
    }

    @Test void keyframeDefinitionsFollowTheirLayerInsteadOfSourceOrder() {
        mode("new", () -> {
            Page p = new TestHost().load("""
                    <style>@layer first, second;
                    @layer second { @keyframes pulse { from { opacity:.8 } to { opacity:.8 } } }
                    @layer first { @keyframes pulse { from { opacity:.2 } to { opacity:.2 } } }
                    </style><div id=a></div>""");
            var frames = p.doc.styleEngine().resolveKeyframes(p.query("#a"),
                    dev.oriel.engine.dom.PseudoElement.NONE, "pulse", p.style("#a"));
            assertEquals(.8f, frames.getFirst().style().opacity);
        });
    }

    @Test void variableTraceUsesTheDeclaringScopeIncludingPseudoElements() {
        mode("new", () -> {
            Page p = new TestHost().load("""
                    <style>#p { --tone:red; --alias:var(--tone) }
                    #a { --tone:blue; color:var(--alias) }
                    #a::before { --tone:green; content:'x'; color:var(--tone) }
                    </style><div id=p><div id=a></div></div>""");
            var trace = p.doc.styleEngine().explainProperty(p.query("#a"), "color");
            assertTrue(trace.get("variableExpansion").toString().contains("name=--tone, resolved=red"));
            var pseudo = p.doc.styleEngine().explainProperty(p.query("#a"), "::before/color");
            assertTrue(pseudo.get("variableExpansion").toString().contains("declaredPseudo=::before"));
        });
    }
}
