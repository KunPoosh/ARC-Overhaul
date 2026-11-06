/* TownPlacementMixin.java — 原生放置阶段按人类/AI 分配槽位；在土地和建筑生成前确定城市类型。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.util.Utils;
import net.poosh.arc.conquest.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.zarkonnen.airships.WorldMap$3", remap = false)
public abstract class TownPlacementMixin {
    /** 落点不满意时重新选址的次数上限；用尽后回退到最后一个可用落点，绝不因为保护逻辑导致生成失败。 */
    private static final int ARC_SPOT_ATTEMPTS = 32;
    /** 连通陆地标记缓存：同一张地图反复选址时复用，避免每次分配整张网格。 */
    @Unique private boolean[][] arc$landSeen;

    @Inject(method = "run(ILcom/zarkonnen/airships/WorldMap;)Z", at = @At("HEAD"), cancellable = true)
    private void arc$skipUnusedSlot(int index, WorldMap map, CallbackInfoReturnable<Boolean> cir) {
        GenerationAccess generation = (GenerationAccess) map;
        StartValues values = generation.arc$options();
        if (values == null || !values.changesLand()) return;
        Empire empire = map.empires.get(index % map.size.empires);
        int slots = values.extraSlots(empire.playerControlled, generation.arc$originalTowns());
        if (index / map.size.empires >= slots) cir.setReturnValue(true);
    }
    @Redirect(method = "run(ILcom/zarkonnen/airships/WorldMap;)Z", at = @At(value = "INVOKE",
        target = "Lcom/zarkonnen/airships/WorldMap;access$700(Lcom/zarkonnen/airships/WorldMap;ZLcom/zarkonnen/airships/City;Lcom/zarkonnen/airships/GuardedRandom;)I"))
    private int arc$settlementIncome(WorldMap map, boolean human, City city, GuardedRandom random, int index, WorldMap ignored) {
        StartValues values = ((GenerationAccess) map).arc$options();
        if (values != null && values.changesLand(human) && index / map.size.empires < values.cityCount(human) - 1) {
            city.isTown = false;
            return ((WorldMapAccess) map).arc$cityIncome(human, city, random);
        }
        return ((WorldMapAccess) map).arc$townIncome(human, city, random);
    }
    /**
     * 把本槽位新建的定居点收紧为连续 ID。
     *
     * <p>跳过槽位会在原生 ID 序列（{@code empires.size() + index}）里留下空洞。而原生
     * {@code ShapeUtils.cityOwnershipAreas} 是从 ID 0 起顺序查找、一旦某个 ID 无对应格子就
     * 永久退出循环，因此空洞之上所有城市都不会生成领土多边形，战略地图上不会被上色。
     * 按创建顺序重新编号即可保持序列连续（无空洞时结果与原生完全相同）。</p>
     */
    @Inject(method = "run(ILcom/zarkonnen/airships/WorldMap;)Z", at = @At("RETURN"))
    private void arc$contiguousSettlementId(int index, WorldMap map, CallbackInfoReturnable<Boolean> cir) {
        GenerationAccess generation = (GenerationAccess) map;
        StartValues values = generation.arc$options();
        if (values == null || !values.changesLand()) return;
        Empire empire = map.empires.get(index % map.size.empires);
        if (empire.cities.isEmpty()) return;
        City settlement = empire.cities.get(empire.cities.size() - 1);
        // 仅当本槽位确实新建了定居点（其 ID 仍是本槽位的原生编号）时才重排。
        if (settlement.id != map.empires.size() + index) return;
        settlement.id = generation.arc$nextSettlementId();
    }
    /**
     * 额外定居点的选址保护：原生 {@code findMapLocationSpot} 只要求落点不是水，孤立的一格陆点、
     * 湖心沙洲都算合格，于是新增城镇可能生成在与任何地块都不相连的小岛上（战略地图上只有一小块
     * 领土、也修不出道路）。这里在原生结果之上加一道「连通陆地必须够大」的检查，不合格就重新选址。
     *
     * <p>只对 ARC 自定义了数量的势力生效；未自定义的一方完全走原生结果。重试次数用尽
     * 后返回最后一个落点，因此不会因为保护逻辑把生成变成失败。</p>
     */
    @Redirect(method = "run(ILcom/zarkonnen/airships/WorldMap;)Z", at = @At(value = "INVOKE",
        target = "Lcom/zarkonnen/airships/WorldMap;access$400(Lcom/zarkonnen/airships/WorldMap;Lcom/zarkonnen/airships/GuardedRandom;ILcom/zarkonnen/airships/Empire;)Lcom/zarkonnen/catengine/util/Utils$Pair;"))
    private Utils.Pair<Integer, Integer> arc$landSpot(WorldMap map, GuardedRandom random, int distance, Empire empire,
                                                     int index, WorldMap ignored) {
        WorldMapAccess access = (WorldMapAccess) map;
        Utils.Pair<Integer, Integer> spot = access.arc$findMapLocationSpot(random, distance, empire);
        StartValues values = ((GenerationAccess) map).arc$options();
        if (values == null || !values.changesLand(empire.playerControlled)) return spot;
        int minimum = arc$landMinimum(map);
        Utils.Pair<Integer, Integer> last = spot;
        for (int attempt = 0; attempt < ARC_SPOT_ATTEMPTS && last != null
                && arc$landMass(map, last.a, last.b, minimum) < minimum; attempt++) {
            spot = access.arc$findMapLocationSpot(random, distance, empire);
            if (spot != null) last = spot;
        }
        return last;
    }
    /** 「像样的陆地」的最小格数：随地图尺寸缩放，孤立小格在网格里远低于这个值。 */
    @Unique private static int arc$landMinimum(WorldMap map) { return Math.max(32, map.size.gridSize / 4); }
    /** 从 (x,y) 出发的 4 邻接连通陆地大小（不含水），最多数到 cap 格即可提前结束。 */
    @Unique private int arc$landMass(WorldMap map, int x, int y, int cap) {
        int grid = map.size.gridSize;
        if (x < 0 || y < 0 || x >= grid || y >= grid || map.water[y][x]) return 0;
        if (arc$landSeen == null || arc$landSeen.length != grid) arc$landSeen = new boolean[grid][grid];
        else for (boolean[] row : arc$landSeen) java.util.Arrays.fill(row, false);
        int[] queue = new int[cap * 4 + 8];
        int head = 0, tail = 0, count = 0;
        arc$landSeen[y][x] = true;
        queue[tail++] = y * grid + x;
        while (head < tail && count < cap) {
            int cell = queue[head++];
            count++;
            int cx = cell % grid, cy = cell / grid;
            for (int d = 0; d < 4; d++) {
                int nx = cx + (d == 0 ? -1 : d == 1 ? 1 : 0);
                int ny = cy + (d == 2 ? -1 : d == 3 ? 1 : 0);
                if (nx < 0 || ny < 0 || nx >= grid || ny >= grid) continue;
                if (arc$landSeen[ny][nx] || map.water[ny][nx]) continue;
                arc$landSeen[ny][nx] = true;
                if (tail < queue.length) queue[tail++] = ny * grid + nx;
            }
        }
        return count;
    }
    @Inject(method = "run(ILcom/zarkonnen/airships/WorldMap;)Z", at = @At("RETURN"))
    private void arc$checkPlacement(int index, WorldMap map, CallbackInfoReturnable<Boolean> cir) {
        GenerationAccess generation = (GenerationAccess) map;
        StartValues values = generation.arc$options();
        if (values == null || !values.changesLand()) return;
        Empire empire = map.empires.get(index % map.size.empires);
        int slot = index / map.size.empires;
        if (values.changesLand(empire.playerControlled)
                && slot < values.extraSlots(empire.playerControlled, generation.arc$originalTowns()) && empire.cities.size() != slot + 2)
            throw new IllegalStateException("ARC: Cannot place all configured settlements. Reduce counts or enlarge the map. / ARC：自定义领地位置不足，请减少数量或增大地图。");
    }
}
