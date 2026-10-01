# Contributing to ARC Overhaul

[中文](CONTRIBUTING.zh-CN.md)

ARC Overhaul is an independent gameplay MOD for Airships: Conquer the Skies, powered by Acbric. Start with [README](README.md), [implementation research](RESEARCH.md), and [test guide](TESTING.md). Owned source and documentation use the [MIT License](LICENSE); retain third-party notices.

## Prepare a checkout

1. Clone this repository into any directory. The local folder name does not determine the MOD ID.
2. Install JDK 21 and set `JAVA_HOME` to its root (a JRE is insufficient). Windows is the currently validated development environment. Integration checks also require Python 3.10 or newer.
3. Obtain a matching Acbric development checkout/build with API **0.3.3-dev.21 or a later compatible version**. The baseline used here is Acbric commit `1be89b2`; do not assume the upstream default branch already contains it. Obtain the matching revision from the maintainer if unavailable. Build it according to its documentation, including `distDir` to produce `loader-libs`.
4. Keep your own game libraries outside this repository. Prepare `asplit-A.zip`, `asplit-B.zip`, and the supporting library JARs. No game binaries, assets or decompiled files are supplied here.
5. Build using the actual local paths:

```powershell
.\gradlew.bat build -PacbricDir="D:/Development/Acbric" -PgameLibDir="D:/Games/Airships/libs"
```

The default is a sibling `../Acbric` directory, including its `libs` for game libraries. First use may download Gradle 8.13; add `--offline` only after it is cached. Machine-specific overrides can be placed in the ignored project `gradle.properties` (`acbricDir=...`, `gameLibDir=...`); use forward slashes. `local.properties` is not read by the build.

This generates only the MOD, `build/libs/ARC-Overhaul-0.1.0-dev.9.jar`. It does not assemble or install a full game. Replace the older ARC JAR when installing; both names use the same `arc_overhaul` ID. Never install both.

## Work and review

- Start a short-lived feature branch from `main`, for example `feature/conquest-ui`. Open a pull request after local validation; keep unrelated gameplay changes separate.
- Gameplay belongs in feature packages such as `conquest/`; game-specific hooks belong in `mixin/`. Prefer existing Acbric APIs. Discuss framework-wide changes in the framework project.
- Keep UI and documentation in English and Chinese. UI language follows the game's current language. Maintain concise Chinese source headers/comments. Preserve upstream headers on third-party files.
- Preserve MOD ID `arc_overhaul`, package `net.poosh.arc`, configuration keys, shared-rule versions and save field `arcStartingLayout` unless a reviewed migration is provided. Renaming the product does not rename these contracts.
- Before changing a Mixin, verify target method descriptors against each supported game build. Decompiled text is a research aid, not the dependency or an exact source of truth.
- New-campaign values must not be reapplied when loading a save. Do not assume local configuration or custom save fields automatically synchronize in multiplayer.
- Include behavior, test inputs/results and remaining limitations in the PR. `build` compiles/packages; `test NO-SOURCE` is not a test pass. Run relevant [isolated checks and manual scenarios](TESTING.md).

## Share source and report problems

Commit owned source, docs, test source and the Gradle wrapper. Keep local dependencies, game files, decompiled material, runtime fixtures, logs, saves, credentials and `build/` outside Git. The ignore rules cover common directories and binary formats, but inspect `git diff --cached` and `git status` before committing. A source checkout does not require any private research folder.

For bugs include versions, enabled MODs, settings, reproduction steps and the relevant log excerpt; remove personal paths or unrelated save data. The repository has no automatic publishing or credentials. When creating its remote, use **ARC-Overhaul** as the repository name; no remote address is hard-coded here.
