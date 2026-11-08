package dev.oriel.fabric;

import com.google.gson.JsonParser;
import dev.oriel.mod.internal.nested.NestedArchive;
import dev.oriel.mod.internal.nested.NestedCheck;
import dev.oriel.mod.internal.nested.NestedManifest;
import dev.oriel.mod.internal.nested.NestedRuntime;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class FabricNestedScan {
    private FabricNestedScan() {}

    static void start() {
        var loader = FabricLoader.getInstance();
        var mods = loader.getAllMods();
        var selected = loader.getModContainer("oriel").orElseThrow();
        var installed = mods.stream().map(m -> m.getMetadata().getId() + "=" + m.getMetadata().getVersion().getFriendlyString()).toList();
        List<Path> paths = mods.stream().filter(m -> m.getOrigin().getKind() == ModOrigin.Kind.PATH)
                .flatMap(m -> m.getOrigin().getPaths().stream()).distinct().toList();
        String selectedSource = origin(selected);
        NestedRuntime.start(loader.getConfigDir(), installed, paths, selectedSource, () -> {
            List<NestedCheck.Copy> copies = new ArrayList<>();
            for (Path path : paths) try (var archive = NestedArchive.open(path)) {
                visit(archive, path.toAbsolutePath().normalize().toString(), "", copies, 0);
            }
            var active = copies.stream().filter(c -> c.source().equals(selectedSource)).findFirst().orElseGet(() -> {
                // Development sources can have separate resource roots and no distribution manifest.
                NestedManifest manifest = null;
                var file = selected.findPath(NestedManifest.PATH);
                if (file.isPresent()) try { manifest = NestedManifest.parse(java.nio.file.Files.readString(file.get())); }
                catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                return new NestedCheck.Copy(selectedSource, selected.getContainingMod().map(m -> m.getMetadata().getName()).orElse("Oriel UI"),
                        selected.getMetadata().getVersion().getFriendlyString(), manifest);
            });
            if (copies.stream().noneMatch(c -> c.source().equals(active.source()))) copies.add(active);
            return NestedCheck.compare(active, copies);
        });
    }

    private static String origin(ModContainer mod) {
        var origin = mod.getOrigin();
        if (origin.getKind() == ModOrigin.Kind.NESTED) {
            var parent = FabricLoader.getInstance().getModContainer(origin.getParentModId()).orElseThrow();
            return origin(parent) + "!/" + origin.getParentSubLocation();
        }
        return origin.getPaths().getFirst().toAbsolutePath().normalize().toString();
    }

    private static void visit(NestedArchive archive, String source, String parent, List<NestedCheck.Copy> copies, int depth) throws IOException {
        if (depth > 16) throw new IOException("Fabric nested archive depth exceeds 16: " + source);
        String json = archive.text("fabric.mod.json");
        if (json == null) return;
        var metadata = JsonParser.parseString(json).getAsJsonObject();
        String id = metadata.get("id").getAsString();
        String name = metadata.has("name") ? metadata.get("name").getAsString() : id;
        if (id.equals("oriel")) copies.add(new NestedCheck.Copy(source, parent.isEmpty() ? name : parent,
                metadata.get("version").getAsString(), archive.manifest()));
        if (metadata.has("jars")) for (var value : metadata.getAsJsonArray("jars")) {
            String nested = value.getAsJsonObject().get("file").getAsString();
            byte[] bytes = archive.read(nested);
            if (bytes == null) throw new IOException("Missing Fabric nested archive " + source + "!/" + nested);
            try (var child = NestedArchive.open(bytes)) {
                visit(child, source + "!/" + nested, parent.isEmpty() ? name : parent + " / " + name, copies, depth + 1);
            }
        }
    }
}
