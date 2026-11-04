package dev.oriel.engine.script;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeProviderTest {
    @Test
    void existingRhinoEntryPointFindsTheOptionalProvider() {
        assertNotNull(Scripting.rhino());
    }

    @Test
    void coreLoadsWithoutAnImplementationAndRequestedRuntimeFailsExplicitly() throws Exception {
        URL core = Scripting.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader isolated = new URLClassLoader(new URL[] {core}, ClassLoader.getPlatformClassLoader())) {
            Class<?> scripting = isolated.loadClass(Scripting.class.getName());
            assertNotNull(isolated.loadClass(ScriptRuntime.class.getName()));
            assertThrows(ClassNotFoundException.class,
                    () -> isolated.loadClass("dev.oriel.engine.script.RhinoScriptRuntime"));
            assertFalse(isolated.getResources("META-INF/services/" + ScriptRuntimeProvider.class.getName()).hasMoreElements());
            InvocationTargetException error = assertThrows(InvocationTargetException.class,
                    () -> scripting.getMethod("rhino").invoke(null));
            assertInstanceOf(IllegalStateException.class, error.getCause());
            assertTrue(error.getCause().getMessage().contains("rhino"));
            assertTrue(error.getCause().getMessage().contains("provider"));
        }
    }
}
