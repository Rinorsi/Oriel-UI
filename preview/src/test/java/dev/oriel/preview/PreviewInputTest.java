package dev.oriel.preview;

import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import javax.swing.JPanel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Command-line options and key mapping. */
class PreviewInputTest {
    @Test
    void propertySummaryReportsValuesInsteadOfMissingSelectorCounts() {
        String summary = Preview.explanationSummary(java.util.Map.of(
                "kind", "cascade-explanation", "element", "div#a", "property", "width",
                "baseValue", "12px", "usedValue", "14px"));
        assertTrue(summary.contains("width"));
        assertTrue(summary.contains("base=12px"));
        assertTrue(summary.contains("used=14px"));
        assertTrue(!summary.contains("candidates"));
    }

    @Test
    void selectorExplanationIsHeadlessAndNeedsTwoQueries() {
        var options = Preview.Options.parse("a.html", "--why", ".on", "#card", "--frames", "4");
        assertTrue(options.headless());
        assertEquals(".on", options.why());
        assertEquals("#card", options.elementQuery());
        assertEquals(4, options.frames());
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("a.html", "--why", ".on"));
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("--canvas-test", "--why", ".on", "#card"));
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("a.html", "--why", ".on", "#card", "--snapshot", "out.png"));
    }

    @Test
    void parsesInputDiagnosticActionsAndRejectsMissingArguments() {
        Actions.parse("trace-input on\npreedit 中文\npreedit\nwhy-no-click #name\nfocus-info #name\ntrace-input off");
        assertThrows(IllegalArgumentException.class, () -> Actions.parse("trace-input yes"));
        assertThrows(IllegalArgumentException.class, () -> Actions.parse("why-no-click"));
        assertThrows(IllegalArgumentException.class, () -> Actions.parse("focus-info"));
    }

    @Test
    void parsesOptions() {
        Preview.Options options = Preview.Options.parse("ui/menu.html", "--scale", "3", "--size", "320x200", "--snapshot", "out.png", "--frames", "5");
        assertEquals(new Preview.Options(Path.of("ui/menu.html"), false, 3, 320, 200, null, Path.of("out.png"), 5, null, null, false, false, null, null, "zh_cn", null, null, false, null, null), options);
        assertEquals(new Preview.Options(null, true, 2, 427, 240, null, null, 1, null, null, false, false, null, null, "zh_cn", null, null, false, null, null), Preview.Options.parse("--canvas-test"));
        assertTrue(Preview.Options.parse("a.html", "--narrate").narrate());
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("--canvas-test", "--narrate"));
        Preview.Options scripted = Preview.Options.parse("ui/menu.html", "--actions", "a.txt", "--snapshot", "shots/end.png");
        assertEquals(Path.of("a.txt"), scripted.actions());
        assertEquals(Path.of("shots/end.png").toAbsolutePath().getParent(), scripted.shots(), "shots go beside the snapshot");
        assertEquals(Path.of("o"), Preview.Options.parse("a.html", "--actions", "a.txt", "--out", "o").shots());
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("--canvas-test", "--actions", "a.txt"));
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse());
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("a.html", "--size", "big"));
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("a.html", "--scale"));
        assertThrows(IllegalArgumentException.class, () -> Preview.Options.parse("a.html", "b.html"));
    }

    @Test
    void mapsKeysToDomNames() {
        assertEquals(new DomKeys.Key("a", "KeyA"), DomKeys.of(key(KeyEvent.VK_A, 'a', 0, KeyEvent.KEY_LOCATION_STANDARD)));
        assertEquals(new DomKeys.Key("A", "KeyA"), DomKeys.of(key(KeyEvent.VK_A, 'A', InputEvent.SHIFT_DOWN_MASK, KeyEvent.KEY_LOCATION_STANDARD)));
        // Ctrl+A types a control character; the DOM key is still "a".
        assertEquals(new DomKeys.Key("a", "KeyA"), DomKeys.of(key(KeyEvent.VK_A, '\u0001', InputEvent.CTRL_DOWN_MASK, KeyEvent.KEY_LOCATION_STANDARD)));
        assertEquals(new DomKeys.Key("Enter", "Enter"), DomKeys.of(key(KeyEvent.VK_ENTER, '\n', 0, KeyEvent.KEY_LOCATION_STANDARD)));
        assertEquals(new DomKeys.Key(" ", "Space"), DomKeys.of(key(KeyEvent.VK_SPACE, ' ', 0, KeyEvent.KEY_LOCATION_STANDARD)));
        assertEquals(new DomKeys.Key("Shift", "ShiftRight"),
                DomKeys.of(key(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED, InputEvent.SHIFT_DOWN_MASK, KeyEvent.KEY_LOCATION_RIGHT)));
        assertEquals(new DomKeys.Key("/", "Slash"), DomKeys.of(key(KeyEvent.VK_SLASH, '/', 0, KeyEvent.KEY_LOCATION_STANDARD)));
        assertEquals(new DomKeys.Key("F5", "F5"), DomKeys.of(key(KeyEvent.VK_F5, KeyEvent.CHAR_UNDEFINED, 0, KeyEvent.KEY_LOCATION_STANDARD)));
    }

    private static KeyEvent key(int code, char c, int modifiers, int location) {
        return new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, 0, modifiers, code, c, location);
    }
}
