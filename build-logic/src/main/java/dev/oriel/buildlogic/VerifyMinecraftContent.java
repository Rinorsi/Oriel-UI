package dev.oriel.buildlogic;

import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Checks local origin and version of generated artifacts excluded from dependency hashes. */
public abstract class VerifyMinecraftContent extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getMinecraftJars();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<String> getExpectedFilePattern();

    @Internal
    public abstract DirectoryProperty getLocalRepository();

    @TaskAction
    public void verify() throws IOException {
        var files = getMinecraftJars().getFiles();
        if (files.size() != 1) throw new GradleException(GeneratedMinecraftArtifact.failure(
                getMinecraftVersion().get(), files.toString(), "one local JAR matching " + getExpectedFilePattern().get(),
                files.size() + " JARs"));
        var jar = files.iterator().next();
        String failure = GeneratedMinecraftArtifact.check(jar.toPath(), getLocalRepository().get().getAsFile().toPath(),
                getMinecraftVersion().get(), getExpectedFilePattern().get());
        if (failure != null) throw new GradleException(failure);
        getLogger().lifecycle("Verified local Loom output origin: " + jar);
    }
}
