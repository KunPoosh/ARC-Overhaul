/* ModuleGroundFrictionMixin.java — 履带/腿的模块弹簧摩擦按接地载荷缩放：升力越大摩擦越小。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.Combat;
import net.poosh.arc.speed.GroundLoad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让接地摩擦正比于法向力 / make spring friction proportional to the normal load.
 *
 * <p>原版在 {@code Module.tick} 的弹簧循环里写死：
 * {@code ship.setxSpeed(ship.getxSpeed() * StrictMath.pow(1.0 - spring.xFriction * fMult, ms))}，
 * 与舰船压在地面上的重量无关。于是悬浮石与龟甲（Shell Armour）带来的升力完全体现不出来。</p>
 *
 * <p>注入落点是那次 {@code StrictMath.pow}（字节码偏移 917，紧跟在 {@code Spring.xFriction} 读取之后、
 * 是 {@code tick} 里第一个 pow；第二个是同一行的纵向摩擦，保持原样），只改摩擦底数。
 * 没有升力的舰船载荷比例为 1，返回值与原版逐位相同；只有 {@code onGround} 舰种参与。</p>
 */
@Mixin(targets = "com.zarkonnen.airships.Module", remap = false)
public abstract class ModuleGroundFrictionMixin {
    /** 目标类的舰船引用，用于判断接地载荷（{@code Module.ship} 是 public 字段）。 */
    @Shadow public Airship ship;

    @Redirect(method = "tick(ILcom/zarkonnen/airships/Combat;ZDDDDD)V",
              at = @At(value = "INVOKE", target = "Ljava/lang/StrictMath;pow(DD)D", ordinal = 0))
    private double arc$loadScaledFriction(double base, double exponent, int ms, Combat combat, boolean onViewingSide,
                                          double fireRateMult, double accuracyMult, double flammabilityMult,
                                          double explosionRiskMult, double commandCooldownMult) {
        return StrictMath.pow(GroundLoad.scaleFrictionBase(base, GroundLoad.loadFraction(this.ship, combat)), exponent);
    }
}
