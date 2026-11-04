package dev.oriel.engine.script;

import dev.oriel.engine.dom.Document;

/** Supplies the existing sandbox and bindings through the optional runtime boundary. */
public final class RhinoRuntimeProvider implements ScriptRuntimeProvider {
    @Override
    public String id() {
        return "rhino";
    }

    @Override
    public ScriptRuntime create(Document document) {
        return RhinoScriptRuntime.create(document);
    }
}
