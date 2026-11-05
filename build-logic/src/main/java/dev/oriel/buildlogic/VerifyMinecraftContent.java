package dev.oriel.buildlogic;

import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Content verification for precisely pinned Loom-generated Minecraft artifacts. */
public abstract class VerifyMinecraftContent extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getMinecraftJars();

    @Input
    public abstract Property<String> getExpectedSha256();

    @TaskAction
    public void verify() throws IOException {
        var files = getMinecraftJars().getFiles();
        if (files.size() != 1) throw new GradleException("Expected one generated Minecraft JAR, found " + files.size());
        var jar = files.iterator().next();
        String actual = CanonicalJarDigest.sha256(jar.toPath());
        String expected = getExpectedSha256().get();
        if (!expected.matches("[0-9a-f]{64}") || !expected.equals(actual)) {
            throw new GradleException("Minecraft content verification failed for " + jar.getName()
                    + ": expected " + expected + ", actual " + actual);
        }
        getLogger().lifecycle("Verified Minecraft entry content: " + jar.getName());
    }
}
