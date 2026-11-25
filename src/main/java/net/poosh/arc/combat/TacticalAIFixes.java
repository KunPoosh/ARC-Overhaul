/* TacticalAIFixes.java — TacticalAI 的确定性修复规则：预备队碰撞、风暴位移与纵向威胁距离。 */
package net.poosh.arc.combat;

/**
 * 原版 {@code TacticalAI} 中几处确定性的决策输入错误。
 *
 * <p>规则保持为纯函数，Mixin 只负责把原版调用接到这里。这样不复制整段 AI，也可以在不启动战斗的
 * 情况下验证方向、边界和距离计算。</p>
 */
public final class TacticalAIFixes {
    /** 原版 {@code SUDDEN_STORM} 希望以 1000 像素模拟能力造成的横向位移。 */
    public static final double SUDDEN_STORM_SHIFT = 1000.0;

    private TacticalAIFixes() { }

    /**
     * 模拟 {@code SUDDEN_STORM} 后敌舰的新 x，只处理原版 {@code StrictMath.min} 的第二个参数；
     * 外侧的 {@code max} 边界仍由原版继续执行。
     */
    public static double stormEnemyX(double enemyX, int sideIndex, double upperLimit) {
        return StrictMath.min(upperLimit, enemyX + stormDirection(sideIndex));
    }

    /**
     * 模拟 {@code SUDDEN_STORM} 后自身的新 x。自身应向阵营方向的相反侧移动；
     * 同样只替换 {@code min} 的结果，外侧边界保持原版。
     */
    public static double stormSelfX(double selfX, int sideIndex, double upperLimit) {
        return StrictMath.min(upperLimit, selfX - stormDirection(sideIndex));
    }

    /**
     * {@code getDamageQuality} 的纵向边距。原版错误地把横坐标 {@code ex} 用进了纵向表达式；
     * 第二个边距本身正确，因此只需要重新计算第一个值再取最小值。
     */
    public static double verticalEdgeDistance(
            double defenderY,
            double attackerY,
            double attackerHeight,
            double oppositeEdgeDistance) {
        double ownEdge = StrictMath.abs(defenderY - attackerY - attackerHeight);
        return StrictMath.min(ownEdge, oppositeEdgeDistance);
    }

    private static double stormDirection(int sideIndex) {
        return sideIndex == 0 ? SUDDEN_STORM_SHIFT : -SUDDEN_STORM_SHIFT;
    }
}
