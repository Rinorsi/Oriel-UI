package dev.oriel.engine.script;

import dev.oriel.engine.dom.Document;

/** Optional runtime implementation discovered without a dependency from the core to that implementation. */
public interface ScriptRuntimeProvider {
    String id();

    ScriptRuntime create(Document document);
}
