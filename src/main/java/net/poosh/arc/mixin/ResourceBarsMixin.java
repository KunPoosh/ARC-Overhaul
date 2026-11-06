/* ResourceBarsMixin.java — 仅接管资源子区域；保留命令按钮、直控面板和状态提示的原生逻辑。 */
package net.poosh.arc.mixin;
import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.CommandButtonsPanel;
import com.zarkonnen.airships.MyDraw;
import net.poosh.arc.client.ResourceBars;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommandButtonsPanel.class, remap = false)
public abstract class ResourceBarsMixin {
    @Inject(method = "renderShipQuantities(Lcom/zarkonnen/airships/MyDraw;Lcom/zarkonnen/airships/Airship;III)V", at = @At("HEAD"), cancellable = true)
    private static void arc$resourceBars(MyDraw draw, Airship ship, int x, int y, int availableWidth, CallbackInfo ci) {
        ResourceBars.draw(draw, ship, x, y, availableWidth);
        ci.cancel();
    }
}
