package dev.oriel.mod.internal.nested;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NestedCheckTest {
    @TempDir Path temporary;
    private static final String A = "a".repeat(64), B = "b".repeat(64);
    private static NestedManifest manifest(String hash, String api, Map<String, String> entries) {
        return new NestedManifest("0.12.0", "slim", "fabric", "26.3", hash, api, entries);
    }
    private static NestedCheck.Copy copy(String parent, String version, NestedManifest manifest) {
        return new NestedCheck.Copy(parent, parent, version, manifest);
    }

    @Test void resourceDifferenceNamesBothParentsButReferenceContentIsIgnored() {
        var active = copy("Backpack", "0.12.1", manifest(A, A, Map.of("assets/oriel/ui/ore.css", A)));
        var old = copy("Tools", "0.12.0", manifest(B, A, Map.of("assets/oriel/ui/ore.css", B)));
        var result = NestedCheck.compare(active, List.of(active, old));
        assertTrue(result.risk());
        assertTrue(result.summary().contains("Backpack"));
        assertTrue(result.summary().contains("Tools"));
        assertEquals(List.of("assets/oriel/ui/ore.css"), result.differences().get("Tools"));
        var reference = copy("Tools", "0.12.0", manifest(B, A, Map.of("assets/oriel/ui/templates/test.html", B,
                "assets/oriel/oriel/showcase/title.html", B, "assets/oriel/ui/styles.html", B,
                "assets/oriel/oriel/dev/test.html", B)));
        assertFalse(NestedCheck.compare(active, List.of(active, reference)).risk());
    }

    @Test void compareIsDirectionalAndApiChangesAreRisks() {
        var active = copy("Active", "0.12.1", manifest(A, A, Map.of("new", A)));
        assertFalse(NestedCheck.compare(active, List.of(copy("Old", "0.12.0", manifest(B, A, Map.of())))).risk());
        assertTrue(NestedCheck.compare(active, List.of(copy("Old", "0.12.0", manifest(B, B, Map.of())))).risk());
        assertTrue(NestedCheck.compare(active, List.of(copy("Old", "0.12.0", manifest(B, A, Map.of("missing", A))))).risk());
    }

    @Test void oldCopiesUseOnlyVersionAndEqualCoreVersionsDoNotWarn() {
        var active = copy("Active", "0.12.1", manifest(A, A, Map.of()));
        assertFalse(NestedCheck.compare(active, List.of(copy("Slim", "0.12.0", manifest(A, A, Map.of())))).risk());
        assertFalse(NestedCheck.compare(active, List.of(copy("Old", "0.12.1", null))).risk());
        assertTrue(NestedCheck.compare(active, List.of(copy("Old", "0.11.0", null))).risk());
    }

    @Test void fingerprintIsOrderIndependentAndTracksInstalledVersionsAndCoreHashes() {
        var first = copy("One", "0.12.0", manifest(A, A, Map.of()));
        var second = copy("Two", "0.12.1", manifest(A, A, Map.of()));
        assertEquals(NestedCheck.fingerprint(List.of("a=1", "b=2"), List.of(first, second)),
                NestedCheck.fingerprint(List.of("b=2", "a=1"), List.of(second, first)));
        assertNotEquals(NestedCheck.fingerprint(List.of("a=1"), List.of(first)), NestedCheck.fingerprint(List.of("a=2"), List.of(first)));
        assertNotEquals(NestedCheck.fingerprint(List.of(), List.of(first)), NestedCheck.fingerprint(List.of(),
                List.of(copy("One", "0.12.0", manifest(B, A, Map.of())))));
    }

    @Test void cacheSkipsScanAndNoticeHistorySurvivesRestartAndModSetChanges() throws Exception {
        var file = temporary.resolve("nested-check.json");
        var calls = new AtomicInteger();
        var active = copy("Active", "0.12.1", manifest(A, A, Map.of()));
        var old = copy("Old", "0.11.0", null);
        var cache = new NestedCache(file);
        var state = cache.check("stamp", List.of("mod=1"), () -> { calls.incrementAndGet(); return NestedCheck.compare(active, List.of(active, old)); });
        assertTrue(cache.shouldNotice(true));
        cache.markNoticed();
        var restarted = new NestedCache(file);
        assertEquals(state.fingerprint(), restarted.check("stamp", List.of("mod=1"), () -> { throw new AssertionError("rescanned"); }).fingerprint());
        assertFalse(restarted.shouldNotice(true));
        restarted.check("different", List.of("mod=2"), () -> NestedCheck.compare(active, List.of(active, old)));
        assertTrue(restarted.shouldNotice(true));
        assertFalse(restarted.shouldNotice(false));
        restarted.check("original-again", List.of("mod=1"), () -> NestedCheck.compare(active, List.of(active, old)));
        assertFalse(restarted.shouldNotice(true), "a previously noticed fingerprint stays noticed after reverting the mod set");
        assertEquals(1, calls.get());
    }

    @Test void incompleteCachedReportIsRejectedBeforeTheClientReadsIt() throws Exception {
        var file = temporary.resolve("bad-cache.json");
        java.nio.file.Files.writeString(file, "{\"installation\":\"x\",\"fingerprint\":\"x\",\"report\":{},\"noticed\":[]}");
        assertThrows(java.io.IOException.class, () -> new NestedCache(file));
    }

    @Test void validManifestParsesPackageHashesAndRejectsDuplicateEntries() {
        String json = "{\"schema\":1,\"version\":\"0.12.0\",\"variant\":\"slim\",\"loader\":\"fabric\",\"mc\":\"26.3\","
                + "\"coreHash\":\"" + A + "\",\"apiHash\":\"" + B + "\",\"entries\":[{\"path\":\"dev/oriel/engine/*.class\",\"sha256\":\"" + A + "\"}]}";
        var parsed = NestedManifest.parse(json);
        assertEquals("0.12.0", parsed.version());
        assertEquals(B, parsed.apiHash());
        assertEquals(Map.of("dev/oriel/engine/*.class", A), parsed.entries());
        assertThrows(IllegalArgumentException.class, () -> NestedManifest.parse(json.replace("}]", "},{\"path\":\"dev/oriel/engine/*.class\",\"sha256\":\"" + A + "\"}]")));
    }

    @Test void malformedManifestIsDiagnosed() {
        assertThrows(IllegalArgumentException.class, () -> NestedManifest.parse("{}"));
        assertThrows(IllegalArgumentException.class, () -> NestedManifest.parse("x".repeat(200 * 1024 + 1)));
    }
}
