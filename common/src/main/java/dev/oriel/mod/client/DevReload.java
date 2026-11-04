package dev.oriel.mod.client;

import com.google.gson.JsonObject;
import dev.oriel.mod.Constants;
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
 * Java hot reload for dev runs: watches the Java sources and the compiled classes the game runs from, shows every
 * page a bar when they change ({@code oriel-dev} data field; Shell draws it) with a 热重载 button, and on that button
 * compiles (Gradle, the loader project's {@code classes}), swaps the changed classes into the running JVM (the JDK's
 * class redefinition, through {@link DevAgent} attached to this process) and reopens the pages. What the JVM cannot
 * swap (a field or a method added, removed or renamed) is named in the bar as needing a restart.
 */
public final class DevReload {
    static final String CHANNEL = "oriel-dev";
    private static final long POLL_MS = 1500;

    private static final Map<Path, Long> classTimes = new HashMap<>(), sourceTimes = new HashMap<>();
    /** The content checksum of each class file, so a build that rewrites files unchanged swaps nothing. */
    private static final Map<Path, Long> classSums = new HashMap<>();
    private static final List<Path> classDirs = new ArrayList<>(), sourceDirs = new ArrayList<>();
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
        }
        if (classDirs.isEmpty()) {
            Constants.LOG.info("Oriel dev reload: the game runs from jars here; Java hot reload is off");
            return;
        }
        snapshotClasses();
        scan(sourceDirs, ".java", sourceTimes, true);
        DocumentDriver.onMessageFromAny(CHANNEL, v -> reload());
        Thread watcher = new Thread(DevReload::watch, "Oriel dev reload");
        watcher.setDaemon(true);
        watcher.start();
        Constants.LOG.info("Oriel dev reload: watching {} class and {} source folders", classDirs.size(), sourceDirs.size());
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
            if (sources > 0) show("changed", "Java 源码有 " + sources + " 个文件改动", "热重载");
            else if (classes > 0) show("changed", "检测到 " + classes + " 个类已重新编译", "热重载");
        }
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

    /** The bar's button: compile when sources changed, then swap the changed classes in and reopen the pages. */
    static void reload() {
        if (busy) return;
        busy = true;
        Thread worker = new Thread(() -> {
            try {
                if (!changed(sourceDirs, ".java", sourceTimes).isEmpty()) {
                    show("building", "正在编译…", null);
                    String error = compile();
                    if (error != null) {
                        show("failed", "编译失败：" + error, "重试");
                        return;
                    }
                    scan(sourceDirs, ".java", sourceTimes, true);
                }
                List<Path> classes = changed(classDirs, ".class", classTimes);
                Minecraft.getInstance().execute(() -> swap(classes));
            } catch (RuntimeException e) {
                Constants.LOG.error("Oriel dev reload failed", e);
                show("failed", "热重载出错：" + e.getMessage(), "重试");
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
                return "超时";
            }
            if (process.exitValue() == 0) return null;
            for (String l : lines) if (l.contains("error:")) return l.substring(l.indexOf("error:")).trim();
            return lines.isEmpty() ? "退出码 " + process.exitValue() : lines.getFirst();
        } catch (IOException e) {
            return e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "被打断";
        }
    }

    /** Swaps the changed classes into the JVM (on the client thread, between frames), then reopens every page. */
    private static void swap(List<Path> files) {
        if (files.isEmpty()) {
            show("done", "没有需要替换的类", null);
            return;
        }
        Instrumentation inst = instrumentation();
        if (inst == null) {
            show("restart", "无法热替换（JVM 不允许自连接）：需要重启游戏", null);
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
            show("restart", "已替换 " + (defs.size() - refused.size()) + " 个类；需要重启游戏：" + String.join("、", refused.stream().distinct().limit(4).toList())
                    + (refused.size() > 4 ? " 等" : "") + "（新增或删除了字段、方法）", null);
        } else {
            show("done", (defs.isEmpty() ? "代码已更新" : "已热替换 " + defs.size() + " 个类") + (fresh > 0 ? "，新增 " + fresh + " 个类" : "") + "，页面已重新打开", null);
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
