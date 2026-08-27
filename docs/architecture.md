# ReMod architecture

## The modules

```
remod-common               JSON, logging, versioning, safe IO, HTTP caching
  └── remod-api            the public API mods compile against
        └── remod-loader   discovery, resolution, lifecycle, launch wrapper
              ├── remod-version-adapters    per-Minecraft-family GameBridge
              ├── remod-compatibility       other loaders: detection + honesty
              ├── remod-installer           manifest, launcher integration, GUI
              │     └── remod-cli           create / build / test / install
              └── remod-dist                merges everything into ReMod.jar
examples/                  three working mods
mdk/                       a generated mod project, checked in
```

Dependencies point one way only. The API knows nothing about the loader; the
loader knows nothing about the installer; adapters know nothing about the GUI.

## The three version numbers

Keeping these apart is a design constraint, not an accident:

| Number | Example | What it describes |
| --- | --- | --- |
| Loader version | `1.0.0` | ReMod itself — the installer and the loader |
| API baseline | `1.0.0` | The shape of the API surface |
| API version | `1.21-1.0.0` | A baseline paired with a Minecraft series; what a mod declares |
| Minecraft version | `1.21.4` | The game. Never baked into ReMod; discovered at runtime |

`ReModVersions` is the only place that combines them.

## Startup, end to end

```
Minecraft Launcher
      │  starts versions/ReMod-1.21.4/ReMod-1.21.4.json
      │  which inheritsFrom "1.21.4" and sets mainClass
      ▼
dev.remod.loader.launch.ReModLaunch
      │  1. read --gameDir and -Dremod.minecraftVersion
      │  2. open remod/logs/remod.log
      │  3. locate Minecraft's own main class -> tells us the side
      │  4. pick a MinecraftVersionAdapter, build its GameBridge
      ▼
ReModLoader.load()
      │  ModDiscovery   scan remod/mods, parse manifests, note foreign mods
      │  ModResolver    duplicates, MC range, API version, side, dependencies,
      │                 incompatibilities, topological order
      │  construct      one ModClassLoader over every mod jar
      │  PRE_INIT -> INIT -> POST_INIT -> CLIENT_INIT | SERVER_INIT
      ▼
GameLocator.launch(original arguments)
      │
      ▼
Minecraft starts normally
```

The last step is the important one. ReMod does not replace Minecraft's startup,
patch its jar, or copy any of its files. It runs first, then gets out of the
way — which is what makes an uninstall a deleted folder and makes coexistence
with other loaders possible.

## The version-adapter boundary

`GameBridge` is the single seam between version-independent code and one
Minecraft build. Everything a mod does that could touch the game arrives there
as a version-independent description — `ItemDefinition`, `CommandSpec`,
`Identifier`, `Text` — and the adapter decides what to do with it.

Consequences, all deliberate:

- Supporting a new Minecraft release means writing one adapter. The API, the
  loader and every existing mod are untouched.
- `ItemDefinition` describing an item, rather than a mod constructing a
  Minecraft `Item`, is why the 1.20.5 data-component rewrite would not have
  broken any ReMod mod.
- Running with **no** adapter is a supported state, not a failure: the
  `HeadlessGameBridge` records everything and reports `isGameAttached() == false`.
  That is what makes `remod test` and the loader's own integration tests
  possible without Minecraft on the classpath.
- Adapters are found through `ServiceLoader`, so a new one is a dropped-in jar.

What the shipped adapter does and does not bind is stated in
[version-support.md](version-support.md).

## Failure isolation

The loader's promise is that one bad mod is one bad mod:

- A malformed manifest becomes a `DiscoveryProblem`; the scan continues.
- An incompatible mod becomes a `ModLoadError` with expected/found/what-to-do;
  every other mod still loads.
- A mod that throws during a lifecycle phase is marked failed, and its
  registrations, commands and event listeners are withdrawn, so no half-
  initialised mod is left visible to the rest of the game. Mods that required it
  are then disabled with their own explanation.
- An event listener that throws is logged against its mod and skipped; one that
  throws five times is disabled, so a broken tick handler cannot write twenty
  stack traces a second.
- A broken log sink cannot silence the others.

Nothing in that list aborts startup.

## Performance choices

ReMod does almost nothing while the game runs, and the things it does at startup
are bounded:

- **No background threads.** No schedulers, no watchers. The only thread ReMod
  adds is a shutdown hook.
- **The version manifest is fetched at most once per session**, cached on disk
  with its ETag, and served from cache for six hours; an offline session uses
  the cache regardless of age.
- **The HTTP client is created lazily**, so an offline install opens no sockets.
- **Event dispatch is a cached array walk.** The per-event-class listener list is
  resolved once and invalidated only on subscribe/unsubscribe, so posting a tick
  event is a map lookup and an iteration with no allocation.
- **`ServiceLoader` runs once** for adapters, not per query.
- **Downloads with a known SHA-1 are skipped** when the file already matches, so
  reinstalling the same version is nearly free.
- **Log records are lazily formatted** — a filtered `debug(() -> ...)` never
  builds its string.

## Security posture

Mods are Java code with no sandbox. This is stated plainly in the README and in
`tutorial.txt`, because it is true of every Minecraft mod loader and pretending
otherwise would be the dangerous choice. What ReMod does control:

- **No automatic downloads.** ReMod never fetches or runs a mod on its own. The
  only things it downloads are Mojang's version manifest and its own bundled
  libraries — which are not downloaded at all, but carried inside `ReMod.jar`.
- **Every archive read goes through `SafeZip`**, which rejects path traversal
  (`../`), absolute and drive-qualified entry names, backslash traversal, zip
  bombs (a byte budget) and entry-count bombs.
- **Manifests are validated before use** — ids, class names, version ranges —
  so a malformed one fails at parse time with a readable error rather than
  somewhere deep in the loader.
- **Packet buffers are bounds-checked and length-capped**, because a payload
  from another player is untrusted input.
- **Mod resources are scoped per mod**: `ArchiveResourceLoader` reads only that
  mod's own archive, and applies the same traversal checks.
- **The installer refuses rather than guesses**: it will not create or overwrite
  `launcher_profiles.json` it cannot parse, and will not delete a version
  directory whose JSON does not carry ReMod's marker.
