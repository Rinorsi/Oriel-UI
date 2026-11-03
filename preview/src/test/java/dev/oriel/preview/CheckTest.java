package dev.oriel.preview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
