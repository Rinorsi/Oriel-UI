package dev.oriel.engine.script;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** Opt into the WIP bindings only for each ecosystem scenario. */
abstract class EcosystemModeTestSupport {
    private String previous;

    @BeforeEach
    void enableEcosystem() {
        previous = System.getProperty("oriel.ecosystem");
        System.setProperty("oriel.ecosystem", "new");
    }

    @AfterEach
    void restoreEcosystem() {
        if (previous == null) System.clearProperty("oriel.ecosystem");
        else System.setProperty("oriel.ecosystem", previous);
    }
}
