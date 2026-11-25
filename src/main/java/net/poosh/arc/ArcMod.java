/* ArcMod.java — ARC 独立入口：配置、规则与 UI 复用 Acbric；玩法适配位于 conquest 包。 */
package net.poosh.arc;

import net.fabricacs.api.AcbricInitializer;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.config.ModConfig;
import net.poosh.arc.conquest.ArcRules;
import net.poosh.arc.conquest.FleetOptions;
import net.poosh.arc.conquest.StartingOptions;
import net.poosh.arc.ui.UiScaleOptions;

public final class ArcMod implements AcbricInitializer {
    public static final String MOD_ID = "arc_overhaul";

    /** 兼容 Acbric 的旧入口签名；正常初始化由带上下文入口完成。 */
    @Override public void onInitializeAcbric() { }

    @Override public void onInitializeAcbric(AcbricModContext context) {
        ModConfig starting = StartingOptions.initialize(context);
        ModConfig fleets = FleetOptions.initialize(context);
        // 框架的共享规则按 MOD ID 只能注册一次，开局设置与 AI 舰队规则合并成一份。
        ArcRules.initialize(context, starting, fleets);
        UiScaleOptions.initialize(context);
    }
}
