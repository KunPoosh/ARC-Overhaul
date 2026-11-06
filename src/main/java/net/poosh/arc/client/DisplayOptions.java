/* DisplayOptions.java — 本机显示设置；不进入共享玩法规则，不改变舰船物理或存档。 */
package net.poosh.arc.client;

import com.zarkonnen.airships.EditShipIntent;
import com.zarkonnen.airships.UniScreen;
import com.zarkonnen.catengine.Input;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.config.*;
import net.fabricacs.api.ui.*;
import net.poosh.arc.conquest.StartingOptions;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;

public final class DisplayOptions {
    private static ModConfig config;
    private static ModUi ui;
    private static boolean tracksBehindArmour;
    private DisplayOptions() { }

    public static void initialize(AcbricModContext context) {
        try {
            config = context.config("display", 1, new JSONObject().put("tracksBehindArmour", false), json -> {
                if (!(json.get("tracksBehindArmour") instanceof Boolean))
                    throw new IllegalArgumentException("Expected boolean / 必须为布尔值: tracksBehindArmour");
            });
            refresh();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load ARC display settings / 无法读取 ARC 显示设置", ex);
        }
        ui = context.ui();
        ui.register("display", DisplayOptions::title, DisplayOptions::window);
    }

    private static void refresh() throws IOException {
        tracksBehindArmour = config.load().data().getBoolean("tracksBehindArmour");
    }
    public static boolean tracksBehindArmour() { return tracksBehindArmour; }
    public static String title() { return StartingOptions.text("ARC Overhaul: Display", "ARC 大修：显示设置"); }
    public static UiWindow window() {
        var field = ConfigField.bool("tracksBehindArmour", new ConfigField.Text("Tracks behind armour", "履带置于装甲后方"), ConfigField.Effect.IMMEDIATE)
            .description(new ConfigField.Text("Off: vanilla foreground. On: armour and decorations cover tracks. Applies to all ships on this client, including the editor; legs and physics are unchanged.",
                "关闭：原版前景。开启：装甲和装饰可覆盖履带。立即用于本机所有舰船（含编辑器）；腿部层次和物理不变。"));
        try {
            return SettingsUi.window(title(), new ConfigEditor(config, List.of(field)), applied -> {
                try { refresh(); }
                catch (IOException ex) { throw new IllegalStateException("Cannot read ARC display settings / 无法读取 ARC 显示设置", ex); }
            });
        } catch (IOException ex) { throw new IllegalStateException("Cannot open ARC display settings / 无法打开 ARC 显示设置", ex); }
    }

    /** 编辑器里直接切换显示方式；布局和键鼠输入交给原生工具栏。 */
    public static final class Button extends UniScreen.Button {
        @Override public boolean visible(UniScreen screen) { return screen.intent instanceof EditShipIntent; }
        @Override public String text(UniScreen screen) { return StartingOptions.text("ARC display", "ARC 显示"); }
        @Override public void click(Input input, UniScreen screen) { if (visible(screen)) ui.open(window()); }
    }
}
