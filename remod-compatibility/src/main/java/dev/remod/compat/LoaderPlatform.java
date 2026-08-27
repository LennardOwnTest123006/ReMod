package dev.remod.compat;

/** The other loaders and server platforms ReMod knows about. */
public enum LoaderPlatform {

    FABRIC("Fabric", "fabric.mod.json", Kind.CLIENT_SERVER_MOD_LOADER),
    QUILT("Quilt", "quilt.mod.json", Kind.CLIENT_SERVER_MOD_LOADER),
    FORGE("Forge", "META-INF/mods.toml", Kind.CLIENT_SERVER_MOD_LOADER),
    NEOFORGE("NeoForge", "META-INF/neoforge.mods.toml", Kind.CLIENT_SERVER_MOD_LOADER),
    BUKKIT("Bukkit", "plugin.yml", Kind.SERVER_PLUGIN_PLATFORM),
    SPIGOT("Spigot", "plugin.yml", Kind.SERVER_PLUGIN_PLATFORM),
    PAPER("Paper", "paper-plugin.yml", Kind.SERVER_PLUGIN_PLATFORM);

    private final String displayName;
    private final String manifestPath;
    private final Kind kind;

    LoaderPlatform(String displayName, String manifestPath, Kind kind) {
        this.displayName = displayName;
        this.manifestPath = manifestPath;
        this.kind = kind;
    }

    public String displayName() {
        return displayName;
    }

    /** The manifest entry that identifies one of this platform's mods. */
    public String manifestPath() {
        return manifestPath;
    }

    public Kind kind() {
        return kind;
    }

    /** What sort of thing a platform is, which decides what is even possible. */
    public enum Kind {

        /** Runs inside the Minecraft client and server, like ReMod itself. */
        CLIENT_SERVER_MOD_LOADER,

        /**
         * Runs only inside a modified <em>server</em> jar and exposes a
         * server-side plugin API. Nothing of this kind can run inside a
         * Minecraft client.
         */
        SERVER_PLUGIN_PLATFORM
    }
}
