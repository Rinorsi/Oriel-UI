package dev.oriel.engine.paint;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LayerRetentionTest {
    private record Target(long bytes, int lastUsed) {}
    @Test
    void keepsRecentBucketsWithinByteBudgetAndEvictsOversizedTargets() {
        Target old = new Target(40, 1), recent = new Target(60, 2), huge = new Target(1000, 3);
        assertEquals(List.of(huge, old), LayerRetention.evict(List.of(old, recent, huge), 64, Target::bytes, Target::lastUsed));
        assertEquals(List.of(huge), LayerRetention.evict(List.of(old, recent, huge), 100, Target::bytes, Target::lastUsed));
        assertThrows(IllegalArgumentException.class, () -> LayerRetention.evict(List.of(old), -1, Target::bytes, Target::lastUsed));
    }
}
