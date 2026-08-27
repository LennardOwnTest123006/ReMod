# The ReMod mod format

## The decision: mods are ordinary `.jar` files

A ReMod mod is a standard Java archive containing a `remod.mod.json` manifest at
its root.

The task allowed for a bespoke container such as `MyMod.remod`, and ReMod does
accept that extension — a `.remod` file is read exactly as a `.jar` is, because
it is one. But the **canonical** format is `.jar`, for reasons that all point
the same way:

- **Every Java tool already produces one.** `gradle build` emits a jar. A custom
  container would mean a custom Gradle plugin before anyone could ship anything.
- **The JVM can load classes from it directly.** A bespoke format would have to
  be unpacked to a temporary directory at startup, which is slower, and which
  means writing an extraction step that has to be secured against the archive
  attacks `SafeZip` already handles.
- **Users can inspect it.** `unzip -p MyMod.jar remod.mod.json` answers "what
  version is this mod for?" with no ReMod-specific tooling.
- **It is what every other Minecraft loader does**, so nothing about
  distribution, hosting or antivirus behaviour is surprising.

Renaming a jar to `.remod` buys a distinctive extension and costs
interoperability with every existing tool. ReMod takes the interoperability.

## Where mods live

```
.minecraft/remod/mods/
```

Not `.minecraft/mods/`, which Fabric, Forge, Quilt and NeoForge all share. A
separate directory is what lets ReMod coexist with them without either loader
trying to read the other's mods.

A file ending in `.disabled` is skipped, which is the conventional way to keep a
mod installed but switched off.

## The manifest

`remod.mod.json`, at the archive root, UTF-8, strict JSON — no comments, no
trailing commas. A typo is reported with the file name, the field name and what
was expected, because the person reading that message is usually the mod's
author.

```json
{
  "schema": 1,
  "id": "simplemod",
  "name": "ReMod Simple Mod",
  "version": "1.0.0",
  "author": "ReMod Developer",
  "description": "A simple ReMod example mod.",
  "minecraft": ">=1.17 <2.0",
  "remod_api": "1.0.0",
  "side": "common",
  "entrypoints": ["dev.example.simplemod.SimpleMod"],
  "dependencies": [],
  "optional_dependencies": [],
  "incompatible": [],
  "license": "MIT",
  "homepage": "https://example.com",
  "issues": "https://example.com/issues"
}
```

### Required fields

| Field | Meaning |
| --- | --- |
| `id` | Lower-case, 2–64 characters, `a-z 0-9 _ -`, starting with a letter. Also the namespace for everything the mod registers. |
| `version` | The mod's own version. Semantic versioning is expected; Minecraft-shaped versions parse too. |
| `minecraft` | A version *range* the mod supports, e.g. `1.21.x`, `>=1.20 <1.22`, `1.20.x \|\| 1.21.x`. |
| `remod_api` | The API baseline built against, e.g. `1.0.0`. See below. |
| `entrypoints` | One or more classes implementing `dev.remod.api.ReModMod`. |

### Optional fields

| Field | Default | Meaning |
| --- | --- | --- |
| `schema` | `1` | Manifest schema version. A higher value than ReMod understands is refused with a message saying to update ReMod. |
| `name` | the `id` | Display name. |
| `side` | `common` | `client`, `server` or `common`. ReMod refuses to load a mod on the wrong side rather than letting it crash there. |
| `dependencies` | `[]` | Must be present, or the mod does not load. |
| `optional_dependencies` | `[]` | Loaded first when present; ignored when absent. |
| `incompatible` | `[]` | If present, this mod refuses to load. |
| `license`, `homepage`, `issues` | — | Metadata. `issues` is quoted back at the user when the mod crashes. |
| `custom` | `{}` | Free-form data for tooling; ReMod ignores it. |

### Dependency syntax

Either a string or an object:

```json
"dependencies": ["baselib@>=2.0 <3.0", "othermod"]
```
```json
"dependencies": [{"id": "baselib", "version": ">=2.0 <3.0"}]
```

The version range accepts `*`, an exact version, `1.21.x`, `>=`/`<=`/`>`/`<`,
`~1.21.2`, `^1.21.2`, Maven intervals `[1.20,1.22)`, and unions with `||`.

An unparseable range is a hard error, not a silent "matches nothing": a typo in
a manifest should be reported to its author, not quietly disable the mod.

## The two version numbers a mod declares

These are different things and confusing them is the most common manifest
mistake:

- **`minecraft`** — which Minecraft versions the mod works on. A *range*.
- **`remod_api`** — which ReMod API the mod was compiled against.

### One jar, every version

`remod_api` normally takes the **portable** form: a bare baseline, `"1.0.0"`.

That works because the ReMod API never references a Minecraft class. A mod
*describes* an item with `ItemDefinition` rather than constructing one, and the
version adapter does the translating — so the API classes are byte-identical on
every Minecraft series. There is nothing for the API version to be tied to.

The practical consequence is the point of the whole design:

```json
"minecraft": ">=1.17 <2.0",
"remod_api": "1.0.0"
```

That single jar loads on 1.17 through 1.21 and on future releases, with no
per-version builds. The `minecraft` range is what decides where it runs; make it
honest about what you have actually tested.

### Pinning, when you really need it

`remod_api` also accepts a **pinned** form, `"1.21-1.0.0"`, meaning "this mod
only works on the 1.21 series". ReMod then refuses to load it anywhere else.
Use it only when the mod genuinely cannot work elsewhere — it costs you the
single-jar property, and ReMod's error message will suggest the portable form
to whoever hits it.

A pinned mod whose `minecraft` range reaches outside its pinned series is
rejected at parse time, because it could never load across the range it claims.

### The baseline, in both forms

The baseline follows semantic versioning: a mod runs on any later baseline with
the same major component. `1.21-1.4.0` installed satisfies a mod needing
`1.0.0`; it does not satisfy one needing `1.5.0` (that mod wants API features
this ReMod does not have) or `2.0.0` (a breaking API change).

## Building one

`remod create` generates a project whose `processResources` step substitutes the
version from `gradle.properties` into `remod.mod.json`, so the manifest and the
build can never disagree. See `mdk/` and `tutorial.txt`.
