package dev.oriel.engine.html;

import dev.oriel.engine.parse.ParserAudit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParserSelectionTest {
    @Test
    void newParserIsDefaultAndUnknownModeIsAnError() {
        String previous = System.getProperty("oriel.parser");
        try {
            System.clearProperty("oriel.parser");
            assertEquals(ParserAudit.Mode.NEW, ParserAudit.mode());
            System.setProperty("oriel.parser", "typo");
            assertThrows(IllegalArgumentException.class, ParserAudit::mode);
            for (String mode : new String[] {"old", "both"}) {
                System.setProperty("oriel.parser", mode);
                assertThrows(IllegalStateException.class, ParserAudit::mode, "retired modes must not pretend to dual-run");
            }
        } finally {
            if (previous == null) System.clearProperty("oriel.parser"); else System.setProperty("oriel.parser", previous);
        }
    }
}
