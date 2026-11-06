/* TrackLayersMixin.java — 在装甲之前增加履带通道；绘制实现继续使用原版动画、着色器和材质。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.ShipLayers;
import com.zarkonnen.airships.UniScreen;
import net.poosh.arc.client.TrackPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShipLayers.class, remap = false)
public abstract class TrackLayersMixin {
    @Inject(method = "<clinit>()V", at = @At("RETURN"))
    private static void arc$addTrackPass(CallbackInfo ci) {
        for (int i = 0; i < ShipLayers.ALL.size(); i++) {
            UniScreen.ShipLayer layer = ShipLayers.ALL.get(i);
            if (!(layer instanceof ShipLayers.Armour)) continue;
            ShipLayers.TracksAndLegs tracks = new ShipLayers.TracksAndLegs(false);
            ((TrackPass) (Object) tracks).arc$tracksOnly();
            ShipLayers.ALL.add(i, tracks);
            return;
        }
        throw new IllegalStateException("ARC: armour layer not found / ARC：找不到装甲绘制层");
    }
}
