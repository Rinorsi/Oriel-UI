package dev.oriel.neoforge;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonParser;
import dev.oriel.mod.internal.nested.NestedArchive;
import dev.oriel.mod.internal.nested.NestedCheck;
import dev.oriel.mod.internal.nested.NestedManifest;
import dev.oriel.mod.internal.nested.NestedRuntime;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.locating.IModFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class NeoForgeNestedScan {
    private NeoForgeNestedScan() {}

    static void start() {
        var mods = ModList.get();
        var selected = mods.getModFileById("oriel").getFile();
        String version = mods.getMods().stream().filter(m -> m.getModId().equals("oriel")).findFirst().orElseThrow().getVersion().toString();
        var installed = mods.getMods().stream().map(m -> m.getModId() + "=" + m.getVersion()).toList();
        List<Path> paths = mods.getModFiles().stream().map(f -> root(f.getFile()).getFilePath()).distinct().toList();
        String selectedRoot = root(selected).getFilePath().toAbsolutePath().normalize().toString();
        NestedRuntime.start(FMLPaths.CONFIGDIR.get(), installed, paths, selectedRoot + ":" + version, () -> {
            List<NestedCheck.Copy> copies = new ArrayList<>();
            for (Path path : paths) try (var archive = NestedArchive.open(path)) {
                visit(archive, path.toAbsolutePath().normalize().toString(), "", copies, 0);
            }
            NestedManifest manifest;
            try (var stream = NeoForgeNestedScan.class.getResourceAsStream("/" + NestedManifest.PATH)) {
                manifest = stream == null ? null : NestedManifest.parse(new String(NestedArchive.bounded(stream, NestedManifest.MAX_BYTES), StandardCharsets.UTF_8));
            }
            var active = copies.stream().filter(c -> (c.source().equals(selectedRoot) || c.source().startsWith(selectedRoot + "!/"))
                    && c.version().equals(version) && (manifest == null || c.coreHash().equals(manifest.coreHash())))
                    .findFirst().orElseGet(() -> new NestedCheck.Copy(selectedRoot,
                            root(selected).getModInfos().stream().map(m -> m.getDisplayName()).reduce((a, b) -> a + " / " + b).orElse("Oriel UI"), version, manifest));
            if (copies.stream().noneMatch(c -> c.source().equals(active.source()))) copies.add(active);
            return NestedCheck.compare(active, copies);
        });
    }

    private static IModFile root(IModFile file) {
        while (file.getDiscoveryAttributes().parent() != null) file = file.getDiscoveryAttributes().parent();
        return file;
    }

    private static void visit(NestedArchive archive, String source, String parent, List<NestedCheck.Copy> copies, int depth) throws IOException {
        if (depth > 16) throw new IOException("NeoForge nested archive depth exceeds 16: " + source);
        String toml = archive.text("META-INF/neoforge.mods.toml");
        String name = parent;
        if (toml != null) {
            var config = new TomlParser().parse(toml);
            List<UnmodifiableConfig> mods = config.getOrElse("mods", List.of());
            String display = mods.stream().map(m -> m.<String>getOrElse("displayName", m.<String>get("modId"))).reduce((a, b) -> a + " / " + b).orElse(source);
            name = parent.isEmpty() ? display : parent;
            for (var mod : mods) if ("oriel".equals(mod.get("modId"))) {
                var manifest = archive.manifest();
                String version = mod.get("version");
                if ("${file.jarVersion}".equals(version) && manifest != null) version = manifest.version();
                copies.add(new NestedCheck.Copy(source, name, version, manifest));
            }
        }
        String metadata = archive.text("META-INF/jarjar/metadata.json");
        if (metadata == null) return;
        var json = JsonParser.parseString(metadata).getAsJsonObject();
        if (!json.has("jars")) return;
        for (var value : json.getAsJsonArray("jars")) {
            String nested = value.getAsJsonObject().get("path").getAsString();
            byte[] bytes = archive.read(nested);
            if (bytes == null) throw new IOException("Missing NeoForge nested archive " + source + "!/" + nested);
            try (var child = NestedArchive.open(bytes)) { visit(child, source + "!/" + nested, name, copies, depth + 1); }
        }
    }
}
