package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.DocumentDriver;
import static dev.oriel.mod.internal.client.ClientText.t;
import static dev.oriel.mod.internal.client.ClientText.plural;

import com.google.gson.JsonObject;
import dev.oriel.mod.internal.Constants;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Hot reload for dev runs, for what saving a page does not already reload ({@link OrielResources} reads pages,
 * stylesheets and their JSON from the sources as they are saved): Java sources and classes, and the resources the
 * game itself loads (images, fonts, language files, models, sounds). Every page gets a bar when they change
 * ({@code oriel-dev} data field; Shell draws it) with a 热重载 button, which compiles and copies the resources
 * (Gradle, the loader project's {@code classes}), swaps the changed classes into the running JVM (the JDK's class
 * redefinition, through {@link DevAgent} attached to this process), reloads the resources when some changed (F3+T)
 * and reopens the pages. What cannot change while the game runs is named in the bar as needing a restart: a class
 * whose fields or methods were added, removed or renamed, and the mod's metadata ({@code fabric.mod.json},
 * {@code neoforge.mods.toml}, mixin configs).
 */
public final class DevReload {
    public static final String CHANNEL = "oriel-dev";
    private static final long POLL_MS = 1500;

    private static final Map<Path, Long> classTimes = new HashMap<>(), sourceTimes = new HashMap<>(), resourceTimes = new HashMap<>();
    /** The content checksum of each class file, so a build that rewrites files unchanged swaps nothing. */
    private static final Map<Path, Long> classSums = new HashMap<>();
    private static final List<Path> classDirs = new ArrayList<>(), sourceDirs = new ArrayList<>(), resourceDirs = new ArrayList<>();
    private static volatile boolean busy;
    private static String root, gradlePath;
    private static String lastState = "";

    private DevReload() {}

    /** Starts watching when this is a dev run ({@code oriel.dev.root} set by the Gradle run). */
    public static void init() {
        root = System.getProperty("oriel.dev.root");
        gradlePath = System.getProperty("oriel.dev.project");
        if (root == null || gradlePath == null) return;
        addClassDir(DevReload.class);
        addClassDir(dev.oriel.kit.OrielKit.class);
        addClassDir(dev.oriel.engine.dom.Document.class);
        for (String module : List.of("kit", "engine", "common")) {
            Path src = Path.of(root, module, "src", "main", "java");
            if (Files.isDirectory(src)) sourceDirs.add(src);
            Path res = Path.of(root, module, "src", "main", "resources");
            if (Files.isDirectory(res)) resourceDirs.add(res);
        }
        // The loader's own resources: its project folder and the ones above it, up to the root (fabric/src/...).
        String loader = System.getProperty("oriel.sourceRoot");
        for (Path dir = loader == null ? null : Path.of(loader).toAbsolutePath(); dir != null && dir.startsWith(Path.of(root).toAbsolutePath()); dir = dir.getParent()) {
            Path res = dir.resolve("src/main/resources");
            if (Files.isDirectory(res) && !resourceDirs.contains(res)) resourceDirs.add(res);
            if (dir.equals(Path.of(root).toAbsolutePath())) break;
        }
        if (classDirs.isEmpty()) {
            Constants.LOG.info("Oriel dev reload: the game runs from jars here; Java hot reload is off");
            return;
        }
        snapshotClasses();
        scan(sourceDirs, ".java", sourceTimes, true);
        scan(resourceDirs, "", resourceTimes, true);
        DocumentDriver.onMessageFromAny(CHANNEL, v -> reload());
        Thread watcher = new Thread(DevReload::watch, "Oriel dev reload");
        watcher.setDaemon(true);
        watcher.start();
        Constants.LOG.info("Oriel dev reload: watching {} class, {} source and {} resource folders", classDirs.size(), sourceDirs.size(), resourceDirs.size());
    }

    private static void addClassDir(Class<?> type) {
        try {
            var source = type.getProtectionDomain().getCodeSource();
            if (source == null) return;
            Path p = Path.of(source.getLocation().toURI());
            if (Files.isDirectory(p) && !classDirs.contains(p)) classDirs.add(p);
        } catch (URISyntaxException | IllegalArgumentException | SecurityException e) {
            Constants.LOG.debug("Oriel dev reload: no folder for {}", type.getName());
        }
    }

    // ---- Watching ----

    private static void watch() {
        while (true) {
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
            if (busy) continue;
            int sources = changed(sourceDirs, ".java", sourceTimes).size(), classes = changed(classDirs, ".class", classTimes).size();
            List<Path> res = changed(resourceDirs, "", resourceTimes);
            List<Path> assets = res.stream().filter(f -> kind(f) == Kind.ASSET).toList();
            List<Path> meta = res.stream().filter(f -> kind(f) == Kind.RESTART).toList();
            if (!meta.isEmpty()) {
                show("restart", meta.size() > 1
                        ? plural("oriel.dev.reload.restart_files", meta.size(), meta.getFirst().getFileName())
                        : t("oriel.dev.reload.restart_file", meta.getFirst().getFileName()), null);
                continue;
            }
            List<String> parts = new ArrayList<>();
            if (sources > 0) parts.add(plural("oriel.dev.reload.java_sources", sources));
            else if (classes > 0) parts.add(plural("oriel.dev.reload.compiled_classes", classes));
            if (!assets.isEmpty()) parts.add(describe(assets));
            if (!parts.isEmpty()) show("changed", t("oriel.dev.reload.changed", String.join(t("oriel.dev.reload.change_separator"), parts)), t("oriel.dev.reload.action_reload"));
        }
    }

    /** What a resource change needs: nothing (pages reload themselves), a resource reload, or a restart. */
    private enum Kind { LIVE, ASSET, RESTART }

    private static Kind kind(Path file) {
        String rel = relative(file);
        String name = file.getFileName().toString();
        if (rel.startsWith("assets/") || rel.startsWith("data/")) {
            // Pages, stylesheets, scripts and their data are read from the sources when they are saved.
            boolean text = name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js") || name.endsWith(".md")
                    || name.endsWith(".json") || name.endsWith(".txt");
            return rel.matches("assets/[^/]+/ui/.*") && text ? Kind.LIVE : Kind.ASSET;
        }
        return name.endsWith(".mod.json") || name.endsWith(".toml") || name.contains("mixins") || rel.startsWith("META-INF/")
                ? Kind.RESTART : Kind.LIVE;
    }

    /** {@code file} relative to its resource folder, with forward slashes. */
    private static String relative(Path file) {
        for (Path dir : resourceDirs) if (file.startsWith(dir)) return dir.relativize(file).toString().replace('\\', '/');
        return file.getFileName().toString();
    }

    /** "资源 3 个文件（图标、字体）". */
    private static String describe(List<Path> assets) {
        java.util.LinkedHashSet<String> what = new java.util.LinkedHashSet<>();
        for (Path f : assets) {
            String rel = relative(f), name = f.getFileName().toString();
            if (rel.contains("/textures/icons/")) what.add(t("oriel.dev.reload.kind_icons"));
            else if (name.endsWith(".png")) what.add(t("oriel.dev.reload.kind_textures"));
            else if (rel.contains("/font/")) what.add(t("oriel.dev.reload.kind_fonts"));
            else if (rel.contains("/lang/")) what.add(t("oriel.dev.reload.kind_language"));
            else if (rel.contains("/sounds") || name.endsWith(".ogg")) what.add(t("oriel.dev.reload.kind_sounds"));
            else if (rel.contains("/models/") || rel.contains("/blockstates/")) what.add(t("oriel.dev.reload.kind_models"));
            else what.add(t("oriel.dev.reload.kind_resources"));
        }
        return plural("oriel.dev.reload.resources", assets.size(), String.join(t("oriel.dev.reload.list_separator"), what));
    }

    /**
     * Files under {@code dirs} ending in {@code ext} whose time differs from {@code times} (new ones included); for
     * class files, only those whose content differs too (a rebuild rewrites every file, most of them the same).
     */
    private static List<Path> changed(List<Path> dirs, String ext, Map<Path, Long> times) {
        Map<Path, Long> now = new HashMap<>();
        scan(dirs, ext, now, false);
        List<Path> out = new ArrayList<>();
        for (Map.Entry<Path, Long> e : now.entrySet()) {
            Long was = times.get(e.getKey());
            if (was != null && was.equals(e.getValue())) continue;
            if (ext.equals(".class") && was != null) {
                Long sum = sum(e.getKey()), old = classSums.get(e.getKey());
                if (old != null && old.equals(sum)) {
                    times.put(e.getKey(), e.getValue()); // the same bytes, rewritten
                    continue;
                }
            }
            out.add(e.getKey());
        }
        return out;
    }

    private static Long sum(Path file) {
        try {
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(Files.readAllBytes(file));
            return crc.getValue();
        } catch (IOException e) {
            return -1L;
        }
    }

    /** Takes the class files as they are now: times and checksums. */
    private static void snapshotClasses() {
        scan(classDirs, ".class", classTimes, true);
        classSums.clear();
        for (Path f : classTimes.keySet()) classSums.put(f, sum(f));
    }

    private static void scan(List<Path> dirs, String ext, Map<Path, Long> into, boolean clear) {
        if (clear) into.clear();
        for (Path dir : dirs) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(f -> f.toString().endsWith(ext)).forEach(f -> {
                    try {
                        into.put(f, Files.getLastModifiedTime(f).toMillis());
                    } catch (IOException ignored) {
                        // deleted while walking
                    }
                });
            } catch (IOException | RuntimeException e) {
                Constants.LOG.debug("Oriel dev reload: cannot read {}", dir);
            }
        }
    }

    // ---- Reloading ----

    /**
     * The bar's button: compile and copy resources when sources or resources changed, then swap the changed classes
     * in, reload the resources if some changed, and reopen the pages.
     */
    public static void reload() {
        if (busy) return;
        busy = true;
        Thread worker = new Thread(() -> {
            try {
                boolean java = !changed(sourceDirs, ".java", sourceTimes).isEmpty();
                List<Path> assetFiles = changed(resourceDirs, "", resourceTimes).stream().filter(f -> kind(f) == Kind.ASSET).toList();
                boolean assets = !assetFiles.isEmpty();
                String assetNote = assets ? describe(assetFiles) : null;
                if (java || assets) {
                    show("building", java ? t("oriel.dev.reload.compiling") : t("oriel.dev.reload.copying_resources"), null);
                    String error = compile();
                    if (error != null) {
                        show("failed", t("oriel.dev.reload.compile_failed", error), t("oriel.dev.reload.action_retry"));
                        return;
                    }
                    scan(sourceDirs, ".java", sourceTimes, true);
                    scan(resourceDirs, "", resourceTimes, true);
                }
                List<Path> classes = changed(classDirs, ".class", classTimes);
                Minecraft.getInstance().execute(() -> swap(classes, assetNote));
            } catch (RuntimeException e) {
                Constants.LOG.error("Oriel dev reload failed", e);
                show("failed", t("oriel.dev.reload.reload_failed", e.getMessage()), t("oriel.dev.reload.action_retry"));
            } finally {
                busy = false;
            }
        }, "Oriel dev reload build");
        worker.setDaemon(true);
        worker.start();
    }

    /** Runs the loader project's {@code classes} task; null when it worked, else the first error line. */
    private static String compile() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path wrapper = Path.of(root, windows ? "gradlew.bat" : "gradlew");
        List<String> cmd = new ArrayList<>(windows ? List.of("cmd", "/c", wrapper.toString()) : List.of(wrapper.toString()));
        cmd.addAll(List.of("--offline", "-q", gradlePath + ":classes"));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(Path.of(root).toFile()).redirectErrorStream(true);
        pb.environment().put("JAVA_HOME", System.getProperty("java.home"));
        try {
            Process process = pb.start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) lines.add(line);
            }
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return t("oriel.dev.reload.timeout");
            }
            if (process.exitValue() == 0) return null;
            for (String l : lines) if (l.contains("error:")) return l.substring(l.indexOf("error:")).trim();
            return lines.isEmpty() ? t("oriel.dev.reload.exit_code", process.exitValue()) : lines.getFirst();
        } catch (IOException e) {
            return e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return t("oriel.dev.reload.interrupted");
        }
    }

    /**
     * Swaps the changed classes into the JVM (on the client thread, between frames), reloads the resources when some
     * changed ({@code assets} says which, else null), then reopens every page.
     */
    private static void swap(List<Path> files, String assets) {
        if (assets != null) {
            show("building", t("oriel.dev.reload.reloading_resources"), null);
            Minecraft.getInstance().reloadResourcePacks().thenRun(() -> Minecraft.getInstance().execute(() -> swapClasses(files, t("oriel.dev.reload.reloaded_resources", assets))));
            return;
        }
        swapClasses(files, null);
    }

    /** {@link #swap} after the resources: the classes, then the pages; {@code reloaded} leads the result, or null. */
    private static void swapClasses(List<Path> files, String reloaded) {
        if (files.isEmpty()) {
            DocumentDriver.reloadAll();
            show("done", t("oriel.dev.reload.done", reloaded != null ? reloaded : t("oriel.dev.reload.unchanged")), null);
            return;
        }
        Instrumentation inst = instrumentation();
        if (inst == null) {
            snapshotClasses();
            show("restart", t("oriel.dev.reload.attach_denied"), null);
            return;
        }
        Map<String, List<Class<?>>> loaded = new HashMap<>();
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c.getName().startsWith("dev.oriel.")) loaded.computeIfAbsent(c.getName(), n -> new ArrayList<>()).add(c);
        }
        List<ClassDefinition> defs = new ArrayList<>();
        int fresh = 0;
        for (Path f : files) {
            String name = className(f);
            List<Class<?>> classes = name == null ? null : loaded.get(name);
            if (classes == null) {
                if (!classSums.containsKey(f)) fresh++; // a new class: the game reads it when it first needs it
                continue;
            }
            try {
                byte[] bytes = Files.readAllBytes(f);
                for (Class<?> c : classes) defs.add(new ClassDefinition(c, bytes));
            } catch (IOException e) {
                Constants.LOG.warn("Oriel dev reload: cannot read {}", f);
            }
        }
        List<String> refused = new ArrayList<>();
        try {
            inst.redefineClasses(defs.toArray(ClassDefinition[]::new));
        } catch (UnsupportedOperationException | ClassNotFoundException | java.lang.instrument.UnmodifiableClassException | LinkageError e) {
            // One at a time, to name the ones the JVM refuses (a schema change).
            for (ClassDefinition d : defs) {
                try {
                    inst.redefineClasses(d);
                } catch (UnsupportedOperationException | ClassNotFoundException | java.lang.instrument.UnmodifiableClassException | LinkageError one) {
                    refused.add(d.getDefinitionClass().getSimpleName());
                }
            }
        }
        snapshotClasses();
        DocumentDriver.reloadAll();
        if (!refused.isEmpty()) {
            String restart = plural(refused.size() > 4 ? "oriel.dev.reload.partial_restart_more" : "oriel.dev.reload.partial_restart",
                    defs.size() - refused.size(), String.join(t("oriel.dev.reload.list_separator"), refused.stream().distinct().limit(4).toList()));
            show("restart", reloaded == null ? restart : t("oriel.dev.reload.resources_restart", reloaded, restart), null);
        } else {
            String code = defs.isEmpty() ? t("oriel.dev.reload.code_updated") : plural("oriel.dev.reload.replaced_classes", defs.size());
            String added = fresh > 0 ? plural("oriel.dev.reload.added_classes", fresh) : null;
            String done = reloaded == null
                    ? added == null ? t("oriel.dev.reload.done", code) : t("oriel.dev.reload.done_added", code, added)
                    : added == null ? t("oriel.dev.reload.resources_done", reloaded, code) : t("oriel.dev.reload.resources_done_added", reloaded, code, added);
            show("done", done, null);
        }
    }

    private static String className(Path file) {
        for (Path dir : classDirs) {
            if (!file.startsWith(dir)) continue;
            String rel = dir.relativize(file).toString().replace('\\', '/');
            return rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
        }
        return null;
    }

    /** The agent's instrumentation, attaching it to this JVM the first time (needs -Djdk.attach.allowAttachSelf). */
    private static Instrumentation instrumentation() {
        Instrumentation inst = agentField();
        if (inst != null) return inst;
        try {
            Path jar = Path.of(System.getProperty("java.io.tmpdir"), "oriel-dev-agent.jar");
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Agent-Class", DevAgent.class.getName());
            manifest.getMainAttributes().putValue("Can-Redefine-Classes", "true");
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest);
                 InputStream agent = DevAgent.class.getResourceAsStream("DevAgent.class")) {
                if (agent == null) return null;
                out.putNextEntry(new JarEntry(DevAgent.class.getName().replace('.', '/') + ".class"));
                agent.transferTo(out);
                out.closeEntry();
            }
            Class<?> vm = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object machine = vm.getMethod("attach", String.class).invoke(null, Long.toString(ProcessHandle.current().pid()));
            try {
                vm.getMethod("loadAgent", String.class).invoke(machine, jar.toString());
            } finally {
                vm.getMethod("detach").invoke(machine);
            }
            return agentField();
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            Constants.LOG.warn("Oriel dev reload: attaching the agent failed", e);
            return null;
        }
    }

    private static Instrumentation agentField() {
        try {
            Class<?> agent = Class.forName(DevAgent.class.getName(), true, ClassLoader.getSystemClassLoader());
            return (Instrumentation) agent.getField("instrumentation").get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // ---- The bar ----

    /** Shows the bar on every page: {@code state} (changed, building, done, failed, restart), text, button label. */
    private static void show(String state, String text, String action) {
        JsonObject o = new JsonObject();
        o.addProperty("state", state);
        o.addProperty("text", text);
        if (action != null) o.addProperty("action", action);
        String json = o.toString();
        if (json.equals(lastState)) return;
        lastState = json;
        Minecraft.getInstance().execute(() -> DocumentDriver.patchEverywhere(CHANNEL, json));
        if (state.equals("done")) {
            // The success note goes away by itself.
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException ignored) {
                    return;
                }
                if (lastState.equals(json)) {
                    lastState = "";
                    Minecraft.getInstance().execute(() -> DocumentDriver.patchEverywhere(CHANNEL, null));
                }
            }, "Oriel dev reload note");
            t.setDaemon(true);
            t.start();
        }
    }
}
