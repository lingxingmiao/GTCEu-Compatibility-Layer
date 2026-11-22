# Licensing notice

This repository contains code under **two different licenses**. Read this file
before redistributing or bundling the built jar.

---

## 1. LGPL-3.0 — vendored GregTech CE: Unofficial 2.8.10 sources

The following source files are **derived from GregTech CE: Unofficial 2.8.10**
(<https://github.com/GregTechCEu/GregTech>), which is licensed under the
**GNU Lesser General Public License, version 3.0**. They are included here so the
compatibility layer can restore the GTCEu 2.8 API contract on a 2.9 host.

The full license text is in [`LICENSE-GTCEu-LGPL-3.0.txt`](LICENSE-GTCEu-LGPL-3.0.txt).

| File | Status vs. upstream |
| --- | --- |
| `src/main/java/gregtech/api/pattern/BlockWorldState.java` | verbatim |
| `src/main/java/gregtech/api/pattern/FactoryBlockPattern.java` | verbatim |
| `src/main/java/gregtech/api/pattern/PatternError.java` | verbatim |
| `src/main/java/gregtech/api/pattern/PatternMatchContext.java` | verbatim |
| `src/main/java/gregtech/api/pattern/PatternStringError.java` | verbatim |
| `src/main/java/gregtech/api/pattern/TraceabilityPredicate.java` | verbatim |
| `src/main/java/gregtech/api/unification/stack/ItemMaterialInfo.java` | verbatim |
| `src/main/java/gregtech/common/covers/filter/ItemFilter.java` | verbatim |
| `src/main/java/gregtech/common/covers/filter/FluidFilter.java` | verbatim |
| `src/main/java/gregtech/api/capability/IMultipleTankHandler.java` | verbatim |
| `src/main/java/gregtech/api/pattern/BlockPattern.java` | **modified** — adapted `GregTechAPI.MTE_REGISTRY` (removed in 2.9) to scan `mteManager`; added public `checkPatternCompat(...)` wrapper; converted `case X ->` switch arrows / `instanceof X x` pattern matching to Java 8-compatible syntax in the GFX-facing helpers |
| `src/main/java/gregtech/api/pattern/MultiblockShapeInfo.java` | **modified** — `instanceof` pattern variable rewritten so the holder can be reassigned |
| `src/main/java/gregtech/common/covers/filter/ItemFilterWrapper.java` | **modified** — `initUI`/`blacklistUI` bodies emptied (2.9 has no `WidgetGroup*Filter`); signatures kept |
| `src/main/java/gregtech/common/covers/filter/FluidFilterWrapper.java` | **modified** — same as above |

The modification list above is the "state the changes" obligation of LGPL-3 §5(a).
The exact upstream versions can be diffed against
`GregTechCEu/GregTech` at tag `2.8.10-beta`.

### Written offer / source availability

This repository **is** the Corresponding Source for the LGPL-covered portions and
for the whole combined work: the complete Java sources for every class shipped in
the built jar live here, and the build is reproducible with the Gradle wrapper
(see [`libs/README.md`](libs/README.md) for the three `compileOnly` reference jars,
which are not redistributed here because they are third-party distributions).

Per LGPL-3 §4(d)(0), you may therefore relink the LGPL-covered portions against a
modified version of the library by rebuilding this project.

---

## 2. Own work — everything else

All remaining original code — the ASM transformers, the version detector, the
diagnostics module, `StructurePredicates`, `FilterTypeRegistry`,
`MetaTileEntitiesCompat`, `LegacyStructureCompat`, and the build scripts — is
released under the **MIT License** (see [`LICENSE`](LICENSE)).

`StructurePredicates.java` in particular is an original reimplementation of the
2.8 static predicate-factory helpers, authored for this project; it is not copied
from upstream.

---

## 3. What this means if you redistribute the built jar

The built `gtceu_compat-<version>.jar` contains compiled bytecode from **both**
groups. Distributing it therefore means distributing a work that includes
LGPL-3.0 code, so you must:

1. keep this notice and the LGPL-3.0 text alongside the jar;
2. make the Corresponding Source available (link back to this repository is
   sufficient);
3. not relicense the LGPL-derived portions as MIT-only.

The runtime dependencies the jar expects (`gregtech` / GTQT 2.9, ModularUI, CTM)
are **not** bundled and remain under their own licenses.

---

## 4. Trademarks / attribution

"GregTech", "GregTech CE: Unofficial", and "GTQT" are the respective projects'
names. This is an unofficial compatibility layer, not affiliated with or endorsed
by them.
