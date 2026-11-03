package dev.oriel.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Command line options of the previewer. */
class PreviewOptionsTest {
    @Test
    void strictIsOffUnlessAsked() {
        assertFalse(Preview.Options.parse("page.html", "--snapshot", "out.png").strict());
        assertTrue(Preview.Options.parse("page.html", "--snapshot", "out.png", "--strict").strict());
    }
}
