package dev.oriel.engine.layout;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LayoutParityTest {
    @Test
    void bothComparesBlockInlinePositionedOverflowAndGeneratedBoxes() {
        String previous = System.getProperty("oriel.layout.block");
        try {
            System.setProperty("oriel.layout.block", "both");
            Page page = new TestHost().load("""
                    <style>
                    #flow {width:160px;height:60px;overflow:auto;position:relative;padding:3px}
                    p {margin:8px 0 -2px} b {padding:2px;border:1px solid}
                    #flow::before {content:'before'} #flow::after {content:'after';display:block}
                    </style>
                    <div id=flow><p>中文 <b>wrapped inline content</b> and text</p>
                    <div style='height:0;margin:12px 0 -4px'></div>
                    <div style='height:120px;aspect-ratio:2'></div>
                    <span style='position:absolute;right:0;bottom:0'>absolute</span></div>
                    <div style='display:flex'><div>flex</div><div style='display:grid'>grid</div></div>
                    """);
            assertEquals("", page.errors());
            LayoutSnapshot before = page.doc.layoutEngine().snapshot();
            assertTrue(before.boxes().size() > 10);
            page.doc.invalidateLayout();
            page.doc.flushLayout();
            before.requireSame(page.doc.layoutEngine().snapshot(), "forced full layout");
        } finally {
            restore(previous);
        }
    }

    @Test
    void snapshotIsImmutableAndIdsSurviveRelayoutAndScroll() {
        Page page = new TestHost().load("<div id=s style='height:20px;overflow:auto'><div id=c style='height:100px'></div></div>");
        LayoutSnapshot before = page.doc.layoutEngine().snapshot();
        String id = before.boxes().stream().filter(b -> b.element().contains("#c")).findFirst().orElseThrow().id();
        Box root = page.doc.layoutEngine().root();
        page.byId("s").scrollTo(0, 10);
        LayoutSnapshot after = page.doc.layoutEngine().snapshot();
        assertSame(root, page.doc.layoutEngine().root());
        assertEquals(id, after.boxes().stream().filter(b -> b.element().contains("#c")).findFirst().orElseThrow().id());
        assertThrows(IllegalStateException.class, () -> before.requireSame(after, "scroll"));
        assertThrows(UnsupportedOperationException.class, () -> before.boxes().clear());
    }

    @Test
    void invalidModeFailsLoudly() {
        String previous = System.getProperty("oriel.layout.block");
        try {
            System.setProperty("oriel.layout.block", "typo");
            assertThrows(IllegalArgumentException.class, () -> new TestHost().load("<div></div>"));
        } finally {
            restore(previous);
        }
    }

    @Test
    void scrollAndTransformFastPathsMatchForcedLayout() {
        String previous = System.getProperty("oriel.layout.block");
        try {
            System.setProperty("oriel.layout.block", "both");
            Page page = new TestHost().load("""
                    <div id=s style='height:40px;width:90px;overflow:auto;position:relative'>
                    <div id=t style='height:120px;transform:translateX(1px)'>text <span>中文</span>
                    <i style='position:absolute;top:4px;right:0'>a</i></div></div>
                    """);
            Box root = page.doc.layoutEngine().root();
            page.byId("s").scrollTo(0, 15);
            page.byId("t").setAttribute("style", "height:120px;transform:translateX(7px)");
            page.doc.flushLayout();
            assertSame(root, page.doc.layoutEngine().root(), "scroll/transform must not reflow");
            LayoutSnapshot fast = page.doc.layoutEngine().snapshot();
            page.doc.invalidateLayout();
            page.doc.flushLayout();
            fast.requireSame(page.doc.layoutEngine().snapshot(), "fast versus forced");
        } finally {
            restore(previous);
        }
    }

    @Test
    void existingMathRatioAndClipContractsRemainAvailable() {
        String previous = System.getProperty("oriel.layout.block");
        try {
            System.setProperty("oriel.layout.block", "both");
            Page page = new TestHost().load("""
                    <div style='width:200px'><div id=t style='width:clamp(20px,50%,120px);aspect-ratio:2;min-height:0;overflow:clip'>
                    <div style='width:max(300px,100%);height:200px'></div></div></div>
                    """);
            assertEquals(100, page.byId("t").box.width);
            assertEquals(50, page.byId("t").box.height);
            page.byId("t").scrollTo(100, 100);
            assertEquals(0, page.byId("t").scrollTop());
            assertEquals(0, page.byId("t").scrollLeft());
        } finally {
            restore(previous);
        }
    }

    @Test
    void emptyGeneratedSpansAreCapturedAndPseudoIdsDoNotFollowSiblingOrdinals() {
        Page page = new TestHost().load("""
                <style>#t::before {content:''} #t::after {content:'after'}
                #t.hide::before {display:none}</style><div id=t><span id=empty></span></div>
                """);
        LayoutSnapshot before = page.doc.layoutEngine().snapshot();
        var beforePseudo = before.boxes().stream().filter(b -> b.element().contains("#t") && b.id().endsWith(":before")).findFirst();
        assertTrue(beforePseudo.isPresent(), "empty pseudo boxes still have a diagnostic identity");
        String afterId = before.boxes().stream().filter(b -> b.id().endsWith(":after")).findFirst().orElseThrow().id();
        assertTrue(before.boxes().stream().anyMatch(b -> b.element().contains("#empty")));
        page.byId("t").setAttribute("class", "hide");
        page.doc.flushLayout();
        LayoutSnapshot after = page.doc.layoutEngine().snapshot();
        assertFalse(after.boxes().stream().anyMatch(b -> b.id().endsWith(":before")));
        assertTrue(after.boxes().stream().anyMatch(b -> b.id().equals(afterId)));
    }

    private static void restore(String value) {
        if (value == null) System.clearProperty("oriel.layout.block");
        else System.setProperty("oriel.layout.block", value);
    }
}
