import java.nio.file.Files;
import java.nio.file.Path;
import dev.oriel.buildlogic.GeneratedMinecraftArtifact;

/** Access-widener outputs change names/bytes; only local origin and version are stable. */
public final class GeneratedMinecraftProbe {
    private GeneratedMinecraftProbe() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Path repository = Files.createDirectories(dir.resolve("loom-cache/minecraftMaven"));
        String version = "26.1.2";
        String pattern = "minecraft-merged-[0-9a-f]{10}-26[.]1[.]2[.]jar";
        for (String hash : new String[]{"012345abcd", "abcdef1234"}) {
            Path output = repository.resolve("net/minecraft/minecraft-merged-" + hash + "/" + version
                    + "/minecraft-merged-" + hash + "-" + version + ".jar");
            Files.createDirectories(output.getParent());
            Files.writeString(output, "Different widened class bytes: " + hash);
            if (GeneratedMinecraftArtifact.check(output, repository, version, pattern) != null)
                throw new AssertionError("Local widened output rejected");
        }
        for (String relative : new String[]{
                "modules-2/26.1.2/minecraft-merged-012345abcd-26.1.2.jar",
                "loom-cache/minecraftMaven/26.2/minecraft-merged-012345abcd-26.1.2.jar",
                "loom-cache/minecraftMaven/26.1.2/minecraft-merged-012345abcd-26.2.jar"}) {
            Path wrong = dir.resolve(relative);
            Files.createDirectories(wrong.getParent());
            Files.writeString(wrong, "unexpected");
            String error = GeneratedMinecraftArtifact.check(wrong, repository, version, pattern);
            if (error == null) throw new AssertionError("Unexpected artifact accepted: " + wrong);
            for (String required : new String[]{version, wrong.toString(), "expected", "actual",
                    "gradle/generated-minecraft.json", "writeDependencyVerification", "gradle/verification-metadata.xml"}) {
                if (!error.contains(required)) throw new AssertionError("Missing diagnostic: " + required);
            }
        }
        System.out.println("PASS");
    }
}
