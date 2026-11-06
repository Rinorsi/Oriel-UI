package dev.oriel.preview;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.preview.host.PreviewHost;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LazyImageLintTest {
    @TempDir Path dir;

    private List<Diagnostic> check(String html) throws Exception {
        try (MinecraftAssets assets = MinecraftAssets.open(List.of(dir), Optional.empty())) {
            PreviewHost host = new PreviewHost(assets, new MinecraftFont(assets));
            host.quiet(true);
            Document doc = Document.parse(host, dir.resolve("page.html").toString(), html);
            try {
                doc.frame(0);
                return PageLint.run(doc, host).stream().filter(d -> d.code().equals("images-without-lazy")).toList();
            } finally { doc.close(); }
        }
    }

    @Test void thresholdCountsImagesAndBackgroundLayersIncludingOffscreenOnes() throws Exception {
        String image = "<img src='icon.png' width=16 height=16>";
        assertTrue(check(image.repeat(24)).isEmpty());
        var warnings = check(image.repeat(25));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().line() > 0);
        assertTrue(warnings.getFirst().suggestion().contains("loading="));
        assertEquals(1, check(image.repeat(23) + "<div style='display:none;background-image:url(a.png),url(b.png)'></div>").size());
    }

    @Test void actualLazySourcesSuppressTheWarningButUnrelatedAttributesDoNot() throws Exception {
        String images = "<img src='icon.png' width=16 height=16>".repeat(25);
        assertTrue(check(images + "<img loading=lazy src='cover.png' width=80 height=40>").isEmpty());
        assertTrue(check(images + "<div data-oriel-lazy style='background-image:url(cover.png)'></div>").isEmpty());
        assertEquals(1, check("<div data-oriel-lazy></div>" + images).size());
        assertEquals(1, check("<div class=o-lazy>" + images + "</div>").size());
        assertEquals(1, check(images + "<img loading=eager src='cover.png'>").size());
    }
}
