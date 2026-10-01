/* LegGroundFrictionMixin.java — 腿部弹簧的接地摩擦按接地载荷缩放：升力越大摩擦越小。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.Combat;
import com.zarkonnen.airships.LandFormation;
import net.poosh.arc.speed.GroundLoad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

/**
 * 走路型陆行舰的同一条修正，作用在 {@code Leg.tick2} 落脚完成时那次
 * {@code StrictMath.pow(1.0 - spec.spring.xFriction * fMult, ms)}（字节码偏移 409，{@code tick2} 里第一个 pow）。
 * 支撑它的那条腿是否着地仍由原版判定（{@code footLerpAmt == 1.0}），这里只改摩擦强度，
 * 不碰步态、落脚点与推力。
 */
@Mixin(targets = "com.zarkonnen.airships.Leg", remap = false)
public abstract class LegGroundFrictionMixin {
    /** 目标类的模块引用，用于取得所属舰船（{@code Leg.module} 是 public 字段）。 */
    @Shadow public com.zarkonnen.airships.Module module;

    @Redirect(method = "tick2(ILcom/zarkonnen/airships/LandFormation;Ljava/util/ArrayList;ZLcom/zarkonnen/airships/Combat;ZZ)Z",
              at = @At(value = "INVOKE", target = "Ljava/lang/StrictMath;pow(DD)D", ordinal = 0))
    private double arc$loadScaledFriction(double base, double exponent, int ms, LandFormation ground,
                                          ArrayList<LandFormation> formations, boolean doStep, Combat combat,
                                          boolean powered, boolean onViewingSide) {
        return StrictMath.pow(GroundLoad.scaleFrictionBase(base, GroundLoad.loadFraction(this.module.ship, combat)), exponent);
    }
}
