package dev.oriel.engine.script;

import dev.oriel.engine.dom.Document;

import java.util.ServiceLoader;
import java.util.function.Function;

/** Script runtimes for hosts: {@code createScriptRuntime(doc)} returns {@code Scripting.rhino().apply(doc)}. */
public final class Scripting {
    private Scripting() {}

    /**
     * The sandboxed JavaScript runtime (Mozilla Rhino, relocated): DOM bindings, the {@code oriel} API and templates.
     * See {@code docs/SCRIPTING.md}.
     */
    public static Function<Document, ScriptRuntime> rhino() {
        ScriptRuntimeProvider found = null;
        for (ScriptRuntimeProvider provider : ServiceLoader.load(ScriptRuntimeProvider.class, Scripting.class.getClassLoader())) {
            if (!provider.id().equals("rhino")) continue;
            if (found != null) throw new IllegalStateException("Multiple rhino script runtime providers are installed");
            found = provider;
        }
        if (found == null) throw new IllegalStateException("No rhino script runtime provider is installed; add oriel-script-rhino");
        return found::create;
    }
}
