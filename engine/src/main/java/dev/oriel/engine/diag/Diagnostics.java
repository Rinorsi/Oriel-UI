package dev.oriel.engine.diag;

import dev.oriel.engine.diag.Diagnostic.Severity;
import dev.oriel.engine.host.Host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A document's diagnostics: each distinct one is kept (up to {@link #MAX}) and handed to the host once, so a rule
 * restyled every frame is reported a single time.
 */
public final class Diagnostics {
    public static final int MAX = 1000;

    private final Host host;
    private final List<Diagnostic> all = new ArrayList<>();
    private final Set<Diagnostic> seen = new HashSet<>();
    private int dropped;

    public Diagnostics(Host host) {
        this.host = host;
    }

    public void report(Diagnostic d) {
        if (!seen.add(d)) return;
        if (all.size() >= MAX) {
            dropped++;
            return;
        }
        all.add(d);
        if (host != null) host.diagnostic(d);
    }

    public List<Diagnostic> all() {
        return Collections.unmodifiableList(all);
    }

    /** How many kept diagnostics are at least as severe as {@code severity}. */
    public int count(Severity severity) {
        int n = 0;
        for (Diagnostic d : all) if (d.severity().compareTo(severity) >= 0) n++;
        return n;
    }

    /** Diagnostics past {@link #MAX}, counted but not kept. */
    public int dropped() {
        return dropped;
    }
}
