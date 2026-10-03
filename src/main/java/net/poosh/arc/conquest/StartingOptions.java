/* StartingOptions.java — 复用配置草稿和共享规则；完成生成时才设置玩家可用现金。 */
package net.poosh.arc.conquest;

import com.zarkonnen.airships.*;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.config.*;
import net.fabricacs.api.event.AirshipsCampaignEvents;
import net.fabricacs.api.ui.*;
import net.fabricacs.api.util.AcbricLanguage;
import java.io.IOException;
import java.util.List;

public final class StartingOptions {
    private static ModConfig config;
    private static ModUi ui;
    private static AcbricModContext mod;
    private StartingOptions() { }
    public static String text(String en, String zh) { return AcbricLanguage.text(en, zh); }
    /** 只建立配置句柄与界面入口；共享规则由 {@code ArcRules} 在两项设置都就绪后一次性注册。 */
    public static ModConfig initialize(AcbricModContext context) {
        try {
            config = context.config("conquest-start", 1, new StartValues(-1, -1, -1, 0).json(), StartValues::read);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load ARC settings / 无法读取 ARC 配置，未覆盖原文件", ex);
        }
        mod = context;
        ui = context.ui();
        ui.register("conquest-start", StartingOptions::title, StartingOptions::window);
        AirshipsCampaignEvents.CREATED.register(world -> finish((CampaignWorld) world));
        context.logger().info("ARC starting options loaded / ARC 开局设置已加载");
        return config;
    }
    public static String title() { return text("ARC Overhaul: Player starting options", "ARC 大修：玩家开局设置"); }
    public static StartValues forMap(WorldMap map) { return ArcRules.startValues(map); }
    public static void open() { ui.open(window()); }
    public static UiWindow window() {
        var effect = ConfigField.Effect.NEW_CAMPAIGN;
        var fields = List.of(
            ConfigField.integer("cities", new ConfigField.Text("Cities per human empire", "每个人类势力的城市数"), -1, StartValues.MAX_CITIES, effect)
                .description(new ConfigField.Text("-1: vanilla (1). Otherwise 1–4; 0 is invalid.", "-1：原版（1 座）。自定义为 1–4，不能填 0。")),
            ConfigField.integer("towns", new ConfigField.Text("Towns per human empire", "每个人类势力的城镇数"), -1, StartValues.MAX_TOWNS, effect)
                .description(new ConfigField.Text("-1: map default. Otherwise 0–8. AI counts remain vanilla.", "-1：地图默认。自定义为 0–8。AI 数量保持原版。")),
            ConfigField.integer("cash", new ConfigField.Text("Player starting cash", "玩家开局现金"), -1, StartValues.MAX_CASH, effect)
                .description(new ConfigField.Text("-1: vanilla. Otherwise 0–1000000 AFTER starting assets. New campaigns only; in a lobby the host's settings apply to everyone.", "-1：原版。自定义为 0–1000000，在初始资产配置后设置。仅新战役生效；联机时以房主设置为准，其他玩家自动采用。")),
            ConfigField.integer("research", new ConfigField.Text("Player starting research points", "玩家开局研发点"), 0, StartValues.MAX_RESEARCH, effect)
                .description(new ConfigField.Text("0: no points granted (vanilla). Otherwise 0–10000000 banked as unassigned research and poured into the first tech you pick. Scale reference: one tier-0 technology costs about 2,240,000 points. New campaigns only.", "0：不发放，等同原版。自定义为 0–10,000,000，作为未分配研发点存入，在你选定第一个科技时全部注入。量级参考：一项 0 级科技约需 2,240,000 点。仅新战役生效；联机时以房主设置为准。")));
        try { return SettingsUi.window(title(), new ConfigEditor(config, fields), applied -> ArcRules.publish()); }
        catch (IOException ex) { throw new IllegalStateException("Cannot open ARC settings / 无法打开 ARC 设置", ex); }
    }
    /** CREATED 仅在新地图第一次 setupPlayer 后触发；不监听 LOADED/RESTORED。 */
    public static void finish(CampaignWorld world) {
        StartValues values = forMap(world.map);
        GenerationAccess generation = (GenerationAccess) world.map;
        if (values.changesLand()) {
            for (Empire empire : world.map.empires) {
                if (!empire.playerControlled) continue;
                long cities = empire.cities.stream().filter(city -> !city.isTown).count();
                long towns = empire.cities.size() - cities;
                if (cities != values.cityCount() || towns != values.townCount(generation.arc$originalTowns()))
                    throw new IllegalStateException("ARC: Not enough settlement space. Reduce counts or use a larger map. / ARC：可用领地位置不足，请减少数量或增大地图。");
            }
        }
        for (Empire empire : world.map.empires) {
            if (!empire.playerControlled) continue;
            // 现金和研究点在同一次 CREATED 回调、同一个循环里发放，时机完全一致。
            // 区别只在游戏怎么用它们：现金是立即可见的余额，研究点是"未分配池"，
            // 只有在玩家选定研究时才被原生逻辑搬进 researchPoints 并显示在进度条上。
            if (values.cash() >= 0) empire.setMoney(values.cash());
            if (values.grantsResearch()) grantResearch(empire, world.map, values.research());
        }
    }

    /**
     * 原生把 {@code unassignedResearchPoints} 当作「未分配研发池」。选择科技的命令
     * （{@code CampaignWorld} 的 set-research 执行器，字节码 223–312）顺序是：先把旧研究的进度
     * 存进 {@code partialResearchPoints}，再把 {@code researchPoints} 覆盖成该研究在
     * {@code partialResearchPoints} 里的值（新研究通常就是 0），最后才把未分配池整池加进去并清零。
     *
     * <p>所以直接写 {@code researchPoints} 会在玩家第一次选择科技时被原生逻辑覆盖成 0，开局点数
     * 等于白给；写进未分配池才会在选定研究时完整注入。开局没有选中研究时进池；已有研究
     * （特殊纪元等）时直接计入当前研究并结算。</p>
     */
    private static void grantResearch(Empire empire, WorldMap map, int points) {
        if (empire.research == null) {
            empire.unassignedResearchPoints = points;
            if (mod != null) mod.logger().info("ARC: empire " + empire.id + " starting cash " + empire.getMoney()
                + ", banked " + points + " unassigned research points (applied when a research is selected)");
            return;
        }
        empire.researchPoints = points;
        empire.checkResearchComplete(map);
        if (mod != null) mod.logger().info("ARC: empire " + empire.id + " starting cash " + empire.getMoney()
            + ", " + points + " research points added to the active research");
    }
}
