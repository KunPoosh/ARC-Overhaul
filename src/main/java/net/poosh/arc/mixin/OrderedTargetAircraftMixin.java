/* OrderedTargetAircraftMixin.java — 舰载机继承母舰 T 指令：无武装航母点名的目标不再因为 dangerCache 为 0 被丢掉。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.Crewman;
import com.zarkonnen.airships.Tile;
import net.poosh.arc.combat.OrderedTarget;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 无武装航母的 T 指令要能落到它放出去的舰载机上。
 *
 * <p>原版就是这样设计的：出击的舰载机在 {@code Crewman.outsideShootingTick} 里继承母舰的
 * {@code fireAt}（原生第 2248-2250 行），所以选航母按 T 点一个敌人，飞机就会去打它。但这条继承
 * 带着 {@code this.poppedOutOfTile.ship.fireAt.dangerCache > 0.0}，与舰炮那边同一套问题：
 * 无武装的目标（建筑、失去武装的飞艇）会被判成「不值得打」，指令被无声丢弃。</p>
 *
 * <p>更麻烦的是同一方法开头（原生第 2239 行）每个 tick 都会清目标：
 * {@code if (… || this.attackTarget.dangerCache <= 0.0 || …) this.attackTarget = null;}。
 * 清空发生在重装填提前返回（原生第 2245 行 {@code if (!this.active() || this.weaponReload > 0) return;}）
 * 之前，而重新继承母舰指令发生在之后 —— 也就是说如果只放开第 2248 行，飞机在装填期间
 * {@code attackTarget} 一直是空的，它的攻击航线（{@code outsideFlyingTick} 里的 strafe 航点）
 * 就会丢目标，飞机根本飞不出这一趟攻击。所以这里两处一起改。</p>
 *
 * <p>两处注入都只替换「判定该目标是否值得打」的那一次 {@code GETFIELD dangerCache}：
 * 第 2248 行是方法内第 2 次读（第 1 次在第 2239 行的清目标条件里，第 3 次在第 2260 行的
 * 自动挑目标循环里），第 2239 行是第 1 次。自动挑目标（第 2251-2267 行，仍要求
 * {@code dangerCache > 0}）没有改动，飞机不会自己去找无武装目标。</p>
 */
@Mixin(value = Crewman.class, remap = false)
public abstract class OrderedTargetAircraftMixin {
    /** 原版公开字段：舰载机出击时所在的机库格；{@code ship} 即母舰。非出击状态为 null。 */
    @Shadow public Tile poppedOutOfTile;

    /** 母舰当前的 T 指令目标；不是出击中的舰载机（或母舰没有指令）时为 null。 */
    @Unique private Airship arc$carrierOrder() {
        Tile from = this.poppedOutOfTile;
        if (from == null) return null;
        Airship carrier = from.ship;
        return carrier == null ? null : carrier.fireAt;
    }

    /** 原版第 2239 行 {@code this.attackTarget.dangerCache <= 0.0}（清目标），方法内第 1 次读。 */
    @Redirect(method = "outsideShootingTick(ILcom/zarkonnen/airships/Combat;Lcom/zarkonnen/airships/Combat$Side;Z)V",
              at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
                       target = "Lcom/zarkonnen/airships/Airship;dangerCache:D"))
    private double arc$keepOrderedTarget(Airship target) {
        return OrderedTarget.visibleDanger(target, arc$carrierOrder());
    }

    /** 原版第 2248 行 {@code this.poppedOutOfTile.ship.fireAt.dangerCache > 0.0}（继承指令），方法内第 2 次读。 */
    @Redirect(method = "outsideShootingTick(ILcom/zarkonnen/airships/Combat;Lcom/zarkonnen/airships/Combat$Side;Z)V",
              at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 1,
                       target = "Lcom/zarkonnen/airships/Airship;dangerCache:D"))
    private double arc$orderedTargetIsWorthShooting(Airship target) {
        // 原版整条链已经判过母舰、指令与目标的存在性，正常情况下 target 就是母舰的 fireAt；
        // 这里仍然以母舰当前的 fireAt 作为「被点名者」，语义才始终与判定所用对象一致。
        return OrderedTarget.visibleDanger(target, arc$carrierOrder());
    }
}
