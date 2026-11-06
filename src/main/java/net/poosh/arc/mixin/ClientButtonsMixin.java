/* ClientButtonsMixin.java — 把观战研究与编辑器显示设置加入原生工具栏，复用布局、快捷键和焦点。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.AirshipGame;
import com.zarkonnen.airships.UniScreen;
import net.poosh.arc.client.DisplayOptions;
import net.poosh.arc.client.SpectatorResearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UniScreen.class, remap = false)
public abstract class ClientButtonsMixin {
    @Inject(method = "<init>(Lcom/zarkonnen/airships/AirshipGame;Lcom/zarkonnen/airships/UniScreen$Intent;)V", at = @At("RETURN"))
    private void arc$clientButtons(AirshipGame game, UniScreen.Intent intent, CallbackInfo ci) {
        UniScreen screen = (UniScreen) (Object) this;
        screen.buttons.add(new SpectatorResearch());
        screen.buttons.add(new DisplayOptions.Button());
    }
}
