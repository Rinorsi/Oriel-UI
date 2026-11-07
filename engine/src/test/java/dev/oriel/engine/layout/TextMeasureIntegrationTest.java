package dev.oriel.engine.layout;

import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.FontSpec;
import dev.oriel.engine.style.ComputedStyle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextMeasureIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"old", "new", "both"})
    void reloadAndProviderEpochInvalidateAllMeasurementConsumers(String mode) {
        String previous = System.getProperty("oriel.inputBackend");
        System.setProperty("oriel.inputBackend", mode);
        try {
            class Fonts implements FontMetrics {
                float advance = 5;
                long epoch;
                public float width(String text, FontSpec font) { return text.length() * advance; }
                public long generation() { return epoch; }
            }
            Fonts fonts = new Fonts();
            TextMeasure measure = new TextMeasure(fonts);
            FontSpec font = new FontSpec(List.of("test"), 8, false, false);
            ComputedStyle style = new ComputedStyle();
            assertEquals(10, measure.width("ab", font));
            assertEquals(5, measure.advance('a', font, style));
            long initial = measure.generation();
            fonts.advance = 7;
            measure.invalidate();
            assertNotEquals(initial, measure.generation());
            assertEquals(14, measure.width("ab", font));
            assertEquals(7, measure.advance('a', font, style));
            long reloaded = measure.generation();
            fonts.advance = 9;
            fonts.epoch++;
            assertNotEquals(reloaded, measure.generation());
            assertEquals(18, measure.width("ab", font));
            assertEquals(9, measure.advance('a', font, style));
            long oldProvider = measure.generation();
            measure.invalidate((text, spec) -> text.length() * 11);
            assertNotEquals(oldProvider, measure.generation());
            assertEquals(22, measure.width("ab", font));
            assertEquals(11, measure.advance('a', font, style));
        } finally {
            if (previous == null) System.clearProperty("oriel.inputBackend");
            else System.setProperty("oriel.inputBackend", previous);
        }
    }
}
