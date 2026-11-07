package dev.oriel.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Command line options of the previewer. */
class PreviewOptionsTest {
    @Test
    void cascadeExplanationDoesNotReinterpretAnExistingSelector() {
        var property = Preview.Options.parse("page.html", "--why-property", "::before/color", "#a");
        assertTrue(property.whyProperty());
        assertEquals("::before/color", property.why());
        assertFalse(Preview.Options.parse("page.html", "--why", "property:focus", "#a").whyProperty());
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("page.html",
                "--why", "div", "#a", "--why-property", "color", "#a"));
    }
    @Test
    void languageCanBeSelectedWithoutChangingTheChineseDefault() {
        assertEquals("zh_cn", Preview.Options.parse("page.html").language());
        assertEquals("en_us", Preview.Options.parse("page.html", "--lang", "en_us").language());
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("page.html", "--lang"));
    }

    @Test
    void backgroundAcceptsOpaqueCssColorsAndKeepsWorldDefault() {
        assertNull(Preview.Options.parse("page.html").background());
        assertEquals(0xFF123456, Preview.Options.parse("page.html", "--background", "#123456").background());
        assertEquals(0xFFFFFFFF, Preview.Options.parse("page.html", "--background", "white").background());
        assertEquals(0xFF010203, Preview.Options.parse("page.html", "--background", "rgb(1, 2, 3)").background());
        for (String invalid : new String[] {"garbage", "transparent", "#1234"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> Preview.Options.parse("page.html", "--background", invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("page.html", "--background"));
    }

    @Test
    void strictIsOffUnlessAsked() {
        assertFalse(Preview.Options.parse("page.html", "--snapshot", "out.png").strict());
        assertTrue(Preview.Options.parse("page.html", "--snapshot", "out.png", "--strict").strict());
    }
}
