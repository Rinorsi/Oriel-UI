package dev.oriel.engine.script;

import dev.oriel.engine.dom.Element;
import dev.oriel.shadow.rhino.Context;
import dev.oriel.shadow.rhino.NativeObject;
import dev.oriel.shadow.rhino.Scriptable;

/** Missing-name resolution: no feature slots, accessors or constructors are registered at page load. */
final class EcosystemScope extends NativeObject {
    private final RhinoScriptRuntime rt;
    private int resolved;

    EcosystemScope(RhinoScriptRuntime rt) {
        this.rt = rt;
    }

    private static int bit(String name) {
        return switch (name) {
            case "HTMLInputElement" -> 1;
            case "HTMLIFrameElement" -> 2;
            case "SVGElement" -> 4;
            case "NodeList" -> 8;
            case "HTMLCollection" -> 16;
            default -> 0;
        };
    }

    private boolean available(String name) {
        int bit = bit(name);
        return bit != 0 && (resolved & bit) == 0 && rt.dom != null;
    }

    @Override
    public Object get(String name, Scriptable start) {
        Object own = super.get(name, start);
        if (own != NOT_FOUND || !available(name)) return own;
        resolved |= bit(name);
        rt.dom.exposeType(name);
        return super.get(name, start);
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return super.has(name, start) || available(name);
    }

    @Override
    public void delete(String name) {
        resolved |= bit(name);
        super.delete(name);
    }

    @Override
    protected DescriptorInfo getOwnPropertyDescriptor(Context cx, Object id) {
        if (id instanceof String name && !super.has(name, this) && available(name)) get(name, this);
        return super.getOwnPropertyDescriptor(cx, id);
    }

    /** Replaces an existing NativeObject prototype; adds no extra per-page object. */
    static final class Prototype extends NativeObject {
        private final DomBindings dom;
        private final String member;
        private boolean deleted;

        Prototype(DomBindings dom, String member) {
            this.dom = dom;
            this.member = member;
        }

        @Override
        public Object get(String name, Scriptable start) {
            Object own = super.get(name, start);
            if (own != NOT_FOUND || deleted || !member.equals(name)) return own;
            dom.installMember(member);
            return super.get(name, start);
        }

        @Override
        public boolean has(String name, Scriptable start) {
            return super.has(name, start) || !deleted && member.equals(name);
        }

        @Override
        public void put(String name, Scriptable start, Object value) {
            if (!deleted && member.equals("namespaceURI") && member.equals(name) && !super.has(name, this)) {
                dom.installMember(member);
            }
            super.put(name, start, value);
        }

        @Override
        public void delete(String name) {
            if (member.equals(name)) deleted = true;
            super.delete(name);
        }

        @Override
        protected DescriptorInfo getOwnPropertyDescriptor(Context cx, Object id) {
            if (member.equals(id) && !deleted && !super.has(member, this)) get(member, this);
            return super.getOwnPropertyDescriptor(cx, id);
        }
    }

    /** Existing inputs adopt the type after its first probe, including detached and cloned wrappers. */
    static final class Input extends HostObject {
        private static final String CUSTOM_PROTOTYPE = "oriel:ecosystem:custom-prototype";

        Input(Scriptable scope, Scriptable prototype, Element target) {
            super(scope, prototype, "Element", target, null);
        }

        @Override
        public Scriptable getPrototype() {
            Scriptable stored = super.getPrototype();
            if (getAssociatedValue(CUSTOM_PROTOTYPE) != null) return stored;
            EcosystemScope scope = (EcosystemScope) getParentScope();
            return scope.rt.dom.inputPrototype(stored);
        }

        @Override
        public void setPrototype(Scriptable prototype) {
            super.setPrototype(prototype);
            // Constructor dispatch occurs before target is assigned; only explicit later changes need a marker.
            if (target != null) associateValue(CUSTOM_PROTOTYPE, Boolean.TRUE);
        }

        @Override
        public String getClassName() {
            EcosystemScope scope = (EcosystemScope) getParentScope();
            return scope.rt.dom.inputPrototype(null) == null ? "Element" : "HTMLInputElement";
        }
    }
}
