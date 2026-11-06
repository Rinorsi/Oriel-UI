package dev.oriel.engine.replaced;

import dev.oriel.engine.host.ArraySurface;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Pixel oracle and allocation budget for repeated particle atlas blits. */
class CanvasBlitTest {
    private static ArraySurface surface(Page page, String id) {
        return (ArraySurface) ((CanvasContent) page.doc.replacedContent(page.byId(id))).surface();
    }

    @Test
    void clippedScaledAndOverlappingBlitsMatchReference() {
        Page page = new TestHost().load("<canvas id=s width=19 height=17></canvas><canvas id=d width=19 height=17></canvas>");
        Context2D source = new Context2D(page.byId("s")), target = new Context2D(page.byId("d"));
        Random random = new Random(759103);
        for (int trial = 0; trial < 600; trial++) {
            boolean self = trial % 3 == 0;
            int[] src = surface(page, "s").pixels(), dst = surface(page, "d").pixels();
            for (int i = 0; i < src.length; i++) {
                src[i] = random.nextInt();
                dst[i] = trial % 4 == 0 ? 0 : random.nextInt();
            }
            float sx = random.nextFloat() * 24 - 5, sy = random.nextFloat() * 22 - 5;
            float sw = random.nextFloat() * 28 - 7, sh = random.nextFloat() * 26 - 7;
            float dx = random.nextFloat() * 24 - 5, dy = random.nextFloat() * 22 - 5;
            float dw = random.nextFloat() * 28 - 7, dh = random.nextFloat() * 26 - 7;
            float alpha = switch (trial % 4) { case 0 -> 0; case 1 -> 1; default -> random.nextFloat(); };
            int[] expected = dst.clone();
            reference(self ? dst.clone() : src.clone(), expected, 19, 17, sx, sy, sw, sh, dx, dy, dw, dh, alpha);
            target.setGlobalAlpha(alpha);
            target.drawImage(self ? target : source, sx, sy, sw, sh, dx, dy, dw, dh);
            assertArrayEquals(expected, dst, "trial " + trial);
        }
    }

    @Test
    void changingSourceAndResizingNeverReusesStalePixels() {
        Page page = new TestHost().load("<canvas id=s width=4 height=4></canvas><canvas id=d width=4 height=4></canvas>");
        Context2D source = new Context2D(page.byId("s")), target = new Context2D(page.byId("d"));
        source.setFillStyle("red");
        source.fillRect(0, 0, 4, 4);
        target.drawImage(source, 0, 0, 4, 4, 0, 0, 4, 4);
        assertEquals(0xffff0000, surface(page, "d").pixels()[0]);
        page.byId("s").setAttribute("width", "2");
        source.setFillStyle("blue");
        source.fillRect(0, 0, 2, 4);
        target.drawImage(source, 0, 0, 2, 4, 0, 0, 4, 4);
        for (int pixel : surface(page, "d").pixels()) assertEquals(0xff0000ff, pixel);
    }

    @Test
    void largeSelfCopyUsesAnIndependentSnapshot() {
        Page page = new TestHost().load("<canvas id=d width=300 height=260></canvas>");
        Context2D target = new Context2D(page.byId("d"));
        int[] dst = surface(page, "d").pixels();
        Random random = new Random(4);
        for (int i = 0; i < dst.length; i++) dst[i] = random.nextInt();
        int[] expected = dst.clone();
        reference(dst.clone(), expected, 300, 260, 0, 0, 300, 250, 0, 7, 300, 250, .6f);
        target.setGlobalAlpha(.6f);
        target.drawImage(target, 0, 0, 300, 250, 0, 7, 300, 250);
        assertArrayEquals(expected, dst);
    }

    @Test
    void transparentAtlasMarginsAndHolesPreserveTheDestination() {
        Page page = new TestHost().load("<canvas id=s width=19 height=17></canvas><canvas id=d width=19 height=17></canvas>");
        Context2D source = new Context2D(page.byId("s")), target = new Context2D(page.byId("d"));
        int[] src = surface(page, "s").pixels(), dst = surface(page, "d").pixels();
        java.util.Arrays.fill(src, 0x00ff00ff);
        java.util.Arrays.fill(dst, 0x6055aabb);
        src[19 * 4 + 6] = 0x80ff3300;
        src[19 * 4 + 9] = 0xffff3300;
        int[] expected = dst.clone();
        reference(src, expected, 19, 17, 0, 0, 19, 17, -1, -2, 24, 23, .7f);
        target.setGlobalAlpha(.7f);
        target.drawImage(source, 0, 0, 19, 17, -1, -2, 24, 23);
        assertArrayEquals(expected, dst);
    }

    @Test
    void sourceOverPreservesIntegerRounding() {
        Random random = new Random(6204);
        for (int sa = 0; sa < 256; sa++) for (int da = 0; da < 256; da++) {
            int src = sa << 24 | random.nextInt() & 0xffffff;
            int dst = da << 24 | random.nextInt() & 0xffffff;
            assertEquals(blend(src, dst), Context2D.over(src, dst));
        }
    }

    @Test
    void warmedBlitsDoNotAllocateAPixelArrayPerParticle() {
        var base = ManagementFactory.getThreadMXBean();
        assumeTrue(base instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) base;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        Page page = new TestHost().load("<canvas id=s width=64 height=64></canvas><canvas id=d width=128 height=128></canvas>");
        Context2D source = new Context2D(page.byId("s")), target = new Context2D(page.byId("d"));
        source.setFillStyle("#f8c06080");
        source.fillRect(0, 0, 64, 64);
        target.setGlobalAlpha(.7f);
        for (int i = 0; i < 3000; i++) target.drawImage(source, 0, 0, 64, 64, 0, 0, 96, 96);
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 1000; i++) target.drawImage(source, 0, 0, 64, 64, 0, 0, 96, 96);
        long bytes = bean.getCurrentThreadAllocatedBytes() - before;
        System.out.println("1000 atlas blits allocated " + bytes + " bytes");
        assertTrue(bytes < 256 * 1024, "particle blits allocated " + bytes + " bytes");
    }

    private static void reference(int[] src, int[] dst, int w, int h, float sx, float sy, float sw, float sh,
                                  float dx, float dy, float dw, float dh, float alpha) {
        if (sw == 0 || sh == 0) return;
        int sl = Math.max(0, Math.round(Math.min(sx, sx + sw))), sr = Math.min(w, Math.round(Math.max(sx, sx + sw)));
        int st = Math.max(0, Math.round(Math.min(sy, sy + sh))), sb = Math.min(h, Math.round(Math.max(sy, sy + sh)));
        int dl = Math.max(0, Math.round(Math.min(dx, dx + dw))), dr = Math.min(w, Math.round(Math.max(dx, dx + dw)));
        int dt = Math.max(0, Math.round(Math.min(dy, dy + dh))), db = Math.min(h, Math.round(Math.max(dy, dy + dh)));
        if (sr <= sl || sb <= st || dr <= dl || db <= dt) return;
        for (int y = dt; y < db; y++) for (int x = dl; x < dr; x++) {
            int tx = (int) Math.floor(sx + (x + .5f - dx) * (sw / dw));
            int ty = (int) Math.floor(sy + (y + .5f - dy) * (sh / dh));
            if (tx < sl || tx >= sr || ty < st || ty >= sb) continue;
            int color = src[ty * w + tx];
            if (alpha < 1) color = Math.round((color >>> 24) * alpha) << 24 | color & 0xffffff;
            dst[y * w + x] = blend(color, dst[y * w + x]);
        }
    }

    private static int blend(int src, int dst) {
        int sa = src >>> 24;
        if (sa == 255) return src;
        if (sa == 0) return dst;
        int da = (dst >>> 24) * (255 - sa) / 255, a = sa + da;
        int r = ((src >> 16 & 255) * sa + (dst >> 16 & 255) * da) / a;
        int g = ((src >> 8 & 255) * sa + (dst >> 8 & 255) * da) / a;
        int b = ((src & 255) * sa + (dst & 255) * da) / a;
        return a << 24 | r << 16 | g << 8 | b;
    }
}
