/* StartingConnectionChecks.java — 海岛首都/内陆城镇运行原生道路、海路、首都连通与动乱判定。 */
package regression;

import com.zarkonnen.airships.*;
import net.poosh.arc.conquest.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;
import static regression.ArcRuntimeProbe.*;

final class StartingConnectionChecks {
    static void run() throws Exception {
        WorldMap map = island(new StartValues(1, 1, -1), true);
        Empire owner = map.empires.get(0);
        City capital = owner.getCapital(), town = owner.cities.get(1);
        nativeRoutes(map);
        check(capital.probablyCoastal && !town.probablyCoastal,
                "island capital is coastal but its inland town is excluded from native sea-route pairs");
        check(!map.hasConnection(town.x, town.y, capital, owner, true, false, true),
                "native land and sea stages reproduce the disconnected inland town");
        check(!map.isConnectedToCapital(owner, town), "native capital cache records the pre-repair disconnection");
        check(!map.connected(town, capital), "native generic connection cache also records the missing route");
        int before = town.unrest(map, null, false);
        boolean[][] water = copy(map.water);
        String settlements = settlements(map);
        map.r = new GuardedRandom(1701);
        finishRoutes(map);
        check(map.isConnectedToCapital(owner, town), "generated inland town connects to its island capital after road finalization");
        int after = town.unrest(map, null, false);
        int penalty = EmpireStat.DISCONNECTED_FROM_CAPITAL_UNREST.get(owner.bonuses);
        check(before - after == penalty && penalty > 0, "native unrest loses exactly the disconnection penalty");
        Road route = map.getConnection(town.x, town.y, capital, owner, true, false, true);
        check(route != null && route.seaRoute, "island connection is a real native sea route");
        check(!map.connectedByLand(town, capital), "sea connection does not grant landships a land route across water");
        check(map.connected(town, capital), "native generic connection cache is rebuilt after adding roads");
        check(Arrays.deepEquals(water, map.water) && settlements.equals(settlements(map)),
                "repair preserves water, settlement positions, IDs, income, ownership and coastal status");
        check(map.r.nextInt() == new GuardedRandom(1701).nextInt(), "road repair consumes no campaign random numbers");
        boolean[][] roads = copy(map.roads);
        invoke(map, "smoothRoads");
        check(Arrays.deepEquals(roads, map.roads), "already repaired road network remains unchanged on another smoothing pass");
        roundTripTopology(map, true);
        // 之后人为切断海路：原生动乱应再次出现，证明没有豁免或强行修改连通结果。
        for (int y = 0; y < map.water.length; y++) for (int x = 0; x < map.water[y].length; x++)
            if (map.water[y][x]) map.roads[y][x] = false;
        set(map, WorldMap.class, "connectionCache", null);
        map.clearPathCaches();
        check(!map.isConnectedToCapital(owner, town) && town.unrest(map, null, false) == before,
                "cutting the sea route restores the native disconnection and unrest penalty");
        invoke(map, "fillConnectionCache");
        roundTripTopology(map, false);
        defaults();
        hostileChokepoint();
        landDetour();
        generatedSettlements();
        targets();
    }

    static void defaults() throws Exception {
        for (StartValues values : List.of(new StartValues(-1, -1, -1), new StartValues(-1, -1, 123))) {
            WorldMap map = island(values, true);
            nativeRoutes(map);
            boolean[][] roads = copy(map.roads);
            map.r = new GuardedRandom(89);
            invoke(map, "smoothRoads");
            check(Arrays.deepEquals(roads, map.roads) && map.r.nextInt() == new GuardedRandom(89).nextInt(),
                    "default or cash-only settings preserve native roads and RNG: " + values);
            check(!map.isConnectedToCapital(map.empires.get(0), map.empires.get(0).cities.get(1)),
                    "default settlement counts do not enable road repair");
        }
        WorldMap unchangedAI = island(new StartValues(1, 1, -1), false);
        nativeRoutes(unchangedAI); finishRoutes(unchangedAI);
        check(!unchangedAI.isConnectedToCapital(unchangedAI.empires.get(0), unchangedAI.empires.get(0).cities.get(1)),
                "human-only counts do not repair an unchanged AI empire");
        for (StartValues values : List.of(new StartValues(-1, -1, -1, 0, 1), new StartValues(-1, -1, -1, 0, -1, 2))) {
            WorldMap ai = island(values, false);
            nativeRoutes(ai); finishRoutes(ai);
            check(ai.isConnectedToCapital(ai.empires.get(0), ai.empires.get(0).cities.get(1)),
                    "AI town or city customization also gets a capital connection: " + values);
            WorldMap unchangedHuman = island(values, true);
            nativeRoutes(unchangedHuman); finishRoutes(unchangedHuman);
            check(!unchangedHuman.isConnectedToCapital(unchangedHuman.empires.get(0), unchangedHuman.empires.get(0).cities.get(1)),
                    "AI-only counts do not repair an unchanged human empire");
        }
        WorldMap single = island(new StartValues(1, 0, -1), true);
        single.empires.get(0).cities.remove(1);
        boolean[][] roads = copy(single.roads);
        invoke(single, "smoothRoads");
        check(Arrays.deepEquals(roads, single.roads), "zero extra settlements need no new route");
    }

    static void hostileChokepoint() throws Exception {
        WorldMap map = island(new StartValues(1, 1, -1), true);
        for (boolean[] row : map.water) Arrays.fill(row, false);
        Empire owner = map.empires.get(0), enemy = map.empires.get(1);
        owner.cities.set(0, city(owner, 0, 20, 64, false));
        owner.cities.set(1, city(owner, 2, 100, 64, true));
        enemy.cities.set(0, city(enemy, 1, 60, 64, false));
        map.nests.add(new MonsterNest(6, 60, 63));
        for (int x = 20; x <= 100; x++) map.roads[64][x] = true;
        City capital = owner.getCapital(), town = owner.cities.get(1);
        check(map.hasConnection(town.x, town.y, capital, null, true, false, false)
                        && !map.hasConnection(town.x, town.y, capital, owner, true, false, true),
                "a visible route through a foreign city does not connect an empire to its capital");
        boolean[][] original = copy(map.roads);
        finishRoutes(map);
        Road route = map.getConnection(town.x, town.y, capital, owner, true, false, true);
        check(route != null && !route.seaRoute && route.intPath.stream().noneMatch(p -> p[0] == 60 && (p[1] == 64 || p[1] == 63)),
                "repair bypasses a foreign-city chokepoint and a monster nest using real land roads");
        check(contains(map.roads, original), "repair retains existing road branches");
        map.clearPathCaches();
        check(map.isConnectedToCapital(owner, town), "capital connection survives native path-cache invalidation");
    }

    static void landDetour() throws Exception {
        WorldMap map = island(new StartValues(1, 1, -1), true);
        for (boolean[] row : map.water) Arrays.fill(row, false);
        for (int y = 0; y < 100; y++) for (int x = 54; x < 57; x++) map.water[y][x] = true;
        finishRoutes(map);
        Empire owner = map.empires.get(0);
        City town = owner.cities.get(1), capital = owner.getCapital();
        Road route = map.getConnection(town.x, town.y, capital, owner, true, false, true);
        check(route != null && !route.seaRoute && map.connectedByLand(town, capital),
                "an available land detour is preferred over a shorter sea crossing");
        check(route.intPath.stream().anyMatch(p -> p[1] >= 100), "land road really goes around the water barrier");
        boolean[][] roads = copy(map.roads);
        invoke(map, "smoothRoads");
        check(Arrays.deepEquals(roads, map.roads), "an already connected land network receives no extra road");
    }

    /** 多种种子与数量：原生城镇放置接原生道路/海路，再经过修补和平滑/缓存。 */
    static void generatedSettlements() throws Exception {
        String deterministic = null;
        int[] seeds = {0, 1, 17, 89, 4711, 170017, 170017};
        for (int seed : seeds) {
            StartValues values = seed == 170017 ? new StartValues(4, 8, -1, 0, 8, 4) : new StartValues(2, 2, -1, 0, 2, 2);
            WorldMap map = island(values, true);
            map.empires.get(0).cities.remove(1);
            map.setupCityNames = new ArrayList<>(List.of("a", "b", "c", "d"));
            map.r = new GuardedRandom(seed);
            Object placement = stage(map, 2);
            int slots = (Integer)invoke(placement, "getSize");
            for (int i = 0; i < slots; i++) runStage(placement, i, map);
            check(map.empires.get(0).cities.size() == values.cityCount() + values.townCount(2)
                            && map.empires.get(1).cities.size() == values.cityCount(false) + values.aiTownCount(2),
                    "native placement produces configured human and AI counts for road test, seed " + seed);
            map.locPairs.clear();
            for (Empire empire : map.empires) for (int i = 0; i < empire.cities.size(); i++)
                for (int j = i + 1; j < empire.cities.size(); j++)
                    map.locPairs.add(new MapLocation[]{empire.cities.get(i), empire.cities.get(j)});
            nativeRoutes(map);
            String before = settlements(map);
            finishRoutes(map);
            for (Empire empire : map.empires)
                check(empire.cities.stream().allMatch(c -> map.isConnectedToCapital(empire, c)),
                        "all generated settlements connect to their own capital, seed " + seed + ", empire " + empire.id);
            check(before.equals(settlements(map)), "road finalization preserves all generated settlements, seed " + seed);
            if (seed == 170017) {
                String snapshot = before + Arrays.deepToString(map.roads) + ":" + map.r.nextInt();
                if (deterministic != null) check(deterministic.equals(snapshot), "same seed and maximum counts reproduce settlements, roads and RNG");
                deterministic = snapshot;
            }
        }
    }

    /** 原生静态地图序列化；势力/资产是测试夹具，恢复后重新挂接，不冒充完整战役存档测试。 */
    static void roundTripTopology(WorldMap map, boolean connected) throws Exception {
        JSONObject data = map.write("mapStatic").put("worldID", "arc-roads-roundtrip").put("empires", new JSONArray());
        WorldMap restored = new WorldMap(data, null, null);
        check(Arrays.deepEquals(map.roads, restored.roads) && Arrays.deepEquals(map.water, restored.water),
                "native static-map save and constructor restore roads/water exactly, connected=" + connected);
        check(((GenerationAccess)restored).arc$options() == null, "native load never arms starting-road repair");
        restored.empires.addAll(map.empires);
        set(restored, WorldMap.class, "mapLocs", null);
        set(restored, WorldMap.class, "cityCache", null);
        Empire owner = restored.empires.get(0);
        check(restored.isConnectedToCapital(owner, owner.cities.get(1)) == connected,
                "restored native topology retains its capital connectivity without migration, connected=" + connected);
        boolean[][] roads = copy(restored.roads);
        publish(new StartValues(4, 8, 900, 0, 8, 4), FleetPlan.empty());
        invoke(restored, "smoothRoads");
        check(Arrays.deepEquals(roads, restored.roads), "changing local starting rules does not rewrite a loaded map");
    }

    static void targets() throws Exception {
        List<String> stage = events(Class.forName("com.zarkonnen.airships.WorldMap$16"), "run", "(ILcom/zarkonnen/airships/WorldMap;)Z");
        String smooth = "M com/zarkonnen/airships/WorldMap.access$1400(Lcom/zarkonnen/airships/WorldMap;)V";
        check(sitesOf(stage, smooth).size() == 1 && stage.indexOf(smooth) > stage.indexOf("M com/zarkonnen/airships/Skeletoniser.canRemove(II[[Z)Z"),
                "shipped road stage smooths exactly once after skeletonization");
        List<String> bridge = events(WorldMap.class, "access$1400", "(Lcom/zarkonnen/airships/WorldMap;)V");
        check(sitesOf(bridge, "M com/zarkonnen/airships/WorldMap.smoothRoads()V").size() == 1,
                "shipped stage accessor resolves to the exact injected smoothRoads descriptor");
        check(Arrays.stream(WorldMap.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("arc$connectStartingSettlements")),
                "Knot installed the starting-road repair on the real WorldMap class");
        check(events(WorldMap.class, "isConnectedToCapital", "(Lcom/zarkonnen/airships/Empire;Lcom/zarkonnen/airships/City;)Z")
                        .contains("M com/zarkonnen/airships/WorldMap.hasConnection(IILcom/zarkonnen/airships/MapLocation;Lcom/zarkonnen/airships/FleetOwner;ZZZ)Z"),
                "shipped capital rule still uses the native road pathfinder");
    }

    static boolean[][] copy(boolean[][] grid) { return Arrays.stream(grid).map(boolean[]::clone).toArray(boolean[][]::new); }
    static boolean contains(boolean[][] grid, boolean[][] original) {
        for (int y = 0; y < grid.length; y++) for (int x = 0; x < grid[y].length; x++) if (original[y][x] && !grid[y][x]) return false;
        return true;
    }
    static String settlements(WorldMap map) {
        StringBuilder out = new StringBuilder();
        for (Empire e : map.empires) for (City c : e.cities) out.append(e.id).append(':').append(c.id).append('@').append(c.x).append(',')
                .append(c.y).append(',').append(c.isTown).append(',').append(c.income).append(',').append(c.coastal).append(';');
        return out.toString();
    }

    /** 只合成地形和首都；道路/航线及后续规则均执行真实发行代码。 */
    static WorldMap island(StartValues values, boolean human) throws Exception {
        base = new MapSize(new JSONObject().put("name", "SMALLISH").put("gridSize", 4).put("empires", 2).put("nests", 0));
        Loadable.map.get(MapSize.class).put(base.name, base);
        WorldMap map = newWorld(values).map;
        int n = map.size.gridSize;
        map.water = new boolean[n][n];
        for (boolean[] row : map.water) Arrays.fill(row, true);
        land(map, 10, 58, 16, 65);
        land(map, 50, 20, 115, 112);
        map.roads = new boolean[n][n];
        map.resistance = new double[n][n];
        map.height = new double[n][n];
        map.cityOwnership = new int[n][n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            map.resistance[y][x] = map.water[y][x] ? 10000 : 3;
            map.cityOwnership[y][x] = -1;
        }
        for (String name : List.of("mapLocs", "cityCache", "connectionCache", "empireToCityCapitalConnectionCache", "dists", "inQ", "hasPort"))
            set(map, WorldMap.class, name, null);
        Empire owner = empire(human, 0), other = empire(!human, 1);
        set(owner, Empire.class, "id", 0); set(other, Empire.class, "id", 1);
        set(owner, Empire.class, "bonuses", BonusSet.empty()); set(other, Empire.class, "bonuses", BonusSet.empty());
        set(owner, Empire.class, "fleets", new ArrayList<Fleet>()); set(other, Empire.class, "fleets", new ArrayList<Fleet>());
        owner.cities.clear(); other.cities.clear();
        owner.cities.add(city(owner, 0, 13, 61, false));
        owner.cities.add(city(owner, 2, 85, 65, true));
        other.cities.add(city(other, 1, 105, 100, false));
        map.empires.add(owner); map.empires.add(other);
        map.locPairs = new ArrayList<>();
        // 即使将首都/城镇直接列为陆路候选，原版仍不能越过海水阻力。
        map.locPairs.add(new MapLocation[]{owner.cities.get(0), owner.cities.get(1)});
        map.locPairs.add(new MapLocation[]{owner.cities.get(1), other.cities.get(0)});
        return map;
    }

    static City city(Empire owner, int id, int x, int y, boolean town) {
        City city = new City(id, x, y, "route-" + id, town, town ? 8 : 30, null, 0);
        city.originalEmpire = owner;
        return city;
    }

    static void land(WorldMap map, int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) map.water[y][x] = false;
    }

    static void nativeRoutes(WorldMap map) throws Exception {
        for (int index : new int[]{10, 12, 13, 14}) {
            Object stage = stage(map, index);
            int count = (Integer)invoke(stage, "getSize");
            for (int i = 0; i < count; i++) runStage(stage, i, map);
        }
    }

    static void finishRoutes(WorldMap map) throws Exception {
        runStage(stage(map, 15), 0, map);
        runStage(stage(map, 16), 0, map);
    }
}
