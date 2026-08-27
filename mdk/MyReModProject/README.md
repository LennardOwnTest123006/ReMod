# MyReModProject

A mod for [ReMod](https://github.com/remod), the Minecraft: Java Edition mod loader.

- Mod id: `myremodproject`
- Minecraft: 1.21.x
- ReMod API: `1.21-1.0.0`

## Building

```
./gradlew build
```

The mod jar appears in `build/libs/`.

## Installing it while you work

```
./gradlew installMod
```

This copies the jar into your ReMod mods folder. Adjust `remodModsPath` in
`gradle.properties` if your Minecraft folder is somewhere else.

## Checking it loads without starting Minecraft

```
java -jar ReMod.jar test --mods build/libs
```

## Where to look next

- `src/main/java/dev/example/myremodproject/MyReModProject.java` -- your mod's entrypoint
- `src/main/resources/remod.mod.json` -- your mod's manifest
- `tutorial.txt` in the ReMod distribution -- a full beginner's guide
