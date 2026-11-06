/* ShipSpeedReportMixin.java — 面板里的 Speed 改用含接地摩擦的等效速度，使显示值与实际战斗稳态速度一致。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.BonusSet;
import net.poosh.arc.speed.EffectiveSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 只改"显示"，不改玩法：编辑器、战略界面与战斗面板的 {@code Speed_} 一行都走
 * {@code ShipEditorUtils.getStats}，这里把取速度的那一次调用换成等效速度。
 * 原版公式漏掉弹簧接地摩擦，陆行舰的面板速度会比实际高 2–6 倍。
 *
 * <p>战略移动由 {@code FleetSpeedMixin} 接入同一有效速度；战斗 AI 与
 * {@code engineForceForX} 的刹车预演不经过这里，仍保持原版。</p>
 */
@Mixin(targets = "com.zarkonnen.airships.ShipEditorUtils", remap = false)
public abstract class ShipSpeedReportMixin {
    /** 原版此处为 {@code ceil(ship.getMainMapSpeed(BonusSet.empty()) * 3600 / 7)} 公里/小时。 */
    @Redirect(method = "getStats(Lcom/zarkonnen/airships/Airship;ILcom/zarkonnen/airships/Airship;)Ljava/util/ArrayList;",
              at = @At(value = "INVOKE",
                       target = "Lcom/zarkonnen/airships/Airship;getMainMapSpeed(Lcom/zarkonnen/airships/BonusSet;)D"))
    private static double arc$groundedSpeed(Airship ship, BonusSet bonuses) {
        return EffectiveSpeed.mainMapSpeed(ship, bonuses);
    }
}
