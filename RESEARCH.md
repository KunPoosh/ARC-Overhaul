# ARC Overhaul implementation research

## dev.16: strategic landship speed

On game 1.2.15.3, `Fleet.speed(ArrayList, BonusSet, WorldMap)` takes the slowest `Airship.getMainMapSpeed`, starting with an upper cap of 1.5, applies the largest captain/medal boosts, then clamps the result to at least 0.03. The earlier `ShipSpeedReportMixin` only redirected the panel call, leaving fleet movement on the old air-drag formula. Native `Fleet.tick` multiplies fleet speed by milliseconds and 0.0125; ETA and interception also use fleet speed. A panel value of 2 km/h can therefore coexist with 60.85 km/h on the campaign map, and simply correcting the speed source would still leave the roughly 15.4 km/h floor.

`FleetSpeedMixin` redirects that single per-ship call for landships and changes the final lower bound to zero only when that group contains a landship. Three separate low-supply constants (instance speed and both `timeTo` overloads) become `min(0.03, corrected fleet speed)` for landships. Native grouping, bonuses, caps, route geometry and pure-airship rules remain in native code. No public campaign API exposes a speed hook, so these five precise injections are checked against shipped bytecode and actual Knot transformation.

`EffectiveSpeed.strategicMapSpeed` shares the existing friction solver but supplies no combat context. `Airship.tick` sets `CURRENT_COMBAT_DELETEME` without clearing it afterward; using that reference on the strategic map would import stale water drag and combat-only available lift. Strategic motion uses the design lift and air drag, with the owner's native map multiplier. Combat reports retain their existing context-aware calculation. Tests and coverage limits are in [TESTING](TESTING.md).

## dev.14 — 2026-11-06

dev.14 restores full-height, native framed resource bars beside the labels while retaining all five resources in two rows. At very small scales where a row is shorter than the native fixed padding, a one-pixel frame keeps the fill visible. Starting options now include independent AI cities (1-4) and towns (0-8), each accepting -1 for vanilla. AI cities use native AI income; extra slots include both cities and towns. Missing `aiCities` in old rules reads as -1. Range labels use font-safe separators.

Game 1.2.15.3 / API 0.3.7: **1,079 checks pass**; evidence `build/runtime-tests/dev14-final/summary.json`, JAR SHA256 `7400c17a72ffe93b865dc2989f6f809ee014fe70c9ad20152e4732657abca436`. New checks cover mixed settlement counts, native AI income, save restoration, and native resource drawing through a recording Frame at 50/100/125/150/200%. GPU appearance, full world generation and real multiplayer remain manual checks. The installed game has not been replaced.


[中文](RESEARCH.zh-CN.md)

## dev.11: resources, tracks, spectator research and AI towns

2026-11-06. Only Airships **1.2.15.3** is supported from this change onward. Research used its shipped bytecode and private decompilation; no game sources are distributed. The older version references below describe historical investigations.

`CommandButtonsPanel.renderShipQuantities(MyDraw, Airship, int, int, int)` shares one two-row area between command and direct-control panels. Its original 2×2 layout only substitutes repair supplies when another supply has no capacity. `ResourceBarsMixin` replaces that method alone. `client/ResourceBars` uses up to three columns/two rows, retains a resource if its original capacity or current amount is positive, and uses exact quantities in tooltips. Thin bars use native rectangles because `MyDraw.progressBar` insets four pixels and cannot draw a one-to-three-pixel bar. Font measurements bound shortened labels to each cell.

`ShipLayers.ALL` draws tracks in its foreground `TracksAndLegs(false)` pass. `TrackLayersMixin` adds a track-only pass immediately before `Armour`; `TrackRenderingMixin` routes the six `Module.wheels` reads to either the original or new pass. Both `Module.legs` reads stay on the original passes. The same native draw code keeps animation and material behavior; module lists and physics are untouched. `DisplayOptions` stores `tracksBehindArmour` in local `display.json`, default false. It applies to all ships on the client and is not a per-design property or shared campaign rule. The editor toolbar and MOD Details open the same public framework settings window.

The requested research fix concerns **a multiplayer campaign player spectating other empires' combat**. `ClientButtonsMixin` adds a native toolbar button; `SpectatorResearch` checks `MultiplayerCampaignCombatIntent.spectate`, the current combat identities, a player-controlled empire, and `playerCombatSideIndex == -1`. It opens the original `TechScreen(ss)`. Its existing input calls `StrategicScreen.backgroundTick`, and technology actions call `CampaignWorld.giveCommand` for `ss.w.player`; multiplayer commands still go through the native transport and execution path during combat. No local research fields, participant permissions or network protocol are changed. The public `CLIENT_TICK_END` event returns from the native close destination to the same still-active spectator screen, while leaving battle-end/resync/disconnection transitions alone.

`StartValues.aiTowns` is independent of human towns, accepts -1 or 0–8, and defaults to -1 when absent in older configs or frozen rules. The existing shared payload remains version 1. Per-map capacity is the maximum of native slots, human extra settlements and AI towns. `WorldMap$3` chooses counts by empire type, retains contiguous IDs, and only converts human slots into extra cities. Explicit AI counts receive the same bounded landmass guard and exact-count validation; default AI rules retain native behavior. CREATED verifies one city plus the configured towns for each AI without granting it human cash/research. Native early capacity restoration remains unchanged. Acbric 0.3.6 distributes these gameplay rules from the host.

The same dev.11 JAR passes 809 checks on 1.2.15.3 with API 0.3.6 and 0.3.7. Resource output uses a recording canvas, tracks use pass/routing assertions, and spectator commands use a capturing network transport. Full world generation, GPU visuals and a real multiplayer session remain unverified. See [TESTING](TESTING.md) for reproduction and evidence.

## Historical phase one: conquest starting options

2026-09-26. dev.2 implements the feature with 71 targeted checks per game build. Full world generation, initial assets, graphical UI and multiplayer remain unverified; signatures and partial tests are not a compatibility guarantee.

## Confirmed semantics

The user explicitly chose starting cities, towns and cash per human empire. AI uses vanilla counts/cash rules. These are not map-wide totals and do not change the number of empires. Random-draw changes can still affect AI positions and assets.

Cash means the spendable balance after initial buildings/ships, not their budget. Each setting independently accepts -1 for vanilla. Custom ranges: cities 1–4, towns 0–8, cash 0–1000000.

## Source locations

Reference: a privately retained decompilation of the Steam 1.2.15.2 build. Approximate lines below belong to that snapshot and will differ with decompiler/version. Game sources and research binaries are not part of this repository; method/class names identify the integration points.

| Source | Approximate lines | Role |
|---|---:|---|
| `asplit-A/com/zarkonnen/airships/GameSetupScreen.java` | 137, 167, 188 | Input, startGame and campaign construction. |
| Same file | 195, 257, 329, 341 | Rendering, settings scroll area, getHeight and draw; account for added controls in scroll height. |
| `asplit-B/com/zarkonnen/airships/WorldGenScreen.java` | 32, 43 | Loading/progress screen and map.doSetup driver, not configuration UI. |
| `asplit-B/com/zarkonnen/airships/WorldMap.java` | 237, 255 | Private generation stages; distance-array capacity derives from empire and town counts. |
| Same file | 285–381 | Creates one empire and one full city per iteration. Initial setupInfos entries identify human empires. |
| Same file | 383–419 | Round-robin town placement. Failed placement may be skipped. |
| Same file | 435–451 | Ritual-site selection indexes a minimum number of towns without a count guard. Zero/few towns need explicit handling. |
| Same file | 660–682, 3389 | Territory distance arrays and city cache use city IDs; adding/removing cities late is unsafe. |
| Same file | 700–710 | Difficulty budget, CitySetup.setup and final cash. |
| Same file | 1417–1418 | setupPlayer immediately precedes first autosave. |
| Same file | 3956, 4170, 4194 | Generation driver, new-world constructor and randomized initial asset budget. |
| `asplit-A/com/zarkonnen/airships/MapSize.java` | 12–22 | Final fields for dimensions/empires/towns; default two towns per empire. |
| `asplit-A/com/zarkonnen/airships/CitySetup.java` | 32–44 | Starting asset allocation depends on full-city/town counts. |
| `asplit-B/com/zarkonnen/airships/StrategicLobbyScreen.java` | 832, 933 | Multiplayer settings message and campaign creation. Single-player UI changes do not implement host-setting broadcast. |

Flow: `GameSetupScreen.startGame → CampaignWorld/WorldMap construction → WorldGenScreen → staged WorldMap.doSetup → setupPlayer → autosave → StrategicScreen`.

## Legacy reference

The relevant reference is `acbric-starting-cities.jar`: ID `acbric_starting_cities`, version 0.3.1, defaults 2 cities/3 towns. SHA256: `4916572246d4781841713ef7601b4e8c08dadf6724ab72de8bd7be151a023165`. A different City Upgrade MOD handles building upgrade chains and is not this feature. Legacy binaries/decompilations remain outside this repository; the implementation here is maintained as independent source.

The old MOD increases global townsPerEmpire to at least towns + cities - 1, affecting AI and subsequent campaigns; it never reduces that capacity. On StrategicScreen construction it converts human towns, excluding ritual sites, and increases income to at least 30 or twice its old value. This occurs after initial assets and first autosave, and may repeat on later screen construction. Fixed-position plus/minus buttons have no upper limit. Optional multiplayer hook names and local static settings do not establish synchronization. Capacity, skipped ritual towns and placement failures mean entered counts are not exact. No cash setting exists.

## Implementation and boundaries

ARC keeps gameplay/configuration in conquest/ and native adapters in mixin/. The framework remains unchanged. An entry at the bottom of the single-player setup scroll area opens framework numeric controls; MOD Details offers the same editor. No lobby editor is added.

Acbric freezes rules before generation. The first doSetup creates a per-map MapSize capacity copy without changing global Loadables, map dimensions or empire count. All native distance arrays, nest IDs and stage sizes use this capacity. WorldMap$3 skips unused AI/player slots and changes the first human slots into cities at the native income call, before land/buildings are generated. City income uses the native function. PR #1 assigns contiguous IDs to newly created settlements at placement return, preventing gaps from terminating native territory tracing. IDs remain vanilla when no slots are skipped; existing saves are not renumbered. WorldMap$4 caps ritual sites to available towns. Failed placement aborts; CREATED rechecks counts and sets cash before autosave. No StrategicScreen/load/restore grants are installed.

Configuration/UI/shared rules reuse public APIs. A technical `arcStartingLayout={version:1,slots:N}` field restores native ID capacity early in WorldMap deserialization, before normal post-construction MOD storage is available; it is not gameplay configuration and never reads local candidates. Native OutPipe/state and disk saves are checked. Old saves missing ARC rules are rejected, not automatically adopted. The original multiplayer implementation required matching local settings; since Acbric 0.3.6 the host distributes the snapshot for clients to validate and adopt. Full sessions remain unverified. No new framework interfaces are introduced. Metadata rejects the legacy Starting Cities MOD.

Test vanilla-default equivalence, count bounds/zero towns, insufficient space, consecutive worlds, exact cash, save/load without repeated application, bilingual UI/scroll/Enter behavior and any multiplayer paths explicitly supported. Do not claim universal compatibility with map sizes or other world-generation MODs.

## AI fleets (ConstructionStrategy)

2026-09-27. An "AI fleet" in the game's own terms is a `ConstructionStrategy` — the class the AI Fleet Creator produces. It carries `shipNames`, `landshipNames` and `buildingNames` per tier, upgrade sequences, `techs`, `charges`/`requiredCharge`, `minDifficultyLevel`/`maxDifficultyLevel` and an `enabled` flag. `Loadable.all(ConstructionStrategy.class)` is the live catalogue; fleet packs ship a `ConstructionStrategy/` data directory, which is why the list grows with installed MODs.

Where the game assigns one:

| Location | Detail |
|---|---|
| `WorldMap$2.run(int, WorldMap)` | The `SetupStage` that creates one empire per call. It builds `ArrayList<ConstructionStrategy> strategies = ConstructionStrategy.forCharge(arms.getActiveCharge(), map.difficulty)` and passes `strategies.get(map.r.nextInt(strategies.size()))` to the `Empire` constructor. |
| `ConstructionStrategy.forCharge(Charge, DifficultyLevel)` | Three passes over the catalogue, each requiring `enabled` and the difficulty window; pass one also requires the heraldic charge to match (`charges.contains(charge)`, or `requiredCharge == charge`), pass two accepts strategies with a null `requiredCharge`, and an empty result makes the native `nextInt(0)` throw. |
| `Empire.constructionStrategy` | `public final`, assigned in the constructor — the fleet cannot be replaced once an empire exists. |
| `AIConstructionUtils`, `CityAI`, `StrategicAI` | Read the field at runtime to decide what the AI builds and researches. |

Four `ArrayList.get` calls exist inside `run`: city names (offset 312), the fleet list (420), tech choices (786) and heroes (1340). Since they are byte-identical `INVOKEVIRTUAL java/util/ArrayList.get:(I)Ljava/lang/Object;` instructions, a `@Redirect` has to select one by `ordinal`; ARC uses `ordinal = 1` and type-guards the receiver so a future drift degrades to vanilla behaviour instead of mis-reading another list. The checked-in probe parses the shipped class file and asserts both the count (four) and the position (only the site followed by `checkcast ConstructionStrategy` is #1).

Settings are stored separately from the starting values but travel with them: the Acbric shared-rules registry allows one declaration per MOD ID, so ARC merges both into a single JSON payload. The rule version stays 1, and a payload without the `fleets` key reads as "all allowed", so campaigns saved earlier keep loading.

## Evidence so far

The feature builds offline with JDK 21 / Gradle 8.13. Historical evidence for dev.1/dev.2 is privately retained by the maintainer; the original dev.3 baseline passed 71 checks per game build. After merging PR #1, contiguous-ID/tracing regressions pass 407 checks per build with API dev.25, and the user confirmed the territory-colour fix manually. See [TESTING](TESTING.md) for reproduction, test fixtures, synthetic ownership grids and limits on full terrain/roads/initial-assets/GPU/multiplayer coverage. Results are written to `build/runtime-tests/<tag>/summary.json`.
