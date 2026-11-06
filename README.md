# ARC Overhaul

Current artifact: **dev.16**. Landship campaign movement now uses the ground-friction speed shown on the panel, without the native minimum that accelerated slow ships. Low-supply return and travel estimates agree with that speed. Includes the dev.15 editor fix and all earlier integrated features. Game 1.2.15.3 / the validated API 0.3.7 artifact: 1,148 checks pass. See TESTING for evidence and manual checks.

## dev.14 — 2026-11-06

dev.14 restores full-height, native framed resource bars beside the labels while retaining all five resources in two rows. At very small scales where a row is shorter than the native fixed padding, a one-pixel frame keeps the fill visible. Starting options now include independent AI cities (1-4) and towns (0-8), each accepting -1 for vanilla. AI cities use native AI income; extra slots include both cities and towns. Missing `aiCities` in old rules reads as -1. Range labels use font-safe separators.

Game 1.2.15.3 / API 0.3.7: **1,079 checks pass**; evidence `build/runtime-tests/dev14-final/summary.json`, JAR SHA256 `7400c17a72ffe93b865dc2989f6f809ee014fe70c9ad20152e4732657abca436`. New checks cover mixed settlement counts, native AI income, save restoration, and native resource drawing through a recording Frame at 50/100/125/150/200%. GPU appearance, full world generation and real multiplayer remain manual checks. The installed game has not been replaced.


## dev.13 integration

dev.13 integrates the four dev.11 features with dev.12 percentage UI scaling. The installed dev.12 was built from dev.10 plus commit `62b8f1c`; it never contained the resource, track, spectator or AI-town changes. Both test suites now run together: **907 checks pass on game 1.2.15.3 / API 0.3.7**, recorded in `build/runtime-tests/dev13-integrated/summary.json`. JAR SHA256: `80886894a18f38ce5727b42028a7fd765da4268a9a0d2fae3e1ca67c603de81b`. Packaged classes and Mixin registrations were checked too. GPU visuals and real multiplayer remain unverified; the running installation was not replaced.

UI scaling is available through native Settings, MOD Details, or the conquest setup list: **ARC Overhaul: UI scale**. Enable custom scaling and choose 50–200%; 100% is native Small. It is a local preference and requires API 0.3.7.


[中文](README.zh-CN.md)

A standalone Airships MOD, separate from Acbric. **0.1.0-dev.16 supports game 1.2.15.3 only**. It includes human starting cities, towns, cash and research, AI fleet selection, AI starting towns, complete five-resource displays, switchable track layers, and research access while spectating multiplayer conquest battles. AI empires keep vanilla cash rules; changes to placement can affect random draws and initial assets.

## Use

Requires Acbric API **0.3.7 or a later compatible version** and Java 21. Exit the game and place `build/libs/ARC-Overhaul-0.1.0-dev.16.jar` in its `game/mods/`. Disable the old `acbric-starting-cities.jar` and restart first; the MODs declare a conflict. Do not install the dev.1 scaffold or fixture JARs.

Open **single-player conquest setup → scroll the settings list to the bottom → ARC Overhaul: Starting options**, or **MOD list → ARC Overhaul → Details**. Edit, Apply, close, then start a new campaign. English/Chinese follow the game language.

The same list has a second entry, **ARC Overhaul: AI fleets**, described below.

| Field | Custom range | Meaning of `-1` |
|---|---|---|
| Cities | 1–4 | Vanilla: one city |
| Human towns | 0–8 | Current map default |
| AI cities | 1–4 | One city |
| AI towns | 0–8 | Current map default |
| Cash | 0–1,000,000 | Vanilla balance after difficulty and initial assets |
| Research | 0–10,000,000 | `0` grants nothing (vanilla) |

Counts and cash default to `-1`, research to `0`; all are independent. AI towns use `aiTowns`, with missing keys in old configs/campaigns read as `-1`. Only new campaigns are affected; multiplayer uses the host’s frozen rules. Zero cities is rejected on Apply; zero cash is valid. Cash is assigned after initial buildings/ships and does not increase their budget. Counts affect native asset allocation and balance.

Research points are banked into the native *unassigned* research pool (`Empire.unassignedResearchPoints`), which the game pours into the first research you select. Writing them into `Empire.researchPoints` instead does not work: the native "select research" command overwrites that field with the new tech's partial progress (0 for a fresh tech) before adding the pool, so the grant would be silently discarded on the first click. The range is in the millions on purpose — a technology costs `BASE_RESEARCH_COST × RESEARCH_COST_MULTIPLIER × 5600 × 1.4^tier` points (`Tech.baseCost`), about 2,240,000 for the first tiers, so a smaller grant is invisible on the progress bar.

ARC-added settlements are never placed on a speck of land: the native spot finder accepts any non-water tile, so ARC re-rolls until the tile belongs to a connected landmass of at least `max(32, gridSize/4)` tiles. Retries are bounded and fall back to the last candidate, so the guard can never turn generation into a failure. The guard applies only to the side with customised counts: AI-only changes leave human placement native; AI placement remains native when AI towns are `-1`.

Aircraft keep their attack runs pointed at the target: the native strafe logic stores one world-coordinate aim point per run and discards it once the target has moved 200 px away from it, then re-picks the point purely from which side of the target the aircraft is on — so a drifting target can send the aircraft the other way mid-run, which a bomber (lowest acceleration of the five aircraft, and it must fly straight over the middle of the target to release) pays for with an entire wasted pass. ARC now translates that aim point with the target's own movement, keeping the offset constant. See [CHANGELOG](CHANGELOG.md) for the mechanism and the measurements.

A T order can now name a **defenceless** target: select a ship, press T and click an enemy building or an unarmed airship, and its guns really do engage it. Vanilla writes "is this target worth shooting" as `dangerCache > 0` (`Module.targetShip`, line 1464), and an unarmed immobile building never gets any threat value from `Airship.danger()` (its `inCombat()` is false), so the order was silently dropped and the guns went back to their own automatic targets. ARC raises that value to a positive number only on the reads that judge the target the player named; automatic target selection still ranks purely by the native threat value, so ships and aircraft never pick a defenceless target on their own — someone has to name it with T. Direct control is unaffected: it aims with the reticle and never consulted `dangerCache` in the first place.

The same rule makes an **unarmed carrier** able to use T: launched aircraft inherit their carrier's `fireAt` (`Crewman.outsideShootingTick`, line 2248), so "select the carrier, then T an enemy" sends its aircraft after that target. Vanilla gated that inheritance on `dangerCache > 0` too and wiped the inherited target during reloads; on top of that, a carrier inside a box selection was filtered out by `canShoot()` so the order never left the client, and the panel's T button stayed grey for a weaponless ship for the same reason.

For the aircraft half of "can this ship take a T order", the flight centre alone is **not** the right test: `canGiveAircraftCommands()` is driven by `ModuleType.canGivePlaneCommands`, which only `FLIGHT_CENTRE` declares in the game's data, while the hangars (`BOMBER_HANGAR` and five more) declare only `quartersType` (whose `CrewType.canFly` is true). A carrier built from hangars without a flight centre therefore reads false there and its T button stayed grey. ARC uses the vanilla `Airship.hasFlyers()` instead so those carriers count too; a transport with neither guns nor aircraft bays is still skipped as in vanilla.

Count limits are conservative. Insufficient space aborts generation instead of silently producing fewer settlements. More than 128 MiB of additional distance-array capacity is rejected; this is not a total game-memory limit.

## Resources, track layers and spectator research

- **Resources:** crew, ammo, water, coal and repair supplies have independent cells, arranged in at most three columns and two rows. Absent supplies occupy no slot; exhausted or battle-damaged supplies retain their original capacity display. Command and direct-control panels share the layout. Narrow cells shorten text; tooltips always retain full names and exact counts.
- **Track layers:** open **ARC display** in the ship-editor toolbar, or **MOD Details → ARC Overhaul: Display**. Apply “Tracks behind armour” to let armour and decorations cover tracks; off restores the vanilla foreground. This immediately affects **all ships on this client**, leaving both leg passes unchanged. It is a local display preference, not a per-design setting, and does not change physics or require matching values on other clients.
- **Spectator research:** while watching another empire’s battle in multiplayer conquest, use **Research** in the toolbar (the research binding, R by default). The native tech tree edits your own research and queue through normal multiplayer commands. Battle continues in the background; closing research returns to the same spectator battle. Empire-less spectators and active combatants do not gain this entry. Ended battles and disconnect/resync screens are not replaced by stale combat screens.


## AI fleets

An "AI fleet" is the game's `ConstructionStrategy`: the ships, landships, buildings and tech trees one empire will use. Every conquest game hands each empire a fleet picked at random out of the loaded ones, filtered only by the empire's heraldic charge and the difficulty — the player has no say in it. **ARC Overhaul: AI fleets** lists every loaded fleet with three states:

| State | Effect |
|---|---|
| Allow | May be picked at random. This is the default and is exactly vanilla. |
| Force enable | Reserved: removed from the random pool and handed to exactly the number of countries you type in. |
| Force disable | Never appears on the map. |

The country count is a number of **non-player** countries; your own country always keeps the vanilla fleet. Because a force-enabled fleet leaves the random pool, entering `3` yields exactly three countries with that fleet, not "at least three". If every fleet available to an empire ends up banned or reserved, ARC keeps the vanilla pick instead of failing generation. With default settings ARC returns before it touches anything, so no random draw is consumed and a seed still produces the vanilla world.

Fleets are listed by the name the game loaded them under, which is what AI fleet packs ship. Settings live in `game/config/arc_overhaul/conquest-fleets.json`; only fleets you changed are written, and entries for fleets from a temporarily disabled MOD are kept.

### Bulk actions and the country-count box

The footer has **Force disable all** and **Force enable all**: one click marks every loaded fleet. Force-enable-all keeps each fleet's current country count (falling back to its last valid number), so it does not wipe numbers you already typed.

The country-count box can be cleared and retyped: it shows exactly what you typed, empty included. An empty box is never an error and is never saved as 0 — it keeps the last valid number. An error appears only for something you really typed wrong (a non-number, or a value outside 1–32) and Apply stays blocked until you fix it. Empty boxes are also restored to the deleted number as soon as you do anything else: type in another box, press any button, or Apply.

Two smaller things are **not** done, because they need Acbric changes: the box cannot be painted grey while it is inactive (the framework's canvas adapter ignores its `enabled` flag when painting glyphs, and it escapes square brackets so a MOD cannot inject the game's rich-text colour codes), and the number is not restored the instant you click into another box without typing (the framework changes focus with no callback). See the changelog for the framework versions involved before deciding to change it.
## Saves and multiplayer

- Preferences live in `game/config/arc_overhaul/conquest-start.json` (starting values) and `game/config/arc_overhaul/conquest-fleets.json` (AI fleets). Acbric freezes shared rules for new campaigns — both files are frozen together — so later preference changes do not affect existing campaigns.
- This version targets new campaigns. Old saves without ARC rules fail with `RULE_MISSING`; no automatic conversion occurs. Disable ARC and restart to play old saves. No ARC-specific migration tool is included.
- Keep ARC installed for ARC campaigns. Loading/restoring never redistributes land or grants starting cash. Expanded ID capacity uses the technical `arcStartingLayout` save field.
- Since Acbric 0.3.6, the host distributes authoritative gameplay rules; peers validate and adopt them without manually matching values in advance. Display preferences stay local in `game/config/arc_overhaul/display.json`. End-to-end multiplayer generation, resume and spectator research still require in-game acceptance.
- Other world-generation MODs may conflict. Building has not changed the framework or the installed game.

## Build

Use JDK 21, Acbric API 0.3.7 or a later compatible version, and game 1.2.15.3 libraries. See [contributor setup](CONTRIBUTING.md) for dependencies and local paths. The original framework baseline `1be89b2` is historical; it does not replace the current API requirement.

```powershell
.\gradlew.bat build
# Override the default sibling Acbric checkout / its game libs:
.\gradlew.bat build -PacbricDir="D:/Development/Acbric" -PgameLibDir="D:/Games/Airships/libs"
```

Output: `build/libs/ARC-Overhaul-0.1.0-dev.16.jar`. Replace the previous ARC JAR instead of installing both. Game, framework and test classes are not bundled. Nothing is automatically installed. `build` checks compilation/packaging; its default `test` has no sources. Run [integration tests](TESTING.md#run-isolated-checks-windows) separately with your own game inputs.

dev.11 passes **809 checks on game 1.2.15.3**, with the same JAR tested on both API 0.3.6 and 0.3.7. Checks include real Fabric transformations, native settlement placement/save restoration, five-resource drawing commands, track routing, and native spectator research commands through a capturing transport. GPU rendering, full world/initial-assets generation and real multiplayer sessions remain manual checks. See [testing](TESTING.md) for evidence and fixtures. Earlier game-version results are historical.

## Layout

- `src/main/java/net/poosh/arc/`: owned source, to be split into feature packages.
- `src/main/resources/fabric.mod.json`: identity and entrypoint.
- `build/`: regenerable outputs.
- [Source investigation and design boundaries](RESEARCH.md): implementation evidence and legacy comparison.

Campaign gameplay lives in `conquest/`, display and spectator controls in `client/`, and native adapters in `mixin/`. Keep future gameplay modules separate from the framework.

## Contribute and license

[Contributor guide](CONTRIBUTING.md) · [Changes](CHANGELOG.md) · [MIT License](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md)

The product name is **ARC Overhaul** (Chinese: **ARC 大修**); suggested remote repository name: `ARC-Overhaul`. The stable MOD ID remains `arc_overhaul`. This is an independent fan MOD, not the game or an official expansion. Git contains owned source/docs/test source and build tooling; it excludes game files, local dependencies and runtime/build outputs. No remote is configured by this local setup.
