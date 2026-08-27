package dev.remod.cli.command;

import dev.remod.cli.CliCommand;
import dev.remod.cli.CommandLine;
import dev.remod.cli.Console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code remod build} -- builds a mod project.
 *
 * <p>ReMod does not reimplement a build system. It runs the project's own
 * Gradle build, preferring the wrapper when the project has one, and streams
 * the output through. What it adds is the part that is easy to get wrong:
 * finding the produced jar, checking its manifest is present and valid, and
 * saying exactly where the file is.</p>
 */
public final class BuildCommand implements CliCommand {

    @Override
    public String name() {
        return "build";
    }

    @Override
    public String description() {
        return "Build a ReMod mod project and verify the result";
    }

    @Override
    public String usage() {
        return "remod build [--directory <path>] [--task build]";
    }

    @Override
    public int run(CommandLine commandLine, Console console) throws Exception {
        Path project = Paths.get(commandLine.option("directory", ".")).toAbsolutePath()
                .normalize();
        if (!Files.isDirectory(project)) {
            console.error(project + " does not exist.",
                    "Run remod build from inside your mod project, or pass --directory.");
            return 1;
        }
        if (!Files.isRegularFile(project.resolve("build.gradle"))
                && !Files.isRegularFile(project.resolve("build.gradle.kts"))) {
            console.error(project + " does not look like a Gradle project.",
                    "Create one with 'remod create MyMod', or pass --directory pointing at"
                            + " your project.");
            return 1;
        }

        List<String> command = gradleCommand(project, commandLine.option("task", "build"));
        console.print("Running: " + String.join(" ", command));
        console.blank();

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(project.toFile());
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            console.error("Could not start Gradle: " + e.getMessage(),
                    "Install Gradle, or run 'gradle wrapper' once in your project so it has"
                            + " a ./gradlew script.");
            return 1;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                console.print(line);
            }
        }
        int exit = process.waitFor();
        if (exit != 0) {
            console.error("The Gradle build failed (exit code " + exit + ").",
                    "Read the compiler output above; the first error is usually the real one.");
            return exit;
        }

        console.heading("Build succeeded");
        List<Path> jars = builtJars(project);
        if (jars.isEmpty()) {
            console.print("The build succeeded but produced no jar in build/libs.");
            return 0;
        }
        for (Path jar : jars) {
            console.field("Mod jar", jar.toString());
            String manifest = readManifest(jar);
            if (manifest == null) {
                console.print("    Warning: this jar has no remod.mod.json, so ReMod will not"
                        + " load it.");
                console.print("    Check src/main/resources/remod.mod.json exists.");
            } else {
                console.print("    Manifest: " + manifest);
            }
        }
        return 0;
    }

    /** Prefers the project's own Gradle wrapper, falling back to a system Gradle. */
    private static List<String> gradleCommand(Path project, String task) {
        List<String> command = new ArrayList<>();
        boolean windows = dev.remod.common.io.Platform.isWindows();
        Path wrapper = project.resolve(windows ? "gradlew.bat" : "gradlew");
        if (Files.isRegularFile(wrapper)) {
            command.add(wrapper.toString());
        } else {
            command.add(windows ? "gradle.bat" : "gradle");
        }
        command.add(task);
        return command;
    }

    private static List<Path> builtJars(Path project) throws IOException {
        Path libs = project.resolve("build/libs");
        List<Path> jars = new ArrayList<>();
        if (!Files.isDirectory(libs)) {
            return jars;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(libs)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().endsWith("-sources.jar"))
                    .sorted()
                    .forEach(jars::add);
        }
        return jars;
    }

    /** Reads the mod's identity out of the built jar, or {@code null} when absent. */
    private static String readManifest(Path jar) {
        try {
            String json = dev.remod.common.io.SafeZip.readEntry(jar,
                    dev.remod.api.mod.ModMetadata.FILE_NAME);
            if (json == null) {
                return null;
            }
            dev.remod.api.mod.ModMetadata metadata =
                    dev.remod.api.mod.ModMetadata.parse(json, jar.getFileName().toString());
            return metadata.id() + " " + metadata.version().raw()
                    + " (Minecraft " + metadata.minecraft().raw()
                    + ", API " + metadata.apiVersion() + ")";
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
