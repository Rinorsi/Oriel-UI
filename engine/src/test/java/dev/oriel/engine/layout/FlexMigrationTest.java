package dev.oriel.engine.layout;

import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Independent-tree differential checks; frozen browser fixtures retain their original tolerances. */
class FlexMigrationTest {
    @Test
    void bothKeepsTheDocumentFontCacheAcrossRelayouts() {
        String before = System.getProperty("oriel.layout.flex");
        System.setProperty("oriel.layout.flex", "both");
        try {
            // Regression: the reference pass used to create a fresh TextMeasure on every layout.
            new TextMeasureTest().relayoutsDoNotMeasureTheSameWordsAgain();
        } finally { restore(before); }
    }

    @Test
    void pooledItemsDoNotKeepStateBetweenNestedContainers() {
        String before = System.getProperty("oriel.layout.flex");
        System.setProperty("oriel.layout.flex", "both");
        try {
            for (String direction : new String[] {"row", "row-reverse", "column", "column-reverse"}) {
                for (String wrap : new String[] {"nowrap", "wrap", "wrap-reverse"}) {
                    StringBuilder html = new StringBuilder();
                    for (int group = 0; group < 6; group++) {
                        html.append("<div style='display:flex;width:163px;height:93px;flex-direction:")
                                .append(direction).append(";flex-wrap:").append(wrap)
                                .append(";gap:3% 7%;align-content:space-around;align-items:baseline'>");
                        for (int item = 0; item <= group; item++) {
                            html.append("<div style='display:flex;flex-direction:column;order:")
                                    .append(3 - item).append(";flex:").append(item % 3)
                                    .append(" 1 19px;min-width:7px;max-width:67px;margin:1px;aspect-ratio:2'>")
                                    .append("<span>line 中文</span><div style='width:50%;height:25%'></div></div>");
                        }
                        html.append("<div style='position:absolute;width:5px;height:6px'></div></div>");
                    }
                    var page = new TestHost().load(html.toString(), 427, 240);
                    page.doc.invalidateLayout();
                    page.doc.flushLayout();
                    page.doc.close();
                }
            }
        } finally { restore(before); }
    }

    @Test
    void comparatorDetectsOneBitGeometryChange() {
        var page = new TestHost().load("<div id=probe style='display:flex;width:80px'><b>text</b></div>");
        Box actual = page.byId("probe").box;
        Box changed = new Box(actual.kind, actual.element, actual.style);
        changed.x = Math.nextUp(actual.x);
        var error = assertThrows(IllegalStateException.class, () -> FlexGridCompare.compare(actual, changed, "probe-page"));
        assertTrue(error.getMessage().contains("probe-page"));
        assertTrue(error.getMessage().contains("old="));
        page.doc.close();
    }

    @Test
    void gapsAndPlaceContentRespectRowAndColumnAxes() {
        String before = System.getProperty("oriel.layout.flex");
        try {
            for (String mode : new String[] {"old", "new", "both"}) {
                System.setProperty("oriel.layout.flex", mode);
                var row = new TestHost().load("""
                        <div style='display:flex;flex-wrap:wrap;width:100px;height:50px;gap:4px 10px;place-content:center'>
                          <div id=a style='width:40px;height:10px'></div>
                          <div id=b style='width:40px;height:10px'></div>
                          <div id=c style='width:40px;height:10px'></div>
                        </div>
                        """);
                BlockLayoutTest.assertRect(row.byId("a"), 5, 13, 40, 10);
                BlockLayoutTest.assertRect(row.byId("b"), 55, 13, 40, 10);
                BlockLayoutTest.assertRect(row.byId("c"), 30, 27, 40, 10);
                row.doc.close();
                var column = new TestHost().load("""
                        <div style='display:flex;flex-direction:column;flex-wrap:wrap;width:50px;height:100px;gap:10px 4px;place-content:center'>
                          <div id=a style='width:10px;height:40px'></div>
                          <div id=b style='width:10px;height:40px'></div>
                          <div id=c style='width:10px;height:40px'></div>
                        </div>
                        """);
                BlockLayoutTest.assertRect(column.byId("a"), 13, 5, 10, 40);
                BlockLayoutTest.assertRect(column.byId("b"), 13, 55, 10, 40);
                BlockLayoutTest.assertRect(column.byId("c"), 27, 30, 10, 40);
                column.doc.close();
            }
        } finally { restore(before); }
    }

    @TestFactory
    Stream<DynamicTest> frozenFixtures() throws Exception {
        TaffyFixtureTest fixtures = new TaffyFixtureTest();
        return Stream.of(fixtures.block(), fixtures.flex(), fixtures.grid()).flatMap(s -> s)
                .map(test -> DynamicTest.dynamicTest(test.getDisplayName(), () -> {
                    String before = System.getProperty("oriel.layout.flex");
                    System.setProperty("oriel.layout.flex", "both");
                    try { test.getExecutable().execute(); } finally { restore(before); }
                }));
    }

    @Test
    void bothHandlesNestedTextAutoMarginsAndResize() {
        String before = System.getProperty("oriel.layout.flex");
        System.setProperty("oriel.layout.flex", "both");
        try {
            for (int width : new int[] {427, 640, 120, 320}) {
                var page = new TestHost().load("""
                        <div style='display:flex;flex-wrap:wrap;gap:7px 11px;padding:3px'>
                          <div style='display:flex;flex-direction:column;max-width:80px'>
                            <span>Chinese 中文 line <b>baseline</b></span><span>second line</span>
                          </div>
                          <div style='flex:1 1 20%;min-width:40px;margin-left:auto'>other</div>
                          <div style='position:absolute;bottom:0'>overlay</div>
                        </div>
                        """, width, 240);
                page.doc.invalidateLayout();
                page.doc.flushLayout();
                page.doc.close();
            }
        } finally { restore(before); }
    }

    @Test
    void rejectsUnknownModeInsteadOfSilentlyFallingBack() {
        String before = System.getProperty("oriel.layout.flex");
        System.setProperty("oriel.layout.flex", "typo");
        try {
            assertThrows(IllegalArgumentException.class, () -> FlexGridCompare.mode("flex"));
        } finally { restore(before); }
    }

    @Test
    void traceExplainsSizeFreezingAndPositionAndIsOffByDefault() {
        String before = System.getProperty("oriel.layout.flex");
        String traceBefore = System.getProperty("oriel.layout.flex.trace");
        String fileBefore = System.getProperty("oriel.layout.traceFile");
        System.setProperty("oriel.layout.flex", "new");
        System.clearProperty("oriel.layout.traceFile");
        try {
            String html = "<div style='display:flex;width:100px'><div id=a style='flex:1;max-width:20px'></div><div id=b style='flex:1'></div></div>";
            System.setProperty("oriel.layout.flex.trace", "off");
            TestHost quiet = new TestHost();
            quiet.load(html).doc.close();
            assertTrue(quiet.logs.stream().noneMatch(s -> s.contains("why-size")));
            System.setProperty("oriel.layout.flex.trace", "all");
            TestHost host = new TestHost();
            host.load(html).doc.close();
            assertTrue(host.logs.stream().anyMatch(s -> s.contains("\"phase\":\"freeze\"")));
            assertTrue(host.logs.stream().anyMatch(s -> s.contains("why-position") && s.contains("div#a")));
            for (String log : host.logs) {
                if (!log.contains("\"context\":\"flex\"")) continue;
                var event = com.google.gson.JsonParser.parseString(log.substring(log.indexOf('{'))).getAsJsonObject();
                assertEquals(1, event.get("schema").getAsInt());
                assertTrue(event.has("baseSize"));
            }
        } finally {
            restore(before);
            if (traceBefore == null) System.clearProperty("oriel.layout.flex.trace");
            else System.setProperty("oriel.layout.flex.trace", traceBefore);
            if (fileBefore == null) System.clearProperty("oriel.layout.traceFile");
            else System.setProperty("oriel.layout.traceFile", fileBefore);
        }
    }

    private static void restore(String value) {
        if (value == null) System.clearProperty("oriel.layout.flex");
        else System.setProperty("oriel.layout.flex", value);
    }
}
