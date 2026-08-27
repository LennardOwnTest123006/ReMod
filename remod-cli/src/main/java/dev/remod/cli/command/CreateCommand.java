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
        console.field("ReMod API", template.apiVersion().baseline().raw()
                + " (portable)");
        console.field("Minecraft", template.minecraftRange()
                + " -- one jar covers all of it");
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
        if (template.isApiJarInstalled()) {
            console.print("The build is already pointed at your installed ReMod API jar.");
        } else {
            console.print("No ReMod API jar was found on this machine, so gradle.properties"
                    + " points at where");
            console.print("the installer will put one. Install ReMod for any Minecraft"
                    + " version, or edit that path.");
        }
        console.blank();
        console.print("Your mod declares the portable API baseline and a wide Minecraft range,"
                + " so the one");
        console.print("jar loads on every version ReMod supports. Narrow 'minecraft' in"
                + " remod.mod.json to");
        console.print("what you have actually tested before you publish.");
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
