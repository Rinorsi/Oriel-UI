package dev.oriel.mod.internal.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageScaleTest {
    @Test void automaticAndEveryExplicitScalePreservePixelPositions() {
        for (double vanilla : new double[] {1, 2, 3, 4, 5}) {
            for (int requested = 0; requested <= 4; requested++) {
                var scale = PageScale.resolve(requested, vanilla);
                assertEquals(requested == 0 ? vanilla : requested, scale.devicePixelRatio());
                for (double point : new double[] {-7, 0, 0.25, 87.75, 427, 1920})
                    assertEquals(point, scale.toGui(scale.toPage(point)), 0.000001);
                for (int size : new int[] {0, 1, 240, 427, 640, 1920}) {
                    assertTrue(scale.extent(size) >= 1);
                    assertTrue(scale.toGui(scale.extent(size)) >= size);
                    if (size > 0) assertTrue(scale.toGui(scale.extent(size) - 1) < size);
                }
            }
        }
    }
    @Test void invalidSettingsAreRejected() {
        for (int value : new int[] {-1, 5, 100, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> PageScale.resolve(value, 2));
        for (double value : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> PageScale.resolve(0, value));
    }
}
