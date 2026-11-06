/* TrackRenderingMixin.java — 按本机选项在前/后通道分流轮履；腿、碰撞和模块列表保持原样。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.Leg;
import com.zarkonnen.airships.Module;
import com.zarkonnen.airships.ShipLayers;
import com.zarkonnen.airships.Wheel;
import net.poosh.arc.client.DisplayOptions;
import net.poosh.arc.client.TrackPass;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;

@Mixin(value = ShipLayers.TracksAndLegs.class, remap = false)
public abstract class TrackRenderingMixin implements TrackPass {
    @Unique private boolean arc$onlyTracks;
    @Unique private static final ArrayList<Wheel> arc$noWheels = new ArrayList<>();
    @Unique private static final ArrayList<Leg> arc$noLegs = new ArrayList<>();
    @Override public void arc$tracksOnly() { arc$onlyTracks = true; }

    @Inject(method = "doDraw(D)Z", at = @At("HEAD"), cancellable = true)
    private void arc$skipUnusedPass(double scale, CallbackInfoReturnable<Boolean> cir) {
        if (arc$onlyTracks && !DisplayOptions.tracksBehindArmour()) cir.setReturnValue(false);
    }
    @Redirect(method = "draw(Lcom/zarkonnen/airships/Airship;Lcom/zarkonnen/airships/WeatherEffect;ILcom/zarkonnen/airships/MyDraw;ZDDDIZZLcom/zarkonnen/airships/CoatOfArms;Lcom/zarkonnen/airships/CoatOfArms;Z[Lorg/newdawn/slick/Image;FLorg/newdawn/slick/Color;FLcom/zarkonnen/catengine/util/Clr;Lcom/zarkonnen/airships/SpritesheetBundle;Ljava/util/HashSet;Lcom/zarkonnen/airships/SpritesheetBundle;Ljava/util/HashSet;)V", at = @At(value = "FIELD", target = "Lcom/zarkonnen/airships/Module;wheels:Ljava/util/ArrayList;"))
    private ArrayList<Wheel> arc$trackWheels(Module module) {
        return arc$onlyTracks == DisplayOptions.tracksBehindArmour() ? module.wheels : arc$noWheels;
    }
    @Redirect(method = "draw(Lcom/zarkonnen/airships/Airship;Lcom/zarkonnen/airships/WeatherEffect;ILcom/zarkonnen/airships/MyDraw;ZDDDIZZLcom/zarkonnen/airships/CoatOfArms;Lcom/zarkonnen/airships/CoatOfArms;Z[Lorg/newdawn/slick/Image;FLorg/newdawn/slick/Color;FLcom/zarkonnen/catengine/util/Clr;Lcom/zarkonnen/airships/SpritesheetBundle;Ljava/util/HashSet;Lcom/zarkonnen/airships/SpritesheetBundle;Ljava/util/HashSet;)V", at = @At(value = "FIELD", target = "Lcom/zarkonnen/airships/Module;legs:Ljava/util/ArrayList;"))
    private ArrayList<Leg> arc$trackLegs(Module module) { return arc$onlyTracks ? arc$noLegs : module.legs; }
}
