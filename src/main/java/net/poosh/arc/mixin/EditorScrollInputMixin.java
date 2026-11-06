/* EditorScrollInputMixin.java — 原生滚轮分支未判空；兼容模态窗口遮蔽后的鼠标坐标，保留底层 tick。 */
package net.poosh.arc.mixin;

import com.zarkonnen.airships.MyDraw;
import com.zarkonnen.airships.UniScreen;
import com.zarkonnen.catengine.util.Pt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = UniScreen.class, remap = false)
public abstract class EditorScrollInputMixin {
    // 1.2.15.3：第 11 次 y 读取在滚轮判断，之前是 Input.scrollAmount / cursor == null。
    // 原生表达式为 (scroll != 0 && cursor == null) || cursor.y > TOP_BAR_H。
    @Redirect(method = "input(Lcom/zarkonnen/catengine/Input;Lcom/zarkonnen/airships/MyDraw$State;Lcom/zarkonnen/catengine/util/Pt;Lcom/zarkonnen/catengine/util/Pt;I)V",
        at = @At(value = "FIELD", target = "Lcom/zarkonnen/catengine/util/Pt;y:D", ordinal = 10))
    private double arc$scrollCursorY(Pt cursor) {
        return cursor == null ? MyDraw.TOP_BAR_H : cursor.y;
    }

    // 无坐标的滚轮仍走原生中心缩放；不能再把它当作顶部工具栏滚动。
    @Redirect(method = "input(Lcom/zarkonnen/catengine/Input;Lcom/zarkonnen/airships/MyDraw$State;Lcom/zarkonnen/catengine/util/Pt;Lcom/zarkonnen/catengine/util/Pt;I)V",
        at = @At(value = "FIELD", target = "Lcom/zarkonnen/catengine/util/Pt;y:D", ordinal = 13))
    private double arc$toolbarCursorY(Pt cursor) {
        return cursor == null ? Double.POSITIVE_INFINITY : cursor.y;
    }
}
