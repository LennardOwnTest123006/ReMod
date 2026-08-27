package dev.remod.compat.bridge;

import dev.remod.compat.CompatibilityLevel;
import dev.remod.compat.LoaderBridge;
import dev.remod.compat.LoaderPlatform;
import dev.remod.loader.ReModPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Bukkit, Spigot and Paper.
 *
 * <p><b>The honest answer, stated once and clearly:</b> a Bukkit, Spigot or
 * Paper plugin cannot run inside a Minecraft client, and cannot run inside a
 * vanilla server either. These are not mod loaders that attach to Minecraft --
 * they are <em>replacement server software</em>. You run
 * {@code paper-1.21.4.jar} <em>instead of</em> {@code minecraft_server.jar},
 * and plugins are written against that server's API
 * ({@code org.bukkit.*}), which only exists inside it.</p>
 *
 * <p>So ReMod does not offer a "Bukkit plugin loader", because there is no
 * honest way to build one. What it does offer is:</p>
 *
 * <ul>
 *   <li><b>Detection</b> -- if a server jar for one of these platforms is
 *       present, ReMod recognises it and explains the situation rather than
 *       failing mysteriously.</li>
 *   <li><b>A stated path</b> -- the way a ReMod server mod and a Paper plugin
 *       could ever share a server is a ReMod <em>plugin</em> running inside
 *       Paper, bridging ReMod's server-side API onto Bukkit's. That is a
 *       separate deliverable against Paper's API, not something the client-side
 *       loader can do, and it is not part of ReMod 1.0.0.</li>
 * </ul>
 */
public final class ServerPlatformBridge implements LoaderBridge {

    private final LoaderPlatform platform;
    private final String[] jarNameMarkers;

    public ServerPlatformBridge(LoaderPlatform platform, String... jarNameMarkers) {
        if (platform.kind() != LoaderPlatform.Kind.SERVER_PLUGIN_PLATFORM) {
            throw new IllegalArgumentException(platform + " is not a server plugin platform");
        }
        this.platform = platform;
        this.jarNameMarkers = jarNameMarkers;
    }

    /** Bukkit. */
    public static ServerPlatformBridge bukkit() {
        return new ServerPlatformBridge(LoaderPlatform.BUKKIT, "bukkit", "craftbukkit");
    }

    /** Spigot. */
    public static ServerPlatformBridge spigot() {
        return new ServerPlatformBridge(LoaderPlatform.SPIGOT, "spigot");
    }

    /** Paper. */
    public static ServerPlatformBridge paper() {
        return new ServerPlatformBridge(LoaderPlatform.PAPER, "paper", "purpur", "folia");
    }

    @Override
    public LoaderPlatform platform() {
        return platform;
    }

    @Override
    public CompatibilityLevel level() {
        return CompatibilityLevel.NOT_POSSIBLE;
    }

    /**
     * Looks for this platform's server jar or its {@code plugins} folder beside
     * the game directory. A Minecraft client install will normally have
     * neither, which is exactly the point.
     */
    @Override
    public Optional<Detection> detect(ReModPaths paths) {
        Path root = paths.gameDirectory();
        Path plugins = root.resolve("plugins");
        if (Files.isDirectory(root)) {
            try (java.util.stream.Stream<Path> stream = Files.list(root)) {
                Optional<Path> jar = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString()
                                    .toLowerCase(java.util.Locale.ROOT);
                            if (!name.endsWith(".jar")) {
                                return false;
                            }
                            for (String marker : jarNameMarkers) {
                                if (name.contains(marker)) {
                                    return true;
                                }
                            }
                            return false;
                        })
                        .sorted()
                        .findFirst();
                if (jar.isPresent()) {
                    return Optional.of(new Detection(platform, null, jar.get(), List.of()));
                }
            } catch (java.io.IOException e) {
                return Optional.empty();
            }
        }
        if (Files.isDirectory(plugins) && platform == LoaderPlatform.BUKKIT) {
            // A bare plugins folder tells us a plugin platform is in use, but
            // not which one; attribute it to Bukkit, the common ancestor.
            return Optional.of(new Detection(platform, null, plugins, List.of()));
        }
        return Optional.empty();
    }

    @Override
    public List<String> coexistenceNotes() {
        return List.of(
                platform.displayName() + " is server software you run instead of the vanilla"
                        + " Minecraft server, not a loader that attaches to Minecraft.",
                "A " + platform.displayName() + " server and a ReMod client can play together"
                        + " over vanilla networking, exactly as a vanilla client would.",
                "ReMod server mods and " + platform.displayName() + " plugins cannot run in the"
                        + " same server process today.");
    }

    @Override
    public String whyNotLoadable() {
        return platform.displayName() + " plugins are written against the org.bukkit API, which"
                + " exists only inside " + platform.displayName() + "'s own server jar. They are"
                + " not Minecraft mods and cannot run in a Minecraft client at all. Bridging"
                + " them would mean writing a ReMod plugin that runs inside "
                + platform.displayName() + " and maps ReMod's server API onto Bukkit's -- a"
                + " separate project against Paper's API, not something this loader can do.";
    }
}
