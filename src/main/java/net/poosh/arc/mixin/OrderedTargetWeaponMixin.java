/* OrderedTargetWeaponMixin.java — 舰炮按 T 指令打无武装目标：指令点名的建筑/飞艇不再因为 dangerCache 为 0 被忽略。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.Module;
import net.poosh.arc.combat.OrderedTarget;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让 T 指令（{@code Airship.fireAt}）对无武装目标也生效。
 *
 * <p>原生 {@code Module.targetShip} 的指令分支是
 * {@code if (this.ship.fireAt != null && this.ship.fireAt.dangerCache > 0.0 && …)}（原生第 1464 行）：
 * 玩家点名的目标只要 {@code dangerCache} 为 0 就被跳过，模块转而在同一方法末尾按
 * {@code dangerCache / (dist + 100)} 自己挑一个敌人 —— 表现为「按了 T，舰炮照旧打别处」。
 * {@code dangerCache} 来自 {@code Airship.danger(c)}，它在 {@code !inCombat(c)} 时是 0：
 * 无武装又不能动的建筑，以及失去武装与动力（或全员阵亡）的飞艇都在此列。</p>
 *
 * <p>本注入只换掉这一次 {@code GETFIELD}：把「原值，或者被 T 点名时的等价正值」交给原版条件。
 * 该读取点是方法里第 2 次读 {@code dangerCache}（第 1 次是 {@code targetShip} 第二段循环里对
 * 全体敌人的 {@code as.dangerCache}），因此 {@code ordinal = 1}。</p>
 *
 * <p>同时放宽 {@code Module.target} 里「沿用上一帧目标」的条件（原生第 1664 行，
 * {@code this.prevTargetShip.dangerCache > 0.0}）：不这样做的话，被 T 点名的无武装目标每帧都会
 * 走到「目标作废 → 重新挑目标」的分支，瞄点整块重掷（{@code prevTarget} 被清空后由
 * {@code Module.target} 用 {@code Combat.r} 重抽），炮弹落点会随帧抖动。该读取点是方法里第 1 次
 * 读 {@code dangerCache}（后两次在两条 fallback 循环里），因此 {@code ordinal = 0}。</p>
 *
 * <p>自动选目标的两条路都没碰：{@code targetShip} 末尾按威胁值排序的挑选、以及
 * {@code target} 里 {@code dangerCache > 0} / {@code dangerCache <= 0} 的两条 fallback 循环
 * 全部保持原样，所以舰炮不会主动去打无武装目标，必须有人用 T 点名。</p>
 */
@Mixin(value = Module.class, remap = false)
public abstract class OrderedTargetWeaponMixin {
    /** 原版公开字段：模块所属舰船，T 指令就挂在它的 {@code fireAt} 上。 */
    @Shadow public Airship ship;

    /** 原版第 1464 行 {@code this.ship.fireAt.dangerCache > 0.0}，方法内第 2 次读 dangerCache。 */
    @Redirect(method = "targetShip(Lcom/zarkonnen/airships/Combat;)Lcom/zarkonnen/airships/Airship;",
              at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 1,
                       target = "Lcom/zarkonnen/airships/Airship;dangerCache:D"))
    private double arc$orderedTargetIsWorthShooting(Airship target) {
        return OrderedTarget.visibleDanger(target, this.ship.fireAt);
    }

    /** 原版第 1664 行 {@code this.prevTargetShip.dangerCache > 0.0}，方法内第 1 次读 dangerCache。 */
    @Redirect(method = "target(Lcom/zarkonnen/airships/Combat;DD)Lcom/zarkonnen/catengine/util/Utils$Pair;",
              at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
                       target = "Lcom/zarkonnen/airships/Airship;dangerCache:D"))
    private double arc$orderedTargetKeepsAim(Airship previous) {
        // 原版这一条还要求 prevTargetShip == ship.fireAt 或没有指令，因此这里只可能抬高被点名的那个。
        return OrderedTarget.visibleDanger(previous, this.ship.fireAt);
    }
}
