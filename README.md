# ARC Overhaul

[中文](README.zh-CN.md)

A standalone gameplay MOD, separate from Acbric. **0.1.0-dev.9** implements starting cities, towns, cash and research points for **human empires only**, plus full control over **which AI fleets appear on the map** and **T orders that can name defenceless targets**. AI starting counts and cash rules remain vanilla, although changes in placement and random draws can alter AI positions and asset combinations for the same seed.

## Use

Requires Acbric API **0.3.3-dev.21 or a later compatible version** and Java 21. Exit the game and place `build/libs/ARC-Overhaul-0.1.0-dev.9.jar` in its `game/mods/`. Disable the old `acbric-starting-cities.jar` and restart first; the MODs declare a conflict. Do not install the dev.1 scaffold or fixture JARs.

Open **single-player conquest setup → scroll the settings list to the bottom → ARC Overhaul: Player starting options**, or **MOD list → ARC Overhaul → Details**. Edit, Apply, close, then start a new campaign. English/Chinese follow the game language.

The same list has a second entry, **ARC Overhaul: AI fleets**, described below.

| Field | Custom range | Meaning of `-1` |
|---|---|---|
| Cities | 1–4 | Vanilla: one city |
| Towns | 0–8 | Current map default |
| Cash | 0–1,000,000 | Vanilla balance after difficulty and initial assets |
| Research | 0–10,000,000 | `0` grants nothing (vanilla) |

The first three default to `-1`, research defaults to `0`, and they work independently. Zero cities is rejected on Apply; zero cash is valid. Cash is assigned after initial buildings/ships and does not increase their budget. Counts affect native asset allocation and balance.

Research points are banked into the native *unassigned* research pool (`Empire.unassignedResearchPoints`), which the game pours into the first research you select. Writing them into `Empire.researchPoints` instead does not work: the native "select research" command overwrites that field with the new tech's partial progress (0 for a fresh tech) before adding the pool, so the grant would be silently discarded on the first click. The range is in the millions on purpose — a technology costs `BASE_RESEARCH_COST × RESEARCH_COST_MULTIPLIER × 5600 × 1.4^tier` points (`Tech.baseCost`), about 2,240,000 for the first tiers, so a smaller grant is invisible on the progress bar.

ARC-added settlements are never placed on a speck of land: the native spot finder accepts any non-water tile, so ARC re-rolls until the tile belongs to a connected landmass of at least `max(32, gridSize/4)` tiles. Retries are bounded and fall back to the last candidate, so the guard can never turn generation into a failure. Only human empires, and only when the counts are customised; AI and default settings keep the untouched native result.

Aircraft keep their attack runs pointed at the target: the native strafe logic stores one world-coordinate aim point per run and discards it once the target has moved 200 px away from it, then re-picks the point purely from which side of the target the aircraft is on — so a drifting target can send the aircraft the other way mid-run, which a bomber (lowest acceleration of the five aircraft, and it must fly straight over the middle of the target to release) pays for with an entire wasted pass. ARC now translates that aim point with the target's own movement, keeping the offset constant. See [CHANGELOG](CHANGELOG.md) for the mechanism and the measurements.

A T order can now name a **defenceless** target: select a ship, press T and click an enemy building or an unarmed airship, and its guns really do engage it. Vanilla writes "is this target worth shooting" as `dangerCache > 0` (`Module.targetShip`, line 1464), and an unarmed immobile building never gets any threat value from `Airship.danger()` (its `inCombat()` is false), so the order was silently dropped and the guns went back to their own automatic targets. ARC raises that value to a positive number only on the reads that judge the target the player named; automatic target selection still ranks purely by the native threat value, so ships and aircraft never pick a defenceless target on their own — someone has to name it with T. Direct control is unaffected: it aims with the reticle and never consulted `dangerCache` in the first place.

The same rule makes an **unarmed carrier** able to use T: launched aircraft inherit their carrier's `fireAt` (`Crewman.outsideShootingTick`, line 2248), so "select the carrier, then T an enemy" sends its aircraft after that target. Vanilla gated that inheritance on `dangerCache > 0` too and wiped the inherited target during reloads; on top of that, a carrier inside a box selection was filtered out by `canShoot()` so the order never left the client, and the panel's T button stayed grey for a weaponless ship for the same reason.

For the aircraft half of "can this ship take a T order", the flight centre alone is **not** the right test: `canGiveAircraftCommands()` is driven by `ModuleType.canGivePlaneCommands`, which only `FLIGHT_CENTRE` declares in the game's data, while the hangars (`BOMBER_HANGAR` and five more) declare only `quartersType` (whose `CrewType.canFly` is true). A carrier built from hangars without a flight centre therefore reads false there and its T button stayed grey. ARC uses the vanilla `Airship.hasFlyers()` instead so those carriers count too; a transport with neither guns nor aircraft bays is still skipped as in vanilla.

Count limits are conservative. Insufficient space aborts generation instead of silently producing fewer settlements. More than 128 MiB of additional distance-array capacity is rejected; this is not a total game-memory limit.

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
- Multiplayer uses existing rule checks, not host configuration broadcasting. All peers must set matching values through MOD Details before joining. No lobby editor is added; full multiplayer generation/resume remains unverified. Test single-player first.
- Other world-generation MODs may conflict. Building has not changed the framework or the installed game.

## Build

Use JDK 21 and a matching Acbric API dev.21 build. See [contributor setup](CONTRIBUTING.md) for exact dependencies and local paths. The framework baseline is commit `1be89b2`; it may not yet be available on the upstream default branch.

```powershell
.\gradlew.bat build
# Override the default sibling Acbric checkout / its game libs:
.\gradlew.bat build -PacbricDir="D:/Development/Acbric" -PgameLibDir="D:/Games/Airships/libs"
```

Output: `build/libs/ARC-Overhaul-0.1.0-dev.9.jar`. Replace the previous ARC JAR instead of installing both. Game, framework and test classes are not bundled. Nothing is automatically installed. `build` checks compilation/packaging; its default `test` has no sources. Run [integration tests](TESTING.md#run-isolated-checks-windows) separately with your own game inputs.

PR #1 fixes missing territory colour for extra towns, with manual confirmation from the user. Both game builds (1.2.15.2 / 1.2.14) pass 407 targeted checks each, 814 total, using API dev.25: real Fabric transformation, native placement/contiguous IDs, territory tracing, one-time cash, configuration and native disk/binary-state persistence. Arms/background/land resources use fixtures and territory tracing uses synthetic ownership grids; full map/roads/initial-assets generation, GPU and multiplayer are not comprehensively verified. See [testing](TESTING.md).

## Layout

- `src/main/java/net/poosh/arc/`: owned source, to be split into feature packages.
- `src/main/resources/fabric.mod.json`: identity and entrypoint.
- `build/`: regenerable outputs.
- [Source investigation and design boundaries](RESEARCH.md): implementation evidence and legacy comparison.

Gameplay lives in `conquest/`; native adapters live in `mixin/`. Keep future gameplay modules separate from the framework.

## Contribute and license

[Contributor guide](CONTRIBUTING.md) · [Changes](CHANGELOG.md) · [MIT License](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md)

The product name is **ARC Overhaul** (Chinese: **ARC 大修**); suggested remote repository name: `ARC-Overhaul`. The stable MOD ID remains `arc_overhaul`. This is an independent fan MOD, not the game or an official expansion. Git contains owned source/docs/test source and build tooling; it excludes game files, local dependencies and runtime/build outputs. No remote is configured by this local setup.
