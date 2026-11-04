package dev.oriel.engine.architecture;

import dev.oriel.engine.css.StyleEngine;
import dev.oriel.engine.host.ExtensionRegistry;
import dev.oriel.engine.host.FontFamilies;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.EntityFocus;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompatibilityExtensionTest {
    private @interface NativeSize { float value(); }

    @NativeSize(ComputedStyle.DEFAULT_FONT_SIZE)
    @Test
    void legacyFontFieldsRemainCompileTimeConstants() {
        String actual = switch ("minecraft:default") {
            case FontFamilies.DEFAULT -> "default";
            case FontFamilies.UNIFORM -> "uniform";
            default -> "other";
        };
        assertEquals("default", actual);
    }

    @Test
    void installedProfilePreservesOriginalFontPolicy() {
        assertEquals(8f, ComputedStyle.DEFAULT_FONT_SIZE);
        assertEquals(9f / 8f, ComputedStyle.NORMAL_LINE_HEIGHT);
        assertEquals("minecraft:default", FontFamilies.DEFAULT);
        assertEquals("minecraft:uniform", FontFamilies.computed("monospace"));
        assertEquals("minecraft:custom", FontFamilies.fontId("CUSTOM"));
        assertEquals("minecraft:ui.button.click", ExtensionRegistry.compatibility().clickSound());
    }

    @Test
    void allTwelvePropertiesStillParseThroughTheInstalledSchema() {
        ComputedStyle style = Page.styleOf("-mc-tint: #123456; -mc-obfuscated: on; -mc-yaw: 30deg;"
                + "-mc-pitch: 10deg; -mc-roll: 20deg; -mc-model-scale: 2; -mc-entity-focus: eyes;"
                + "-mc-gaze-reach: 24px; -mc-gaze-limit: 40deg 20deg; -mc-translate: 1 2 3;"
                + "-mc-scene-center: 4 5 6; -mc-tooltip-delay: 0.2s");
        for (String property : List.of("-mc-tint", "-mc-obfuscated", "-mc-yaw", "-mc-pitch", "-mc-roll",
                "-mc-model-scale", "-mc-entity-focus", "-mc-gaze-reach", "-mc-gaze-limit",
                "-mc-translate", "-mc-scene-center", "-mc-tooltip-delay")) {
            assertNotNull(StyleEngine.computedValue(style, property), property);
        }
        assertEquals(0xFF123456, style.tint);
        assertEquals(EntityFocus.EYES, style.entityFocus);
        assertEquals(30, style.modelYaw);
        assertEquals(3, style.translateZ);
        assertEquals(6, style.sceneCenterZ);
        assertEquals(200, style.tooltipDelay);
    }

    @Test
    void richTextExpansionStillRunsOnLoadInsertionAndAttributeChanges() {
        TestHost host = new TestHost() {
            @Override public String translate(String key, String... args) { return key + String.join("/", args); }
        };
        Page page = host.load("<mc-text id=t key='title' args='a,b'></mc-text>");
        assertEquals("titlea/b", page.byId("t").textContent());
        page.byId("t").setAttribute("args", "c");
        assertEquals("titlec", page.byId("t").textContent());
        var element = page.byId("t").ownerDocument().createElement("mc-text");
        element.setAttribute("key", "inserted");
        page.byId("t").parentElement().appendChild(element);
        assertEquals("inserted", element.textContent());
    }

    @Test
    void isolatedCoreUsesNeutralPolicyAndDoesNotContainTheCompatibilityImplementations() throws Exception {
        URL core = ExtensionRegistry.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader isolated = new URLClassLoader(new URL[] {core}, ClassLoader.getPlatformClassLoader())) {
            Class<?> families = isolated.loadClass(FontFamilies.class.getName());
            assertEquals("minecraft:default", families.getField("DEFAULT").get(null));
            Class<?> style = isolated.loadClass(ComputedStyle.class.getName());
            assertEquals(8f, style.getField("DEFAULT_FONT_SIZE").get(null));
            assertEquals(16f, style.getField("fontSize").get(style.getConstructor().newInstance()));
            assertNotNull(Class.forName("dev.oriel.engine.css.Properties", true, isolated));
            for (String name : List.of("dev.oriel.engine.host.MinecraftCompatibilityProfile",
                    "dev.oriel.engine.css.MinecraftPropertySchema", "dev.oriel.engine.dom.MinecraftText")) {
                assertThrows(ClassNotFoundException.class, () -> isolated.loadClass(name));
            }
        }
    }
}
