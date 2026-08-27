# ReMod Mod Development Kit

`MyReModProject/` is a complete, buildable ReMod mod project. It is checked in
so you can read a real one before generating your own, and so the layout is
covered by ReMod's own tests.

It is byte-for-byte what this command produces:

```
java -jar ReMod.jar create MyReModProject --package dev.example.myremodproject
```

Use the command rather than copying this folder — it fills in your mod id, your
package, your author name and the ReMod API version for the Minecraft version
you chose.

## Building it

The project compiles against the ReMod API jar that the installer places in
your Minecraft folder, so install ReMod for a Minecraft version first. Then:

```
cd MyReModProject
./gradlew build          # -> build/libs/myremodproject-1.0.0.jar
./gradlew installMod     # copies the jar into your ReMod mods folder
```

If the build cannot find the API jar, point `remodApiPath` in
`gradle.properties` at it. The installer reports the path when it finishes; it
is normally:

| Operating system | Path |
| --- | --- |
| Windows | `%APPDATA%\.minecraft\remod\api\remod-api-<series>-<baseline>.jar` |
| macOS | `~/Library/Application Support/minecraft/remod/api/remod-api-<series>-<baseline>.jar` |
| Linux | `~/.minecraft/remod/api/remod-api-<series>-<baseline>.jar` |

## Checking your mod loads

```
java -jar ReMod.jar test --mods build/libs
```

This runs the real loader with no Minecraft attached and prints what your mod
registered — a couple of seconds instead of a full game launch.

## The full walkthrough

`tutorial.txt` in the ReMod distribution covers every step from installing Java
to publishing a finished mod.
