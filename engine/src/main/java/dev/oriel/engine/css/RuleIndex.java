package dev.oriel.engine.css;

import dev.oriel.engine.css.Selector.MatchContext;
import dev.oriel.engine.css.Stylesheet.ImportRule;
import dev.oriel.engine.css.Stylesheet.KeyframesRule;
import dev.oriel.engine.css.Stylesheet.MediaRule;
import dev.oriel.engine.css.Stylesheet.StyleRule;
import dev.oriel.engine.dom.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The rules that apply under the current media environment, flattened in cascade order and indexed by the rightmost
 * compound's id, first class or tag (anything else goes in a universal bucket), so each element only tests selectors
 * that can match it. Also holds the active {@code @keyframes} by name (the last definition wins).
 */
final class RuleIndex {
    /** A stylesheet with the media query of the element that included it. */
    record Source(Stylesheet sheet, MediaQuery media) {}

    /**
     * One selector of a style rule. {@code key} orders entries by origin, then specificity, then source order, so
     * sorting matched entries by key gives the cascade order of normal declarations.
     */
    record Entry(Selector selector, List<Decl> decls, boolean userAgent, long key) {}

    static final Comparator<Entry> CASCADE_ORDER = Comparator.comparingLong(Entry::key);
    private static final int MAX_IMPORT_DEPTH = 8;

    private final Map<String, List<Entry>> byId = new HashMap<>(), byClass = new HashMap<>(), byTag = new HashMap<>();
    private final List<Entry> universal = new ArrayList<>();
    final Map<String, KeyframesRule> keyframes = new HashMap<>();
    /** The attributes the rules' selectors read: changes to others cannot change which rules match. */
    final Set<String> attributes = new HashSet<>();
    private final MediaQuery.Environment env;
    private final Function<String, Stylesheet> imports;
    private int order;
    private final SelectorAudit.Mode mode = SelectorAudit.mode();
    private final CompiledRuleIndex compiled = mode == SelectorAudit.Mode.OLD ? null : new CompiledRuleIndex();
    final SelectorInvalidation invalidation = mode == SelectorAudit.Mode.NEW ? new SelectorInvalidation() : null;

    private RuleIndex(MediaQuery.Environment env, Function<String, Stylesheet> imports) {
        this.env = env;
        this.imports = imports;
    }

    /**
     * Builds the index from the user-agent sheet and the author sheets in document order. {@code imports} loads an
     * {@code @import}ed sheet by resolved URL (sheet URL as base).
     */
    static RuleIndex build(Stylesheet userAgent, List<Source> author, MediaQuery.Environment env,
                           Function<String, Stylesheet> imports) {
        RuleIndex index = new RuleIndex(env, imports);
        index.add(userAgent, true, new HashSet<>());
        for (Source s : author) if (s.media().matches(env)) index.add(s.sheet(), false, new HashSet<>());
        return index;
    }

    private void add(Stylesheet sheet, boolean userAgent, Set<String> importChain) {
        if (!importChain.add(sheet.url == null ? "" : sheet.url) || importChain.size() > MAX_IMPORT_DEPTH) return;
        add(sheet, sheet.rules, userAgent, importChain);
        importChain.remove(sheet.url == null ? "" : sheet.url);
    }

    private void add(Stylesheet sheet, List<Stylesheet.Rule> rules, boolean userAgent, Set<String> importChain) {
        for (Stylesheet.Rule rule : rules) {
            switch (rule) {
                case StyleRule r -> {
                    int position = order++;
                    for (Selector s : r.selectors()) {
                        long key = (userAgent ? 0L : 1L) << 61 | (long) s.specificity << 31 | position;
                        bucket(s).add(new Entry(s, r.decls(), userAgent, key));
                        if (compiled != null) compiled.add(s, r.decls(), userAgent, position);
                        if (invalidation != null) invalidation.add(s);
                        s.attributesRead(attributes);
                    }
                }
                case MediaRule m -> {
                    if (m.media().matches(env)) add(sheet, m.rules(), userAgent, importChain);
                }
                case ImportRule i -> {
                    Stylesheet imported = i.media().matches(env) ? imports.apply(i.url()) : null;
                    if (imported != null) add(imported, userAgent, importChain);
                }
                case KeyframesRule k -> keyframes.put(k.name(), k);
            }
        }
    }

    private List<Entry> bucket(Selector s) {
        Selector.Compound subject = s.subject();
        if (subject.id() != null) return byId.computeIfAbsent(subject.id(), k -> new ArrayList<>());
        if (subject.firstClass() != null) return byClass.computeIfAbsent(subject.firstClass(), k -> new ArrayList<>());
        if (subject.tag() != null) return byTag.computeIfAbsent(subject.tag(), k -> new ArrayList<>());
        return universal;
    }

    /** Appends the entries whose selector matches {@code e} (ignoring pseudo-elements), in cascade order. */
    void match(Element e, MatchContext ctx, List<Entry> out) {
        if (mode == SelectorAudit.Mode.NEW) {
            compiled.match(e, ctx, out);
            return;
        }
        List<Entry> alternate = mode == SelectorAudit.Mode.BOTH ? new ArrayList<>(out) : null;
        String id = e.getAttribute("id");
        if (id != null) test(byId.get(id), e, ctx, out);
        for (String c : e.classes()) test(byClass.get(c), e, ctx, out);
        test(byTag.get(e.tagName()), e, ctx, out);
        test(universal, e, ctx, out);
        out.sort(CASCADE_ORDER);
        if (alternate != null) {
            compiled.match(e, ctx.fork(SelectorAudit.Mode.NEW), alternate);
            boolean equal = sameMatches(out, alternate);
            SelectorAudit.compare("index", e, equal,
                    "\"oldCount\":" + out.size() + ",\"newCount\":" + alternate.size(),
                    equal ? null : out + " vs " + alternate);
        }
    }

    private static boolean sameMatches(List<Entry> legacy, List<Entry> compiled) {
        if (legacy.size() != compiled.size()) return false;
        // Selectors of the same rule have identical declarations. Their relative bucket visitation order is
        // immaterial, but multiplicity, pseudo-elements and cascade keys must still agree exactly.
        Map<Entry, Integer> counts = new HashMap<>();
        for (int i = 0; i < legacy.size(); i++) {
            if (legacy.get(i).key != compiled.get(i).key) return false;
            counts.merge(legacy.get(i), 1, Integer::sum);
            counts.merge(compiled.get(i), -1, Integer::sum);
        }
        return counts.values().stream().allMatch(count -> count == 0);
    }

    /** Allocated only by an explicit inspection request, never by restyle. */
    List<Entry> inspectionEntries() {
        List<Entry> entries = new ArrayList<>(universal);
        for (List<Entry> bucket : byId.values()) entries.addAll(bucket);
        for (List<Entry> bucket : byClass.values()) entries.addAll(bucket);
        for (List<Entry> bucket : byTag.values()) entries.addAll(bucket);
        entries.sort(CASCADE_ORDER);
        return entries;
    }

    private static void test(List<Entry> entries, Element e, MatchContext ctx, List<Entry> out) {
        if (entries == null) return;
        for (int i = 0, n = entries.size(); i < n; i++) {
            Entry entry = entries.get(i);
            if (entry.selector.matches(e, ctx)) out.add(entry);
        }
    }
}
