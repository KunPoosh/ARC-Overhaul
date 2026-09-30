/* EffectiveSpeed.java — 把弹簧的接地摩擦并入有效速度：面板速度按稳态方程 F/m = f·v² + λ·v 求解，
   让编辑器与战略/战斗面板显示的速度与实际战斗中能达到的稳态速度一致。 */
package net.poosh.arc.speed;

import com.zarkonnen.airships.*;
import java.util.ArrayList;

/**
 * 陆行舰的真实稳态速度 / effective grounded speed.
 *
 * <p>原版面板用 {@code Airship.getSpeed()}，它只减了二次空气阻力：
 * {@code v = sqrt(getPropulsion() / (mass × frontAirFriction()))}。但接地舰船还有一项
 * <b>线性粘性阻尼</b>：只要弹簧（履带/腿）压到地面，原版就会在每个 tick 把水平速度乘以
 * {@code (1 - Spring.xFriction × fMult)}（{@code Module.java} 的模块弹簧与 {@code Leg.java} 的
 * 腿部弹簧，前者默认 {@code xFriction = 0.002}、后者 {@code 0.004}）。面板完全不知道这一项，
 * 于是陆行舰的面板速度会比实际稳态速度高出 2–6 倍。</p>
 *
 * <p>本类把两项合并求解，得到与物理一致的稳态速度；飞艇没有弹簧，λ = 0，结果与原生完全一致。</p>
 */
public final class EffectiveSpeed {
    /** 原生 tick 长度，对应 {@code Combat.TICK_LENGTH}。 */
    public static final int TICK_MS = 16;
    /** 同一 tick 内除第一个之外其它接地弹簧的折算比例，对应原版 {@code hasHadSpringFriction} 的 0.1。 */
    public static final double SECONDARY_SPRING_MULT = 0.1;
    private static final double[] NO_SPRINGS = new double[0];

    private EffectiveSpeed() { }

    /**
     * 一个 tick 内所有接地弹簧造成的乘性衰减因子。
     * 与原版一致：第一个弹簧吃满 {@code xFriction}，其余只吃 10%。
     */
    public static double perTickFactor(double[] xFrictions, int ms, double load) {
        if (xFrictions == null || xFrictions.length == 0) return 1.0;
        double factor = StrictMath.pow(clamp(1.0 - xFrictions[0] * load), ms);
        for (int i = 1; i < xFrictions.length; i++)
            factor *= StrictMath.pow(clamp(1.0 - xFrictions[i] * SECONDARY_SPRING_MULT * load), ms);
        return factor;
    }

    /** 没有升力时的每 tick 衰减因子，等价于载荷比例 1（原版）。 */
    public static double perTickFactor(double[] xFrictions, int ms) {
        return perTickFactor(xFrictions, ms, 1.0);
    }

    /**
     * 接地摩擦速率 λ（单位 1/ms）；没有接地弹簧时为 0。
     *
     * <p>{@code load} 是 {@link GroundLoad#loadFraction} 给出的接地载荷比例：它与物理侧
     * （{@code ModuleGroundFrictionMixin} / {@code LegGroundFrictionMixin}）用的是同一条规则
     * ——把每个弹簧的 {@code xFriction} 乘以载荷比例，所以这里也逐项乘同一个数，
     * 面板与实际逐项对应。</p>
     */
    public static double groundFrictionRate(double[] xFrictions, int ms, double load) {
        double factor = perTickFactor(xFrictions, ms, load);
        if (!(factor < 1.0)) return 0.0;
        return -StrictMath.log(StrictMath.max(factor, 1.0e-9)) / ms;
    }

    /** 没有升力时的接地摩擦速率（载荷比例 1，原版口径）。 */
    public static double groundFrictionRate(double[] xFrictions, int ms) {
        return groundFrictionRate(xFrictions, ms, 1.0);
    }

    /**
     * 收集这艘舰所有弹簧的 {@code xFriction}（模块弹簧 + 腿弹簧）。
     * 只有 {@code onGround} 的舰种（陆行舰/建筑）才算：飞艇的弹簧只在起降瞬间碰地，
     * 不能把整段飞行都按接地摩擦折算，否则会反过来低估飞艇。
     */
    public static double[] springFrictions(Airship ship) {
        if (ship == null || ship.type == null || !ship.type.onGround || ship.modules == null) return NO_SPRINGS;
        ArrayList<Double> found = new ArrayList<Double>();
        for (int i = 0; i < ship.modules.size(); i++) {
            com.zarkonnen.airships.Module m = ship.modules.get(i);
            if (m == null || m.type == null) continue;
            for (Spring spring : m.type.getSprings())
                if (spring != null && spring.xFriction > 0) found.add(Double.valueOf(spring.xFriction));
            for (Leg.Spec spec : m.type.getLegSpecs())
                if (spec != null && spec.spring != null && spec.spring.xFriction > 0) found.add(Double.valueOf(spec.spring.xFriction));
        }
        if (found.isEmpty()) return NO_SPRINGS;
        double[] out = new double[found.size()];
        for (int i = 0; i < out.length; i++) out[i] = found.get(i).doubleValue();
        return out;
    }

    /** 稳态水平速度：解 {@code F/m = f·v² + λ·v}；λ = 0 时退化为原生的 {@code sqrt(F/(m·f))}。 */
    public static double steadySpeed(double force, int mass, double airFriction, double groundFriction) {
        if (!(force > 0.0) || mass <= 0 || !(airFriction > 0.0)) return 0.0;
        double acceleration = force / mass;
        if (!(groundFriction > 0.0)) return StrictMath.sqrt(acceleration / airFriction);
        return (-groundFriction
                + StrictMath.sqrt(groundFriction * groundFriction + 4.0 * airFriction * acceleration))
                / (2.0 * airFriction);
    }

    /**
     * 这艘舰此刻的物理等效速度（未乘地图倍率）。
     *
     * <p>接地摩擦速率 λ 还要乘以接地载荷比例：悬浮石与龟甲（Shell Armour）提供的升力抵消掉的那部分
     * 重力不会压在地面上，摩擦随之减轻（见 {@link GroundLoad}）。物理侧由
     * {@code ModuleGroundFrictionMixin} / {@code LegGroundFrictionMixin} 施加同一比例，两边口径一致。</p>
     */
    public static double effectiveSpeed(Airship ship) {
        if (ship == null) return 0.0;
        Combat combat = ship.CURRENT_COMBAT_DELETEME;
        double ground = groundFrictionRate(springFrictions(ship), TICK_MS, GroundLoad.loadFraction(ship, combat));
        double air = ship.frontAirFriction() * waterMultiplier(ship);
        return StrictMath.min(ship.getMaxXSpeed(), steadySpeed(ship.getPropulsion(), ship.getMass(), air, ground));
    }

    /** 面板用的地图速度：与原生 {@code Airship.getMainMapSpeed} 同口径，只是速度换成等效速度。 */
    public static double mainMapSpeed(Airship ship, BonusSet bonuses) {
        double mult = ship.type.onGround
                ? EmpireStat.LANDSHIP_MAP_SPEED_MULT.get(bonuses)
                : EmpireStat.AIRSHIP_MAP_SPEED_MULT.get(bonuses);
        return effectiveSpeed(ship) * mult;
    }

    /** 水里的阻力倍率沿用原版 {@code GridBody.frictionMult}；拿不到战斗上下文时为 1。 */
    private static double waterMultiplier(Airship ship) {
        Combat combat = ship.CURRENT_COMBAT_DELETEME;
        if (combat == null || combat.landFormations == null || combat.landFormations.isEmpty()) return 1.0;
        try {
            return ship.frictionMult(combat);
        } catch (RuntimeException ignored) {
            return 1.0;
        }
    }

    private static double clamp(double factor) {
        return factor > 1.0e-6 ? factor : 1.0e-6;
    }
}
