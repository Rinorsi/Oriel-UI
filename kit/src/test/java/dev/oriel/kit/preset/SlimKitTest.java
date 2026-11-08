package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.host.PageControllers;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Executes real kit classes with reference resources and controllers physically unavailable. */
class SlimKitTest {
    private static final class SlimLoader extends ClassLoader {
        SlimLoader() { super(SlimKitTest.class.getClassLoader()); }
        @Override public URL getResource(String name) {
            if (name.startsWith("assets/oriel/ui/templates/")) return null;
            return super.getResource(name);
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("dev.oriel.kit.")) return super.loadClass(name, resolve);
            if (name.equals("dev.oriel.kit.ReferenceKit") || name.startsWith("dev.oriel.kit.preset.")
                    && !name.equals("dev.oriel.kit.preset.NestedNoticePreset")
                    && !name.equals("dev.oriel.kit.preset.AboutPreset") && !name.startsWith("dev.oriel.kit.preset.ModSettingsPreset"))
                throw new ClassNotFoundException("Reference content removed: " + name);
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                try (var in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (in == null) throw new ClassNotFoundException(name);
                    byte[] bytes = in.readAllBytes();
                    type = defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) { throw new ClassNotFoundException(name, e); }
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }

    @Test void noCatalogStillAllowsLocalMetadataAndCorePages() throws Exception {
        ClassLoader loader = new SlimLoader();
        Class<?> catalog = loader.loadClass("dev.oriel.kit.TemplateCatalog");
        assertEquals(List.of(), catalog.getMethod("ids").invoke(null));
        var metadata = com.google.gson.JsonParser.parseString("{\"locales\":{\"en_us\":{\"name\":\"My page\"}}}").getAsJsonObject();
        Object text = catalog.getMethod("localized", com.google.gson.JsonObject.class, String.class).invoke(null, metadata, "en_us");
        assertEquals("My page", text.getClass().getMethod("name").invoke(text));
        loader.loadClass("dev.oriel.kit.OrielKit").getMethod("register").invoke(null);
        try {
            for (String name : List.of("modsettings", "about")) {
                KitHost host = new KitHost();
                var controller = (PageControllers.Controller) loader.loadClass("dev.oriel.kit.preset."
                        + (name.equals("about") ? "AboutPreset" : "ModSettingsPreset")).getConstructor().newInstance();
                Document doc = host.open("oriel:ui/" + name + ".html", controller);
                assertNull(doc.querySelector("[data-go=gallery]"));
                assertNull(doc.querySelector("[data-go=styles]"));
                assertNotNull(doc.querySelector("[data-go=" + (name.equals("about") ? "modsettings" : "about") + "]"));
                loader.loadClass("dev.oriel.kit.Shell").getMethod("back", Document.class).invoke(null, doc);
                assertTrue(host.closed);
                assertTrue(host.navigations.isEmpty());
            }
        } finally {
            // Restore the normal factories for the other kit tests in this JVM.
            PageControllers.registerShared(() -> dev.oriel.kit.Shell::setup);
            PageControllers.register(dev.oriel.kit.OrielKit.ABOUT, AboutPreset::new);
            PageControllers.register(dev.oriel.kit.OrielKit.MODSETTINGS, ModSettingsPreset::new);
            PageControllers.register(dev.oriel.kit.OrielKit.page("nested-notice"), NestedNoticePreset::new);
        }
    }
}
