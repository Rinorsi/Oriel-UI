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
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@code --check}: a clean page passes; a missing image fails; a CSS slip is listed. */
class CheckTest {
    @Test
    void keepsLegacyWrappingTextOverflowCheck(@TempDir Path dir) throws Exception {
        Path page = dir.resolve("word.html"), report = dir.resolve("report.json");
        Files.writeString(page, "<div style='font-size:8px;width:10px;overflow:hidden'>unbreakableword</div>");
        assertEquals(3, Check.run(page, "zh_cn", true, report));
        var results = com.google.gson.JsonParser.parseString(Files.readString(report)).getAsJsonArray();
        assertTrue(results.get(0).getAsJsonObject().getAsJsonArray("design").toString().contains("文字被截断"));
    }

    @Test
    void strictCheckingIncludesSmallViewportLayoutWarnings(@TempDir Path dir) throws Exception {
        Path page = dir.resolve("small.html"), report = dir.resolve("report.json");
        Files.writeString(page, "<style>div {font-size:8px;width:200px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}"
                + "@media (max-width:500px) {div {width:20px}}</style><div>Long item name</div>");
        assertEquals(3, Check.run(page, "zh_cn", true, report));
        var results = com.google.gson.JsonParser.parseString(Files.readString(report)).getAsJsonArray();
        assertFalse(results.get(0).toString().contains("text-ellipsis-tooltip"));
        assertTrue(results.get(1).toString().contains("text-ellipsis-tooltip"));
    }

    @Test
    void writesStructuredDiagnosticsAtBothViewports(@TempDir Path dir) throws Exception {
        Path page = dir.resolve("page.html"), report = dir.resolve("report.json");
        Files.writeString(page, "<style>p { colour: red; }</style><p>hello</p>");
        assertEquals(3, Preview.check(new String[]{"--check", page.toString(), "--strict", "--report", report.toString()}));
        var results = com.google.gson.JsonParser.parseString(Files.readString(report)).getAsJsonArray();
        assertEquals(2, results.size());
        assertEquals(640, results.get(0).getAsJsonObject().get("width").getAsInt());
        assertEquals(427, results.get(1).getAsJsonObject().get("width").getAsInt());
        var diagnostic = results.get(0).getAsJsonObject().getAsJsonArray("diagnostics").get(0).getAsJsonObject();
        assertTrue(diagnostic.has("source"));
        assertTrue(diagnostic.has("code"));
        assertTrue(diagnostic.has("line"));
        assertTrue(diagnostic.has("suggestion"));
    }

    @Test
    void strictCheckingRejectsWarningsButDefaultRemainsCompatible(@TempDir Path dir) throws Exception {
        Path page = dir.resolve("warning.html");
        Files.writeString(page, "<html><head><style>p { colour: red; }</style></head><body><p>x</p></body></html>");
        assertEquals(0, Check.run(page));
        assertEquals(3, Preview.check(new String[]{"--check", page.toString(), "--strict", "--lang", "en_us"}));
        assertEquals(3, Preview.check(new String[]{"--check", page.toString(), "--lang", "zh_cn", "--strict"}));
        Files.writeString(page, "<html><body><p>x</p></body></html>");
        assertEquals(0, Check.run(page, "zh_cn", true));
        Files.writeString(page, "<html><body><img src='missing.png'></body></html>");
        assertEquals(1, Check.run(page, "zh_cn", true));
    }

    @Test
    void checkingRejectsMissingOrUnknownArguments() {
        assertThrows(IllegalArgumentException.class, () -> Preview.check(new String[]{"--check"}));
        assertThrows(IllegalArgumentException.class, () -> Preview.check(new String[]{"--check", "page.html", "--lang"}));
        assertThrows(IllegalArgumentException.class, () -> Preview.check(new String[]{"--check", "page.html", "--lang", "--strict"}));
        assertThrows(IllegalArgumentException.class, () -> Preview.check(new String[]{"--check", "page.html", "--mystery"}));
    }

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
        assertTrue(report.contains("css: (640x360) "), report);
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
