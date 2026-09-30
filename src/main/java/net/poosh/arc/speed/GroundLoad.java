/* GroundLoad.java — 接地载荷：悬浮石与龟甲装甲提供的升力抵消多少重力，接地摩擦就按比例减轻多少。 */
package net.poosh.arc.speed;

import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.Combat;

/**
 * 压在地面上的重量比例 / fraction of the ship's weight resting on the ground.
 *
 * <p>原版把接地摩擦写成与载荷无关的固定系数：只要弹簧被压到，每个 tick 就把水平速度乘以
 * {@code (1 - Spring.xFriction × fMult)}（{@code Module.tick} 的模块弹簧与 {@code Leg.tick2} 的腿部弹簧）。
 * 但摩擦在物理上正比于法向力，而悬浮石与 <b>龟甲（Shell Armour）</b> 都能提供升力
 * （{@code ArmourType/81_Turtle_Shell.json} 的 {@code "lift": 35}，由 {@code Airship.getLift()} 连同模块升力一起汇总）。
 * 于是升力越大，压在地面上的重量越小，摩擦越小，陆行舰越快。原版完全没有这一项：
 * 堆了龟甲/悬浮石的陆行舰在游戏里跑得和裸舰一样慢（实测见
 * {@code tools/arc/.work/speedprobe/SpeedProbe10}）。</p>
 *
 * <p>本类给出"压在地面上的重量比例"，供物理侧的摩擦缩放（{@code ModuleGroundFrictionMixin} /
 * {@code LegGroundFrictionMixin}）与显示侧的有效速度（{@link EffectiveSpeed}）共用，保证面板与实际一致。
 * 没有升力的舰船比例恒为 1，行为等同原版。</p>
 */
public final class GroundLoad {
    /** 与 {@code Physics.gravity} 同口径：单位质量的重力（px/ms²）。 */
    public static final double GRAVITY = 0.001;
    private static final double FLOOR_Y = 512.0;

    private GroundLoad() { }

    /** 重力：{@code mass × 0.001}，与 {@code Airship.groundOffset()} 的算法一致。 */
    public static double weightForce(Airship ship) {
        return ship == null ? 0.0 : ship.getMass() * GRAVITY;
    }

    /**
     * 当前可用的升力（与 {@code Airship.availableSuspendiumForce} 同口径，px·kg/ms²）。
     *
     * <p>有战斗上下文时用 {@code availableLift(combat)}：它会统计还能运行的悬浮石模块与<b>未损毁</b>的装甲格
     * （{@code Airship.java:5441-5472}），所以打坏龟甲就会掉速度。面板拿不到战斗上下文时用设计值
     * {@code getLift()}，并按"舰船正贴地"取满额。</p>
     */
    public static double liftForce(Airship ship, Combat combat) {
        if (ship == null || ship.type == null || !ship.type.onGround) return 0.0;
        try {
            int lift = combat == null ? ship.getLift() : ship.availableLift(combat);
            if (lift <= 0) return 0.0;
            double weight = weightForce(ship);
            double distanceFromFloor = combat == null
                    ? 0.0
                    : StrictMath.pow(StrictMath.max(1.0, FLOOR_Y - ship.getY()), 1.5) / 30.0;
            double force = (double)lift * 400.0 * 2.0 / (400.0 + distanceFromFloor) * GRAVITY;
            return StrictMath.min(force, weight);
        } catch (RuntimeException ignored) {
            return 0.0;
        }
    }

    /** 压在地面上的重量比例：1 = 与原版一致（没有升力），0 = 升力完全抵消重力。 */
    public static double loadFraction(Airship ship, Combat combat) {
        return loadFraction(weightForce(ship), liftForce(ship, combat));
    }

    /** 纯函数版本，便于测试与复用。 */
    public static double loadFraction(double weightForce, double liftForce) {
        if (!(weightForce > 0.0)) return 1.0;
        double fraction = (weightForce - liftForce) / weightForce;
        return fraction < 0.0 ? 0.0 : (fraction > 1.0 ? 1.0 : fraction);
    }

    /**
     * 把一个原版摩擦底数按载荷比例缩放。
     *
     * <p>原版底数是 {@code 1 - xFriction × fMult}（见 {@code Module.tick}/{@code Leg.tick2} 里的
     * {@code StrictMath.pow(1.0 - … , ms)}）。载荷比例为 L 时改成 {@code 1 - xFriction × fMult × L}：
     * 物理上就是摩擦正比于法向力。L = 1（没有升力）时逐位返回原值，行为与原版完全一致；
     * L = 0 时返回 1，即这一步不再削速度。</p>
     */
    public static double scaleFrictionBase(double base, double load) {
        if (!(load < 1.0)) return base;
        double friction = 1.0 - base;
        if (!(friction > 0.0)) return base;
        return 1.0 - friction * load;
    }
}
