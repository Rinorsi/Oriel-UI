package dev.oriel.engine.css;

import dev.oriel.engine.css.Selector.MatchContext;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.dom.PseudoElement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.Collections;

/** Immutable predicate program compiled from the stage-2 selector AST. No DOM or matching state is retained. */
final class CompiledSelector {
    @FunctionalInterface
    interface Trace {
        void tested(String selector, int compound, String predicate, Element element, boolean matched);
    }
    @FunctionalInterface
    private interface Test {
        boolean matches(Element element, MatchContext context);
    }

    /** Primitive compounds need no per-predicate lambda dispatch or diagnostic wrapper. */
    private static final class PrimitiveCompound implements Test {
        private final String tag, source;
        private final Selector.Simple[] simples;
        private final int maximumWork;

        PrimitiveCompound(Selector.Compound compound, String source) {
            tag = compound.tag();
            simples = compound.simples().clone();
            this.source = source;
            maximumWork = 1 + (tag == null ? 0 : 1) + simples.length;
        }

        @Override public boolean matches(Element element, MatchContext context) {
            // These predicates cannot recursively spend matching work. Near the limit, retain exact step failures.
            if (!context.hasPrimitiveWork(maximumWork)) return counted(element, context);
            int used = 1;
            try {
                if (tag != null) {
                    used++;
                    if (!tag.equals(element.tagName())) return false;
                }
                for (Selector.Simple simple : simples) {
                    used++;
                    if (!simple.matches(element, context)) return false;
                }
                return true;
            } finally {
                context.recordPrimitiveWork(used);
            }
        }

        private boolean counted(Element element, MatchContext context) {
            context.step(source);
            if (tag != null) {
                context.step(source);
                if (!tag.equals(element.tagName())) return false;
            }
            for (Selector.Simple simple : simples) {
                context.step(source);
                if (!simple.matches(element, context)) return false;
            }
            return true;
        }
    }

    private final Test[] compounds;
    private final char[] combinators;
    /** Only descendant combinators: the nearest ancestor that matches each step is enough, nothing to backtrack. */
    private final boolean descendantsOnly;
    final int specificity;
    final PseudoElement pseudoElement;

    private CompiledSelector(Selector source, Trace trace) {
        compounds = new Test[source.compounds.length];
        combinators = source.combinators.clone();
        boolean descendants = true;
        for (char c : combinators) descendants &= c == ' ';
        descendantsOnly = descendants;
        pseudoElement = source.pseudoElement;
        int weight = pseudoElement == PseudoElement.NONE ? 0 : Selector.TYPE;
        for (int i = 0; i < compounds.length; i++) {
            Selector.Compound c = source.compounds[i];
            int position = i;
            boolean primitive = trace == null;
            if (c.tag() != null) weight += Selector.TYPE;
            for (Selector.Simple simple : c.simples()) {
                weight += weight(simple);
                primitive &= switch (simple) {
                    case Selector.IdSelector ignored -> true;
                    case Selector.ClassSelector ignored -> true;
                    case Selector.AttributeSelector ignored -> true;
                    case Selector.PseudoClass ignored -> true;
                    case Selector.Anchor ignored -> true;
                    default -> false;
                };
            }
            if (primitive) {
                compounds[i] = new PrimitiveCompound(c, source.toString());
                continue;
            }
            List<Test> tests = new ArrayList<>();
            if (c.tag() != null) {
                tests.add(traced((e, ctx) -> c.tag().equals(e.tagName()), trace, source, position, c.tag()));
            }
            for (Selector.Simple simple : c.simples()) {
                tests.add(traced(compileSimple(simple, trace), trace, source, position, trace == null ? null : simple.toString()));
            }
            Test[] program = tests.toArray(Test[]::new);
            compounds[i] = (e, ctx) -> {
                ctx.step(source.toString());
                for (Test test : program) {
                    ctx.step(source.toString());
                    if (!test.matches(e, ctx)) return false;
                }
                return true;
            };
        }
        specificity = weight;
    }

    static CompiledSelector compile(Selector selector) {
        return new CompiledSelector(selector, null);
    }

    static CompiledSelector compile(Selector selector, Trace trace) {
        return new CompiledSelector(selector, trace);
    }

    private static Test traced(Test test, Trace trace, Selector source, int compound, String predicate) {
        if (trace == null) return test;
        return (e, ctx) -> {
            boolean matched = test.matches(e, ctx);
            trace.tested(source.toString(), compound, predicate, e, matched);
            return matched;
        };
    }

    private static List<CompiledSelector> compile(List<Selector> selectors, Trace trace) {
        return selectors.stream().map(s -> new CompiledSelector(s, trace)).toList();
    }

    private static int maxWeight(List<Selector> selectors) {
        int max = 0;
        for (Selector s : selectors) {
            int weight = s.pseudoElement == PseudoElement.NONE ? 0 : Selector.TYPE;
            for (Selector.Compound c : s.compounds) {
                if (c.tag() != null) weight += Selector.TYPE;
                for (Selector.Simple simple : c.simples()) weight += weight(simple);
            }
            max = Math.max(max, weight);
        }
        return max;
    }

    private static int weight(Selector.Simple simple) {
        return switch (simple) {
            case Selector.IdSelector ignored -> Selector.ID;
            case Selector.Anchor ignored -> 0;
            case Selector.Logical l -> l.zeroSpecificity() ? 0 : maxWeight(l.selectors());
            case Selector.Has h -> maxWeight(h.relative());
            case Selector.Nth n -> Selector.CLASS + (n.of() == null ? 0 : maxWeight(n.of()));
            default -> Selector.CLASS;
        };
    }

    private static Test compileSimple(Selector.Simple simple, Trace trace) {
        return switch (simple) {
            case Selector.Logical l -> {
                List<CompiledSelector> list = compile(l.selectors(), trace);
                yield (e, ctx) -> any(list, e, ctx) != l.negate();
            }
            case Selector.Has h -> {
                List<CompiledSelector> list = compile(h.relative(), trace);
                yield (e, ctx) -> {
                    Element saved = ctx.anchor;
                    ctx.anchor = e;
                    try {
                        for (CompiledSelector relative : list) if (relative.relative(e, ctx)) return true;
                        return false;
                    } finally {
                        ctx.anchor = saved;
                    }
                };
            }
            case Selector.Nth n -> {
                List<CompiledSelector> of = n.of() == null ? null : compile(n.of(), trace);
                Object filterKey = new Object();
                yield (e, ctx) -> {
                    Selector.Siblings siblings = ctx.siblings(e);
                    int position = 1;
                    if (of != null && siblings != null) {
                        Selector.FilteredPositions filtered = siblings.filtered(filterKey);
                        if (filtered == null) {
                            boolean saved = ctx.interactionRead;
                            ctx.interactionRead = false;
                            try {
                                int[] ranks = new int[siblings.elements.length];
                                int count = 0;
                                for (int i = 0; i < ranks.length; i++) {
                                    if (any(of, siblings.elements[i], ctx)) ranks[i] = ++count;
                                }
                                filtered = new Selector.FilteredPositions(ranks, count, ctx.interactionRead);
                                siblings.filtered(filterKey, filtered);
                            } finally {
                                ctx.interactionRead |= saved;
                            }
                        }
                        ctx.interactionRead |= filtered.interactionRead();
                        position = filtered.positions()[ctx.position(e)];
                        if (position == 0) return false;
                        if (n.last()) position = filtered.total() + 1 - position;
                    } else if (siblings != null) {
                        int index = ctx.position(e);
                        if (n.ofType()) position = siblings.typePosition(index, n.last());
                        else position = n.last() ? siblings.elements.length - index : index + 1;
                    } else if (of != null && !any(of, e, ctx)) return false;
                    int diff = position - n.b();
                    return n.a() == 0 ? position == n.b() : diff % n.a() == 0 && diff / n.a() >= 0;
                };
            }
            // Primitive DOM/state predicates are shared; traversal and functional pseudos are independent.
            default -> simple::matches;
        };
    }

    private static boolean any(List<CompiledSelector> list, Element e, MatchContext ctx) {
        for (CompiledSelector s : list) if (s.pseudoElement == PseudoElement.NONE && s.matches(e, ctx)) return true;
        return false;
    }

    /** Each (compound, element) is evaluated once, avoiding exponential descendant/sibling backtracking. */
    boolean matches(Element element, MatchContext ctx) {
        if (!compounds[compounds.length - 1].matches(element, ctx)) return false;
        if (compounds.length == 1) return true;
        if (compounds.length == 2) return predecessor(element, ctx);
        if (descendantsOnly) {
            Element cursor = element;
            for (int i = compounds.length - 2; i >= 0; i--) {
                Element p = cursor.parentElement();
                while (p != null && !compounds[i].matches(p, ctx)) p = p.parentElement();
                if (p == null) return false;
                cursor = p;
            }
            return true;
        }
        List<Element> frontier = List.of(element);
        for (int i = compounds.length - 2; i >= 0; i--) {
            List<Element> next = new ArrayList<>();
            Set<Element> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Element e : frontier) {
                char relation = combinators[i];
                if (relation == ' ' || relation == '>') {
                    for (Element p = e.parentElement(); p != null; p = p.parentElement()) {
                        if (!visited.add(p)) break;
                        if (compounds[i].matches(p, ctx)) {
                            if (i == 0) return true;
                            next.add(p);
                        }
                        if (relation == '>') break;
                    }
                } else {
                    Selector.Siblings siblings = ctx.siblings(e);
                    if (siblings == null) continue;
                    for (int k = ctx.position(e) - 1; k >= 0; k--) {
                        Element p = siblings.elements[k];
                        if (!visited.add(p)) break;
                        if (compounds[i].matches(p, ctx)) {
                            if (i == 0) return true;
                            next.add(p);
                        }
                        if (relation == '+') break;
                    }
                }
            }
            if (next.isEmpty()) return false;
            frontier = next;
        }
        return false;
    }

    /** Common two-compound programs need no frontier allocation or duplicate suppression. */
    private boolean predecessor(Element e, MatchContext ctx) {
        char relation = combinators[0];
        if (relation == ' ' || relation == '>') {
            for (Element p = e.parentElement(); p != null; p = p.parentElement()) {
                if (compounds[0].matches(p, ctx)) return true;
                if (relation == '>') break;
            }
        } else {
            Selector.Siblings siblings = ctx.siblings(e);
            if (siblings == null) return false;
            for (int k = ctx.position(e) - 1; k >= 0; k--) {
                if (compounds[0].matches(siblings.elements[k], ctx)) return true;
                if (relation == '+') break;
            }
        }
        return false;
    }

    /** Relative programs run forward from their anchor, restricting each step to its actual relation. */
    private boolean relative(Element anchor, MatchContext ctx) {
        List<Element> frontier = List.of(anchor);
        for (int i = 1; i < compounds.length; i++) {
            List<Element> next = new ArrayList<>();
            Set<Element> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            char relation = combinators[i - 1];
            for (Element e : frontier) {
                ArrayDeque<Element> candidates = new ArrayDeque<>();
                if (relation == ' ' || relation == '>') addChildren(e, candidates);
                else {
                    Selector.Siblings siblings = ctx.siblings(e);
                    if (siblings == null) continue;
                    int start = ctx.position(e) + 1;
                    int end = relation == '+' ? Math.min(start + 1, siblings.elements.length) : siblings.elements.length;
                    for (int k = start; k < end; k++) candidates.add(siblings.elements[k]);
                }
                while (!candidates.isEmpty()) {
                    Element candidate = candidates.removeFirst();
                    if (!visited.add(candidate)) continue;
                    if (compounds[i].matches(candidate, ctx)) {
                        if (i == compounds.length - 1) return true;
                        next.add(candidate);
                    }
                    if (relation == ' ') addChildren(candidate, candidates);
                }
            }
            if (next.isEmpty()) return false;
            frontier = next;
        }
        return false;
    }

    private static void addChildren(Node node, ArrayDeque<Element> out) {
        for (int i = 0; i < node.childCount(); i++) if (node.childAt(i) instanceof Element e) out.addLast(e);
    }
}
