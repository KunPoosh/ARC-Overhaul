/* FleetSpeedRuntimeChecks.java — 用极慢陆行舰驱动真实舰队速度、道路行军与 ETA，防止显示修正遗漏战略移动。 */
package regression;

import com.zarkonnen.airships.*;
import net.poosh.arc.speed.EffectiveSpeed;
import org.objectweb.asm.*;
import java.util.*;
import static regression.ArcRuntimeProbe.*;

final class FleetSpeedRuntimeChecks {
    static int shipId;
    static void run() throws Exception {
        WorldMap map = new WorldMap(mapData(), null, null);
        BonusSet bonuses = BonusSet.empty();
        Airship slow = ship(ShipType.LANDSHIP, 0.014, false);
        double corrected = EffectiveSpeed.mainMapSpeed(slow, bonuses);
        double actual = Fleet.speed(list(slow), bonuses, map);
        System.out.println("INFO ARC slow landship: panel=" + StrictMath.ceil(corrected * 3600 / 7)
                + " km/h, effective=" + corrected * 3600 / 7 + " km/h, native ship="
                + slow.getMainMapSpeed(bonuses) * 3600 / 7 + " km/h, fleet=" + actual * 3600 / 7 + " km/h");
        Airship tiny = ship(ShipType.LANDSHIP, 0.00001, true);
        System.out.println("INFO ARC below-floor landship: effective=" + EffectiveSpeed.mainMapSpeed(tiny, bonuses)
                + ", native ship=" + tiny.getMainMapSpeed(bonuses) + ", fleet=" + Fleet.speed(list(tiny), bonuses, map));
        check(StrictMath.ceil(corrected * 3600 / 7) == 2, "heavy low-thrust landship displays 2 km/h");
        close(actual, corrected, "native fleet movement uses the same corrected speed as the 2 km/h panel");
        check(slow.getMainMapSpeed(bonuses) > corrected * 30, "combat planning's native Airship speed remains unchanged");
        check(tiny.getMainMapSpeed(bonuses) < Fleet.MIN_SPEED, "weaker tracked fixture also falls below the old native floor");
        close(Fleet.speed(list(tiny), bonuses, map), EffectiveSpeed.mainMapSpeed(tiny, bonuses),
                "weak tracks retain a positive speed below the old fleet floor");
        check(Fleet.speed(list(tiny), bonuses, map) > 0 && Fleet.speed(list(tiny), bonuses, map) < 0.00001,
                "very slow motion is not rounded up or down to zero");
        Airship stopped = ship(ShipType.LANDSHIP, 0, true);
        close(Fleet.speed(list(stopped), bonuses, map), 0, "a landship without propulsion gets no free movement");
        Airship fast = ship(ShipType.LANDSHIP, 1.1, true);
        close(Fleet.speed(list(fast), bonuses, map), EffectiveSpeed.mainMapSpeed(fast, bonuses),
                "ordinary tracked ships also use ground friction above the old floor");
        Airship flyer = ship(ShipType.AIRSHIP, 0.014, false);
        close(Fleet.speed(list(flyer), bonuses, map), flyer.getMainMapSpeed(bonuses), "ordinary airships keep their native speed");
        Airship slowFlyer = ship(ShipType.AIRSHIP, 0.00001, false);
        close(Fleet.speed(list(slowFlyer), bonuses, map), Fleet.MIN_SPEED, "airship-only fleets keep the vanilla floor");
        close(Fleet.speed(list(), bonuses, map), 1.5, "an empty active/reserve group keeps the native sentinel speed");
        close(Fleet.speed(list(ship(ShipType.AIRSHIP, 100, false)), bonuses, map), 1.5,
                "the native upper speed cap remains intact");
        close(Fleet.speed(list(slow, flyer, fast), bonuses, map), corrected, "mixed fleets follow their slowest landship");
        close(Fleet.speed(list(fast, flyer, slow), bonuses, map), corrected, "ship ordering does not change mixed-fleet speed");
        checkBonuses(map, slow, flyer, corrected);
        checkLift(map);
        checkTravel(map, slow, fast, flyer, stopped, corrected);
        checkTargets();
    }

    static void checkBonuses(WorldMap map, Airship slow, Airship flyer, double corrected) throws Exception {
        BonusSet boosted = new BonusSet();
        BonusableValue<Double> previous = EmpireStat.LANDSHIP_MAP_SPEED_MULT;
        // 用传入集合身份区分两种科技快照，避免把空科技或舰船本地集合误当国家加成。
        EmpireStat.LANDSHIP_MAP_SPEED_MULT = new BonusableValue<>() {
            public Double get(BonusSet bonuses) { return bonuses == boosted ? 2.0 : 1.0; }
            public String explain(BonusSet b, ValueFormatter<Double> f, ValueTransform<Double> t) { return "fixture"; }
            public List<String> descriptions(BonusSet b) { return List.of(); }
        };
        try {
            close(Fleet.speed(list(slow), boosted, map), corrected * 2, "owner map-speed bonuses reach corrected landship speed");
            close(Fleet.speed(list(slow), BonusSet.empty(), map), corrected, "another owner's bonuses do not leak into speed");
            close(Fleet.speed(list(flyer), boosted, map), flyer.getMainMapSpeed(boosted), "landship multiplier does not affect airships");
        } finally { EmpireStat.LANDSHIP_MAP_SPEED_MULT = previous; }

        Hero captain = (Hero) unsafe(Hero.class);
        HeroType type = (HeroType) unsafe(HeroType.class);
        set(type, HeroType.class, "fleetSpeedPercent", 25);
        putObject(captain, Hero.class, "type", type);
        set(captain, Hero.class, "inShip", slow);
        map.heroes.add(captain);
        flyer.fleetSpeedPercentFromMedals = 15;
        try {
            close(Fleet.speed(list(slow, flyer), BonusSet.empty(), map), corrected * 1.4,
                    "native captain and best medal bonuses still apply after choosing the slowest ship");
        } finally { map.heroes.remove(captain); flyer.fleetSpeedPercentFromMedals = 0; }
    }

    static void checkLift(WorldMap map) throws Exception {
        Airship lifted = ship(ShipType.LANDSHIP, 0.014, true);
        double plain = Fleet.speed(list(lifted), BonusSet.empty(), map);
        set(lifted.modules.get(0).type, ModuleType.class, "lift", BonusableValue.of(Integer.valueOf(250)));
        double partial = Fleet.speed(list(lifted), BonusSet.empty(), map);
        check(partial > plain, "partial lift reduces map friction as it does on the panel");
        set(lifted.modules.get(0).type, ModuleType.class, "lift", BonusableValue.of(Integer.valueOf(500)));
        close(Fleet.speed(list(lifted), BonusSet.empty(), map), lifted.getMainMapSpeed(BonusSet.empty()),
                "full lift removes the landship's ground penalty on the map");
        lifted.CURRENT_COMBAT_DELETEME = (Combat) unsafe(Combat.class);
        check(EffectiveSpeed.mainMapSpeed(lifted, BonusSet.empty()) < lifted.getMainMapSpeed(BonusSet.empty()),
                "fixture exposes the stale combat lift context that the strategic map must ignore");
        close(Fleet.speed(list(lifted), BonusSet.empty(), map), lifted.getMainMapSpeed(BonusSet.empty()),
                "stale combat context cannot change strategic speed or the design lift allowance");
        lifted.CURRENT_COMBAT_DELETEME = null;
        set(lifted.modules.get(0).type, ModuleType.class, "maxXSpeed", BonusableValue.of(Double.valueOf(0.001)));
        close(Fleet.speed(list(lifted), BonusSet.empty(), map), 0.001, "module speed limits below the old floor remain effective");
    }

    @SuppressWarnings("unchecked")
    static void checkTravel(WorldMap map, Airship slow, Airship fast, Airship flyer, Airship stopped,
                            double corrected) throws Exception {
        Empire owner = empire(true, 100);
        owner.bonuses = BonusSet.empty();
        set(owner, Empire.class, "fleets", new ArrayList<Fleet>());
        map.empires.add(owner);
        map.toggles.clear();
        City origin = new City(1000, 1, 1, "origin", false, 30, null, 1);
        City destination = new City(1001, 101, 1, "destination", false, 30, null, 1);
        Fleet fleet = new Fleet(origin, map);
        owner.getFleets().add(fleet);
        fleet.actives.add(fast); fleet.reserve.add(slow);
        close(fleet.speed(owner.bonuses, map), corrected, "a slow reserve ship limits actual fleet speed");
        fleet.actives.clear();
        close(fleet.speed(owner.bonuses, map), corrected, "reserve-only fleets use corrected speed");
        fleet.reserve.clear(); fleet.actives.add(slow);
        close(fleet.speed(owner.bonuses, map), corrected, "active-only fleets use corrected speed");
        Road road = new Road();
        map.roadXs = new float[3][103]; map.roadYs = new float[3][103];
        for (int x = 1; x <= 101; x++) {
            road.intPath.add(new int[]{x, 1}); map.roadXs[1][x] = x; map.roadYs[1][x] = 1;
        }
        fleet.location = null; fleet.destination = destination; fleet.road = road;
        for (int tick = 0; tick < 600; tick++) fleet.tick(100, owner, map);
        close(fleet.progress, corrected * 60000 * Fleet.SPEED_TO_MAP_PX_PER_MS,
                "600 real Fleet.tick calls advance the road at corrected speed");
        close(fleet.realX(map), 1 + fleet.progress, "native road position matches measured travel progress");
        System.out.println("INFO ARC 60-second road travel: corrected=" + fleet.progress + ", old="
                + slow.getMainMapSpeed(owner.bonuses) * 60000 * Fleet.SPEED_TO_MAP_PX_PER_MS);
        check(fleet.travelTimeLeft(owner.bonuses, map) == (int)((100 - fleet.progress) / (corrected * Fleet.SPEED_TO_MAP_PX_PER_MS)),
                "remaining ETA agrees with actual progress and corrected speed");
        Map<MapLocation, Road> paths = (Map<MapLocation, Road>) field(fleet, Fleet.class, "pathCache");
        paths.put(destination, road);
        int slowEta = (int)(road.intPath.size() / (corrected * Fleet.SPEED_TO_MAP_PX_PER_MS));
        check(fleet.timeTo(destination, map, false) == slowEta, "route preview uses corrected speed");
        check(fleet.timeTo(destination, map, list(slow), false) == slowEta, "selected-ship route preview uses corrected speed");
        fleet.isLimpHome = true;
        close(fleet.speed(owner.bonuses, map), corrected, "low-supply return cannot accelerate a slow landship");
        check(fleet.timeTo(destination, map, true) == slowEta, "low-supply route preview agrees with actual return speed");
        check(fleet.timeTo(destination, map, list(slow), true) == slowEta, "selected low-supply preview agrees with actual speed");
        double before = fleet.progress;
        fleet.tick(1000, owner, map);
        close(fleet.progress - before, corrected * 1000 * Fleet.SPEED_TO_MAP_PX_PER_MS,
                "real low-supply movement does not gain the old minimum speed");
        fleet.actives.clear(); fleet.actives.add(fast);
        close(fleet.speed(owner.bonuses, map), Fleet.MIN_SPEED, "fast landships retain the low-supply slowdown");
        fleet.actives.clear(); fleet.actives.add(flyer);
        close(fleet.speed(owner.bonuses, map), Fleet.MIN_SPEED, "airship low-supply movement is unchanged");
        paths.put(destination, road);
        double previewDistance = fleet.distanceTo(destination, map);
        int limpEta = (int)(previewDistance / (Fleet.MIN_SPEED * Fleet.SPEED_TO_MAP_PX_PER_MS));
        check(fleet.timeTo(destination, map, list(flyer), true) == limpEta, "airship-only selected low-supply ETA is unchanged");
        check(fleet.timeTo(destination, map, list(slow), true)
                        == (int)(previewDistance / (corrected * Fleet.SPEED_TO_MAP_PX_PER_MS)),
                "selected preview examines selected ships rather than the parent fleet");
        fleet.actives.clear(); fleet.actives.add(stopped);
        for (boolean limp : new boolean[]{false, true}) {
            fleet.isLimpHome = limp;
            before = fleet.progress;
            fleet.tick(1000, owner, map);
            close(fleet.progress, before, "zero-propulsion landship stays still, low supply=" + limp);
            check(fleet.travelTimeLeft(owner.bonuses, map) == Integer.MAX_VALUE,
                    "stationary fleet uses native saturated ETA without overflow, low supply=" + limp);
        }
        // 静止道路目标仍可被飞艇拦截；避免速度为零后原生逐步搜索不终止。
        Fleet interceptor = new Fleet(origin, map); interceptor.actives.add(flyer); owner.getFleets().add(interceptor);
        var point = interceptor.getFlightIntercept(fleet, null, map);
        check(point != null && Double.isFinite(point.x) && Double.isFinite(point.y),
                "native airship interception of a stationary road fleet still terminates");
        close(point.x, fleet.realX(map), "stationary interception point is the real road position");
    }

    static void checkTargets() throws Exception {
        String group = "(Ljava/util/ArrayList;Lcom/zarkonnen/airships/BonusSet;Lcom/zarkonnen/airships/WorldMap;)D";
        List<String> code = events(Fleet.class, "speed", group);
        check(sitesOf(code, "M com/zarkonnen/airships/Airship.getMainMapSpeed(Lcom/zarkonnen/airships/BonusSet;)D").size() == 1,
                "shipped Fleet.speed has exactly one ship map-speed call");
        check(sitesOf(code, "M java/lang/StrictMath.max(DD)D").size() == 1,
                "shipped Fleet.speed has exactly one double minimum-speed clamp");
        Map<String, Integer> floors = new HashMap<>();
        try (var in = Fleet.class.getResourceAsStream("/com/zarkonnen/airships/Fleet.class")) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                    if (!name.equals("speed") && !name.equals("timeTo")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        public void visitLdcInsn(Object value) {
                            if (Double.valueOf(0.03).equals(value)) floors.merge(name + desc, 1, Integer::sum);
                        }
                    };
                }
            }, 0);
        }
        check(floors.size() == 4 && floors.values().stream().allMatch(n -> n == 1),
                "shipped speed and both ETA overloads contain exactly the four expected 0.03 constants");
        for (String name : List.of("arc$groundedMapSpeed", "arc$landshipSpeedFloor", "arc$limpMapSpeed",
                "arc$limpTravelTime", "arc$selectedLimpTravelTime"))
            check(Arrays.stream(Fleet.class.getDeclaredMethods()).anyMatch(m -> m.getName().endsWith(name)),
                    "real Knot transformation installed Fleet handler " + name);
    }

    /** 人工模块/质量夹具；速度、摩擦汇总和舰队方法全部运行发行游戏代码。 */
    static Airship ship(ShipType kind, double propulsion, boolean tracks) throws Exception {
        Airship ship = (Airship) unsafe(Airship.class);
        ship.type = kind;
        ship.networkID = "arc-speed-fixture-" + shipId++;
        ship.modules = new ArrayList<>();
        ship.tiles = new ArrayList<>();
        set(ship, Airship.class, "weight", 1000);
        putObject(ship, Airship.class, "currentBonuses", BonusSet.empty());
        ModuleType type = moduleType(propulsion, tracks ? List.of() : List.of(legSpec(0.004)),
                tracks ? List.of(new Spring(0, 110, 85, 0.06, 0.002, 0.003)) : List.of());
        set(type, ModuleType.class, "lift", BonusableValue.of(Integer.valueOf(0)));
        ship.modules.add(module(type));
        return ship;
    }

    static ArrayList<Airship> list(Airship... ships) { return new ArrayList<>(List.of(ships)); }
    static void close(double actual, double expected, String label) {
        check(Double.isFinite(actual) && StrictMath.abs(actual - expected) <= 1e-12,
                label + " (" + actual + " vs " + expected + ")");
    }
}
