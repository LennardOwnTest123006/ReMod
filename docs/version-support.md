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
| 1.21.x | Supported | yes | yes | yes | commands yes, content no |
| 1.20.x | Supported | yes | yes | yes | commands yes, content no |
| 1.19.x | Supported | yes | yes | yes | commands yes, content no |
| 1.18.x | Partial | yes | yes | yes | commands yes, content no |
| 1.17.x | Partial | yes | yes | yes | commands yes, content no |
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

## The safe default: Minecraft always starts

In-game binding is **off by default**, because the layer that reaches into a
running Minecraft is unverified against every real build and a class-loader
problem there could stop the game launching. With it off, ReMod launches
Minecraft exactly as vanilla and loads your mods -- the proven, safe path.

To try binding commands into the live game, add
`-Dremod.experimental.gamebinding=true` to the installation's JVM arguments in
the launcher. If Minecraft then fails to start, remove it and the game launches
normally.

Either way, `remod play` runs your mods' commands against a real simulated
player and shows them working, with no Minecraft and no uncertainty. That is
the reliable way to see a mod do what it claims.

## Commands: the in-game binding (experimental, opt-in)

When `-Dremod.experimental.gamebinding=true` is set, commands registered by a
mod are inserted into Minecraft's own command tree, so `/fly` becomes a real
command in game rather than "Unknown command".

Three pieces make that work, and the first is the interesting one:

**1. A transforming class loader.** ReMod's launch wrapper already runs before
Minecraft. It now loads the game through its own class loader, which passes every
game class through ReMod's transformers on the way in. No `-javaagent`, no
patched jar on disk — the same approach Forge's ModLauncher and the old
LaunchWrapper take.

**2. Finding the command class without mappings.** Minecraft's `Commands` class
is called something unpredictable in a stock jar. But it holds a field of type
`com.mojang.brigadier.CommandDispatcher`, and **Brigadier ships unobfuscated** —
it is a separate Mojang library whose names survive. So ReMod does not look for
a name at all: it looks for the class declaring a field of that type, and injects
a callback at the end of its constructor. That works on any version, obfuscated
or not, with no mapping file involved.

**3. A Brigadier bridge.** A mod's `CommandSpec` becomes a real Brigadier node
tree — literals, arguments, subcommands, aliases as redirects, permission levels
as `requires(...)`. Built reflectively, so ReMod carries no Brigadier dependency
to version-match against whatever Minecraft bundles.

Mods register during `INIT`, long before Minecraft builds its dispatcher, so
commands are queued and flushed the moment the hook fires.

## Game internals: mappings

Reaching a field like `Abilities.mayfly` still needs to know it is called `c` in
this particular build. Mojang publishes that mapping per version and names it in
the version JSON under `downloads.client_mappings`, so ReMod's installer now
downloads it to `remod/mappings/<version>.txt` and resolves names through it.

Absence is a supported state rather than a failure: a development environment
runs deobfuscated, where the readable name *is* the runtime name and an empty
mapping set falls through correctly.

## What is still not bound

**Items, blocks and creative tabs.** Registering content into Minecraft's own
registries has to happen *inside* the game's bootstrap, before it freezes them —
a different and larger injection than hooking a constructor. `bindItem`,
`bindBlock` and `bindCreativeTab` still return `false`, and
`capabilities()` reports `COMMANDS` only.

## What is verified, and what is not

Being precise about this matters more than the feature list.

**Verified by tests** (268 of them): the class loader's delegation rules; hook
injection into a class shaped like Minecraft's `Commands`; that the hook fires
with the game's own dispatcher, fully built; that the Brigadier bridge produces
the right node tree, including the two-overload `then` trap that silently broke
every command with subcommands until a test caught it; the mapping parser.

**Not verified**: that Minecraft's real `Commands` class has exactly that shape
on every version; that the launcher's classpath lets ReMod's loader win the race
for game classes; that the reflective field access into `Abilities` lands. None
of this could be run against a real Minecraft here. The mechanism is sound and
tested; whether it fits the real game is the open question, and the ReMod log
says clearly which parts attached on any given launch.

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
