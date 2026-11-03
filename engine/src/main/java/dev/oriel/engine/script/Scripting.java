package dev.oriel.engine.script;

import dev.oriel.engine.dom.Document;

import java.util.function.Function;

/** Script runtimes for hosts: {@code createScriptRuntime(doc)} returns {@code Scripting.rhino().apply(doc)}. */
public final class Scripting {
    private Scripting() {}

    /**
     * The sandboxed JavaScript runtime (Mozilla Rhino, relocated): DOM bindings, the {@code oriel} API and templates.
     * See {@code docs/SCRIPTING.md}.
     */
    public static Function<Document, ScriptRuntime> rhino() {
        return RhinoScriptRuntime::create;
    }
}
