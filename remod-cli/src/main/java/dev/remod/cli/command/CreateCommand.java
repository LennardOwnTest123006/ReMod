package dev.remod.cli.command;

import dev.remod.cli.CliCommand;
import dev.remod.cli.CommandLine;
import dev.remod.cli.Console;
import dev.remod.installer.manifest.VersionManifestService;
import dev.remod.loader.ReModVersions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * {@code remod create <name>} -- scaffolds a mod project that builds immediately.
 */
public final class CreateCommand implements CliCommand {

    @Override
    public String name() {
        return "create";
    }

    @Override
    public String description() {
        return "Create a new ReMod mod project";
    }

    @Override
    public String usage() {
        return "remod create <ProjectName> [--package dev.example.mymod]"
                + " [--minecraft 1.21.4] [--author \"Your Name\"] [--directory <path>]";
    }

    @Override
    public int run(CommandLine commandLine, Console console) throws Exception {
        String projectName = commandLine.positional(0);
        if (projectName == null || projectName.isBlank()) {
            console.error("No project name was given.", "Usage: " + usage());
            return 2;
        }
        String modId = ProjectTemplate.toModId(projectName);
        String packageName = commandLine.option("package", "dev.example." + modId);
        String minecraftVersion = commandLine.option("minecraft", defaultMinecraftVersion());
        String author = commandLine.option("author", System.getProperty("user.name", "You"));
        Path root = Paths.get(commandLine.option("directory", projectName)).toAbsolutePath();

        ProjectTemplate template;
        try {
            template = new ProjectTemplate(projectName, packageName, minecraftVersion, author);
        } catch (IllegalArgumentException e) {
            console.error(e.getMessage(),
                    "Pass a release version, for example --minecraft 1.21.4.");
            return 1;
        }

        List<Path> written;
        try {
            written = template.writeTo(root);
        } catch (java.io.IOException e) {
            console.error(e.getMessage(),
                    "Choose a different project name, or delete the existing folder.");
            return 1;
        }

        console.heading("Created " + projectName);
        console.field("Location", root.toString());
        console.field("Mod id", template.modId());
        console.field("Main class", template.mainClass());
        console.field("Minecraft", minecraftVersion);
        console.field("ReMod API", template.apiVersion().toString());
        console.blank();
        console.print("Files:");
        for (Path file : written) {
            console.bullet(root.relativize(file).toString());
        }
        console.blank();
        console.print("Next steps:");
        console.bullet("cd " + root.getFileName());
        console.bullet("./gradlew build          # produces build/libs/" + template.modId()
                + "-1.0.0.jar");
        console.bullet("./gradlew installMod     # copies it into your ReMod mods folder");
        console.blank();
        console.print("If the build cannot find the ReMod API jar, install ReMod for Minecraft "
                + minecraftVersion + " first,");
        console.print("or point gradle.properties' remodApiPath at a ReMod API jar you already"
                + " have.");
        return 0;
    }

    /**
     * The newest Minecraft release ReMod supports, from the cached manifest.
     * Falls back to a fixed hint when the manifest is unavailable offline.
     */
    private String defaultMinecraftVersion() {
        try {
            VersionManifestService service = VersionManifestService.standard();
            String latest = service.get().latestRelease().orElse(null);
            if (latest != null && ReModVersions.apiVersionFor(latest) != null) {
                return latest;
            }
        } catch (RuntimeException e) {
            // No network and no cache: fall through to the documented default.
        }
        return "1.21.4";
    }
}
