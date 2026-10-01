/* OrderedTargetCommandMixin.java — 无武装航母也能下 T 指令：多选时不再因为 canShoot() 为假被跳过。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.TargetCommandTool;
import net.poosh.arc.combat.OrderedTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 无武装航母用 T 指定攻击目标时报文根本发不出去。
 *
 * <p>原生 {@code TargetCommandTool.click} 有两条路：单选（{@code us.selectedShip}）直接发
 * {@code fireAt}；多选/框选（{@code us.selectedShips}，此时 {@code selectedShip} 为 null）则要求
 * {@code s.readyForCommand() && s.canShoot()}（原生第 94 行）才发。航母通常没有武器，
 * {@code canShoot()} 为假 —— 于是「框选整支舰队 + 按 T 点敌人」时只有带炮的舰船收到指令，
 * 航母被静默跳过，它的舰载机自然没有任何反应。</p>
 *
 * <p>这里把那一次 {@code canShoot()} 换成 {@link OrderedTarget#canOrder}：有武器，或者能把指令
 * 交给舰载机（{@code canGiveAircraftCommands()} 的飞机指挥台，或 {@code hasFlyers()} 认下的任意
 * 飞机舱位，例如只带机库的航母）都算能接受 T 指令。无武器又没有飞机舱位的运输/登陆舰仍然被跳过，
 * 与原版一致。</p>
 *
 * <p>只改这一个调用点：{@code click} 里 {@code canShoot()} 只出现这一次，单选分支、
 * {@code doAbility} 分支与光标绘制都不碰。</p>
 */
@Mixin(value = TargetCommandTool.class, remap = false)
public abstract class OrderedTargetCommandMixin {
    /** 原版第 94 行 {@code if (!s.readyForCommand() || !s.canShoot()) continue;}。 */
    @Redirect(method = "click(Lcom/zarkonnen/catengine/Input;Lcom/zarkonnen/catengine/util/Pt;Lcom/zarkonnen/catengine/util/ScreenMode;Lcom/zarkonnen/airships/UniScreen;)Z",
              at = @At(value = "INVOKE", target = "Lcom/zarkonnen/airships/Airship;canShoot()Z"))
    private boolean arc$canOrderTarget(Airship ship) {
        return OrderedTarget.canOrder(ship);
    }
}
