/* OrderedTarget.java — 显式 T 指令（Airship.fireAt）的判定规则：指令点名的目标即使 dangerCache 为 0
   （无武装建筑、无武装飞艇）也算有效目标，而自动选目标的分支完全不受影响。 */
package net.poosh.arc.combat;

import com.zarkonnen.airships.Airship;
import java.util.List;

/**
 * 显式 T 指令 / explicit attack order.
 *
 * <p>原版 {@code TargetCommandTool} 的 T 指令最终落到 {@code Airship.fireAt}。舰炮在自己的
 * {@code Module.targetShip} 里确实会优先取它，但那条分支带着一个额外条件：
 * {@code this.ship.fireAt.dangerCache > 0.0}。{@code dangerCache} 是 {@code Airship.danger(c)} 的
 * 缓存，而 {@code danger(c)} 在 {@code !inCombat(c)} 时直接返回 0；无武装且不能动的建筑，
 * 以及失去全部武装与动力（或全员阵亡）的飞艇都落在这个 0 上。于是玩家用 T 点名这些目标时，
 * 舰炮会当它不存在，转头去打自己的自动目标 —— 这正是「T 打了没用」的根源。</p>
 *
 * <p>反过来，手操（直控）模式不受影响：{@code Module.target} 的直控分支只看
 * {@code prevTargetShip}/{@code prevTarget}（原生第 1637-1648 行），从来不看 dangerCache，
 * 所以手操下用准星指着无武装目标是可以开火的。本类只把「显式指令」这一条路补齐到同一水平。</p>
 *
 * <p><b>不改变自动选目标</b>：原版自动挑目标仍以 {@code dangerCache} 为依据
 * （{@code targetShip} 末尾的 {@code dangerCache / (dist + 100)}，以及 {@code target} 里两条
 * fallback 循环）。本类只被注入到「判定显式指令目标」的那几次读取上，因此舰船与舰载机
 * 不会主动去打无武装目标 —— 必须有人用 T 点名。</p>
 */
public final class OrderedTarget {
    /**
     * 给显式指令目标用的等价威胁值。原版这几处判定全是 {@code > 0.0} 或 {@code <= 0.0}，
     * 任何正数都能让判定通过；取 1.0 是为了与被注入的那条 {@code > 0.0} 保持同样的量纲，
     * 万一将来有别的读取点被卷进来，它也只是「最小的威胁」而不是「最大的威胁」。
     */
    public static final double ORDERED = 1.0;

    private OrderedTarget() { }

    /**
     * 某次「这个目标值得打吗」判定里应当看到的 {@code dangerCache}。
     *
     * @param target  被判定的目标
     * @param ordered 本条指令链上的显式 T 指令目标（没有则为 {@code null}）
     * @return 原值；只有「本来就为 0 且正是被 T 点名的那个目标」才抬到 {@link #ORDERED}
     */
    public static double visibleDanger(Airship target, Airship ordered) {
        if (target == null) return 0.0;
        double danger = target.dangerCache;
        if (danger > 0.0) return danger;
        return target == ordered ? ORDERED : 0.0;
    }

    /**
     * 这艘船能不能接受 T 指令：自己有武器，或者能把指令转交给舰载机。
     *
     * <p>舰载机那一路对应无武装航母：{@code Crewman.outsideShootingTick} 会让出击的舰载机继承母舰的
     * {@code fireAt}（原生第 2248 行），所以航母完全用得上 T 指令，原版却因为 {@code canShoot()}
     * 为假而把它排除在外。</p>
     *
     * <p>注意<b>不能只用 {@code canGiveAircraftCommands()}</b>：它认的是
     * {@code ModuleType.canGivePlaneCommands}，而游戏数据里只有 {@code FLIGHT_CENTRE}（飞机指挥台）
     * 有这个键。机库（{@code BOMBER_HANGAR} / {@code BIPLANE_HANGAR} / {@code TRIPLANE_HANGAR} /
     * {@code TORPEDO_BOMBER_HANGAR} / {@code ROCKET_PLANE_HANGAR} / {@code GATLING_SKYBOAT_HANGAR}）
     * 只声明 {@code quartersType}（机组的 {@code CrewType.canFly} 为真），所以「只带机库、不带指挥台」
     * 的航母在 {@code canGiveAircraftCommands()} 上是假 —— 但它的飞机照样会出击、照样会继承母舰的
     * {@code fireAt}。这里用原版 {@code Airship.hasFlyers()}（任一模块的 {@code quartersType.canFly}
     * 为真）把这类航母一并算进来；没有任何飞机舱位、也没有武器的运输/登陆舰仍然是假，与原版一致。</p>
     */
    public static boolean canOrder(Airship ship) {
        return ship != null && (ship.canShoot() || ship.canGiveAircraftCommands() || ship.hasFlyers());
    }

    /**
     * 当前选择里是否有「已就绪且能接受 T 指令」的舰船。
     *
     * <p>对齐原版 T 按钮的 {@code anyShipsReady} 语义：冷却中的舰船不算，否则面板会在
     * 指令点不下去的时候亮着按钮。{@code single} 是单选时的那一艘（原版单选优先），
     * 为 {@code null} 时才看多选列表。</p>
     */
    public static boolean anyReadyToOrder(Airship single, List<Airship> selection) {
        if (single != null) return single.readyForCommand() && canOrder(single);
        if (selection == null) return false;
        for (Airship ship : selection) {
            if (ship != null && ship.readyForCommand() && canOrder(ship)) return true;
        }
        return false;
    }
}
