# ReMod

A mod loader for Minecraft: Java Edition.

ReMod installs itself as an ordinary Minecraft Launcher installation, loads mods
from its own folder, and gives mod authors a clean, version-independent API with
a project generator, a build command and a test runner that does not need to
start the game.

```
java -jar ReMod.jar                    # opens the installer
java -jar ReMod.jar install 1.21.4     # or install from the command line
java -jar ReMod.jar create MyMod       # scaffold a mod project
cd MyMod && ./gradlew build            # build it
java -jar ReMod.jar play --mods build/libs   # RUN your mod and watch it work
```

## Seeing a mod actually work, without Minecraft

`remod play` loads your mods into a real, in-memory single-player world -- a
player whose flight state genuinely changes, a server that genuinely is
single-player -- and runs your commands through the mod's own code. When
`/fly` flips the player from grounded to flying, that is the mod doing it.

```
$ java -jar ReMod.jar play --mods build/libs --run "/fly ; /fly status"

$ /fly
  [reply] Flight enabled. Double-tap jump.
  -> flight allowed: false -> true,  flying: false -> false
$ /fly status
  [reply] Flight allowed: true
  [reply] Speed: 0.05
```

No Minecraft, no obfuscation, no uncertainty -- this is the mod's real logic,
and it is covered by tests. Run it with no `--run` for an interactive console
where you type commands yourself.

## What works today, precisely

ReMod 1.0.0 is a working loader for everything a mod does in its own process,
and it is explicit about the one thing it does not yet do.

**It does:**

- install a launcher profile the official Minecraft Launcher recognises, using
  the launcher's own `inheritsFrom` mechanism — no Minecraft file is copied,
  patched or redistributed;
- run ahead of Minecraft's own `main`, load mods, then hand control to the game;
- discover mods, validate manifests, resolve versions and dependencies, sort
  load order, and reject incompatible mods with an explanation naming what was
  expected, what was found and what to do;
- run the full lifecycle — `PRE_INIT`, `INIT`, `POST_INIT`, `CLIENT_INIT` or
  `SERVER_INIT`, `SHUTDOWN` — giving each mod its own logger, config file, data
  directory, resource loader, event bus view, registries, commands and network
  channels;
- isolate failures, so one broken mod is one broken mod;
- generate, build and test mod projects from the command line.

- **bind mod commands into Minecraft's own command tree**, so `/fly` is a real
  command in game. ReMod loads the game through a transforming class loader and
  injects a hook into whichever class holds Brigadier's dispatcher — found by
  the *type* of its field, since Brigadier ships unobfuscated, so no mapping
  file is needed for it;
- **download Mojang's official mappings** for the installed version, so ReMod
  can reach the game's own fields on an obfuscated install.

**It does not, yet:** insert registered items, blocks and creative tabs into
Minecraft's *own* registries. That has to happen inside the game's bootstrap
before it freezes them, which is a larger injection than hooking a constructor.
`capabilities()` reports `COMMANDS` only, and the log says what attached on any
given launch. See [docs/version-support.md](docs/version-support.md), which is
also explicit about which parts are verified by tests and which are not.

Nothing in ReMod claims a capability it does not have.

## Features

- **One mod jar, every version.** A mod declares an API baseline rather than a
  Minecraft series, so a single jar loads on every Minecraft version its
  `minecraft` range allows — 1.17 through 1.21 and beyond, no per-version
  builds. This works because the API never references a Minecraft class.
- **Dynamic version list.** Read live from Mojang's official version manifest,
  so a Minecraft version released tomorrow appears with no ReMod update.
  Searchable, filterable by release/snapshot, with each version's ReMod support
  level shown.
- **Downloads Minecraft for you.** The installer fetches the version file and
  client jar from Mojang, checksum-verified, so the first launch is instant.
  Optional — the launcher does it otherwise.
- **Version-adapter architecture.** One `GameBridge` per Minecraft family;
  supporting a new release means one new adapter, not a change to the API, the
  loader or any mod.
- **A real API.** Lifecycle, events, registries, commands, keybinds, HUD,
  config, networking, resources, logging, client/server separation.
- **Useful errors.** Never a bare `NullPointerException`.
- **Safe by construction.** Path-traversal and zip-bomb protection on every
  archive read; the installer refuses rather than guessing; worlds, mods and
  settings are never deleted.
- **Lightweight.** No background threads, one cached manifest fetch per session,
  allocation-free event dispatch, no third-party runtime dependencies.
- **Coexists** with Fabric, Forge, NeoForge and Quilt — separate profile,
  separate mods folder. See [docs/compatibility.md](docs/compatibility.md).

## Installation

1. Install Java 17 or newer.
2. Run the official Minecraft Launcher once, so it creates
   `launcher_profiles.json`. (ReMod will not create that file itself — doing so
   could discard installations the launcher has not written yet.)
3. Download `ReMod.jar` and double-click it, or run `java -jar ReMod.jar`.
4. Pick a Minecraft version and click **Install ReMod**. ReMod downloads that
   Minecraft version, installs its own libraries and the matching ReMod API, and
   adds the launcher installation.
5. Open the Minecraft Launcher; **ReMod 1.21.4** is in the installations list.

Mods go in:

| Operating system | Folder |
| --- | --- |
| Windows | `%APPDATA%\.minecraft\remod\mods` |
| macOS | `~/Library/Application Support/minecraft/remod/mods` |
| Linux | `~/.minecraft/remod/mods` |

## Supported Minecraft versions

| Minecraft | Level |
| --- | --- |
| 1.21.x, 1.20.x, 1.19.x | Supported |
| 1.18.x, 1.17.x | Partial |
| 1.16.5 and older | Not supported |
| Weekly snapshots | Not supported |

Pre-releases and release candidates are supported by their series. Full detail,
including exactly what each level covers:
[docs/version-support.md](docs/version-support.md).

## Mod development

The complete beginner's guide — installing Java through publishing a finished
mod — is in [tutorial.txt](tutorial.txt). The short version:

```
java -jar ReMod.jar create MyMod --package dev.example.mymod
cd MyMod
./gradlew build
./gradlew installMod
java -jar ReMod.jar test --mods build/libs
```

A minimal mod:

```java
public class MyMod implements ReModMod {

    @Override
    public void onInitialize(ReModContext context) {
        context.logger().info("Hello from " + context.modName());

        context.registries().items().register(
                ItemDefinition.builder(Identifier.of("mymod", "ruby"))
                        .displayName(Text.literal("Ruby"))
                        .maxStackSize(16)
                        .build());

        context.commands().register(CommandBuilder.create("mymod")
                .description("Say hello")
                .executes(command -> {
                    command.source().sendFeedback(Text.literal("Hello!"));
                    return 1;
                }));

        context.events().subscribe(PlayerJoinEvent.class, event ->
                event.player().sendMessage(Text.literal("Welcome!")));
    }
}
```

Mods are ordinary `.jar` files with a `remod.mod.json` manifest at the root —
see [docs/mod-format.md](docs/mod-format.md) for the format and the reasoning
behind it.

## The API

| Area | Entry point |
| --- | --- |
| Lifecycle | `ReModMod`, `LifecyclePhase` |
| Events | `context.events()` — lifecycle, tick, player, world, client, server, resource |
| Content | `context.registries()` — items, blocks, creative tabs |
| Commands | `context.commands()`, `CommandBuilder` |
| Configuration | `context.config()`, `ConfigSpec` |
| Client | `context.client()` — keybinds, HUD layers |
| Networking | `context.network()`, `PacketBuffer` |
| Resources | `context.resources()` |
| Logging | `context.logger()` |
| Storage | `context.dataDirectory()` |

Everything is reached through the `ReModContext` a mod is handed. Nothing
requires reflection, and nothing requires compiling against Minecraft.

## Command-line tools

| Command | Purpose |
| --- | --- |
| `remod create <name>` | Scaffold a mod project |
| `remod build` | Build a project and verify the result |
| `remod test --mods <dir>` | Load mods without starting Minecraft |
| `remod install <version>` | Install ReMod (add `--no-download` to skip fetching Minecraft) |
| `remod uninstall <version>` | Remove it (mods, settings and worlds are kept) |
| `remod init` | Create ReMod's folders (for servers and scripts) |
| `remod list installs\|mods\|versions\|loaders` | Inspect what is present |

Run `java -jar ReMod.jar help <command>` for the usage of any of them.

## Building ReMod

Requires JDK 17 or newer.

```
./gradlew build
```

Outputs:

| Artifact | Path |
| --- | --- |
| `ReMod.jar` | `remod-dist/build/libs/ReMod.jar` |
| Simple example mod | `examples/simple-mod/build/libs/ReMod-Simple-Mod-1.0.0.jar` |
| Client example mod | `examples/client-example/build/libs/ReMod-Example-Client-Mod-1.0.0.jar` |
| Server example mod | `examples/server-example/build/libs/ReMod-Example-Server-Mod-1.0.0.jar` |

`./gradlew test` runs the test suite.

## Example mods

- **[ReMod Simple Mod](examples/simple-mod)** — an item, a block, a creative
  tab, a command with subcommands, configuration and events.
- **[ReMod Example Client Mod](examples/client-example)** — a keybind, a HUD
  overlay and client tick handling.
- **[ReMod Example Server Mod](examples/server-example)** — server lifecycle
  events, player join/quit tracking, a command and chat filtering.
- **[ReMod Fly Mod](examples/fly-mod)** — `/fly` in your own single-player
  world: a permission-gated command, flight abilities, and per-player state
  that survives a relog.

All three compile against the public API only and are built by the main build.

## Architecture

See [docs/architecture.md](docs/architecture.md) for the module graph, the
startup sequence, the version-adapter boundary, the failure-isolation rules and
the performance and security posture.

## Compatibility with other loaders

ReMod coexists with Fabric, Forge, NeoForge and Quilt but cannot load their
mods; Bukkit/Spigot/Paper plugins are a different kind of software entirely and
cannot run in a Minecraft client at all. The reasoning for each is in
[docs/compatibility.md](docs/compatibility.md) — with no overstatement.

## Troubleshooting

**The installer says it cannot find `launcher_profiles.json`.**
Run the official Minecraft Launcher once and let it reach the login screen.
ReMod deliberately will not create that file, because it holds your existing
installations.

**"ReMod 1.21.4" does not appear in the launcher.**
Close and reopen the launcher — it reads the profile list at startup. Check
`.minecraft/versions/ReMod-1.21.4/ReMod-1.21.4.json` exists.

**A mod is not loading.**
Check `.minecraft/remod/logs/remod.log`. Every rejection is printed with the
mod, the reason, what was expected, what was found and what to do. Or run
`java -jar ReMod.jar list mods` for a summary.

**"This is a Fabric mod, not a ReMod mod."**
Exactly what it says. Fabric mods go in `.minecraft/mods`; ReMod mods go in
`.minecraft/remod/mods`.

**The game crashes on startup.**
Move mods out of `remod/mods` and add them back a few at a time. The log names
the mod that threw, and its `issues` URL when the author supplied one.

**My mod's item does not appear in game.**
Expected in 1.0.0 — see [docs/version-support.md](docs/version-support.md). Mods
load and run; content is not yet bound into Minecraft's registries.

## Security

Mods are Java code and run with your full user permissions. There is no sandbox
— this is true of every Minecraft mod loader. Install mods only from sources you
trust. ReMod never downloads or runs a mod on its own initiative.

## Legal

Minecraft is a trademark of Mojang AB. ReMod is an independent project, not
affiliated with or endorsed by Mojang or Microsoft. ReMod bundles no Minecraft
code or assets: it installs a launcher profile that inherits from your existing
Minecraft installation, which the official launcher downloads through its own
normal mechanism.

## Licence

MIT. See [LICENSE](LICENSE).
