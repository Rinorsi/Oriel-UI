package dev.oriel.engine.css;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.PseudoElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** On-demand probes. Production matching never allocates a trace or calls this class. */
final class SelectorDiagnostics {
    private static final int MAX_TRACE = 4096;

    private SelectorDiagnostics() {}

    static Map<String, Object> explain(RuleIndex index, Element element, String query) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "selector-explanation");
        result.put("page", element.ownerDocument().url());
        result.put("element", element.toString());
        result.put("query", query);
        result.put("probeBackend", "compiled");
        List<Object> queryResults = new ArrayList<>();
        for (Selector selector : SelectorParser.parse(query)) queryResults.add(probe(selector, element));
        result.put("queryResults", queryResults);
        List<Object> rules = new ArrayList<>();
        List<RuleIndex.Entry> matched = new ArrayList<>();
        int oldCandidates = 0, newCandidates = 0;
        long[] histogram = new long[5];
        long work = 0;
        for (RuleIndex.Entry entry : index.inspectionEntries()) {
            Selector selector = entry.selector();
            boolean oldCandidate = candidate(selector, element, false);
            boolean newCandidate = candidate(selector, element, true);
            if (oldCandidate) oldCandidates++;
            if (newCandidate) newCandidates++;
            Map<String, Object> rule = probe(selector, element);
            long elapsed = (long) rule.get("elapsedNs");
            int bucket = elapsed < 1_000 ? 0 : elapsed < 10_000 ? 1 : elapsed < 100_000 ? 2 : elapsed < 1_000_000 ? 3 : 4;
            histogram[bucket]++;
            work += (long) rule.get("workUsed");
            rule.put("candidateOld", oldCandidate);
            rule.put("candidateNew", newCandidate);
            rule.put("origin", entry.userAgent() ? "user-agent" : "author");
            rule.put("sourceOrder", entry.key() & 0x7FFFFFFFL);
            rules.add(rule);
            if (Boolean.TRUE.equals(rule.get("matched"))) matched.add(entry);
        }
        result.put("candidateCountOld", oldCandidates);
        result.put("candidateCountNew", newCandidates);
        result.put("rules", rules);
        result.put("workUsed", work);
        result.put("elapsedHistogramNs", Map.of("lt1000", histogram[0], "lt10000", histogram[1],
                "lt100000", histogram[2], "lt1000000", histogram[3], "gte1000000", histogram[4]));
        Map<String, Object> winners = new LinkedHashMap<>();
        List<RuleIndex.Entry> normalOrder = new ArrayList<>(matched);
        normalOrder.sort(java.util.Comparator.comparing((RuleIndex.Entry entry) -> !entry.userAgent())
                .thenComparingInt(RuleIndex.Entry::layerRank).thenComparingLong(RuleIndex.Entry::key));
        List<RuleIndex.Entry> importantOrder = new ArrayList<>(matched);
        importantOrder.sort(java.util.Comparator.comparingInt(RuleIndex.Entry::layerRank).reversed()
                .thenComparingLong(RuleIndex.Entry::key));
        for (PseudoElement pseudo : PseudoElement.values()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<Decl> inline = pseudo == PseudoElement.NONE ? ElementState.of(element).inline : null;
            for (RuleIndex.Entry entry : normalOrder) if (entry.selector().pseudoElement == pseudo) put(properties, entry, false);
            putInline(properties, inline, false);
            for (RuleIndex.Entry entry : importantOrder) {
                if (entry.selector().pseudoElement == pseudo && !entry.userAgent()) put(properties, entry, true);
            }
            putInline(properties, inline, true);
            for (RuleIndex.Entry entry : importantOrder) {
                if (entry.selector().pseudoElement == pseudo && entry.userAgent()) put(properties, entry, true);
            }
            if (!properties.isEmpty()) winners.put(pseudo.name(), properties);
        }
        result.put("declarationWinners", winners);
        result.put("note", "Declaration winners precede var resolution, inheritance and animation. Probe timings include trace overhead.");
        return result;
    }

    private static Map<String, Object> probe(Selector selector, Element element) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Object> trace = new ArrayList<>();
        int[] dropped = {0};
        CompiledSelector program = CompiledSelector.compile(selector, (source, compound, predicate, node, matched) -> {
            if (trace.size() < MAX_TRACE) trace.add(Map.of("selector", source, "compound", compound,
                    "predicate", predicate, "element", node.toString(), "matched", matched));
            else dropped[0]++;
        });
        Selector.MatchContext context = new Selector.MatchContext(element.ownerDocument().documentElement(), SelectorAudit.Mode.NEW);
        long start = System.nanoTime();
        boolean matched = program.matches(element, context);
        result.put("elapsedNs", System.nanoTime() - start);
        result.put("selector", selector.toString());
        result.put("matched", matched);
        result.put("specificity", program.specificity);
        result.put("pseudoElement", program.pseudoElement.name());
        result.put("trace", trace);
        result.put("traceDropped", dropped[0]);
        result.put("workUsed", context.workUsed());
        result.put("workLimit", context.workLimit);
        if (!matched) result.put("reason", "No complete matching path; false trace entries identify failed predicates, absent paths indicate a missing relationship.");
        return result;
    }

    private static boolean candidate(Selector selector, Element element, boolean compiled) {
        Selector.Compound subject = selector.subject();
        if (!compiled) {
            if (subject.id() != null) return subject.id().equals(element.id());
            if (subject.firstClass() != null) return element.hasClass(subject.firstClass());
        } else {
            for (Selector.Simple simple : subject.simples()) {
                if (simple instanceof Selector.IdSelector id) return id.id().equals(element.id());
            }
            for (Selector.Simple simple : subject.simples()) {
                if (simple instanceof Selector.ClassSelector cls) return element.hasClass(cls.name());
            }
        }
        if (subject.tag() != null) return subject.tag().equals(element.tagName());
        if (compiled) for (Selector.Simple simple : subject.simples()) {
            if (simple instanceof Selector.AttributeSelector attr) return element.hasAttribute(attr.name());
        }
        return true;
    }

    private static void put(Map<String, Object> winners, RuleIndex.Entry entry, boolean important) {
        for (Decl declaration : entry.decls()) {
            if (declaration.important != important) continue;
            Map<String, Object> winner = declaration(declaration);
            winner.put("selector", entry.selector().toString());
            winner.put("specificity", entry.selector().specificity);
            winner.put("sourceOrder", entry.key() & 0x7FFFFFFFL);
            winner.put("origin", entry.userAgent() ? "user-agent" : "author");
            winners.put(declaration.isCustom() ? declaration.customName : declaration.property.name, winner);
        }
    }

    private static void putInline(Map<String, Object> winners, List<Decl> declarations, boolean important) {
        if (declarations == null) return;
        for (Decl declaration : declarations) {
            if (declaration.important != important) continue;
            Map<String, Object> winner = declaration(declaration);
            winner.put("origin", "inline");
            winners.put(declaration.isCustom() ? declaration.customName : declaration.property.name, winner);
        }
    }

    private static Map<String, Object> declaration(Decl declaration) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", ComponentValue.text(declaration.value));
        result.put("important", declaration.important);
        result.put("url", declaration.baseUrl);
        return result;
    }
}
