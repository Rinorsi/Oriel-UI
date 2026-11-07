package dev.oriel.kit;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionsTest {
    @Test void comparatorsAreAllRequiredAndMissingPartsCountAsZero() {
        assertTrue(Versions.satisfies("0.11.0", ">=0.11.0"));
        assertTrue(Versions.satisfies("0.11.2", ">=0.11.0 <0.12.0"));
        assertFalse(Versions.satisfies("0.12.0", ">=0.11.0 <0.12.0"));
        assertFalse(Versions.satisfies("0.10.9", ">=0.11.0"));
        assertTrue(Versions.satisfies("26.1.2", ">=26.1"));
        assertTrue(Versions.satisfies("26.1", ">26.0.9, <=26.1"));
        assertTrue(Versions.satisfies("1.0", "=1.0.0"));
        assertTrue(Versions.satisfies("v0.11.0-preview+build5", ">=0.11.0"));
    }

    @Test void anUnknownVersionOrRangeNeverLocksAnything() {
        assertTrue(Versions.satisfies(null, ">=9.0.0"));
        assertTrue(Versions.satisfies("", ">=9.0.0"));
        assertTrue(Versions.satisfies("dev", ">=9.0.0"));
        assertTrue(Versions.satisfies("0.11.0", null));
        assertTrue(Versions.satisfies("0.11.0", "  "));
    }

    @Test void aTemplateNamesTheFirstRequirementTheHostMisses() {
        var meta = JsonParser.parseString("{\"requires\":{\"oriel\":\">=0.12.0\",\"minecraft\":\">=26.2\"}}").getAsJsonObject();
        var unmet = TemplateCatalog.unmet(meta, "0.11.0", "26.1.2");
        assertEquals("oriel", unmet.what());
        assertEquals(">=0.12.0", unmet.range());
        assertEquals("0.11.0", unmet.have());
        assertEquals("minecraft", TemplateCatalog.unmet(meta, "0.12.0", "26.1.2").what());
        assertNull(TemplateCatalog.unmet(meta, "0.12.0", "26.3"));
        assertNull(TemplateCatalog.unmet(meta, null, null));
        assertNull(TemplateCatalog.unmet(JsonParser.parseString("{}").getAsJsonObject(), "0.1", "1"));
    }

    @Test void everyPackagedTemplateAsksForAVersion() {
        for (String id : TemplateCatalog.ids()) {
            var meta = TemplateCatalog.metadata(id, url -> {
                try (var in = VersionsTest.class.getResourceAsStream("/assets/oriel/" + url.substring(url.indexOf(':') + 1))) {
                    return in == null ? null : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    return null;
                }
            });
            assertTrue(meta.has("requires") && meta.getAsJsonObject("requires").has("oriel"), id + " needs a requires.oriel");
        }
    }
}
