# libs/ — local compile-time-only jars (NOT committed)

These jars are referenced as `compileOnly` in `gradle/scripts/dependencies.gradle`.
**Nothing from them is bundled into the built mod jar** — they only provide the
`gregtech.*` / ModularUI / CTM API types that the compatibility layer's vendored
sources and transformers compile against. At runtime the same classes come from
the mods already installed in the Prism/Cleanroom instance.

They are excluded from git (see `../.gitignore`) because they are third-party
distributions and the GTQT jar alone is ~33 MB.

## Required files

| File name (must match exactly) | Where to get it |
| --- | --- |
| `gregtech-gtqt-1.12.2-2.9.0.jar` | GTQT's GregTech fork (GTCEu 2.9 based), `gregtech-gtqt-1.12.2-2.9.0.jar`, e.g. from the GTQT project releases. |
| `modularui-3.1.5.jar` | Cleanroom ModularUI 3.1.5 (bundled in the target pack's `mods/`). |
| `ctm.jar` | CTM for 1.12.2 (any `CTM-MC1.12.2-*.jar`; it provides `team.chisel.ctm.api.IFacade`). |

## Quickest way (copy from the running instance)

```powershell
$mods = 'C:\Users\<you>\AppData\Roaming\PrismLauncher\instances\<instance>\minecraft\mods'
$dst  = '<repo>\libs'
Copy-Item "$mods\gregtech-gtqt-1.12.2-2.9.0.jar" "$dst\gregtech-gtqt-1.12.2-2.9.0.jar"
Copy-Item (Get-ChildItem $mods -Filter 'modularui*.jar' | Select-Object -First 1).FullName "$dst\modularui-3.1.5.jar"
Copy-Item (Get-ChildItem $mods -Filter '*CTM*.jar'   | Select-Object -First 1).FullName "$dst\ctm.jar"
```

Then build normally:

```powershell
.\gradlew.bat build
```

Output: `build/libs/gtceu_compat-<version>.jar`.

## Note on the Java toolchain

This project compiles to **Java 21 bytecode** (`use_modern_java_syntax = true`,
toolchain JDK 21, no `--release 8`), because it targets Cleanroom, which runs
Minecraft 1.12.2 on Java 25. The Jabel annotation processor is deliberately
disabled (its bundled Byte Buddy rejects class file version 65).
