/* StartingConnections.java — 新地图原生路网完成后，为自定义开局领地补齐通往首都的真实道路/航线。 */
package net.poosh.arc.conquest;

import com.zarkonnen.airships.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

public final class StartingConnections {
    private StartingConnections() { }

    /**
     * 原生陆路候选来自领地接壤，海路候选只含 probablyCoastal 城市，两者不保证新增内陆城镇
     * 能返回海岛首都。只在新地图生成期间修补显式配置数量的一方；旧档和默认数量不参与。
     * 必须在原生道路细化之后、smoothRoads/连接缓存之前执行，避免细化再次移除绕城支路。
     */
    public static boolean repair(WorldMap map, StartValues values) {
        if (values == null || !values.changesLand()) return false;
        boolean changed = false;
        for (Empire empire : map.empires) {
            if (!values.changesLand(empire.playerControlled) || empire.cities.size() < 2) continue;
            City capital = empire.getCapital();
            boolean[] blocked = null;
            for (City city : empire.cities) {
                if (city == capital || map.hasConnection(city.x, city.y, capital, empire, true, false, true)) continue;
                if (blocked == null) blocked = blockedLocations(map, empire);
                // 陆路可达就不新开海路；必须跨海时仍保留 water 网格，由原生规则限制陆行舰。
                if (!connect(map, capital, city, blocked, false) && !connect(map, capital, city, blocked, true))
                    throw new IllegalStateException("ARC: Cannot connect starting settlements to their capital. Reduce counts or regenerate the map. / ARC：无法连接开局领地与首都，请减少数量或重新生成地图。");
                changed = true;
            }
        }
        return changed;
    }

    /** 不借道外国城市或怪物巢穴；领土边界本身不阻挡通行，与原生道路规则一致。 */
    private static boolean[] blockedLocations(WorldMap map, Empire empire) {
        int width = map.water[0].length;
        boolean[] blocked = new boolean[map.water.length * width];
        for (Empire other : map.empires) if (other != empire)
            for (City city : other.cities) blocked[city.y * width + city.x] = true;
        for (MonsterNest nest : map.nests) blocked[nest.y * width + nest.x] = true;
        return blocked;
    }

    /**
     * 确定性的 8 邻接 A*：沿用原生道路网格，优先复用道路，其次铺陆路，最后开海路。
     * 原生 Mapping oceans 已将内陆 resistance 改为 10000，不能再拿它计算海陆混合路径。
     * 固定代价与格子序号打破同分，不读取/消耗随机数，不改变城市、领土或沿海属性。
     */
    private static boolean connect(WorldMap map, City from, City to, boolean[] blocked, boolean allowSea) {
        int width = map.water[0].length, height = map.water.length;
        int source = from.y * width + from.x, target = to.y * width + to.x;
        int[] distance = new int[width * height], previous = new int[width * height];
        Arrays.fill(distance, Integer.MAX_VALUE);
        Arrays.fill(previous, -1);
        PriorityQueue<Step> queue = new PriorityQueue<>(Comparator.comparingInt(Step::score)
                .thenComparingInt(Step::distance).thenComparingInt(Step::cell));
        distance[source] = 0;
        queue.add(new Step(source, 0, estimate(from.x, from.y, to)));
        while (!queue.isEmpty()) {
            Step step = queue.remove();
            if (step.distance != distance[step.cell]) continue;
            if (step.cell == target) {
                for (int cell = target; cell != -1; cell = previous[cell]) map.roads[cell / width][cell % width] = true;
                return true;
            }
            int x = step.cell % width, y = step.cell / width;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (dx == 0 && dy == 0 || nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int next = ny * width + nx;
                if (blocked[next] || !allowSea && map.water[ny][nx]) continue;
                int cost = (dx == 0 || dy == 0 ? 10 : 14) * (map.roads[ny][nx] ? 1 : map.water[ny][nx] ? 16 : 4);
                int candidate = step.distance + cost;
                if (candidate >= distance[next]) continue;
                distance[next] = candidate;
                previous[next] = step.cell;
                queue.add(new Step(next, candidate, candidate + estimate(nx, ny, to)));
            }
        }
        return false;
    }

    private static int estimate(int x, int y, City target) {
        int dx = Math.abs(x - target.x), dy = Math.abs(y - target.y);
        return 10 * Math.max(dx, dy) + 4 * Math.min(dx, dy);
    }

    private record Step(int cell, int distance, int score) { }
}
