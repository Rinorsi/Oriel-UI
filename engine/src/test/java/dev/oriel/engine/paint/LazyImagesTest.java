package dev.oriel.engine.paint;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LazyImagesTest {
    private static final class ImagesHost extends TestHost {
        final Set<String> requested = new LinkedHashSet<>();
        final java.util.List<String> prepared = new java.util.ArrayList<>();

        @Override public void prepareImage(String url) { prepared.add(url); }

        @Override public float[] imageSize(String url) {
            requested.add(url);
            return new float[] {80, 40};
        }
    }

    private static String images(int count) {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < count; i++) html.append("<img loading='lazy' id='i").append(i)
                .append("' src='").append(i).append(".png' style='display:block;width:80px;height:40px'>");
        return html.toString();
    }

    @Test void twoHundredImagesWaitForLayoutAndRespectBatchesAndScrolling() {
        ImagesHost host = new ImagesHost();
        Document doc = Document.parse(host, "test:page.html", "<body style='margin:0;height:240px;overflow:auto'>" + images(200),
                null, new Viewport(320, 240, 1));
        assertTrue(host.requested.isEmpty(), "parsing must not read lazy images");
        doc.frame(0);
        assertEquals(3, host.requested.size());
        assertEquals(3, host.prepared.size());
        float[] bounds = doc.getElementById("i4").getBoundingClientRect();
        doc.frame(49);
        assertEquals(3, host.requested.size());
        doc.frame(50);
        assertEquals(6, host.requested.size());
        assertEquals(6, host.prepared.size());
        doc.frame(100);
        doc.frame(150);
        assertEquals(9, host.requested.size(), "one half-screen of prefetch");
        assertArrayEquals(bounds, doc.getElementById("i4").getBoundingClientRect(), 0.001f);
        doc.getElementById("i100").scrollIntoView(dev.oriel.engine.dom.Element.ScrollAlign.START,
                dev.oriel.engine.dom.Element.ScrollAlign.NEAREST, dev.oriel.engine.dom.Element.ScrollBehavior.INSTANT);
        doc.frame(200);
        assertEquals(12, host.requested.size());
        assertFalse(host.requested.contains("test:199.png"));
        assertNull(doc.error());
    }

    @Test void eagerAndDefaultStillReadImmediately() {
        ImagesHost host = new ImagesHost();
        Document.parse(host, "test:page.html", "<img src='e.png' loading='eager'><img src='d.png'>");
        assertEquals(Set.of("test:e.png", "test:d.png"), host.requested);
    }

    @Test void switchingToEagerLoadsOffscreenAndUpdatesNaturalSize() {
        ImagesHost host = new ImagesHost();
        Page page = host.load("<img id=i loading=lazy src=far.png style='position:absolute;top:2000px'>");
        assertTrue(host.requested.isEmpty());
        page.byId("i").setAttribute("loading", "eager");
        page.frame(10);
        assertEquals(Set.of("test:far.png"), host.requested);
        assertEquals(80, page.byId("i").box.width, 0.001f);
        assertEquals(40, page.byId("i").box.height, 0.001f);
    }

    @Test void nestedScrollportsHiddenElementsAndRemovalDoNotLoad() {
        ImagesHost host = new ImagesHost();
        Page page = host.load("""
                <body style='margin:0'><div id=s style='width:120px;height:80px;overflow:auto'>
                <div style='height:400px'></div><img id=far loading=lazy src=far.png width=80 height=40>
                </div><div style='display:none'><img loading=lazy src=hidden.png></div>
                <img id=removed loading=lazy src=removed.png style='position:absolute;top:2000px;width:80px;height:40px'>
                """);
        assertTrue(host.requested.isEmpty());
        page.byId("removed").remove();
        page.byId("s").scrollTo(0, 400);
        page.frame(50);
        assertEquals(Set.of("test:far.png"), host.requested);
        page.doc.close();
        page.frame(100);
        assertEquals(1, host.requested.size());
    }

    @Test void backgroundsShareBudgetWithImagesAndNeverBypassItInPaint() {
        ImagesHost host = new ImagesHost();
        Page page = host.load("""
                <html data-oriel-lazy-budget=1><body style='margin:0'>
                <div data-oriel-lazy style='width:80px;height:40px;background-image:url(a.png),url(b.png)'></div>
                <img loading=lazy src=c.png width=80 height=40>
                <div data-oriel-lazy style='position:absolute;top:2000px;width:80px;height:40px;background-image:url(far.png)'></div>
                """);
        assertEquals(Set.of("test:a.png"), host.requested);
        assertEquals(1, page.paint().ops("drawImage").size());
        assertEquals(1, host.requested.size());
        page.frame(50).paint();
        assertEquals(2, host.requested.size());
        page.frame(100).paint();
        assertEquals(Set.of("test:a.png", "test:b.png", "test:c.png"), host.requested);
    }

    @Test void aChangedSourceWaitsForItsOwnBudgetAndDetachedSourcesStayCancelled() {
        ImagesHost host = new ImagesHost();
        Page page = host.load("<html data-oriel-lazy-budget=1><body>" + images(4));
        page.byId("i0").setAttribute("src", "changed.png");
        page.byId("i1").remove();
        page.frame(10).paint();
        assertEquals(Set.of("test:0.png"), host.requested);
        page.frame(50).paint();
        assertEquals(Set.of("test:0.png", "test:changed.png"), host.requested);
        page.frame(100).paint();
        assertFalse(host.requested.contains("test:1.png"));
    }

    @Test void idleHostsKeepPendingBatchesAliveAndNewNodesJoinTheQueue() {
        ImagesHost host = new ImagesHost();
        Page page = host.load("<html data-oriel-lazy-budget=1><body>" + images(2));
        page.paint();
        assertTrue(page.doc.needsFrame(50));
        assertFalse(page.doc.settled());
        page.frame(50).paint();
        assertFalse(page.doc.needsFrame(100));
        assertTrue(page.doc.settled());
        var image = page.doc.createElement("img");
        image.setAttribute("loading", "lazy");
        image.setAttribute("src", "new.png");
        image.setAttribute("width", "80");
        image.setAttribute("height", "40");
        page.doc.body().appendChild(image);
        assertEquals(2, host.prepared.size());
        page.frame(100);
        assertEquals(3, host.prepared.size());
    }

    @Test void horizontalScrollAndHiddenAncestorWaitForVisibility() {
        ImagesHost host = new ImagesHost();
        Page page = host.load("""
                <body style='margin:0'><div id=s style='width:100px;height:80px;overflow:auto'>
                  <div style='width:600px;height:50px;position:relative'>
                    <img loading=lazy src=right.png width=80 height=40 style='position:absolute;left:400px'>
                  </div></div>
                <div id=hidden style='opacity:0'><img loading=lazy src=hidden.png width=80 height=40></div>
                """);
        assertTrue(host.requested.isEmpty());
        page.byId("s").scrollTo(400, 0);
        page.frame(50);
        assertEquals(Set.of("test:right.png"), host.requested);
        page.byId("hidden").setAttribute("style", "opacity:1");
        page.frame(100);
        assertEquals(Set.of("test:right.png", "test:hidden.png"), host.requested);
    }

    @Test void invalidBudgetReportsTheSourceInsteadOfSilentlyChangingPolicy() {
        ImagesHost host = new ImagesHost();
        Page page = host.load("<html data-oriel-lazy-budget=0><body>" + images(5));
        assertEquals(3, host.prepared.size());
        assertTrue(host.logs.stream().anyMatch(s -> s.contains("lazy-image-budget") && s.contains("test:page.html")));
        assertNull(page.doc.error());
    }

    @Test void backgroundSourceChangesAndLateOptInUseTheSameQueue() {
        ImagesHost host = new ImagesHost();
        Page page = host.load("""
                <body style='margin:0'>
                <div id=bg data-oriel-lazy style='width:80px;height:40px;background-image:url(a.png)'></div>
                <div id=late style='width:80px;height:40px'></div>
                """);
        page.byId("bg").setAttribute("style", "width:80px;height:40px;background-image:url(b.png)");
        page.byId("late").setAttribute("data-oriel-lazy", "");
        page.byId("late").setAttribute("style", "width:80px;height:40px;background-image:url(c.png)");
        page.frame(10);
        assertTrue(page.paint().ops("drawImage").isEmpty());
        assertEquals(Set.of("test:a.png"), host.requested);
        page.frame(50).paint();
        assertEquals(Set.of("test:a.png", "test:b.png", "test:c.png"), host.requested);
    }

    @Test void visibleChildCanOverrideInheritedHiddenVisibility() {
        ImagesHost host = new ImagesHost();
        Page page = host.load("""
                <div style='visibility:hidden'>
                  <img loading=lazy src=visible.png width=80 height=40 style='visibility:visible'>
                  <img loading=lazy src=hidden.png width=80 height=40>
                </div>
                """);
        assertEquals(Set.of("test:visible.png"), host.requested);
        assertEquals(1, page.paint().ops("drawImage").size());
    }
}
