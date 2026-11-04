package dev.oriel.engine.css;

import dev.oriel.engine.style.ClipShape;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.Filter;
import org.junit.jupiter.api.Test;

import static dev.oriel.engine.css.StyleEngine.computedValue;
import static dev.oriel.engine.testing.Page.styleOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** filter, backdrop-filter, clip-path, masks, blend modes and conic gradients, as values. */
class EffectsTest {
    @Test
    void filtersParseInOrder() {
        ComputedStyle s = styleOf("filter: blur(2px) brightness(150%) drop-shadow(1px 2px 3px red) hue-rotate(90deg)");
        assertEquals("blur(2px) brightness(1.5) drop-shadow(rgb(255, 0, 0) 1px 2px 3px) hue-rotate(90deg)", computedValue(s, "filter"));
        assertEquals(Filter.Kind.DROP_SHADOW, s.filter.get(2).kind());
        assertTrue(s.createsStackingContext(), "a filter makes a stacking context");
        assertEquals("none", computedValue(styleOf("filter: url(#f)"), "filter"), "url() filters are invalid");
        assertEquals("blur(4px)", computedValue(styleOf("-webkit-backdrop-filter: blur(4px)"), "backdrop-filter"));
    }

    @Test
    void colourMatricesFollowFilterEffects() {
        float[] grey = Filter.of(Filter.Kind.GRAYSCALE, 1).matrix();
        assertEquals(0.2126f, grey[0], 1e-4f);
        assertEquals(0.7152f, grey[6], 1e-4f);
        float[] none = Filter.then(Filter.of(Filter.Kind.BRIGHTNESS, 2).matrix(), Filter.of(Filter.Kind.BRIGHTNESS, 0.5f).matrix());
        assertArrayEquals(Filter.identity(), none, 1e-5f, "brightness 2 then 0.5 is no change");
    }

    @Test
    void clipPathShapes() {
        ComputedStyle s = styleOf("clip-path: polygon(0 0, 100% 0, 50% 100%)");
        assertEquals("polygon(0px 0px, 100% 0px, 50% 100%)", computedValue(s, "clip-path"));
        assertArrayEquals(new float[] {10, 20, 110, 20, 60, 70}, s.clipPath.outline(10, 20, 100, 50), 1e-4f);
        float[] circle = styleOf("clip-path: circle(10px at 50% 50%)").clipPath.outline(0, 0, 40, 40);
        assertEquals(ClipShape.CIRCLE_SEGMENTS * 2, circle.length);
        assertEquals(30, circle[0], 1e-4f, "the first point is on the right, 10px out");
        assertEquals("inset(1px 2px 1px 2px round 3px)", computedValue(styleOf("-webkit-clip-path: inset(1px 2px round 3px)"), "clip-path"));
        assertEquals(ClipShape.NONE, styleOf("clip-path: path('M0 0')").clipPath, "path() is not supported");
    }

    @Test
    void maskShorthandsAndWebkitNames() {
        ComputedStyle s = styleOf("-webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0); -webkit-mask-composite: xor");
        assertEquals(2, s.maskImage.size());
        assertEquals("content-box, border-box", computedValue(s, "mask-clip"));
        assertEquals("exclude", computedValue(s, "mask-composite"));
        assertTrue(styleOf("mask: url(a.png) center / cover").maskImage.isEmpty(), "positions and sizes are not supported: invalid");
        assertFalse(styleOf("mask-image: radial-gradient(#000, transparent)").maskImage.isEmpty());
    }

    @Test
    void blendAndIsolation() {
        assertEquals("multiply", computedValue(styleOf("mix-blend-mode: multiply"), "mix-blend-mode"));
        assertTrue(styleOf("isolation: isolate").createsStackingContext());
        assertFalse(styleOf("isolation: auto").createsStackingContext());
    }

    @Test
    void conicGradients() {
        assertEquals("conic-gradient(from 90deg at 50% 50%, rgb(255, 0, 0) 0px, rgb(0, 0, 255) 25%)",
                computedValue(styleOf("background-image: conic-gradient(from 90deg, red 0, blue 90deg)"), "background-image"));
        assertEquals("repeating-conic-gradient(from 0deg at 25% 75%, rgb(0, 0, 0), rgb(255, 255, 255) 10%)",
                computedValue(styleOf("background-image: repeating-conic-gradient(at 25% 75%, black, white 10%)"), "background-image"));
        assertEquals("none", computedValue(styleOf("background-image: conic-gradient(red 10px, blue)"), "background-image"),
                "lengths are not angles");
    }
}
