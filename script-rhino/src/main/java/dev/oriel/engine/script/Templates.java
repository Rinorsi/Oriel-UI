package dev.oriel.engine.script;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.Host;
import dev.oriel.shadow.rhino.Callable;
import dev.oriel.shadow.rhino.Context;
import dev.oriel.shadow.rhino.Scriptable;

import java.util.ArrayList;
import java.util.List;

/**
 * Templates: Vue-style syntax bound by dirty checking. When the document has loaded it is compiled once into
 * {@link Binding}s ({@link TemplateCompiler}) and rendered. Every entry then marks the templates {@link #invalidate
 * dirty}, and once per frame the runtime runs {@link #digest}, which re-evaluates the bindings, touches the DOM only
 * where a rendered value changed, and repeats until nothing changes.
 */
final class Templates {
    private final RhinoScriptRuntime rt;
    private final TemplateScope scope;
    private final List<Element> cloaked = new ArrayList<>();
    private final TemplateCompiler compiler;
    private final Binding.Block root = new Binding.Block();
    /** {@code oriel.nextTick} callbacks waiting for the next digest. */
    private List<Callable> afterDigest = new ArrayList<>();
    private boolean installed, dirty;

    Templates(RhinoScriptRuntime rt) {
        this.rt = rt;
        scope = new TemplateScope(rt);
        compiler = new TemplateCompiler(rt, cloaked);
    }

    /** Compiles and renders the document (once scripts have run, so the load event sees rendered templates). */
    void install(Context cx) {
        if (installed) return;
        installed = true;
        Element html = rt.document.documentElement();
        if (html != null) compiler.compileElement(html, scope, root);
        digest(cx);
    }

    /** Something may have changed what the templates show: the next frame digests. */
    void invalidate() {
        dirty = true;
    }

    boolean needsDigest() {
        return dirty && (installed || !afterDigest.isEmpty());
    }

    /** {@code oriel.nextTick(fn)}: runs {@code fn} after the next digest, when the DOM shows the current state. */
    void nextTick(Callable fn) {
        afterDigest.add(fn);
        dirty = true;
    }

    /** {@code oriel.state(object)}: adds the object's properties to the template scope and returns it. */
    Object state(Object value) {
        if (!(value instanceof Scriptable state)) throw Js.typeError("oriel.state() takes an object");
        scope.addState(state);
        return state;
    }

    void digest(Context cx) {
        dirty = false;
        if (installed) {
            int passes = 1;
            while (root.update(cx)) {
                if (++passes > rt.limits.maxTemplatePasses()) {
                    rt.document.host().log(Host.LogLevel.WARN, "Templates still changing after " + rt.limits.maxTemplatePasses()
                            + " passes: an expression changes state each time it is evaluated");
                    break;
                }
            }
            for (Element e : cloaked) e.removeAttribute("v-cloak");
            cloaked.clear();
        }
        if (!afterDigest.isEmpty()) {
            List<Callable> callbacks = afterDigest;
            afterDigest = new ArrayList<>();
            for (Callable fn : callbacks) rt.call(() -> "Error in oriel.nextTick callback", fn, rt.global);
            dirty = true; // what the callbacks changed shows at the next frame
        }
    }
}
