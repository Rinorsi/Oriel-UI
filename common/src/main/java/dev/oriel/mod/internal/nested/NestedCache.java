package dev.oriel.mod.internal.nested;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent scan result and notice history. An unchanged installation never opens nested archives again. */
public final class NestedCache {
    private static final Gson JSON = new Gson();
    public record State(String installation, String fingerprint, NestedCheck.Report report, Set<String> noticed) {}
    @FunctionalInterface public interface Scan { NestedCheck.Report run() throws IOException; }
    private final Path file;
    private State state;

    public NestedCache(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            try {
                state = JSON.fromJson(Files.readString(file), State.class);
                if (state == null || state.installation() == null || state.fingerprint() == null || state.report() == null || state.noticed() == null
                        || state.noticed().contains(null) || !valid(state.report().active()) || state.report().copies() == null
                        || state.report().copies().isEmpty() || state.report().copies().stream().anyMatch(copy -> !valid(copy))
                        || state.report().differences() == null || state.report().differences().entrySet().stream()
                        .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue().contains(null)))
                    throw new IllegalArgumentException("incomplete cache");
            } catch (RuntimeException e) { throw new IOException("Invalid Oriel nested cache: " + file, e); }
        }
    }

    private static boolean valid(NestedCheck.Copy copy) {
        return copy != null && copy.source() != null && copy.parent() != null && copy.version() != null;
    }

    public State check(String installation, List<String> installed, Scan scan) throws IOException {
        if (state != null && state.installation().equals(installation)) return state;
        NestedCheck.Report report = scan.run();
        state = new State(installation, NestedCheck.fingerprint(installed, report.copies()), report,
                state == null ? new HashSet<>() : new HashSet<>(state.noticed()));
        save();
        return state;
    }

    public boolean shouldNotice(boolean enabled) {
        return enabled && state != null && state.report().risk() && !state.noticed().contains(state.fingerprint());
    }

    public void markNoticed() throws IOException {
        if (state == null) return;
        state.noticed().add(state.fingerprint());
        save();
    }

    private void save() throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".nested-check-", ".json");
        try {
            Files.writeString(temporary, JSON.toJson(state));
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
