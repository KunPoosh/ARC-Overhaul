# ARC Overhaul test guide

## Current: dev.16

Game 1.2.15.3 / pinned API 0.3.7: **1,148 checks pass** (1,093 existing + 55 new). Evidence: `build/runtime-tests/dev16-final/summary.json`; ARC SHA256 `bf253562b1ad9cb8005059b8b82c66438606de161bddb4983f53372ff20ecc23`. Final tests against dev.15 fail at the expected speed mismatch in `build/runtime-tests/dev16-negative-final/v153/runtime.log`: a 2 km/h panel fixture travels at 60.85 km/h, and a weaker fixture with native ship speed `0.003162` is lifted to `0.03`. With dev.16, the same fixtures travel at `0.0034899565` and `0.0000049950` respectively.

`FleetSpeedRuntimeChecks` exercises real `Fleet.speed`, 600 `Fleet.tick` calls, road position, remaining ETA, both route-preview overloads and interception of a stationary road target. It covers legs/tracks, zero propulsion, active/reserve/mixed/empty groups, owner map multipliers, captain/medal boosts, lift, module speed caps, low-supply return, stale combat references and unchanged airship-only behavior. All five injected handlers are present under Knot; shipped bytecode checks the ship-speed call and four `0.03` constants. Ship/module/road inputs and one bonus table are synthetic; this is not a run of the player's original supply landship, a graphical campaign, complete world generation or real multiplayer.

Dependencies are pinned under `build/framework-inputs/dev16-api037` from the dev.15 acceptance fixture (API SHA256 `4cb122d61c16e38ce1eb48f2abc975330d875cba9a5f6105d90e92b44296ab46`). The mutable framework checkout produced different API bytes and failed the existing 125% UI-metric check before any speed test; that alternate artifact is not certified by this run. The Gradle build passed with these pinned inputs. On this machine JDK's default temporary Unix socket failed; the build used a workspace-local `jdk.net.unixdomain.tmpdir`, without changing machine settings.

Manual acceptance: in a campaign, send a very heavy, weakly powered landship alone and then in a mixed fleet along the same road, compare its travel time with a normal design, and repeat with it in reserve and on low-supply return. A displayed 2 km/h still means slow positive movement; zero propulsion must produce zero progress. No player files were changed. Retest the installed artifact and real multiplayer separately.

## dev.15

Game 1.2.15.3 / API 0.3.7: **1,093 checks pass**, `build/runtime-tests/dev15-final/summary.json`. JAR SHA256 `346cbcc61b46dd65c07f918b87a4cad9d16411498a64923737416fc96c301ea4`. EditorDisplayChecks opens the real display window, sends framework-filtered input through native UniScreen.input over multiple frames, verifies ticking without click-through, closes the modal and resumes normal clicks. Editor intent/tool fixtures stand in for a full graphical session. The same test against dev.14 reproduces the reported null cursor exception (`dev15-negative-final/v153/runtime.log`). Both field-read injection sites are checked in shipped bytecode. Manual acceptance: open ARC display in an editor with unsaved changes, Apply/Cancel/close it, confirm the same ship stays open and ordinary editing resumes. The installed game has not been replaced.

## dev.14 — 2026-11-06

dev.14 restores full-height, native framed resource bars beside the labels while retaining all five resources in two rows. At very small scales where a row is shorter than the native fixed padding, a one-pixel frame keeps the fill visible. Starting options now include independent AI cities (1-4) and towns (0-8), each accepting -1 for vanilla. AI cities use native AI income; extra slots include both cities and towns. Missing `aiCities` in old rules reads as -1. Range labels use font-safe separators.

Game 1.2.15.3 / API 0.3.7: **1,079 checks pass**; evidence `build/runtime-tests/dev14-final/summary.json`, JAR SHA256 `7400c17a72ffe93b865dc2989f6f809ee014fe70c9ad20152e4732657abca436`. New checks cover mixed settlement counts, native AI income, save restoration, and native resource drawing through a recording Frame at 50/100/125/150/200%. GPU appearance, full world generation and real multiplayer remain manual checks. The installed game has not been replaced.


## Current: dev.13

dev.13 integrates the four dev.11 features with dev.12 percentage UI scaling. The installed dev.12 was built from dev.10 plus commit `62b8f1c`; it never contained the resource, track, spectator or AI-town changes. Both test suites now run together: **907 checks pass on game 1.2.15.3 / API 0.3.7**, recorded in `build/runtime-tests/dev13-integrated/summary.json`. JAR SHA256: `80886894a18f38ce5727b42028a7fd765da4268a9a0d2fae3e1ca67c603de81b`. Packaged classes and Mixin registrations were checked too. GPU visuals and real multiplayer remain unverified; the running installation was not replaced.

UI scaling is available through native Settings, MOD Details, or the conquest setup list: **ARC Overhaul: UI scale**. Enable custom scaling and choose 50–200%; 100% is native Small. It is a local preference and requires API 0.3.7.


[中文](TESTING.zh-CN.md) · 0.1.0-dev.11 · 2026-11-06

Only **game 1.2.15.3** is supported. The dev.11 JAR passes **809 checks** on both Acbric API 0.3.6 and 0.3.7 (622 existing checks plus 187 new checks). `gradlew.bat build` also passes; Gradle's `test NO-SOURCE` is not counted as a test run.

| Input / evidence | Value |
|---|---|
| API 0.3.6 run | `build/runtime-tests/features-final-api036/summary.json` |
| API 0.3.7 run | `build/runtime-tests/features-6/summary.json` |
| ARC JAR SHA256 | `0d4bfe37be77642f4b9d02dc92fac62d8b032855bd7a7e4f7d89ccb436c2976c` |
| Game `asplit-A.zip` SHA256 | `a0043b256558c16d90d2610514fc534181efb0eb4eb3f843dbf1fe73b28a2e75` |
| Game `asplit-B.zip` SHA256 | `fc8dc9f0976c49311cb6cfe96d246518ac809341efe4868be3a68262d9806759` |

The new checks drive the transformed resource-panel entry point with a recording canvas at widths 72/250/600 in English and Chinese, including all five resources and exhausted repair supplies. They check layer ordering, wheel/leg routing and bytecode read counts (six wheel reads, two leg reads); spectator eligibility, the real native technology click and `CampaignWorld.giveCommand` with a capturing `sendMessage` transport, and returning from research; AI towns at 0/1/8, mixed human/AI layouts, contiguous IDs, frozen rules and native save restoration. There is **no GPU/glyph rasterization or real multiplayer session**. Arms/background/land assets and fleet registration still use fixtures; full world generation, roads and initial assets remain manual checks. Older results below are historical and do not extend current version support.

## Historical verification

dev.9 adds T orders against defenceless targets and T orders given to unarmed carriers: game 1.2.15.2 / 1.2.15.3 each passes **606 checks**, 1,212 total (including the 15 load-friction assertions from feat5 on this branch), recorded in `build/runtime-tests/ordered-target-dev9/summary.json`, JAR SHA256 `f89013758fa04609675cb3057e90c74c86b27493b9360ac8108f0632e2a64d5a`. The four injection points are asserted by parsing the shipped kernel bytecode and another 34 assertions drive the injected handlers on the real kernel classes. The static injection check (`arc verify`) last ran on the **same mixin set** (only the version string differs) and reported OK for all fifteen ARC injections; this round it could not run because the framework has been upgraded to 0.3.5.1 and the tool can no longer materialise its kernel view. The rest of `CommandButtonsPanel.draw`, full world generation, the graphical UI and end-to-end multiplayer remain unverified.

2026-09-27 integration of PRs #3 / #4 / #5: dev.7 combines AI fleet settings, target-relative aircraft strafe points and grounded speed reporting. Conflict resolution retains both Mixin registrations and all test calls, with dev.7 used consistently; feature source semantics are unchanged. Build passed with JDK 21 / Acbric API 0.3.3-dev.33. Game 1.2.15.2 and 1.2.14 each passed 529 checks (1,058 total), recorded in `build/runtime-tests/pr3-5-integrated-20260927/summary.json`. Fleet registration and some assets use fixtures; strafe checks directly invoke the transformed handler. Full world generation, graphical combat, roads/starting assets and end-to-end multiplayer remain unverified. Player installations were not overwritten. Earlier branch-specific records and counts below are historical.

## Install and open

1. Exit the game. Use game 1.2.15.3 and API 0.3.7 or a later compatible version. Disable Starting Cities and restart, or keep its JAR outside `mods/`. Install only one ARC version.
2. Copy `build/libs/ARC-Overhaul-0.1.0-dev.15.jar` to the running copy's `game/mods/`, replacing the old ARC JAR. No installed copy was overwritten by the build. **After copying, check its SHA256 against the dev.13 value above** (`Get-FileHash <jar> -Algorithm SHA256`).
3. Start a new single-player conquest setup. Scroll its settings list to the bottom and select **ARC Overhaul: Starting options**, or use **MOD list → ARC Overhaul → Details**.
4. Edit, Apply, close, then use the native Start button. Counts and cash accept `-1` for vanilla; research defaults to `0`. Fields are independent.
5. Do the same with the second entry, **ARC Overhaul: AI fleets**; it lists every loaded AI fleet and is described in [AI fleets](README.md#ai-fleets).

## Manual checks

New dev.11 scenarios (not yet verified in a graphical game or a live multiplayer session):

| Scenario | Expected result |
|---|---|
| Ship with crew, ammo, water, coal and repair; command and direct control | All five appear within the same two-row area; repair never replaces another resource |
| Empty supplies, destroyed supply modules, narrow panel, language/UI scale changes | Original capacities remain; no overlap; hover shows exact quantities when text is shortened |
| Editor → ARC display → Tracks behind armour; toggle and Apply both ways | All local tracks move behind armour/decorations and back; legs keep their layer; movement/physics unchanged |
| Restart and open MOD Details → ARC Overhaul: Display | Applied track preference persists; another client's preference may differ |
| Own a country, spectate two other empires fighting in multiplayer, click Research or press R | Own technology tree opens; select/change/queue research through native controls; battle continues |
| Close technology tree with Esc and with its close button | Returns to the same ongoing spectator battle |
| Battle ends or network resynchronizes while the technology tree is open | Native transition proceeds; no forced return to an obsolete combat screen |
| Participate in the battle, watch a replay/skirmish, or have no player empire | This spectator research entry is absent |
| AI towns 0, 1 and 8 on new campaigns; human values unchanged | Every AI has one city plus exactly the selected towns; human settings remain independent |
| AI towns -1 or old rule without aiTowns; save/load a custom campaign | Default AI rules stay native; saved counts/capacity restore without repeating grants |
| Multiplayer host AI towns 8; joining client local AI towns 0 | New campaign uses host's frozen 8; no manual matching of local numbers |

Existing scenarios below assume AI towns `-1` unless stated otherwise.

| Scenario | Expected result |
|---|---|
| Counts/cash -1, research 0 | Vanilla one city, map-default towns and cash |
| Cities 3, towns 3, cash 12345 | Exactly those player counts/cash on entering the map; AI follows vanilla counts |
| Cities 2, towns 8 | Every extra player town has territory colour; check AI territory as well |
| Cities 1, towns 0, cash 0 | One city, no towns/cash, no ritual-selection bounds error |
| Cash only, counts -1 | Counts unchanged; initial asset budget is not increased |
| Research 3000000, new campaign | On entering the map nothing is shown yet (the game has no display for banked points); selecting any tech immediately fills its progress bar, and a cheap tech completes at once |
| Research 0 | No research is granted; the tech screen behaves exactly like vanilla |
| Research -1, letters, over 10000000 | Rejected in the field or on Apply; previous values remain |
| Extra towns on a map with small islands | No player settlement sits on a speck connected to nothing; AI placement is unchanged |
| Bombers attacking a moving enemy ship | Bombers finish their pass instead of turning around far short of the target; they still turn after crossing it, as vanilla does. Compare a slow target (near-stationary) with a fast one moving away: the run length should not collapse |
| T an unarmed building (e.g. a defence platform with no guns) with a ship that has guns | The ship's guns turn on that building and keep firing at it instead of going back to their own automatic target |
| T an unarmed (or disarmed) airship with a ship that has guns | Same: the shells land on the ship you named |
| The same ship and the same defenceless target without pressing T | The ship does **not** engage it on its own (automatic selection still ranks by the native threat value) |
| Direct control: aim the reticle at that same defenceless target | Unchanged and still works; this change does not touch direct control |
| Box-select a fleet that contains an unarmed carrier, then T an enemy | The carrier receives the order too (previously only the armed ships did) and its aircraft go after the target |
| Select only the unarmed carrier and look at the panel's T button | The button is usable (it used to stay grey for a weaponless ship); T an unarmed enemy building and the aircraft attack it |
| A carrier with only hangars (say three bomber hangars) and **no flight centre**, same steps | Same as above: the button is usable, a box selection still delivers the order, and the aircraft attack the named target (the flight centre is not the test) |
| Select a weaponless transport with no aircraft bays | The T button stays grey exactly as in vanilla |
| Look at the T button while the selected ship is cooling down | Grey; this change never lights it up |
| While the carrier's aircraft are reloading | The aircraft keep chasing the named defenceless target instead of dropping it mid-run |
| Cities changed, towns -1 | Additional cities plus the map-default number of towns |
| Zero cities, letters, fractions, out-of-range | Rejected in the field or on Apply; previous values remain |
| Cancel / Esc / X after editing | Confirmed discard preserves previous values; Enter does not start a campaign through the editor |
| Small window, scaling, scrolling, focus changes | Visible entry clickable, hidden entry not clickable; fields and close controls work |
| Change game language and reopen | English/Chinese titles, descriptions and controls |
| Custom campaign A, then default campaign B | B uses vanilla counts without global map-setting contamination |
| Spend money/change territory, save/reload A | Actual state restored without starting grants or repeated conversion on strategy-screen entry |
| Restart | Applied preferences persist; damaged files not silently overwritten |
| Insufficient space | Actionable failure, no mismatched partial campaign saved as successful |
| AI fleets: everything left on Allow | AI fleets on the map are indistinguishable from a vanilla run with the same seed |
| AI fleets: one fleet Force disabled, new campaign | That fleet is never on the map, however many starts you try |
| AI fleets: one fleet Force enabled with 3, new campaign | Exactly three non-player countries use it; your own country does not |
| AI fleets: count larger than the number of AI countries | Every AI country gets it; no error, and no leftover quota carries into the next campaign |
| AI fleets: every candidate for an empire banned | The campaign still generates; the log records the fallback instead of an error |
| AI fleets: fleet pack disabled, reopen the screen | The fleet disappears from the list; re-enabling the pack restores its previous setting |
| AI fleets: Cancel / Esc / X after editing | Confirmed discard preserves previous values |
| AI fleets: change game language and reopen | Fleet list, three states and buttons follow the game language |
| AI fleets: Force disable all, then Apply | No loaded AI fleet appears on the map |
| AI fleets: Force enable all, then Apply | Each AI country gets a different fleet from the list until the fleets run out |
| AI fleets: clear a country count and type a new one | The box stays empty while you type instead of snapping back; the new number is used |
| AI fleets: clear a country count, then type in another fleet | The cleared box shows its previous number again |
| AI fleets: clear a country count, then press Apply | Nothing is lost: that fleet keeps the number it had before you cleared it |

Start with a medium map and the first three scenarios, then test minimum/maximum sizes. Record seed, difficulty and values. Changed parameters alter random draws; AI positions and assets need not match another run exactly.

## Saves and multiplayer

Use new test campaigns. Old saves without ARC rules produce `RULE_MISSING`; no automatic conversion occurs. Skip old-save adoption experiments for now. Keep ARC installed for ARC campaigns.

Full multiplayer remains unverified. Use compatible matching ARC/API installations. Since Acbric 0.3.6 the host broadcasts the gameplay-rule snapshot and other clients validate/adopt it; local starting numbers do not have to match in advance. This includes AI towns. Track display is local and does not enter shared rules. Spectator research sends native campaign commands for the player's own empire; test on a disposable new campaign with at least two clients. There is no additional lobby editor.

## Automated coverage and diagnostics

The paragraphs below retain the earlier feature-specific evidence; the current combined run and its limits are at the top of this guide.

The AI fleet change runs **506 checks** on game 1.2.15.2 with Acbric API `0.3.3-dev.21` — the previous 426 plus 80 fleet assertions (`build/runtime-tests/fleet-final2/summary.json`). They cover rule parsing/round-trip and rejection of invalid modes, counts and unknown keys; the settings window built on the real config handle in both languages and its component tree walked node by node (one row per loaded fleet, each row a label plus a three-state chooser plus an editable country-count field that starts at 1 and stays disabled until the fleet is force-enabled); the draft session driven through its real editor (invalid counts block saving, saving writes the file and publishes the shared rules, a rule for a fleet that is no longer loaded survives both, reload discards the draft and defaults clear everything); real config-file persistence; the real shared-rule payload carrying starting values and fleet rules together; the country-count box keeping its raw contents (clearing it does not snap back, an empty box reports no error and does not block Apply, an out-of-range or non-numeric entry does, and an empty box is restored once you type elsewhere or press a button or Apply), the bulk force-disable-all / force-enable-all actions, and the selection function itself against real `Loadable` lookups, real map `GuardedRandom` draws and real frozen campaign rules — default rules return the native pick without consuming a random draw, a banned fleet never appears, a force-enabled fleet lands on exactly its country count and is not also handed out at random, the player's country is skipped, and an unloaded fleet only clears its quota.

The injection point is asserted by parsing the shipped `WorldMap$2` class file: `run(ILcom/zarkonnen/airships/WorldMap;)Z` contains four `ArrayList.get` call sites, and the only one whose result is cast to `ConstructionStrategy` is call site **#1** — exactly what `ordinal = 1` selects. What is **not** automated: the fixture has no `ConstructionStrategy` data directory, so the fleet catalogue is registered synthetically, and the native empire-creation stage (`WorldMap$2.run`) is not executed end to end because it needs arms assets the harness does not provide. Only one game build is installed on this machine, so 1.2.14 was not re-run for this change, and the two new buttons in the native settings list were not driven headlessly — the window behind the second button is checked, the native list row that opens it is not.
After merging PR #1, game 1.2.15.2 / 1.2.14 each pass **407 checks**, **814 total**, using Acbric API `0.3.3-dev.25` and JDK 21. Local results: `build/runtime-tests/pr1-contiguous-final/summary.json`. The original dev.3 result of 71 checks per build is historical.

The 33 added layouts cover two/four empires, different human positions, extra/reduced/zero towns, maximum counts, defaults/cash-only/explicit vanilla counts, and native AI placement failure. They check counts, types, unique contiguous IDs, city-cache identity, default placement/RNG equivalence, and directly invoke native `ShapeUtils.cityOwnershipAreas` to check that every settlement receives an area. Running the same test against the pre-fix dev.3 JAR fails the new contiguous-ID assertion as expected (local `pr1-negative-old-mod` evidence).

Aircraft strafe anchoring adds 11 checks (the Crewman transformation plus 10 assertions) and does not start a battle: they fabricate a `Crewman` and an `Airship` with `Unsafe`, write the target's x straight into `PhysicsRect`, and invoke the injected handler with a real `CallbackInfoReturnable`. They assert the target-relative offset stays exactly constant across 1000 px of target movement, that a stationary target does not keep shifting the point, that a native re-pick and a target switch re-anchor without shifting, and that a null target or null aim point is a no-op. What they do **not** cover: a live `Combat`, the steering loop that acts on the shifted point, and therefore the actual turn-around in a real battle — that still needs manual gameplay testing.

The T-order change adds **62 checks**, none of which starts a battle, in three parts. (1) Transformation: all injected handlers are present on the four classes (two on `Module`, two on `Crewman`, one on `TargetCommandTool`, two on `CommandButtonsPanel`, including one `@Inject` and one `@Unique` field). (2) Injection points: ASM parses the **raw class bytes from the archive** (never the Mixin-rewritten class) and asserts, in field-read order, that the `ordinal` used really points at the intended read — the 2nd of the four `dangerCache` reads in `Module.targetShip` follows a `fireAt` read, the 1st in `Module.target` follows `prevTargetShip`, the 1st and 2nd in `Crewman.outsideShootingTick` follow `attackTarget` and `fireAt`; `TargetCommandTool.click` contains exactly one `canShoot()` call; and of the fifteen 5-argument `iconButton` call sites in `CommandButtonsPanel.draw`, the 13th uses the `CommandButtonsPanel.TARGET` icon. (3) Behaviour: the injected handlers are invoked directly (`Module` and `Crewman` are fabricated with `Unsafe` and their fields written by hand; the panel uses its real constructor, which only news up `Img` coordinates; `TargetCommandTool` is driven with an `Airship` subclass). They assert that only a target named by T passes the worth-shooting test while an unnamed one still reads 0, that ships with guns, with a flight centre, or with hangars alone can be given the order but a plain transport cannot, that a cooling-down ship never lights the button, that a non-`TARGET` icon is forwarded untouched, and that nothing is added before the panel has drawn. The "hangars alone" case runs through the **vanilla decision chain**: a synthesized hangar module (its `quartersType` pointing at a crew type with `canFly = true`, `canGivePlaneCommands = false`) is attached to an `Airship` subclass and the checks assert that `canShoot()` and `canGiveAircraftCommands()` are both false while the native `hasFlyers()` is true, so ARC still lets it through — exactly the case the earlier flight-centre-only version missed. What they do **not** cover: the rest of `CommandButtonsPanel.draw` beyond the one redirected `iconButton` call, and how the button actually looks in the game. In game terms, "T orders against defenceless targets" was confirmed manually by the user, while the earlier "unarmed carrier can use T" report was made against the pre-fix dev.8 (same file name, without the `hasFlyers()` rule), so the carrier rows above still need a re-test with dev.9.

Real Fabric transformations, native placement, CREATED, disk saves and binary state reconstruction run with test-only arms/background/land fixtures. Territory tracing uses separated single-cell ownership fixtures retaining actual settlement IDs, not full territory influence generation. Automated coverage does not include full terrain, roads, initial assets, GPU or multiplayer sessions.

On 2026-09-26 the user confirmed that PR #1's fix passed manual gameplay testing. No version, seed or scenario matrix was supplied; record this as manual confirmation of the territory-colour fix, not completion of every manual or multiplayer case.

Keep native user-data `log.txt` (usually under `AirshipsGame`) and the latest `game/logs/acbric/` startup directory when reporting errors. Include game version, seed, map size, all starting values, display options, enabled MODs and steps. Historical maintainer evidence is private; you can reproduce the checks with the committed test source and your own game inputs below.

## Run isolated checks (Windows)

Use Python 3.10+, JDK 21 (`JAVA_HOME` or `--java-home`) and a built ARC Overhaul JAR. See [dependency setup](CONTRIBUTING.md). Each game input must contain `libs/asplit-A.zip`, `libs/asplit-B.zip`, supporting library JARs, and `data/fontmetrics` / `data/lang`. Existing isolated layouts with `game/data` are also accepted. The example paths must be replaced with your local paths.

```powershell
python tests/run_runtime.py --tag first-check --acbric-dir "D:/Development/Acbric" --game "steam=D:/Games/Airships"
# Select the dev.11 artifact explicitly if multiple local builds exist:
python tests/run_runtime.py --tag dev11 --arc-jar "build/libs/ARC-Overhaul-0.1.0-dev.15.jar" --game "v153=D:/Games/Airships"
```

Output defaults to `build/runtime-tests/<tag>/`: `summary.json` records counts and MOD/API/game input hashes; each labelled directory has `runtime.log`. Each tag must be new. `--output-root` changes the destination; it must be separate from game inputs. `--arc-jar` selects the artifact if multiple `ARC-Overhaul-*.jar` versions exist in `build/libs`. Check the version separately: labels are chosen by the caller, not detected version numbers.

The harness copies libraries and minimal resources into a fresh directory, isolates `APPDATA`, `user.home` and native user data, and runs only its probe main. It does not open a normal campaign. Test output contains copied game files; never commit or redistribute it. The source checkout needs no maintainer-specific workspace folders. Current support is limited to 1.2.15.3; a new game version requires its own checks and manual validation.
