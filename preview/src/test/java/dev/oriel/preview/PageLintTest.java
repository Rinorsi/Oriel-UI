package dev.oriel.preview;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.preview.host.PreviewHost;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PageLintTest {
    @TempDir Path dir;

    private List<Diagnostic> lint(String html) throws Exception {
        try (MinecraftAssets assets = MinecraftAssets.open(List.of(dir), Optional.empty())) {
            PreviewHost host = new PreviewHost(assets, new MinecraftFont(assets));
            host.quiet(true);
            Document doc = Document.parse(host, dir.resolve("page.html").toString(),
                    "<style>html {font-size:8px} body {margin:0}</style>" + html, null, new Viewport(200, 200, 1));
            try {
                doc.frame(0);
                doc.frame(1000);
                assertNull(doc.error());
                return PageLint.run(doc, host);
            } finally {
                doc.close();
            }
        }
    }

    private long count(List<Diagnostic> issues, String code) {
        return issues.stream().filter(d -> d.code().equals(code)).count();
    }

    @Test void actualEllipsisNeedsTooltipAndCarriesSourceLocation() throws Exception {
        String text = "<div id=name style='width:25px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis'>Long item name</div>";
        var issues = lint(text);
        var issue = issues.stream().filter(d -> d.code().equals("text-ellipsis-tooltip")).findFirst().orElseThrow();
        assertTrue(issue.line() > 0);
        assertTrue(issue.column() > 0);
        assertTrue(issue.suggestion().contains("title"));
        assertEquals(Diagnostic.Severity.WARNING, issue.severity());
        assertEquals(0, count(lint("<div title='Long item name'>" + text + "</div>"), issue.code()));
        assertEquals(0, count(lint(text.replace("id=name", "title-json='{\"text\":\"Long item name\"}'")), issue.code()));
        assertEquals(0, count(lint(text.replace("25px", "190px")), issue.code()));
        assertEquals(0, count(lint(text.replace("id=name", "title='Long item name'")), issue.code()));
        assertEquals(1, count(lint(text.replace("id=name", "data-tip='Long item name'")), issue.code()));
        assertEquals(0, count(lint(text.replace("Long item name", "…")), issue.code()));
        assertEquals(0, count(lint("<style>#literal::before {content:'…'}</style>"
                + "<div id=literal style='width:100px;overflow:hidden;text-overflow:ellipsis'></div>"), issue.code()));
        assertEquals(1, count(lint("<div title='outer'>" + text.replace("id=name", "title=''") + "</div>"), issue.code()));
    }

    @Test void bundledExceptionUsesActualRepositoryPathsNotTheNamespace() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("gradlew"))) root = root.getParent();
        assertNotNull(root);
        assertTrue(PageLint.bundledPage(root.resolve("kit/src/main/resources/assets/oriel/ui/catalog.html")));
        Path external = dir.resolve("assets/oriel/ui/catalog.html");
        Files.createDirectories(external.getParent());
        Files.writeString(external, "<p>External page</p>");
        assertFalse(PageLint.bundledPage(external));
    }

    @Test void catchesOwnAndAncestorClippingButNotWrappingOrOffscreenRows() throws Exception {
        String text = "<div style='width:25px;overflow:hidden;white-space:nowrap'>Long item name</div>";
        assertEquals(1, count(lint(text), "text-clipped"));
        assertEquals(0, count(lint(text.replace("hidden", "visible")), "text-clipped"));
        assertEquals(0, count(lint(text.replace("nowrap", "normal")), "text-clipped"));
        assertEquals(1, count(lint("<div style='width:25px;overflow:hidden'><div style='width:100px;white-space:nowrap'>Custom badge</div></div>"), "text-clipped"));
        assertEquals(1, count(lint(text.replace("hidden", "auto")), "text-clipped"));
        assertEquals(0, count(lint("<div style='height:10px;overflow:auto'><div style='height:20px'></div>" + text + "</div>"), "text-clipped"));
        assertEquals(0, count(lint("<div style='display:none'>" + text + "</div>"), "text-clipped"));
    }

    @Test void completedEntranceAnimationDoesNotHideClipping() throws Exception {
        var issues = lint("<style>@keyframes enter {from {opacity:0;transform:scale(0.9)} to {opacity:1;transform:scale(1)}}</style>"
                + "<div style='animation:enter 100ms both'><div style='width:20px;overflow:hidden;white-space:nowrap'>Long badge</div></div>");
        assertEquals(1, count(issues, "text-clipped"));
    }

    @Test void squeezedFlexItemWarnsButUnshrunkAndEllipsizedItemsDoNot() throws Exception {
        String row = "<div style='display:flex;width:80px'><div id=tab style='width:100px;flex-shrink:1;overflow:hidden;white-space:nowrap'>"
                + "<span style='display:inline-block;width:16px;height:8px'></span>Long tab name<span>99</span></div><div style='width:40px;flex:none'></div></div>";
        assertEquals(1, count(lint(row), "flex-content-clipped"));
        assertEquals(1, count(lint(row.replace("width:80px", "width:80px;overflow:hidden")
                .replace("flex-shrink:1;overflow:hidden", "flex-shrink:1;min-width:0;overflow:visible")), "flex-content-clipped"));
        assertEquals(0, count(lint(row.replace("flex-shrink:1", "flex-shrink:0")), "flex-content-clipped"));
        assertEquals(0, count(lint(row.replace("white-space:nowrap", "white-space:nowrap;text-overflow:ellipsis")), "flex-content-clipped"));
        assertEquals(0, count(lint(row.replace("width:80px", "width:200px")), "flex-content-clipped"));
        assertEquals(1, count(lint(row.replace("width:100px;", "")), "flex-content-clipped"));
        assertEquals(0, count(lint(row.replace("width:80px", "width:200px").replace("width:100px;", "max-width:25px;")), "flex-content-clipped"));
    }

    @Test void repeatedRuleIsBoundedWithTotalCount() throws Exception {
        var issues = lint("<div style='width:20px;overflow:hidden;white-space:nowrap'>Long item</div>".repeat(15));
        assertEquals(PageLint.MAX_PER_RULE + 1, count(issues, "text-clipped"));
        assertTrue(issues.stream().anyMatch(d -> d.message().contains("15") && d.message().contains("汇总")));
    }

    @Test void flexClippingAlsoFindsReplacedContentWithoutText() throws Exception {
        String row = "<div style='display:flex;width:80px'><div style='width:100px;overflow:hidden'>"
                + "<canvas width=100 height=8 style='width:100px;height:8px'></canvas></div><div style='width:40px;flex:none'></div></div>";
        assertEquals(1, count(lint(row), "flex-content-clipped"));
        assertEquals(0, count(lint(row.replace("width:100px;overflow", "width:100px;flex:none;overflow")), "flex-content-clipped"));
    }

    @Test void pngHeaderLimitsUseStrictThresholdsWithoutDecodingPixels() throws Exception {
        Path image = dir.resolve("large.png");
        writeHeader(image, 2049, 8, 24);
        assertNotNull(PageLint.largePng(image));
        writeHeader(image, 8, 2049, 24);
        assertNotNull(PageLint.largePng(image));
        writeHeader(image, 2048, 2048, 1024 * 1024);
        assertNull(PageLint.largePng(image));
        writeHeader(image, 8, 8, 1024 * 1024 + 1);
        assertNotNull(PageLint.largePng(image));
        Files.writeString(image, "not a png");
        assertNull(PageLint.largePng(image));
    }

    @Test void referencedImagesIncludeRelativeFilesAndNamespacedBackgroundsOnce() throws Exception {
        writeHeader(dir.resolve("large.png"), 2049, 8, 24);
        Path texture = dir.resolve("assets/oriel/textures/large.png");
        Files.createDirectories(texture.getParent());
        writeHeader(texture, 8, 2049, 24);
        var issues = lint("<img src='large.png'><img src='large.png'><div style='background-image:url(oriel:textures/large.png);width:8px;height:8px'></div>");
        assertEquals(2, count(issues, "image-too-large"));
        assertEquals(2, count(lint("<div style='border-image-source:url(large.png);mask-image:url(oriel:textures/large.png)'></div>"), "image-too-large"));
        assertEquals(2, count(lint("<div style='background-image:url(large.png),url(oriel:textures/large.png)'></div>"), "image-too-large"));
        writeHeader(dir.resolve("small.png"), 8, 8, 24);
        assertEquals(0, count(lint("<img src='small.png'>"), "image-too-large"));
    }

    private static void writeHeader(Path path, int width, int height, int bytes) throws Exception {
        byte[] data = new byte[bytes];
        ByteBuffer.wrap(data).putLong(0x89504e470d0a1a0aL).putInt(13).putInt(0x49484452).putInt(width).putInt(height);
        Files.write(path, data);
    }
}
