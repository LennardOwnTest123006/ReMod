# Minecraft version support

This document states exactly what ReMod does on each Minecraft version, and
what it does not do. It is deliberately blunt: a loader that overstates its
capabilities produces mods that appear to load and then silently do nothing,
which is worse than a clear refusal.

The machine-readable version of this table lives in
`remod-version-adapters/src/main/java/dev/remod/adapter/VersionSupportTable.java`
and is what the installer's version list and the `remod list versions` command
both read, so this page cannot drift away from the code.

## The table

| Minecraft | Level | Installs a launcher profile | Starts the game | Loads mods | Binds content into the game |
| --- | --- | --- | --- | --- | --- |
| 1.21.x | Supported | yes | yes | yes | no — see below |
| 1.20.x | Supported | yes | yes | yes | no — see below |
| 1.19.x | Supported | yes | yes | yes | no — see below |
| 1.18.x | Partial | yes | yes | yes | no |
| 1.17.x | Partial | yes | yes | yes | no |
| 1.16.5 and older | Not supported | no | — | — | — |
| Weekly snapshots (`24w14a`) | Not supported | no | — | — | — |
| A release newer than this build knows | Partial | yes | yes | yes | no |

## What "loads mods" means

On every supported version, ReMod:

- installs a launcher profile the official Minecraft Launcher recognises;
- runs as the launch wrapper, ahead of Minecraft's own `main`;
- discovers mods in `.minecraft/remod/mods`, validates their manifests, resolves
  their dependencies and load order, and rejects incompatible ones with a full
  explanation;
- constructs each mod and runs the complete lifecycle — `PRE_INIT`, `INIT`,
  `POST_INIT`, and `CLIENT_INIT` or `SERVER_INIT`, then `SHUTDOWN`;
- gives every mod its own logger, configuration file, data directory, resource
  loader, event bus view and registries;
- records everything a mod registers, so mods can query each other's content;
- hands control to Minecraft, which then starts normally.

That is a working mod loader for anything a mod does in its own process. It is
what the example mods and the generated MDK project exercise, and it is covered
end to end by ReMod's tests.

## What "binds content into the game" means, and why it is not active

Registering an item so that it appears in Minecraft's own registry — so a player
can hold it — requires calling into Minecraft's internals. Two things stand in
the way, and neither is solved by writing more of the same kind of code:

**1. Minecraft ships obfuscated.** A stock launcher install has no class called
`net.minecraft.core.registries.BuiltInRegistries`; it is called something like
`fx`, and the name changes every release. Mojang publishes official mappings,
but applying them means remapping either the game or the mod at install time.
ReMod does not ship a remapper.

**2. Minecraft freezes its registries during startup.** Even with the right
names, registration has to happen inside the game's own bootstrap, not after it.
Reaching that point means transforming bytecode before the game class loads —
which is what Forge's ModLauncher and Fabric's Mixin bootstrap exist to do.
ReMod is a plain launch wrapper with no transformation layer.

So the adapter probes for the mapped names at attach time and reports the
result. `GameBridge.capabilities()` returns an empty set on every version,
every `bind*` method returns `false`, and `context.game().isGameAttached()`
tells a mod the truth. Nothing pretends.

Closing this gap — a mapping layer plus a transformation layer — is the next
milestone for ReMod, and it is the reason the `GameBridge` and
`MinecraftVersionAdapter` interfaces exist in the shape they do: when it lands,
it lands behind those interfaces, and no mod, and no part of the loader, has to
change.

## Why 1.17 is the floor

1.17 is where Minecraft moved to Java 17 and to the modern
`net.minecraft.client.main.Main` entry point with a bundled library list in its
version JSON. Everything from 1.17 up shares one launch shape, which is why a
single adapter covers the range. Below it the version JSON schema, the required
Java version and the class layout all differ enough that claiming support would
mean shipping something untested — so ReMod refuses to install instead.

## Why weekly snapshots are refused

A snapshot id such as `24w14a` does not say which release it belongs to. ReMod
keys adapters and API versions off the release series, so it cannot tell which
adapter would apply. Guessing would let ReMod claim a compatibility it has no
way to honour, so `MinecraftVersions.series` returns `null` for snapshots and
the installer declines them. Pre-releases and release candidates
(`1.21-pre1`, `1.20.2-rc2`) *do* carry a series and are supported normally.

## A release newer than this build

ReMod reads Mojang's live version manifest, so a Minecraft version released
after this build appears in the list immediately. Because the modern launch
shape has been stable since 1.17, such a version is treated as **Partial**: the
install proceeds and mods load, with a note in the installer saying it has not
been tested. That is the honest middle ground between refusing something that
probably works and claiming something that has never been run.
