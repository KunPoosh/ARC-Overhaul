/* UiScaleSettingsMixin.java — 在原版界面大小档位下加入百分比入口，并让原生档位按钮退出自定义模式。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.*;
import net.fabricacs.api.ui.UiScale;
import net.poosh.arc.ui.UiScaleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = SettingsScreen.class, remap = false)
public abstract class UiScaleSettingsMixin {
    @Shadow @Final public AirshipGame g;

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lcom/zarkonnen/airships/ResChooserWidget;render(IIIILcom/zarkonnen/airships/MyDraw;Lcom/zarkonnen/catengine/util/ScreenMode;Lcom/zarkonnen/catengine/Hooks;Lcom/zarkonnen/catengine/util/Pt;)V"), remap = false)
    private void arc$scaleEntry(Args args) {
        int x = args.get(0), y = args.get(1), width = args.get(2), height = args.get(3);
        int row = MyDraw.BUTTON_H + MyDraw.BUTTON_SPACING;
        // 极小窗口不再挤压分辨率列表；MOD 详情和开局设置仍保留同一个入口。
        if (height < row * 2) return;
        MyDraw draw = args.get(4);
        draw.button(x, y, width, UiScaleOptions.nativeLabel(), UiScaleOptions::open, true);
        args.set(1, y + row);
        args.set(3, height - row);
    }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lcom/zarkonnen/airships/MyDraw;toggle(IIILjava/lang/String;Ljava/lang/String;Lcom/zarkonnen/airships/InputRunnable;ZZ)V", ordinal = 1), remap = false)
    private void arc$autoChoice(Args args) { arc$vanillaChoice(args); }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lcom/zarkonnen/airships/MyDraw;toggle(IIILjava/lang/String;Ljava/lang/String;Lcom/zarkonnen/airships/InputRunnable;ZZ)V", ordinal = 2), remap = false)
    private void arc$presetChoice(Args args) { arc$vanillaChoice(args); }

    @Unique private void arc$vanillaChoice(Args args) {
        InputRunnable nativeAction = args.get(5);
        if (UiScale.percent() != 0) args.set(6, false);
        args.set(5, (InputRunnable) input -> {
            try { UiScaleOptions.useVanilla(); }
            catch (java.io.IOException ex) {
                g.showError("Cannot save UI scale / 无法保存界面大小：" + ex.getMessage());
                return;
            }
            nativeAction.run(input);
        });
    }
}
