/* TacticalAIFixesMixin.java — 修正 TacticalAI 的预备队碰撞、Sudden Storm 模拟、威胁距离和撞区朝向。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.Combat;
import com.zarkonnen.airships.TacticalAI;
import net.poosh.arc.combat.TacticalAIFixes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

/**
 * 只修正原版 TacticalAI 的确定性错误，不改变候选网格、权重或行动优先级。
 *
 * <p>四处修复都来自对发行版 {@code TacticalAI} 字节码的直接核对：</p>
 * <ol>
 *   <li>{@code sideTick} 的两次 {@code Combat.canPlace} 必须用待投放的 {@code res} 检查，而不是
 *       当前执行决策的 {@code ship}。</li>
 *   <li>{@code SUDDEN_STORM} 的 {@code + sideIndex == 0 ? ...} 被 Java 优先级解析成
 *       “目标 x 加阵营序号是否为零”；这里用 {@link TacticalAIFixes} 明确计算相对位移。</li>
 *   <li>{@code getDamageQuality} 的纵向距离应使用 {@code ey}，不是横坐标 {@code ex}。</li>
 *   <li>{@code CRASH_ZONE} 评估敌舰火力时当前朝向就是该舰自身朝向，不应再次翻转。</li>
 * </ol>
 */
@Mixin(value = TacticalAI.class, remap = false)
public abstract class TacticalAIFixesMixin {
    /** 当前 sideTick 正在处理的预备队。由 Iterator.next 决定，随后用于两次部署检查。 */
    @Unique private Airship arc$reserveBeingPlaced;
    /** Sudden Storm 第一次模拟中用到的敌舰 x。 */
    @Unique private double arc$stormEnemyX;
    /** Sudden Storm 第二次模拟中用到的自身 x。 */
    @Unique private double arc$stormSelfX;

    @Shadow public Combat c;
    @Shadow public Combat.Side mySide;

    @Redirect(method = "sideTick()V",
              at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"))
    private Object arc$rememberReserve(Iterator<Airship> iterator) {
        Airship reserve = iterator.next();
        this.arc$reserveBeingPlaced = reserve;
        return reserve;
    }

    @Redirect(method = "sideTick()V",
              at = @At(value = "INVOKE", ordinal = 0,
                       target = "Lcom/zarkonnen/airships/Combat;canPlace(Lcom/zarkonnen/airships/Airship;Ljava/util/List;IIILcom/zarkonnen/airships/Combat$Side;)Z"))
    private boolean arc$placeGroundReserve(
            Combat combat,
            Airship ignored,
            List<Airship> others,
            int x,
            int y,
            int spacing,
            Combat.Side side) {
        return arc$placeReserve(combat, ignored, others, x, y, spacing, side);
    }

    @Redirect(method = "sideTick()V",
              at = @At(value = "INVOKE", ordinal = 1,
                       target = "Lcom/zarkonnen/airships/Combat;canPlace(Lcom/zarkonnen/airships/Airship;Ljava/util/List;IIILcom/zarkonnen/airships/Combat$Side;)Z"))
    private boolean arc$placeFlyingReserve(
            Combat combat,
            Airship ignored,
            List<Airship> others,
            int x,
            int y,
            int spacing,
            Combat.Side side) {
        return arc$placeReserve(combat, ignored, others, x, y, spacing, side);
    }

    @Unique
    private boolean arc$placeReserve(
            Combat combat,
            Airship fallback,
            List<Airship> others,
            int x,
            int y,
            int spacing,
            Combat.Side side) {
        Airship reserve = this.arc$reserveBeingPlaced;
        return combat.canPlace(reserve == null ? fallback : reserve, others, x, y, spacing, side);
    }

    @Inject(method = "sideTick()V", at = @At("RETURN"))
    private void arc$clearReserve(CallbackInfo callback) {
        this.arc$reserveBeingPlaced = null;
    }

    @Redirect(method = "move()V",
              at = @At(value = "INVOKE", ordinal = 13,
                       target = "Lcom/zarkonnen/airships/Airship;getX()D"))
    private double arc$rememberStormEnemyX(Airship enemy) {
        this.arc$stormEnemyX = enemy.getX();
        return this.arc$stormEnemyX;
    }

    @Redirect(method = "move()V",
              at = @At(value = "INVOKE", ordinal = 15,
                       target = "Lcom/zarkonnen/airships/Airship;getX()D"))
    private double arc$rememberStormSelfX(Airship self) {
        this.arc$stormSelfX = self.getX();
        return this.arc$stormSelfX;
    }

    @Redirect(method = "move()V",
              at = @At(value = "INVOKE", ordinal = 2,
                       target = "Ljava/lang/Math;min(DD)D"))
    private double arc$stormEnemyShift(double upperLimit, double ignored) {
        return TacticalAIFixes.stormEnemyX(
                this.arc$stormEnemyX,
                this.c.sides.indexOf(this.mySide),
                upperLimit);
    }

    @Redirect(method = "move()V",
              at = @At(value = "INVOKE", ordinal = 3,
                       target = "Ljava/lang/Math;min(DD)D"))
    private double arc$stormSelfShift(double upperLimit, double ignored) {
        return TacticalAIFixes.stormSelfX(
                this.arc$stormSelfX,
                this.c.sides.indexOf(this.mySide),
                upperLimit);
    }

    @Redirect(method = "getDamageQuality(Lcom/zarkonnen/airships/Airship;Lcom/zarkonnen/airships/Airship;DDZ)D",
              at = @At(value = "INVOKE", ordinal = 1, target = "Ljava/lang/StrictMath;min(DD)D"))
    private double arc$verticalEdgeDistance(
            double ignored,
            double oppositeEdgeDistance,
            Airship attacker,
            Airship defender,
            double attackerX,
            double attackerY,
            boolean flipped) {
        return TacticalAIFixes.verticalEdgeDistance(
                defender.getY(),
                attackerY,
                attacker.getBBHeight(),
                oppositeEdgeDistance);
    }

    @ModifyArg(method = "move()V",
               at = @At(value = "INVOKE", ordinal = 12,
                        target = "Lcom/zarkonnen/airships/TacticalAI;getDamageQuality(Lcom/zarkonnen/airships/Airship;Lcom/zarkonnen/airships/Airship;DDZ)D"),
               index = 4)
    private boolean arc$crashZoneUsesActualFacing(boolean flipped) {
        return false;
    }
}
