# ARC Overhaul starting-options test guide

dev.9 adds T orders against defenceless targets and T orders given to unarmed carriers: game 1.2.15.2 / 1.2.15.3 each passes **606 checks**, 1,212 total (including the 15 load-friction assertions from feat5 on this branch), recorded in `build/runtime-tests/ordered-target-dev9/summary.json`, JAR SHA256 `f89013758fa04609675cb3057e90c74c86b27493b9360ac8108f0632e2a64d5a`. The four injection points are asserted by parsing the shipped kernel bytecode and another 34 assertions drive the injected handlers on the real kernel classes. The static injection check (`arc verify`) last ran on the **same mixin set** (only the version string differs) and reported OK for all fifteen ARC injections; this round it could not run because the framework has been upgraded to 0.3.5.1 and the tool can no longer materialise its kernel view. The rest of `CommandButtonsPanel.draw`, full world generation, the graphical UI and end-to-end multiplayer remain unverified.

2026-09-27 integration of PRs #3 / #4 / #5: dev.7 combines AI fleet settings, target-relative aircraft strafe points and grounded speed reporting. Conflict resolution retains both Mixin registrations and all test calls, with dev.7 used consistently; feature source semantics are unchanged. Build passed with JDK 21 / Acbric API 0.3.3-dev.33. Game 1.2.15.2 and 1.2.14 each passed 529 checks (1,058 total), recorded in `build/runtime-tests/pr3-5-integrated-20260927/summary.json`. Fleet registration and some assets use fixtures; strafe checks directly invoke the transformed handler. Full world generation, graphical combat, roads/starting assets and end-to-end multiplayer remain unverified. Player installations were not overwritten. Earlier branch-specific records and counts below are historical.

[中文](TESTING.zh-CN.md) · 0.1.0-dev.9 · 2026-09-30

## Install and open

1. Exit the game. Use API dev.21 or later compatible. Disable Starting Cities and restart, or keep its JAR outside `mods/`. Install only one ARC version.
2. Copy `build/libs/ARC-Overhaul-0.1.0-dev.9.jar` to the running copy's `game/mods/`. No installed copy was overwritten. **After copying, check its SHA256 against the evidence value above** (`Get-FileHash <jar> -Algorithm SHA256`) and delete any older same-named ARC JAR: the pre-fix and post-fix builds were both called `dev.8`, and keeping the stale file makes a working change look broken.
3. Start a new single-player conquest setup. Scroll its settings list to the bottom and select **ARC Overhaul: Player starting options**, or use **MOD list → ARC Overhaul → Details**.
4. Edit, Apply, close, then use the native Start button. `-1` preserves that field's vanilla value; fields are independent.
5. Do the same with the second entry, **ARC Overhaul: AI fleets**; it lists every loaded AI fleet and is described in [AI fleets](README.md#ai-fleets).

## Manual checks

| Scenario | Expected result |
|---|---|
| All -1 | Vanilla one city, map-default towns and cash |
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

Full multiplayer remains unverified. Optional testing requires matching ARC/API and identical values set through MOD Details before joining. Differences should block preparation; matching rules do not prove full compatibility. Matching JARs do not synchronize configuration. There is no lobby editor.

## Automated coverage and diagnostics

The AI fleet change runs **506 checks** on game 1.2.15.2 with Acbric API `0.3.3-dev.21` — the previous 426 plus 80 fleet assertions (`build/runtime-tests/fleet-final2/summary.json`). They cover rule parsing/round-trip and rejection of invalid modes, counts and unknown keys; the settings window built on the real config handle in both languages and its component tree walked node by node (one row per loaded fleet, each row a label plus a three-state chooser plus an editable country-count field that starts at 1 and stays disabled until the fleet is force-enabled); the draft session driven through its real editor (invalid counts block saving, saving writes the file and publishes the shared rules, a rule for a fleet that is no longer loaded survives both, reload discards the draft and defaults clear everything); real config-file persistence; the real shared-rule payload carrying starting values and fleet rules together; the country-count box keeping its raw contents (clearing it does not snap back, an empty box reports no error and does not block Apply, an out-of-range or non-numeric entry does, and an empty box is restored once you type elsewhere or press a button or Apply), the bulk force-disable-all / force-enable-all actions, and the selection function itself against real `Loadable` lookups, real map `GuardedRandom` draws and real frozen campaign rules — default rules return the native pick without consuming a random draw, a banned fleet never appears, a force-enabled fleet lands on exactly its country count and is not also handed out at random, the player's country is skipped, and an unloaded fleet only clears its quota.

The injection point is asserted by parsing the shipped `WorldMap$2` class file: `run(ILcom/zarkonnen/airships/WorldMap;)Z` contains four `ArrayList.get` call sites, and the only one whose result is cast to `ConstructionStrategy` is call site **#1** — exactly what `ordinal = 1` selects. What is **not** automated: the fixture has no `ConstructionStrategy` data directory, so the fleet catalogue is registered synthetically, and the native empire-creation stage (`WorldMap$2.run`) is not executed end to end because it needs arms assets the harness does not provide. Only one game build is installed on this machine, so 1.2.14 was not re-run for this change, and the two new buttons in the native settings list were not driven headlessly — the window behind the second button is checked, the native list row that opens it is not.
After merging PR #1, game 1.2.15.2 / 1.2.14 each pass **407 checks**, **814 total**, using Acbric API `0.3.3-dev.25` and JDK 21. Local results: `build/runtime-tests/pr1-contiguous-final/summary.json`. The original dev.3 result of 71 checks per build is historical.

The 33 added layouts cover two/four empires, different human positions, extra/reduced/zero towns, maximum counts, defaults/cash-only/explicit vanilla counts, and native AI placement failure. They check counts, types, unique contiguous IDs, city-cache identity, default placement/RNG equivalence, and directly invoke native `ShapeUtils.cityOwnershipAreas` to check that every settlement receives an area. Running the same test against the pre-fix dev.3 JAR fails the new contiguous-ID assertion as expected (local `pr1-negative-old-mod` evidence).

Aircraft strafe anchoring adds 11 checks (the Crewman transformation plus 10 assertions) and does not start a battle: they fabricate a `Crewman` and an `Airship` with `Unsafe`, write the target's x straight into `PhysicsRect`, and invoke the injected handler with a real `CallbackInfoReturnable`. They assert the target-relative offset stays exactly constant across 1000 px of target movement, that a stationary target does not keep shifting the point, that a native re-pick and a target switch re-anchor without shifting, and that a null target or null aim point is a no-op. What they do **not** cover: a live `Combat`, the steering loop that acts on the shifted point, and therefore the actual turn-around in a real battle — that still needs manual gameplay testing.

The T-order change adds **62 checks**, none of which starts a battle, in three parts. (1) Transformation: all injected handlers are present on the four classes (two on `Module`, two on `Crewman`, one on `TargetCommandTool`, two on `CommandButtonsPanel`, including one `@Inject` and one `@Unique` field). (2) Injection points: ASM parses the **raw class bytes from the archive** (never the Mixin-rewritten class) and asserts, in field-read order, that the `ordinal` used really points at the intended read — the 2nd of the four `dangerCache` reads in `Module.targetShip` follows a `fireAt` read, the 1st in `Module.target` follows `prevTargetShip`, the 1st and 2nd in `Crewman.outsideShootingTick` follow `attackTarget` and `fireAt`; `TargetCommandTool.click` contains exactly one `canShoot()` call; and of the fifteen 5-argument `iconButton` call sites in `CommandButtonsPanel.draw`, the 13th uses the `CommandButtonsPanel.TARGET` icon. (3) Behaviour: the injected handlers are invoked directly (`Module` and `Crewman` are fabricated with `Unsafe` and their fields written by hand; the panel uses its real constructor, which only news up `Img` coordinates; `TargetCommandTool` is driven with an `Airship` subclass). They assert that only a target named by T passes the worth-shooting test while an unnamed one still reads 0, that ships with guns, with a flight centre, or with hangars alone can be given the order but a plain transport cannot, that a cooling-down ship never lights the button, that a non-`TARGET` icon is forwarded untouched, and that nothing is added before the panel has drawn. The "hangars alone" case runs through the **vanilla decision chain**: a synthesized hangar module (its `quartersType` pointing at a crew type with `canFly = true`, `canGivePlaneCommands = false`) is attached to an `Airship` subclass and the checks assert that `canShoot()` and `canGiveAircraftCommands()` are both false while the native `hasFlyers()` is true, so ARC still lets it through — exactly the case the earlier flight-centre-only version missed. What they do **not** cover: the rest of `CommandButtonsPanel.draw` beyond the one redirected `iconButton` call, and how the button actually looks in the game. In game terms, "T orders against defenceless targets" was confirmed manually by the user, while the earlier "unarmed carrier can use T" report was made against the pre-fix dev.8 (same file name, without the `hasFlyers()` rule), so the carrier rows above still need a re-test with dev.9.

Real Fabric transformations, native placement, CREATED, disk saves and binary state reconstruction run with test-only arms/background/land fixtures. Territory tracing uses separated single-cell ownership fixtures retaining actual settlement IDs, not full territory influence generation. Automated coverage does not include full terrain, roads, initial assets, GPU or multiplayer sessions.

On 2026-09-26 the user confirmed that PR #1's fix passed manual gameplay testing. No version, seed or scenario matrix was supplied; record this as manual confirmation of the territory-colour fix, not completion of every manual or multiplayer case.

Keep native user-data `log.txt` (usually under `AirshipsGame`) and the latest `game/logs/acbric/` startup directory when reporting errors. Include game version, seed, map size, all three values, enabled MODs and steps. Historical maintainer evidence is private; you can reproduce the checks with the committed test source and your own game inputs below.

## Run isolated checks (Windows)

Use Python 3.10+, JDK 21 (`JAVA_HOME` or `--java-home`) and a built ARC Overhaul JAR. See [dependency setup](CONTRIBUTING.md). Each game input must contain `libs/asplit-A.zip`, `libs/asplit-B.zip`, supporting library JARs, and `data/fontmetrics` / `data/lang`. Existing isolated layouts with `game/data` are also accepted. The example paths must be replaced with your local paths.

```powershell
python tests/run_runtime.py --tag first-check --acbric-dir "D:/Development/Acbric" --game "steam=D:/Games/Airships"
# Repeat --game to check a second owned game build in the same run:
python tests/run_runtime.py --tag both-builds --game "steam=D:/Games/Airships" --game "older=D:/Games/Airships-older"
```

Output defaults to `build/runtime-tests/<tag>/`: `summary.json` records counts and MOD/API/game input hashes; each labelled directory has `runtime.log`. Each tag must be new. `--output-root` changes the destination; it must be separate from game inputs. `--arc-jar` selects the artifact if multiple `ARC-Overhaul-*.jar` versions exist in `build/libs`. Check the version separately: labels are chosen by the caller, not detected version numbers.

The harness copies libraries and minimal resources into a fresh directory, isolates `APPDATA`, `user.home` and native user data, and runs only its probe main. It does not open a normal campaign. Test output contains copied game files; never commit or redistribute it. The source checkout needs no maintainer-specific workspace folders. Only the two previously checked game builds have recorded results; a new version requires its own checks and manual validation.
