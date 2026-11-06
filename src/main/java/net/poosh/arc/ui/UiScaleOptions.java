/* UiScaleOptions.java — ARC 本机界面偏好；复用 Acbric 百分比接口与事务配置，不进入战役共享规则。 */
package net.poosh.arc.ui;

import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.config.*;
import net.fabricacs.api.ui.*;
import net.fabricacs.api.util.AcbricLanguage;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;

public final class UiScaleOptions {
    private static ModConfig config;
    private static ModUi ui;
    private UiScaleOptions() { }
    private static String text(String en, String zh) { return AcbricLanguage.text(en, zh); }
    public static String title() { return text("ARC Overhaul: UI scale", "ARC 大修：界面大小"); }

    public static void initialize(AcbricModContext context) {
        try {
            config = context.config("ui-scale", 1, new JSONObject().put("enabled", false).put("percent", 100), UiScaleOptions::validate);
            apply(config.load().data());
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load ARC UI scale / 无法读取 ARC 界面大小配置，未覆盖原文件", ex);
        }
        ui = context.ui();
        ui.register("ui-scale", UiScaleOptions::title, UiScaleOptions::window);
    }

    private static void validate(JSONObject data) {
        if (!(data.opt("enabled") instanceof Boolean)) throw new IllegalArgumentException("enabled must be boolean / 启用值必须是布尔值");
        Object value = data.opt("percent");
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() != n.intValue()
                || n.intValue() < UiScale.MIN_PERCENT || n.intValue() > UiScale.MAX_PERCENT)
            throw new IllegalArgumentException("UI scale must be an integer from 50 to 200 / 界面大小须为 50 至 200 的整数");
    }

    private static void apply(JSONObject data) {
        validate(data);
        if (data.getBoolean("enabled")) UiScale.setPercent(data.getInt("percent")); else UiScale.useVanilla();
    }

    public static void open() { ui.open(window()); }

    /** 原生自动/小/中/大按钮仍可直接恢复原版；先保存成功，才改变运行期状态。 */
    public static void useVanilla() throws IOException {
        ConfigSnapshot snapshot = config.read();
        if (snapshot.data().getBoolean("enabled")) config.save(snapshot, snapshot.data().put("enabled", false));
        UiScale.useVanilla();
    }

    public static String nativeLabel() {
        return text("UI scale", "界面大小") + (UiScale.percent() == 0 ? "..." : " (" + UiScale.percent() + "%)...");
    }

    public static UiWindow window() {
        var fields = List.of(
            ConfigField.bool("enabled", new ConfigField.Text("Use custom UI scale", "启用自定义界面大小"), ConfigField.Effect.IMMEDIATE)
                .description(new ConfigField.Text("Off: follow the game's Auto / Small / Medium / Large setting. Local display only; other players keep their own preferences.",
                    "关闭时沿用游戏的自动／小／中／大设置。仅影响本机显示，其他玩家保留各自偏好。")),
            ConfigField.integer("percent", new ConfigField.Text("UI size (%)", "界面大小（%）"), UiScale.MIN_PERCENT, UiScale.MAX_PERCENT, ConfigField.Effect.IMMEDIATE)
                .description(new ConfigField.Text("50-200%, in 1% steps. 100% is vanilla Small. Apply to change buttons, text and panels; the camera and resolution stay unchanged. If space is tight, use a lower value. Defaults then Apply restores vanilla.",
                    "50 至 200%，精确到 1%。100% 为原版小号界面。应用后调整按钮、文字与面板；镜头和分辨率保持原值。空间不足时请调低；恢复默认值后点击应用可恢复原版。")));
        try {
            return SettingsUi.window(title(), new ConfigEditor(config, fields), saved -> apply(config.read().data()));
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot open ARC UI scale / 无法打开 ARC 界面大小设置", ex);
        }
    }
}
