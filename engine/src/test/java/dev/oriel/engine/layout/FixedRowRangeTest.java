package dev.oriel.engine.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedRowRangeTest {
    @Test
    void rangeIncludesPartialRowsAndOverscanAndPreservesScrollExtent() {
        FixedRowRange range = FixedRowRange.calculate(1000, 20, 205, 50, 2);
        assertEquals(8, range.from());
        assertEquals(15, range.to());
        assertEquals(160, range.leading());
        assertEquals(19700, range.trailing());
        assertEquals(20000, range.leading() + (range.to() - range.from()) * 20 + range.trailing());
    }

    @Test
    void clampsEndAndRejectsInvalidGeometry() {
        assertEquals(1000, FixedRowRange.calculate(1000, 20, 999999, 50, 2).to());
        assertEquals(0, FixedRowRange.calculate(0, 20, 200, 50, 2).to());
        assertEquals(0, FixedRowRange.calculate(1000, 20, 200, 0, 2).to());
        assertThrows(IllegalArgumentException.class, () -> FixedRowRange.calculate(10, 0, 0, 100, 2));
        assertThrows(IllegalArgumentException.class, () -> FixedRowRange.calculate(10, 20, Double.NaN, 100, 2));
        assertThrows(IllegalArgumentException.class, () -> FixedRowRange.calculate(-1, 20, 0, 100, 2));
        assertThrows(IllegalArgumentException.class, () -> FixedRowRange.calculate(10, 20, 0, 100, -1));
    }
}
