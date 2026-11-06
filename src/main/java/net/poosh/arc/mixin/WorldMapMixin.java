/* WorldMapMixin.java — 生成前固定本地图参数；保存/恢复只处理 ID 容量，不重复初始化领地或现金。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.*;
import net.poosh.arc.conquest.*;
import org.json.JSONObject;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldMap.class, remap = false)
public abstract class WorldMapMixin implements GenerationAccess {
    @Unique private StartValues arc$values;
    @Unique private int arc$baseTowns;
    @Unique private boolean arc$layoutModified;
    @Unique private int arc$nextSettlementId;
    @Override public StartValues arc$options() { return arc$values; }
    @Override public int arc$originalTowns() { return arc$baseTowns; }
    @Override public int arc$nextSettlementId() { return arc$nextSettlementId++; }

    @Inject(method = "doSetup()Z", at = @At("HEAD"))
    private void arc$prepare(CallbackInfoReturnable<Boolean> cir) {
        if (arc$values != null) return;
        WorldMap map = (WorldMap)(Object)this;
        StartValues options = StartingOptions.forMap(map);
        arc$baseTowns = map.size.townsPerEmpire;
        int slots = options.capacity(arc$baseTowns);
        // 避免极端地图尺寸新增数百 MB 数组；原版自身的内存占用不在此改变。
        long extraBytes = (long)(slots - arc$baseTowns) * map.size.empires * map.size.gridSize * map.size.gridSize;
        if (extraBytes > 128L * 1024 * 1024)
            throw new IllegalArgumentException("ARC: Too many settlements for this map; reduce counts. / ARC：此地图的领地缓存过大，请减少数量。");
        MapSize layout = MapLayout.copy(map.size, slots);
        arc$layoutModified = layout != map.size;
        map.size = layout;
        // 首都已占用 ID 0..empires-1，定居点从其后连续分配。
        arc$nextSettlementId = map.size.empires;
        arc$values = options;
    }
    @Redirect(method = "<init>(Lorg/json/JSONObject;Lcom/zarkonnen/airships/AirshipGame;Lcom/zarkonnen/airships/InPipe;)V",
        at = @At(value = "INVOKE", target = "Lcom/zarkonnen/airships/MapSize;ofName(Ljava/lang/String;)Lcom/zarkonnen/airships/MapSize;"))
    private MapSize arc$restoreLayout(String name, JSONObject data, AirshipGame game, InPipe pipe) {
        MapSize base = MapSize.ofName(name);
        MapSize restored = MapLayout.read(base, data);
        arc$layoutModified = restored != base;
        arc$baseTowns = base.townsPerEmpire;
        return restored;
    }
    @Inject(method = "toJSON(Lcom/zarkonnen/airships/OutPipe;)Lorg/json/JSONObject;", at = @At("RETURN"))
    private void arc$saveLayout(OutPipe pipe, CallbackInfoReturnable<JSONObject> cir) {
        MapLayout.write(cir.getReturnValue(), ((WorldMap)(Object)this).size, arc$layoutModified);
    }
}
