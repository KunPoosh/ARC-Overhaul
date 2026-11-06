/* GameSetupMixin.java — 将设置入口加入原生滚动内容底部，编辑交给公共模态组件处理焦点。 */
package net.poosh.arc.mixin;
import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.util.Pt;
import net.poosh.arc.conquest.FleetOptions;
import net.poosh.arc.conquest.StartingOptions;
import net.poosh.arc.ui.UiScaleOptions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = GameSetupScreen.class, remap = false)
public abstract class GameSetupMixin {
    @Shadow private IntRect settingsScrollBarR;
    @Unique private int arc$nativeHeight;
    @Inject(method = "getHeight(Ljava/lang/Object;Lcom/zarkonnen/airships/MyDraw;I)I", at = @At("RETURN"), cancellable = true)
    private void arc$height(Object item, MyDraw draw, int width, CallbackInfoReturnable<Integer> cir) {
        arc$nativeHeight = cir.getReturnValue();
        // 三个入口在滚动内容底部各占一行，尺寸随原版 UI 度量变化。
        cir.setReturnValue(arc$nativeHeight + 3 * (MyDraw.UI_SPACING + MyDraw.BUTTON_H));
    }
    @Inject(method = "draw(Ljava/lang/Object;Lcom/zarkonnen/airships/MyDraw;III)V", at = @At("HEAD"))
    private void arc$entry(Object item, MyDraw draw, int x, int y, int width, CallbackInfo ci) {
        int top = y + arc$nativeHeight + MyDraw.UI_SPACING;
        arc$button(draw, x, top, width, StartingOptions.title(), StartingOptions::open);
        arc$button(draw, x, top + MyDraw.BUTTON_H + MyDraw.UI_SPACING, width, FleetOptions.title(), FleetOptions::open);
        arc$button(draw, x, top + 2 * (MyDraw.BUTTON_H + MyDraw.UI_SPACING), width, UiScaleOptions.title(), UiScaleOptions::open);
    }
    /** 跟随滚动内容及原生裁剪；不可点击滚动框外的隐藏按钮。 */
    @Unique private void arc$button(MyDraw draw, int x, int top, int width, String label, Runnable action) {
        if (!settingsScrollBarR.contains(new Pt(x, top)) || !settingsScrollBarR.contains(new Pt(x, top + MyDraw.BUTTON_H - 1))) return;
        draw.button(x, top, width, label, action, true);
    }
}
