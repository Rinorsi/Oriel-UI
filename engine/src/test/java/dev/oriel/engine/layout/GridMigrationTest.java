package dev.oriel.engine.layout;

import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class GridMigrationTest {
    @Test
    void dedicatedReferencePageHasExpectedGeometry() throws Exception {
        String before = System.getProperty("oriel.layout.grid");
        System.setProperty("oriel.layout.grid", "new");
        try (var resource = getClass().getResourceAsStream("/grid-migration/reference.html")) {
            assertNotNull(resource);
            var page = new TestHost().load(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            BlockLayoutTest.assertRect(page.byId("baseline"), 0, 0, 80, 37);
            BlockLayoutTest.assertRect(page.byId("a"), 0, 2, 40, 20);
            BlockLayoutTest.assertRect(page.byId("b"), 40, 12, 40, 10);
            BlockLayoutTest.assertRect(page.byId("c"), 0, 60, 20, 10);
            BlockLayoutTest.assertRect(page.byId("d"), 20, 60, 60, 10);
            BlockLayoutTest.assertRect(page.byId("e"), 80, 60, 40, 10);
            BlockLayoutTest.assertRect(page.byId("f"), 0, 90, 80, 10);
            page.doc.close();
        } finally { restore(before); }
    }

    @Test
    void changingOnlyLineNamesInvalidatesLayoutWithoutChangingComputedText() {
        String before = System.getProperty("oriel.layout.grid");
        System.setProperty("oriel.layout.grid", "new");
        try {
            String prefix = "display:grid;grid-template-rows:10px;grid-template-columns:";
            var page = new TestHost().load("<div id=g style='" + prefix + "[target] 20px 30px'><div id=a style='grid-column:target'></div></div>");
            String computed = dev.oriel.engine.css.StyleEngine.computedValue(page.byId("g").style, "grid-template-columns");
            assertEquals(0, page.byId("a").box.x);
            page.byId("g").setAttribute("style", prefix + "20px [target] 30px");
            page.doc.flushLayout();
            assertEquals(20, page.byId("a").box.x);
            assertEquals(computed, dev.oriel.engine.css.StyleEngine.computedValue(page.byId("g").style, "grid-template-columns"));
            page.doc.close();
        } finally { restore(before); }
    }

    @Test
    void namedLinesSurviveIntegerAndAutomaticRepeats() {
        String before = System.getProperty("oriel.layout.grid");
        System.setProperty("oriel.layout.grid", "new");
        try {
            for (String repeat : new String[] {"2", "auto-fill"}) {
                var page = new TestHost().load("<div style='display:grid;width:120px;grid-template-columns:[left] 20px repeat("
                        + repeat + ",[cell] 30px [stop]) [right] 40px;grid-template-rows:10px'>"
                        + "<div id=a style='grid-column:left / cell'></div>"
                        + "<div id=b style='grid-column:cell / right'></div>"
                        + "<div id=c style='grid-column:right / -1'></div></div>");
                BlockLayoutTest.assertRect(page.byId("a"), 0, 0, 20, 10);
                BlockLayoutTest.assertRect(page.byId("b"), 20, 0, 60, 10);
                BlockLayoutTest.assertRect(page.byId("c"), 80, 0, 40, 10);
                page.doc.close();
            }
        } finally { restore(before); }
    }

    @Test
    void traceHasUnifiedEnvelopeAndPlacementResults() {
        String before = System.getProperty("oriel.layout.grid");
        String trace = System.getProperty("oriel.layout.grid.trace");
        System.setProperty("oriel.layout.grid", "new");
        System.setProperty("oriel.layout.grid.trace", "all");
        try {
            TestHost host = new TestHost();
            host.load("<div style='display:grid;grid-template-columns:20px 30px'><div>one</div><div>two</div></div>").doc.close();
            assertTrue(host.logs.stream().anyMatch(s -> s.contains("layout.grid.placement")));
            assertTrue(host.logs.stream().anyMatch(s -> s.contains("layout.grid.track")));
            assertTrue(host.logs.stream().anyMatch(s -> s.contains("layout.grid.position")));
            for (String log : host.logs) {
                if (!log.contains("layout.grid.")) continue;
                var event = com.google.gson.JsonParser.parseString(log.substring(log.indexOf('{'))).getAsJsonObject();
                assertEquals(1, event.get("schemaVersion").getAsInt());
                assertTrue(event.get("seq").getAsLong() > 0);
                assertTrue(event.has("source"));
                assertTrue(event.getAsJsonObject("data").has("reason"));
            }
        } finally {
            restore(before);
            if (trace == null) System.clearProperty("oriel.layout.grid.trace");
            else System.setProperty("oriel.layout.grid.trace", trace);
        }
    }

    @TestFactory
    Stream<DynamicTest> frozenFixtures() throws Exception {
        var fixtures = new TaffyFixtureTest();
        return Stream.of(fixtures.block(), fixtures.flex(), fixtures.grid()).flatMap(s -> s)
                .map(t -> DynamicTest.dynamicTest(t.getDisplayName(), () -> {
                    String before = System.getProperty("oriel.layout.grid");
                    System.setProperty("oriel.layout.grid", "both");
                    try { t.getExecutable().execute(); } finally { restore(before); }
                }));
    }

    @Test
    void baselineGroupIncludesDescentInAutomaticRowSize() {
        String before = System.getProperty("oriel.layout.grid");
        System.setProperty("oriel.layout.grid", "new");
        try {
            var page = new TestHost().load("""
                    <div id=grid style='display:grid;grid-template-columns:40px 40px;align-items:baseline'>
                      <div id=a style='height:20px;margin-top:2px;margin-bottom:3px'></div>
                      <div id=b style='height:10px;margin-bottom:15px'></div>
                    </div>
                    """);
            assertEquals(2, page.byId("a").box.y);
            assertEquals(12, page.byId("b").box.y);
            assertEquals(37, page.byId("grid").box.height);
            page.doc.close();
        } finally { restore(before); }
    }

    @Test
    void baselineIgnoresItemsWithAutoBlockMargins() {
        String before = System.getProperty("oriel.layout.grid");
        System.setProperty("oriel.layout.grid", "new");
        try {
            var page = new TestHost().load("""
                    <div style='display:grid;grid-template-columns:40px 40px;grid-template-rows:40px;align-items:baseline'>
                      <div id=a style='height:20px'></div>
                      <div id=b style='height:10px;margin-top:auto;margin-bottom:auto'></div>
                    </div>
                    """);
            assertEquals(0, page.byId("a").box.y);
            assertEquals(15, page.byId("b").box.y);
            page.doc.close();
        } finally { restore(before); }
    }

    private static void restore(String value) {
        if (value == null) System.clearProperty("oriel.layout.grid");
        else System.setProperty("oriel.layout.grid", value);
    }
}
