/* FleetSpeedMixin.java — 战略舰队采用陆行舰接地有效速度，移除抬高慢舰的下限，返航也不能凭空加速。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.BonusSet;
import com.zarkonnen.airships.Fleet;
import com.zarkonnen.airships.MapLocation;
import com.zarkonnen.airships.ShipType;
import com.zarkonnen.airships.WorldMap;
import java.util.ArrayList;
import net.poosh.arc.speed.EffectiveSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 游戏 1.2.15.3 的 Fleet.speed 同时用于实际行军、拦截与 ETA。
 * 保留原生最慢舰、预备舰、科技、舰长和勋章规则，只替换陆行舰速度及其 0.03 下限。
 * 原版低补给返航另外硬编码了三处 0.03；对含陆行舰的舰队改为速度上限，防止返航反而提速。
 */
@Mixin(value = Fleet.class, remap = false)
public abstract class FleetSpeedMixin {
    @Shadow public ArrayList<Airship> actives;
    @Shadow public ArrayList<Airship> reserve;

    @Redirect(method = "speed(Ljava/util/ArrayList;Lcom/zarkonnen/airships/BonusSet;Lcom/zarkonnen/airships/WorldMap;)D",
            at = @At(value = "INVOKE", target = "Lcom/zarkonnen/airships/Airship;getMainMapSpeed(Lcom/zarkonnen/airships/BonusSet;)D"),
            require = 1, allow = 1)
    private static double arc$groundedMapSpeed(Airship ship, BonusSet bonuses) {
        return ship.type == ShipType.LANDSHIP
                ? EffectiveSpeed.strategicMapSpeed(ship, bonuses) : ship.getMainMapSpeed(bonuses);
    }

    @ModifyConstant(method = "speed(Ljava/util/ArrayList;Lcom/zarkonnen/airships/BonusSet;Lcom/zarkonnen/airships/WorldMap;)D",
            constant = @Constant(doubleValue = 0.03), require = 1, allow = 1)
    private static double arc$landshipSpeedFloor(double original, ArrayList<Airship> ships,
                                                BonusSet bonuses, WorldMap map) {
        return arc$hasLandship(ships) ? 0.0 : original;
    }

    @ModifyConstant(method = "speed(Lcom/zarkonnen/airships/BonusSet;Lcom/zarkonnen/airships/WorldMap;)D",
            constant = @Constant(doubleValue = 0.03), require = 1, allow = 1)
    private double arc$limpMapSpeed(double original, BonusSet bonuses, WorldMap map) {
        return arc$limpSpeed(original, bonuses, map);
    }

    @ModifyConstant(method = "timeTo(Lcom/zarkonnen/airships/MapLocation;Lcom/zarkonnen/airships/WorldMap;Z)I",
            constant = @Constant(doubleValue = 0.03), require = 1, allow = 1)
    private double arc$limpTravelTime(double original, MapLocation destination, WorldMap map, boolean limp) {
        return arc$limpSpeed(original, map.owner((Fleet) (Object) this).bonuses(), map);
    }

    @ModifyConstant(method = "timeTo(Lcom/zarkonnen/airships/MapLocation;Lcom/zarkonnen/airships/WorldMap;Ljava/util/ArrayList;Z)I",
            constant = @Constant(doubleValue = 0.03), require = 1, allow = 1)
    private double arc$selectedLimpTravelTime(double original, MapLocation destination, WorldMap map,
                                             ArrayList<Airship> ships, boolean limp) {
        return arc$hasLandship(ships)
                ? StrictMath.min(original, Fleet.speed(ships, map.owner((Fleet) (Object) this).bonuses(), map))
                : original;
    }

    @Unique
    private double arc$limpSpeed(double ceiling, BonusSet bonuses, WorldMap map) {
        if (!arc$hasLandship(actives) && !arc$hasLandship(reserve)) return ceiling;
        return StrictMath.min(ceiling,
                StrictMath.min(Fleet.speed(actives, bonuses, map), Fleet.speed(reserve, bonuses, map)));
    }

    @Unique
    private static boolean arc$hasLandship(ArrayList<Airship> ships) {
        for (Airship ship : ships) if (ship.type == ShipType.LANDSHIP) return true;
        return false;
    }
}
