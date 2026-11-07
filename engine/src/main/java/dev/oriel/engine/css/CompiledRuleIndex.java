package dev.oriel.engine.css;

import dev.oriel.engine.css.Selector.MatchContext;
import dev.oriel.engine.dom.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A separate candidate index for compiled programs; each rule occupies exactly one necessary-condition bucket. */
final class CompiledRuleIndex {
    private record Candidate(CompiledSelector program, RuleIndex.Entry entry) {}
    private final Map<String, List<Candidate>> ids = new HashMap<>(), classes = new HashMap<>(), tags = new HashMap<>();
    private final Map<String, List<Candidate>> attributes = new HashMap<>();
    private final List<Candidate> universal = new ArrayList<>();

    void add(Selector selector, List<Decl> declarations, boolean userAgent, int order) {
        long key = (userAgent ? 0L : 1L) << 61 | (long) selector.specificity << 31 | order;
        add(new RuleIndex.Entry(selector, declarations, userAgent, key));
    }

    void add(RuleIndex.Entry entry) {
        Selector selector = entry.selector();
        Candidate candidate = new Candidate(selector.compiled(), entry);
        Selector.Compound subject = selector.subject();
        Map<String, List<Candidate>> bucket = null;
        String name = null;
        for (Selector.Simple simple : subject.simples()) {
            if (simple instanceof Selector.IdSelector id) {
                bucket = ids;
                name = id.id();
                break;
            }
            if (bucket == null && simple instanceof Selector.ClassSelector c) {
                bucket = classes;
                name = c.name();
            }
        }
        if (bucket == null && subject.tag() != null) {
            bucket = tags;
            name = subject.tag();
        }
        if (bucket == null) {
            for (Selector.Simple simple : subject.simples()) {
                if (simple instanceof Selector.AttributeSelector attr) {
                    bucket = attributes;
                    name = attr.name();
                    break;
                }
            }
        }
        if (bucket == null) universal.add(candidate);
        else bucket.computeIfAbsent(name, ignored -> new ArrayList<>()).add(candidate);
    }

    void match(Element e, MatchContext ctx, List<RuleIndex.Entry> out) {
        String id = e.getAttribute("id");
        if (id != null) test(ids.get(id), e, ctx, out);
        for (String c : e.classes()) test(classes.get(c), e, ctx, out);
        test(tags.get(e.tagName()), e, ctx, out);
        // Attribute names are normalized by the DOM. Visit present attributes, not every name in the stylesheet.
        if (!attributes.isEmpty()) {
            for (String name : e.attributes().keySet()) test(attributes.get(name), e, ctx, out);
        }
        test(universal, e, ctx, out);
        out.sort(RuleIndex.CASCADE_ORDER);
    }

    private static void test(List<Candidate> candidates, Element e, MatchContext ctx, List<RuleIndex.Entry> out) {
        if (candidates == null) return;
        for (Candidate candidate : candidates) if (candidate.program.matches(e, ctx)) out.add(candidate.entry);
    }
}
