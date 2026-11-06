package dev.oriel.engine.layout;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A constrained scrollport contributes its used size, while retaining the full scrollable content. */
class ScrollboxLayoutTest {
    static Stream<Arguments> containers() {
        return Stream.of(
                "display: block",
                "display: flex; flex-direction: column",
                "display: flex; flex-direction: row")
                .flatMap(parent -> Stream.of("auto", "scroll", "hidden")
                        .flatMap(overflow -> Stream.of("max-height", "height")
                                .flatMap(limit -> Stream.of("", "min-height: 0;")
                                        .map(min -> Arguments.of(parent, overflow, limit, min)))));
    }

    @ParameterizedTest
    @MethodSource("containers")
    void autoHeightUsesTheConstrainedScrollport(String parent, String overflow, String limit, String min) {
        Page page = new TestHost().load("""
                <div id=parent style="%s; padding: 6px">
                  <div id=scroller style="width: 100px; %s: 40px; overflow-y: %s; %s">
                    <div style="height: 96px"></div>
                  </div>
                </div><div id=after style="height: 10px"></div>
                """.formatted(parent, limit, overflow, min));
        assertEquals(40, page.byId("scroller").box.height, 0.01, "scrollport height");
        assertEquals(52, page.byId("parent").box.height, 0.01, "auto parent height");
        assertEquals(52, page.byId("after").getBoundingClientRect()[1], 0.01, "following sibling");
        assertScrollRange(page.byId("scroller"), 96, 56);
    }

    @Test
    void nestedAutoHeightContainersUseTheConstrainedContribution() {
        for (String middle : new String[]{"display: block", "display: flex; flex-direction: column"}) {
            Page page = new TestHost().load("""
                    <div id=outer style="display: flex; flex-direction: column; padding: 6px">
                      <div id=middle style="%s; padding: 4px">
                        <div id=scroller style="max-height: 40px; overflow: auto">
                          <div style="height: 96px"></div>
                        </div>
                      </div>
                    </div>
                    """.formatted(middle));
            assertEquals(48, page.byId("middle").box.height, 0.01, middle);
            assertEquals(60, page.byId("outer").box.height, 0.01, middle);
            assertScrollRange(page.byId("scroller"), 96, 56);
        }
    }

    @Test
    void blockParentContainsAColumnWithALimitedScrollport() {
        Page page = new TestHost().load("""
                <div id=outer style="padding: 6px">
                  <div id=parent style="display: flex; flex-direction: column">
                    <div style="max-height: 40px; overflow: auto">
                      <div style="height: 96px"></div>
                    </div>
                  </div>
                </div>
                """);
        assertEquals(40, page.byId("parent").box.height, 0.01);
        assertEquals(52, page.byId("outer").box.height, 0.01);
    }

    @ParameterizedTest
    @CsvSource({"flex-shrink: 0,40", "flex: none,40", "min-height: 60px,60"})
    void explicitFlexMinimumsAndNonShrinkingItemsStillRespectHeightLimits(String extra, float expected) {
        Page page = new TestHost().load("""
                <div id=parent style="display: flex; flex-direction: column">
                  <div id=scroller style="max-height: 40px; overflow: auto; %s">
                    <div style="height: 96px"></div>
                  </div>
                </div>
                """.formatted(extra));
        assertEquals(expected, page.byId("scroller").box.height, 0.01);
        assertEquals(expected, page.byId("parent").box.height, 0.01);
        assertScrollRange(page.byId("scroller"), 96, 96 - expected);
    }

    @Test
    void maxHeightLargerThanContentDoesNotForceGrowth() {
        Page page = new TestHost().load("""
                <div id=parent style="display: flex; flex-direction: column; padding: 6px">
                  <div id=scroller style="max-height: 100px; overflow: auto">
                    <div style="height: 20px"></div>
                  </div>
                </div>
                """);
        assertEquals(20, page.byId("scroller").box.height, 0.01);
        assertEquals(32, page.byId("parent").box.height, 0.01);
        assertScrollRange(page.byId("scroller"), 20, 0);
    }

    @Test
    void paddingBordersAndMarginsAreCountedOutsideTheLimitedContentBox() {
        Page page = new TestHost().load("""
                <div id=parent style="display: flex; flex-direction: column; padding: 6px; gap: 3px">
                  <div style="height: 10px"></div>
                  <div id=scroller style="box-sizing: content-box; max-height: 40px; overflow: auto;
                        padding: 4px; border: 2px solid; margin: 5px 0">
                    <div style="height: 96px"></div>
                  </div>
                </div>
                """);
        assertEquals(52, page.byId("scroller").box.height, 0.01);
        assertEquals(87, page.byId("parent").box.height, 0.01);
        assertScrollRange(page.byId("scroller"), 104, 56);
    }

    @Test
    void changingContentRelayoutsTheParentAndReclampsScrolling() {
        Page page = new TestHost().load("""
                <div id=parent style="display: flex; flex-direction: column; padding: 6px">
                  <div id=scroller style="max-height: 40px; overflow: auto">
                    <div id=content style="height: 96px"></div>
                  </div>
                </div>
                """);
        assertEquals(52, page.byId("parent").box.height, 0.01);
        assertScrollRange(page.byId("scroller"), 96, 56);
        page.byId("content").setAttribute("style", "height: 20px");
        page.frame();
        assertEquals(32, page.byId("parent").box.height, 0.01);
        assertScrollRange(page.byId("scroller"), 20, 0);
        page.byId("content").setAttribute("style", "height: 120px");
        page.frame();
        assertEquals(52, page.byId("parent").box.height, 0.01);
        assertScrollRange(page.byId("scroller"), 120, 80);
    }

    @Test
    void visibleOverflowStillEscapesTheConstrainedBox() {
        Page page = new TestHost().load("""
                <div id=parent style="display: flex; flex-direction: column">
                  <div id=limited style="max-height: 40px; overflow: visible">
                    <div style="height: 96px"></div>
                  </div>
                </div>
                """);
        assertEquals(40, page.byId("limited").box.height, 0.01);
        assertEquals(40, page.byId("parent").box.height, 0.01);
        assertEquals(96, page.byId("parent").scrollHeight(), 0.01);
    }

    private static void assertScrollRange(Element scroller, float height, float maxScroll) {
        assertEquals(height, scroller.scrollHeight(), 0.01, "full scrollable content");
        scroller.scrollTo(0, 10000);
        assertEquals(maxScroll, scroller.scrollTop(), 0.01, "can reach the bottom");
    }
}
