/* OrderedTargetButtonMixin.java — 指令面板的 T 按钮：无武装航母也点得亮，能向舰载机下达攻击目标。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.CommandButtonsPanel;
import com.zarkonnen.airships.MyDraw;
import com.zarkonnen.airships.UniScreen;
import com.zarkonnen.catengine.Hooks;
import com.zarkonnen.catengine.Img;
import com.zarkonnen.catengine.util.Pt;
import com.zarkonnen.catengine.util.ScreenMode;
import net.poosh.arc.combat.OrderedTarget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 指令面板上的 T 按钮对无武装航母要可用。
 *
 * <p>原生画按钮时用的使能条件是 {@code anyShipsReady && anyShipsCanShoot}
 * （原生第 570 行 {@code d.iconButton(x, y, this.TARGET, …, anyShipsReady && anyShipsCanShoot)}；
 * {@code anyShipsCanShoot} 来自第 291 行的 {@code s.canShoot()}）。航母没有武器，这个按钮永远
 * 是灰的，鼠标流玩家根本下不了 T 指令 —— 键盘 T 虽然走的是另一条路（{@code tick} 里的
 * {@code target_ship} 快捷键），但按钮亮不亮直接决定玩家以为「能不能用」。</p>
 *
 * <p>注入方式：重定向 {@code MyDraw.iconButton(int,int,Img,Runnable,boolean)} 的第 13 个调用点
 * （{@code ordinal = 12}，两个已核对过的游戏构建里都是 T 按钮），只在实参图标就是
 * {@code CommandButtonsPanel.TARGET} 时把使能条件补上
 * 「有已就绪、且能接受 T 指令的舰船」（{@link OrderedTarget#anyReadyToOrder}，与原生
 * {@code anyShipsReady} 同样把冷却中的舰船排除在外；「能接受 T 指令」包含武器、飞机指挥台，
 * 以及只有机库的无武装航母）。图标不是 TARGET 时原样转发，
 * 所以即使将来按钮顺序变了、注入落到别的按钮上，也只是本功能失效，不会点亮别的按钮。</p>
 *
 * <p>{@code draw} 里一共 15 个这种 5 参 {@code iconButton} 调用点，本注入只认序号 12；
 * 落点由测试对发行内核字节码断言（第 13 个调用点前一条 {@code getfield} 必须是
 * {@code CommandButtonsPanel.TARGET}）。{@code us} 在重定向处理器里取不到，所以在
 * {@code draw} 开头存进一个 {@code @Unique} 字段。</p>
 */
@Mixin(value = CommandButtonsPanel.class, remap = false)
public abstract class OrderedTargetButtonMixin {
    /** 原版私有 final 字段：T 按钮的图标，用作「这个调用点是不是 T 按钮」的判据。 */
    @Shadow @Final private Img TARGET;
    /** 本帧正在绘制的界面；只在 {@code draw} 开头写入。 */
    @Unique private UniScreen arc$screen;

    @Inject(method = "draw(Lcom/zarkonnen/airships/MyDraw;Lcom/zarkonnen/catengine/util/Pt;Lcom/zarkonnen/catengine/util/ScreenMode;Lcom/zarkonnen/catengine/Hooks;Lcom/zarkonnen/airships/UniScreen;)V",
            at = @At("HEAD"))
    private void arc$rememberScreen(MyDraw d, Pt cursor, ScreenMode sm, Hooks hs, UniScreen us, CallbackInfo ci) {
        this.arc$screen = us;
    }

    @Redirect(method = "draw(Lcom/zarkonnen/airships/MyDraw;Lcom/zarkonnen/catengine/util/Pt;Lcom/zarkonnen/catengine/util/ScreenMode;Lcom/zarkonnen/catengine/Hooks;Lcom/zarkonnen/airships/UniScreen;)V",
              at = @At(value = "INVOKE", ordinal = 12,
                       target = "Lcom/zarkonnen/airships/MyDraw;iconButton(IILcom/zarkonnen/catengine/Img;Ljava/lang/Runnable;Z)V"))
    private void arc$enableTargetButton(MyDraw d, int x, int y, Img img, Runnable action, boolean enabled) {
        d.iconButton(x, y, img, action, enabled || (img == this.TARGET && arc$anyCanOrder()));
    }

    /** 当前选择里是否有人能接受 T 指令；没有界面（尚未绘制过）时一律不加成。 */
    @Unique private boolean arc$anyCanOrder() {
        UniScreen us = this.arc$screen;
        return us != null && OrderedTarget.anyReadyToOrder(us.selectedShip, us.selectedShips);
    }
}
