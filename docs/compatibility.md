# Compatibility with other loaders and platforms

ReMod ships a `LoaderBridge` for each of the projects below. The levels are
declared in code (`remod-compatibility`), surfaced by
`remod list loaders`, and shown in the installer after an install — so what
follows is generated from the same source the loader uses, not a wish list.

## Summary

| Platform | Level | Coexists with ReMod | ReMod can load its mods |
| --- | --- | --- | --- |
| Fabric | Coexistence only | yes | no |
| Quilt | Coexistence only | yes | no |
| Forge | Coexistence only | yes | no |
| NeoForge | Coexistence only | yes | no |
| Bukkit | Not possible | n/a — different kind of software | no |
| Spigot | Not possible | n/a | no |
| Paper | Not possible | n/a | no |

Nothing here is marked "experimental", because nothing here half-works. A
bridge that loaded a Fabric mod far enough to crash in an unrelated place would
be worse than one that declines clearly.

## Fabric, Quilt, Forge and NeoForge

### Coexistence: yes, fully

All four install a launcher profile and read `.minecraft/mods`. ReMod installs
its own profile and reads `.minecraft/remod/mods`. The two sets of files never
overlap:

```
.minecraft/
  versions/fabric-loader-0.15.11-1.21.4/   <- Fabric's
  versions/ReMod-1.21.4/                   <- ReMod's
  mods/                                    <- Fabric's, Forge's, Quilt's...
  remod/mods/                              <- ReMod's
```

You can have all of them installed at once and pick one from the launcher's
installation list per launch. ReMod detects the others during install and says
so, so a misplaced mod is never a silent failure: put a Fabric mod in
`remod/mods` and ReMod names it, identifies which loader it belongs to, and
tells you where it should go.

What you cannot do is run two of them in the *same* launch. Each one installs
itself as the game's `mainClass`, and there is only one of those.

### Loading their mods: no

Each of these has a runtime its mods are written against, and that runtime is
another project's code:

- **Fabric and Quilt** mods are compiled against *intermediary* mappings and
  expect Fabric/Quilt Loader's entrypoint container and Mixin bootstrap to be
  running. Loading one means bundling or reimplementing that loader.
- **Forge and NeoForge** mods run inside ModLauncher, which applies Forge's own
  bytecode transformations and Access Transformers before Minecraft starts. A
  Forge `@Mod` class and its event bus have no meaning outside that
  environment.

ReMod is a launch wrapper with no transformation layer, so there is nothing for
those mods to attach to. This is a statement about architecture, not about
effort: a "Fabric bridge" that worked would be a Fabric Loader implementation,
which is a different project.

## Bukkit, Spigot and Paper

**These are not mod loaders.** They are replacement server software. You run
`paper-1.21.4.jar` *instead of* the vanilla Minecraft server, and plugins are
written against that server's own API (`org.bukkit.*`), which exists only
inside it.

So the honest answers are:

- A Bukkit/Spigot/Paper plugin **cannot** run in a Minecraft client. Not with
  ReMod, not with Fabric, not with Forge. There is no client-side loader for
  which this is possible.
- A Bukkit/Spigot/Paper plugin cannot run in a vanilla server either.
- A **Paper server and a ReMod client play together fine**, over vanilla
  networking, exactly as a vanilla client would — as long as the ReMod mods
  installed on the client are client-side and do not require a server
  counterpart.

ReMod detects these platforms (a server jar or a `plugins/` folder beside the
game directory) purely so it can explain the situation rather than failing
mysteriously.

### The one thing that could be built

A ReMod **plugin for Paper** — a jar loaded by Paper, implementing ReMod's
server-side API on top of Bukkit's — would let a ReMod server mod run inside a
Paper server. That is a real, buildable thing, but it is a separate deliverable
written against Paper's API, and it is not part of ReMod 1.0.0. It is not
something the client-side loader can do, so it is not shipped as a stub that
looks like it might.

## Adding a compatibility module later

`LoaderBridge` is the whole extension point. A new module implements it,
registers with `CompatibilityRegistry`, and declares its own
`CompatibilityLevel`. Nothing in the loader, the API or any existing mod
changes. That is the architecture the task asked for, and it is why the levels
are an enum in code rather than prose in a README.
