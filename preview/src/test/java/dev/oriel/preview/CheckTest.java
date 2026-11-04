package dev.oriel.preview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code --check}: a clean page passes; a missing image fails; a CSS slip is listed. */
class CheckTest {
    @Test
    void reportsMissingImagesAndCss(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("good.html"), "<html><body><div style=\"color: red\">ok</div></body></html>");
        Files.writeString(dir.resolve("bad.html"), "<html><head><style>.a { colour: red; }</style></head>"
                + "<body><img src=\"nowhere/missing.png\"><div class=\"a\">x</div></body></html>");
        PrintStream out = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int code;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            code = Check.run(dir);
        } finally {
            System.setOut(out);
        }
        String report = captured.toString(StandardCharsets.UTF_8);
        assertEquals(1, code, report);
        assertTrue(report.contains("FAIL bad.html"), report);
        assertTrue(report.contains("missing image"), report);
        assertTrue(report.contains("css: "), report);
        assertTrue(report.contains("OK   good.html"), report);
    }

    @Test
    void designRulesCatchCheapLooks(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("ugly.html"), "<html><head><style>.o-tile { padding: 4px; }</style></head><body>"
                + "<div class=\"o-tile\"><div class=\"o-tile\">inner</div></div>"
                + "<div style=\"font-size: 11px\">odd size</div>"
                + "<div style=\"font-family: oriel:kai; font-size: 13px\">outline fonts take any size</div>"
                + "<div style=\"font-family: oriel:pixel_12; font-size: 16px\">twelve-pixel type</div>"
                + "<div style=\"background-color: #303030; color: #3a3a3a\">faint</div></body></html>");
        PrintStream out = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            Check.run(dir);
        } finally {
            System.setOut(out);
        }
        String report = captured.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("WARN ugly.html"), report);
        assertTrue(report.contains("块里套块"), report);
        assertTrue(report.contains("字号 11px"), report);
        assertTrue(report.contains("字号 16px 不在 12/24"), report);
        assertFalse(report.contains("字号 13px"), report);
        assertTrue(report.contains("对比度"), report);
    }
}
