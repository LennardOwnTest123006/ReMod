package dev.remod.cli.command;

import dev.remod.common.io.IOUtil;
import dev.remod.common.version.ApiVersion;
import dev.remod.loader.ReModVersions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates a ready-to-build ReMod mod project.
 *
 * <p>The generated project compiles against the ReMod API jar the installer
 * placed in {@code remod/api/}, so a developer needs no repository, no network
 * and no extra setup -- {@code ./gradlew build} works immediately after
 * {@code remod create}.</p>
 */
public final class ProjectTemplate {

    private final String modId;
    private final String modName;
    private final String packageName;
    private final String mainClassName;
    private final String minecraftVersion;
    private final ApiVersion apiVersion;
    private final String minecraftRange;
    private final String author;

    public ProjectTemplate(String modName, String packageName, String minecraftVersion,
                           String author) {
        this.modName = modName;
        this.modId = toModId(modName);
        this.packageName = packageName;
        this.mainClassName = toClassName(modName);
        this.minecraftVersion = minecraftVersion;
        ApiVersion resolved = ReModVersions.apiVersionFor(minecraftVersion);
        if (resolved == null) {
            throw new IllegalArgumentException("ReMod has no API for Minecraft "
                    + minecraftVersion + ". Choose a release version such as 1.21.4.");
        }
        this.apiVersion = resolved;
        // Every Minecraft version ReMod supports. Because the generated mod
        // declares the portable API baseline, one jar really does cover it.
        this.minecraftRange = ">=" + dev.remod.adapter.VersionSupportTable.OLDEST_SUPPORTED
                + " <2.0";
        this.author = author;
    }

    /** Turns a project name into a legal mod id: lower case, letters and digits. */
    public static String toModId(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        if (sb.length() == 0 || !Character.isLetter(sb.charAt(0))) {
            sb.insert(0, "mod");
        }
        // ModMetadata requires at least two characters, so a one-letter name
        // must not produce a manifest that the loader would then reject.
        if (sb.length() < 2) {
            sb.append("mod");
        }
        return sb.length() > 64 ? sb.substring(0, 64) : sb.toString();
    }

    /** Turns a project name into a legal Java class name. */
    public static String toClassName(String name) {
        StringBuilder sb = new StringBuilder();
        boolean capitalise = true;
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(capitalise ? Character.toUpperCase(c) : c);
                capitalise = false;
            } else {
                capitalise = true;
            }
        }
        if (sb.length() == 0 || !Character.isJavaIdentifierStart(sb.charAt(0))) {
            sb.insert(0, "Mod");
        }
        return sb.toString();
    }

    public String modId() {
        return modId;
    }

    public String mainClass() {
        return packageName + "." + mainClassName;
    }

    public ApiVersion apiVersion() {
        return apiVersion;
    }

    /** The Minecraft range the generated manifest declares. */
    public String minecraftRange() {
        return minecraftRange;
    }

    /**
     * Writes the project.
     *
     * @return every file created, in the order it was written
     * @throws IOException when the target exists and is not empty, or writing fails
     */
    public List<Path> writeTo(Path root) throws IOException {
        if (Files.exists(root) && Files.isDirectory(root)) {
            try (java.util.stream.Stream<Path> stream = Files.list(root)) {
                if (stream.findAny().isPresent()) {
                    throw new IOException(root + " already exists and is not empty."
                            + " Choose a different name or delete the folder first.");
                }
            }
        }
        List<Path> written = new ArrayList<>();
        String packagePath = packageName.replace('.', '/');

        write(written, root.resolve("settings.gradle"), settingsGradle());
        write(written, root.resolve("build.gradle"), buildGradle());
        write(written, root.resolve("gradle.properties"), gradleProperties());
        write(written, root.resolve("README.md"), readme());
        write(written, root.resolve(".gitignore"), gitignore());
        write(written, root.resolve("src/main/resources/remod.mod.json"), manifest());
        write(written, root.resolve("src/main/java/" + packagePath + "/"
                + mainClassName + ".java"), mainSource());
        write(written, root.resolve("src/main/resources/assets/" + modId + "/lang/en_us.json"),
                languageFile());
        return written;
    }

    private void write(List<Path> written, Path file, String content) throws IOException {
        IOUtil.writeAtomically(file, content);
        written.add(file);
    }

    private String settingsGradle() {
        return "rootProject.name = '" + modId + "'\n";
    }

    private String buildGradle() {
        return "plugins {\n"
                + "    id 'java'\n"
                + "}\n"
                + "\n"
                + "group = '" + packageName + "'\n"
                + "version = project.property('modVersion')\n"
                + "\n"
                + "java {\n"
                + "    // Matches the Java version Minecraft " + minecraftVersion + " runs on.\n"
                + "    sourceCompatibility = JavaVersion.VERSION_17\n"
                + "    targetCompatibility = JavaVersion.VERSION_17\n"
                + "}\n"
                + "\n"
                + "repositories {\n"
                + "    mavenCentral()\n"
                + "}\n"
                + "\n"
                + "// The ReMod API jar the installer placed in your Minecraft folder.\n"
                + "// Override with:  ./gradlew build -PremodApiJar=/path/to/remod-api.jar\n"
                + "def remodApiJar = project.findProperty('remodApiJar')\n"
                + "        ?: \"${project.property('remodApiPath')}\"\n"
                + "\n"
                + "dependencies {\n"
                + "    // compileOnly: ReMod provides the API at runtime, so it must NOT be\n"
                + "    // bundled into your mod jar.\n"
                + "    compileOnly files(remodApiJar)\n"
                + "    testImplementation files(remodApiJar)\n"
                + "    testImplementation platform('org.junit:junit-bom:5.10.2')\n"
                + "    testImplementation 'org.junit.jupiter:junit-jupiter'\n"
                + "    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'\n"
                + "}\n"
                + "\n"
                + "tasks.withType(JavaCompile).configureEach {\n"
                + "    options.encoding = 'UTF-8'\n"
                + "}\n"
                + "\n"
                + "tasks.named('test') {\n"
                + "    useJUnitPlatform()\n"
                + "}\n"
                + "\n"
                + "tasks.named('processResources') {\n"
                + "    // Keeps remod.mod.json's version in step with gradle.properties.\n"
                + "    inputs.property('modVersion', project.version)\n"
                + "    filesMatching('remod.mod.json') {\n"
                + "        expand(modVersion: project.version)\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "tasks.named('jar') {\n"
                + "    archiveBaseName = '" + modId + "'\n"
                + "    manifest {\n"
                + "        attributes(\n"
                + "            'ReMod-Mod-Id': '" + modId + "',\n"
                + "            'ReMod-Api-Version': '" + apiVersion + "'\n"
                + "        )\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "// Copies the built mod straight into your ReMod mods folder.\n"
                + "tasks.register('installMod', Copy) {\n"
                + "    dependsOn tasks.jar\n"
                + "    from tasks.jar\n"
                + "    into project.property('remodModsPath')\n"
                + "}\n";
    }

    private String gradleProperties() {
        java.nio.file.Path minecraftDir = resolveMinecraftDirectory();
        String api = minecraftDir.resolve("remod/api").resolve(ReModVersions.apiArtifactName())
                .toString().replace("\\", "/");
        String mods = minecraftDir.resolve("remod/mods").toString().replace("\\", "/");
        boolean apiPresent = java.nio.file.Files.isRegularFile(java.nio.file.Paths.get(api));
        return "# Your mod's own version. Bump it when you release.\n"
                + "modVersion=1.0.0\n"
                + "\n"
                + "# The ReMod API jar to compile against, put here by the ReMod installer.\n"
                + "# The same jar works for every Minecraft version, so this path does not\n"
                + "# change when you install ReMod for another one.\n"
                + (apiPresent ? "" : "# NOTE: this file was not found when the project was"
                        + " generated. Install ReMod\n#       for any Minecraft version, or"
                        + " edit the path below.\n")
                + "remodApiPath=" + api + "\n"
                + "\n"
                + "# Where './gradlew installMod' copies the built jar.\n"
                + "remodModsPath=" + mods + "\n"
                + "\n"
                + "org.gradle.jvmargs=-Xmx1g\n";
    }

    /** The Minecraft directory that actually exists on this machine, if any. */
    private static java.nio.file.Path resolveMinecraftDirectory() {
        java.nio.file.Path found = dev.remod.common.io.Platform.findExistingMinecraftDirectory();
        return found != null ? found
                : dev.remod.common.io.Platform.defaultMinecraftDirectory();
    }

    /** True when the API jar the generated project points at is already present. */
    public boolean isApiJarInstalled() {
        return java.nio.file.Files.isRegularFile(resolveMinecraftDirectory()
                .resolve("remod/api").resolve(ReModVersions.apiArtifactName()));
    }

    private String manifest() {
        return "{\n"
                + "  \"schema\": 1,\n"
                + "  \"id\": \"" + modId + "\",\n"
                + "  \"name\": \"" + modName + "\",\n"
                + "  \"version\": \"${modVersion}\",\n"
                + "  \"author\": \"" + author + "\",\n"
                + "  \"description\": \"A ReMod mod.\",\n"
                // The portable API baseline plus a range: one jar, every
                // Minecraft version ReMod supports. Narrow either if your mod
                // turns out to need a specific series.
                + "  \"minecraft\": \"" + minecraftRange + "\",\n"
                + "  \"remod_api\": \"" + apiVersion.baseline().raw() + "\",\n"
                + "  \"side\": \"common\",\n"
                + "  \"entrypoints\": [\"" + mainClass() + "\"],\n"
                + "  \"dependencies\": []\n"
                + "}\n";
    }

    private String mainSource() {
        return "package " + packageName + ";\n"
                + "\n"
                + "import dev.remod.api.ReModContext;\n"
                + "import dev.remod.api.ReModMod;\n"
                + "import dev.remod.api.command.CommandBuilder;\n"
                + "import dev.remod.api.config.ConfigSpec;\n"
                + "import dev.remod.api.event.lifecycle.ModsLoadedEvent;\n"
                + "import dev.remod.api.game.Identifier;\n"
                + "import dev.remod.api.game.Text;\n"
                + "import dev.remod.api.registry.ItemDefinition;\n"
                + "\n"
                + "/**\n"
                + " * " + modName + ".\n"
                + " *\n"
                + " * <p>Every ReMod mod starts here: implement {@link ReModMod} and name this\n"
                + " * class in remod.mod.json. See tutorial.txt for the full walkthrough.</p>\n"
                + " */\n"
                + "public class " + mainClassName + " implements ReModMod {\n"
                + "\n"
                + "    /** Your mod id. Use it as the namespace for everything you register. */\n"
                + "    public static final String MOD_ID = \"" + modId + "\";\n"
                + "\n"
                + "    private static final ConfigSpec CONFIG = ConfigSpec.builder()\n"
                + "            .comment(\"Printed to the log when the mod starts.\")\n"
                + "            .define(\"greeting\", \"Hello from " + modName + "!\")\n"
                + "            .comment(\"Set to false to keep quiet.\")\n"
                + "            .define(\"enabled\", true)\n"
                + "            .build();\n"
                + "\n"
                + "    @Override\n"
                + "    public void onPreInitialize(ReModContext context) {\n"
                + "        // Attach the schema; the file is created with defaults on first run.\n"
                + "        context.config().withSpec(CONFIG);\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public void onInitialize(ReModContext context) {\n"
                + "        if (context.config().getBoolean(\"enabled\")) {\n"
                + "            context.logger().info(context.config().getString(\"greeting\"));\n"
                + "        }\n"
                + "\n"
                + "        // Register an item.\n"
                + "        context.registries().items().register(\n"
                + "                ItemDefinition.builder(Identifier.of(MOD_ID, \"example_item\"))\n"
                + "                        .displayName(Text.literal(\"Example Item\"))\n"
                + "                        .maxStackSize(16)\n"
                + "                        .build());\n"
                + "\n"
                + "        // Register a command: /" + modId + "\n"
                + "        context.commands().register(CommandBuilder.create(MOD_ID)\n"
                + "                .description(\"" + modName + " information\")\n"
                + "                .executes(command -> {\n"
                + "                    command.source().sendFeedback(\n"
                + "                            Text.literal(\"" + modName + " \"\n"
                + "                                    + context.modVersion() + \" is running.\"));\n"
                + "                    return 1;\n"
                + "                }));\n"
                + "\n"
                + "        // React to an event.\n"
                + "        context.events().subscribe(ModsLoadedEvent.class, event ->\n"
                + "                context.logger().info(\"ReMod finished loading \"\n"
                + "                        + event.modCount() + \" mod(s).\"));\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public void onShutdown(ReModContext context) {\n"
                + "        context.logger().info(\"Goodbye from " + modName + "\");\n"
                + "    }\n"
                + "}\n";
    }

    private String languageFile() {
        return "{\n"
                + "  \"item." + modId + ".example_item\": \"Example Item\"\n"
                + "}\n";
    }

    private String readme() {
        return "# " + modName + "\n"
                + "\n"
                + "A mod for [ReMod](https://github.com/remod), the Minecraft: Java Edition mod"
                + " loader.\n"
                + "\n"
                + "- Mod id: `" + modId + "`\n"
                + "- Minecraft: " + apiVersion.minecraftSeries() + ".x\n"
                + "- ReMod API: `" + apiVersion + "`\n"
                + "\n"
                + "## Building\n"
                + "\n"
                + "```\n"
                + "./gradlew build\n"
                + "```\n"
                + "\n"
                + "The mod jar appears in `build/libs/`.\n"
                + "\n"
                + "## Installing it while you work\n"
                + "\n"
                + "```\n"
                + "./gradlew installMod\n"
                + "```\n"
                + "\n"
                + "This copies the jar into your ReMod mods folder. Adjust `remodModsPath` in\n"
                + "`gradle.properties` if your Minecraft folder is somewhere else.\n"
                + "\n"
                + "## Checking it loads without starting Minecraft\n"
                + "\n"
                + "```\n"
                + "java -jar ReMod.jar test --mods build/libs\n"
                + "```\n"
                + "\n"
                + "## Where to look next\n"
                + "\n"
                + "- `src/main/java/" + packageName.replace('.', '/') + "/" + mainClassName
                + ".java` -- your mod's entrypoint\n"
                + "- `src/main/resources/remod.mod.json` -- your mod's manifest\n"
                + "- `tutorial.txt` in the ReMod distribution -- a full beginner's guide\n";
    }

    private String gitignore() {
        return "build/\n"
                + ".gradle/\n"
                + "*.class\n"
                + ".idea/\n"
                + "*.iml\n"
                + ".vscode/\n";
    }
}
