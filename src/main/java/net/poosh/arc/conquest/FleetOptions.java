/* FleetOptions.java — AI 舰队设置页与生成期选择：三态规则 + 出场国家数；默认设置完全走原版随机。 */
package net.poosh.arc.conquest;

import com.zarkonnen.airships.*;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.config.*;
import net.fabricacs.api.ui.*;
import net.fabricacs.api.util.AcbricLanguage;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;

/**
 * 「AI 舰队」设置：列出当前游戏实际加载的每一支 AI 舰队（{@code ConstructionStrategy}），
 * 让玩家逐支选择「允许 / 强制启用 / 强制禁用」，并在强制启用时指定出场国家数。
 *
 * <p>选择发生在原生生成阶段把舰队交给 {@code Empire} 之前（见 {@code FleetAssignmentMixin}），
 * 因为 {@code Empire.constructionStrategy} 是 final 字段，事后无法改写。规则通过共享规则随战役固化，
 * 所以生成期读到的一定是本局真正生效的那一份。</p>
 */
public final class FleetOptions {
    static final String CONFIG_NAME = "conquest-fleets";

    private static ModConfig config;
    private static ModUi ui;
    private static AcbricModContext mod;
    /** 本次生成已用掉的名额；生成是按势力顺序单线程推进的，重置发生在第一个势力上。 */
    private static final LinkedHashMap<String, Integer> QUOTA = new LinkedHashMap<>();
    private static WorldMap quotaMap;
    /** 已经就同一张地图报过一次错的标记，避免逐个势力重复刷日志。 */
    private static WorldMap warnedMap;

    private FleetOptions() { }

    public static String text(String en, String zh) { return AcbricLanguage.text(en, zh); }

    public static ModConfig initialize(AcbricModContext context) {
        try {
            config = context.config(CONFIG_NAME, 1, new JSONObject().put(FleetPlan.KEY, new JSONObject()), FleetPlan::validate);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load ARC fleet settings / 无法读取 ARC 舰队配置，未覆盖原文件", ex);
        }
        mod = context;
        ui = context.ui();
        ui.register("conquest-fleets", FleetOptions::title, FleetOptions::window);
        context.logger().info("ARC AI fleet options loaded / ARC AI 舰队设置已加载");
        return config;
    }

    public static String title() { return text("ARC Overhaul: AI fleets", "ARC 大修：AI 舰队"); }

    public static void open() { ui.open(window()); }

    /** 当前游戏真正加载的舰队，按显示名排序；名单随启用的 MOD 变化，因此每次开窗都重新取。 */
    public static List<ConstructionStrategy> loadedFleets() {
        List<ConstructionStrategy> result = new ArrayList<>();
        ArrayList<ConstructionStrategy> all;
        try { all = Loadable.all(ConstructionStrategy.class); }
        catch (RuntimeException ex) { all = null; }
        if (all != null) for (ConstructionStrategy fleet : all)
            if (fleet != null && fleet.name != null && !fleet.name.isBlank()) result.add(fleet);
        result.sort(Comparator.comparing(FleetOptions::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(fleet -> fleet.name));
        return result;
    }

    public static String displayName(ConstructionStrategy fleet) {
        String shown = fleet.displayName;
        return shown == null || shown.isBlank() ? fleet.name : shown;
    }

    /**
     * 悬浮提示：内部名 + 来源。显示名可能和加载名对不上，而「哪些是游戏自带的、哪些来自舰队包」
     * 正是挑舰队时要看的信息，来源就是 mods 目录下的文件夹名。
     */
    public static String sourceTag(ConstructionStrategy fleet) {
        Mod source = fleet.sourceMod;
        String origin = source == null || source.dir == null
            ? text("base game", "游戏自带") : source.dir.getName();
        return fleet.name + " · " + origin;
    }

    /**
     * 生成期选择。
     *
     * <p>顺序：强制启用的名额优先，用完后再从「允许」的候选里随机挑一个（用地图自己的随机数，
     * 保持同种子可复现）；玩家自己的势力不参与替换，玩家手动造船，舰队策略对它没有意义。</p>
     *
     * <p>任何一步出错（规则缺失、舰队被卸载、候选被全部禁掉）都原样返回原版选择：设置出问题
     * 不能把世界生成变成失败。</p>
     */
    public static ConstructionStrategy choose(List<ConstructionStrategy> pool, ConstructionStrategy vanilla, int empireIndex, WorldMap map) {
        try {
            FleetPlan plan = ArcRules.fleetPlan(map);
            if (plan.isVanilla()) return vanilla;
            if (quotaMap != map || empireIndex == 0) {
                quotaMap = map;
                QUOTA.clear();
                QUOTA.putAll(plan.forced());
                warnedMap = null;
            }
            if (isPlayerEmpire(map, empireIndex)) return vanilla;
            for (Map.Entry<String, Integer> entry : QUOTA.entrySet()) {
                if (entry.getValue() <= 0) continue;
                // ofName 在找不到时抛 NotFoundException，所以先用 hasOfName 判断：MOD 被卸载后
                // 配置里仍留着它的名字，这时只把名额清零，不能中断生成。
                if (!Loadable.hasOfName(ConstructionStrategy.class, entry.getKey())) { entry.setValue(0); continue; }
                ConstructionStrategy forced = ConstructionStrategy.ofName(entry.getKey());
                entry.setValue(entry.getValue() - 1);
                if (mod != null) mod.logger().info("ARC: AI fleet " + forced.name + " forced for empire " + empireIndex
                    + " (" + entry.getValue() + " countries left) / 强制启用");
                return forced;
            }
            List<ConstructionStrategy> allowed = new ArrayList<>();
            for (ConstructionStrategy candidate : pool)
                if (plan.mode(candidate.name) == FleetPlan.Mode.ALLOW) allowed.add(candidate);
            if (allowed.isEmpty()) {
                warn(map, "every AI fleet available here is banned or reserved; keeping the vanilla pick / 本图可用的 AI 舰队都被禁用或占用，保留原版选择");
                return vanilla;
            }
            ConstructionStrategy picked = allowed.get(map.r.nextInt(allowed.size()));
            // 只有真的换掉了原版结果才记日志，否则一次生成会刷满整段清单。
            if (mod != null && picked != vanilla)
                mod.logger().info("ARC: AI fleet " + picked.name + " chosen for empire " + empireIndex
                    + " (native pick was " + vanilla.name + ") / 随机选择");
            return picked;
        } catch (RuntimeException ex) {
            warn(map, "AI fleet settings ignored for this map: " + ex + " / 本次地图忽略 AI 舰队设置");
            return vanilla;
        }
    }

    /** 与原版同样的判断：遍历设置信息里所有玩家，谁声明了本势力索引，这个势力就归玩家。 */
    private static boolean isPlayerEmpire(WorldMap map, int empireIndex) {
        List<StrategicSetupInfo> infos = map.setupInfos;
        if (infos == null) return false;
        for (StrategicSetupInfo info : infos) {
            if (info == null || info.strategicPlayerInfos == null) continue;
            for (StrategicPlayerInfo player : info.strategicPlayerInfos)
                if (player != null && player.claimedEmpireIndex == empireIndex) return true;
        }
        return false;
    }

    private static void warn(WorldMap map, String message) {
        if (mod == null || warnedMap == map) return;
        warnedMap = map;
        mod.logger().warn("ARC: " + message);
    }

    // ---------------------------------------------------------------- 界面

    /**
     * 一页显示多少行。
     *
     * <p>大型舰队包加载上百支 AI 舰队很常见，而框架的组件树有规模上限、每帧还会对整棵树做测量：
     * 一次建出全部行既可能触发上限，也会明显拖慢设置页。分页把节点数固定在几百以内，并配合
     * 搜索框让玩家能直接找到某一支舰队（游戏自带的搜索框打不出白名单外的字符，这里不受影响）。</p>
     */
    private static final int PAGE_SIZE = 40;

    private static final List<Ui.Choice> MODES = List.of(
        new Ui.Choice(FleetPlan.Mode.ALLOW.wire(), () -> text("Allow (random)", "允许（随机出现）")),
        new Ui.Choice(FleetPlan.Mode.FORCE.wire(), () -> text("Force enable", "强制启用")),
        new Ui.Choice(FleetPlan.Mode.BAN.wire(), () -> text("Force disable", "强制禁用")));

    /**
     * 出场国家数单元格。
     *
     * <p>没有直接用 {@code Ui.integerField}：它自带的错误行会在文本框被清空时立刻报“无效”，而清空正是
     * 玩家换数字的第一步。这里换成受控文本框 + 自己的提示行，空值不报错，只有真的输入了非法内容才提示。</p>
     *
     * <p>顶部留一段空白把输入框压到和同行按钮差不多的位置：原生行是按整格（输入框＋提示行）垂直居中的，
     * 输入框会贴在格子上沿，看起来比按钮高。</p>
     */
    private static UiNode arc$countCell(Editor editor, String name) {
        return Ui.column(2,
                Ui.space(8),
                Ui.textField(() -> editor.countText(name), 3, value -> editor.setCount(name, value)),
                Ui.label(() -> editor.countError(name)))
            .enabled(() -> editor.mode(name) == FleetPlan.Mode.FORCE)
            .width(120);
    }

    public static UiWindow window() {
        Editor editor;
        try { editor = new Editor(); }
        catch (RuntimeException ex) {
            // 配置文件损坏时给出可关闭的说明窗口，而不是让设置入口抛异常。
            System.err.println("[ARC fleet settings] " + ex);
            return new UiWindow(title(), 560, 320, true, Ui.column(10,
                Ui.label(() -> text("Cannot open AI fleet settings. Check game/config/arc_overhaul/conquest-fleets.json.",
                    "无法打开 AI 舰队设置，请检查 game/config/arc_overhaul/conquest-fleets.json。")),
                Ui.button(() -> text("Close", "关闭"), UiWindowHandle::close)));
        }
        return build(editor);
    }

    /** 换页或改过滤条件时重建窗口：复用同一个草稿会话，未应用的修改不会丢。 */
    private static void show(Editor editor, UiWindowHandle handle) {
        editor.rebuilding = true;
        handle.close();
        ui.open(build(editor));
    }

    private static UiWindow build(Editor editor) {
        List<ConstructionStrategy> fleets = loadedFleets();
        List<ConstructionStrategy> shown = editor.visible(fleets);
        int pages = Math.max(1, (shown.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(editor.page(), pages - 1);
        editor.setPage(page);
        int from = page * PAGE_SIZE, to = Math.min(shown.size(), from + PAGE_SIZE);

        List<UiNode> rows = new ArrayList<>();
        for (int i = from; i < to; i++) {
            ConstructionStrategy fleet = shown.get(i);
            String name = fleet.name;
            String label = displayName(fleet);
            UiNode head = Ui.label(label).width(360).tooltip(sourceTag(fleet) + editor.tag(fleet));
            rows.add(Ui.row(8, Ui.Align.CENTER,
                head,
                Ui.choice(() -> editor.mode(name).wire(), MODES, value -> editor.setMode(name, FleetPlan.Mode.of(value))).width(190),
                arc$countCell(editor, name)));
        }
        FleetSources.Report report = editor.report();
        UiNode list;
        if (fleets.isEmpty())
            list = Ui.label(() -> text("No AI fleet is loaded. Enable an AI fleet MOD and reopen this window.", "当前没有加载任何 AI 舰队。请启用 AI 舰队 MOD 后重新打开本窗口。"));
        else if (shown.isEmpty())
            list = Ui.label(() -> text("No AI fleet matches the search text.", "没有匹配搜索内容的 AI 舰队。"));
        else
            list = Ui.scroll(430, Ui.column(6, rows.toArray(UiNode[]::new)));

        List<UiNode> footer = new ArrayList<>();
        footer.add(Ui.label(() -> editor.isDirty()
            ? text("Unapplied changes", "有未应用的修改")
            : editor.status));
        footer.add(Ui.row(8,
            Ui.button(() -> text("Apply", "应用"), editor::apply).enabled(editor::isValid),
            Ui.button(() -> text("Cancel", "取消"), UiWindowHandle::requestClose)));
        footer.add(Ui.row(8,
            Ui.button(() -> text("Force disable all", "全部强制禁用"), handle -> editor.setAll(FleetPlan.Mode.BAN)),
            Ui.button(() -> text("Force enable all", "全部强制启用"), handle -> editor.setAll(FleetPlan.Mode.FORCE))));
        footer.add(Ui.row(8,
            Ui.button(() -> text("Reset to vanilla", "恢复原版默认"), handle -> editor.restoreDefaults()),
            Ui.button(() -> text("Reload file", "重新读取文件"), handle -> handle.confirm(
                text("Reload fleet settings", "重新读取舰队设置"),
                text("Discard this draft and read the file again?", "丢弃草稿并重新读取文件？"),
                text("Reload", "重新读取"), text("Cancel", "取消"), editor::reload))));
        footer.add(Ui.row(8,
            Ui.button(() -> text("Previous page", "上一页"), handle -> { editor.setPage(page - 1); show(editor, handle); }).enabled(() -> page > 0),
            Ui.button(() -> text("Next page", "下一页"), handle -> { editor.setPage(page + 1); show(editor, handle); }).enabled(() -> page + 1 < pages),
            Ui.label(() -> editor.pageSummary(shown.size(), fleets.size(), page, pages))));

        List<UiNode> body = new ArrayList<>();
        body.add(Ui.label(() -> text(
            "Each row is one AI fleet loaded by the game. Allow: may be picked at random (vanilla). "
                + "Force enable: reserved and handed to exactly the given number of non-player countries. "
                + "Force disable: never used. The country count applies to AI countries only; your own country keeps the vanilla fleet. "
                + "You can clear the country count and type a new one; an empty box is never saved as 0, it falls back to the last number you had.",
            "每一行是游戏当前加载的一支 AI 舰队。允许：可能被随机选中（等同原版）。"
                + "强制启用：从随机池里拿掉，改为强制分配给指定数量的非玩家国家。"
                + "强制禁用：永不出现。出场国家数只统计 AI 国家，你自己的国家仍按原版处理。"
                + "出场国家数可以先清空再重新输入；留空不会被保存成 0，而是沿用上一次的有效数字。")));
        body.add(Ui.row(8,
            Ui.label(() -> text("Search", "搜索")).width(60),
            Ui.textField(() -> editor.filterText(), 64, editor::setFilter).width(260)
                .onSubmit(handle -> show(editor, handle)),
            Ui.button(() -> text("Find", "查找"), handle -> show(editor, handle)),
            Ui.button(() -> text("Clear", "清除"), handle -> { editor.clearFilter(); show(editor, handle); })));
        body.add(Ui.label(() -> editor.pageSummary(shown.size(), fleets.size(), page, pages)));
        for (String message : report.messages()) body.add(Ui.label(message));
        body.add(list);
        body.add(Ui.label(() -> editor.status));

        return new UiWindow(title(), 820, 660, true, Ui.column(8, body.toArray(UiNode[]::new)),
                reason -> { if (editor.rebuilding) editor.rebuilding = false; else editor.cancel(); })
            .withFooter(Ui.column(8, footer.toArray(UiNode[]::new)))
            .onCloseRequest(handle -> {
                if (!editor.isDirty()) handle.close();
                else handle.confirm(text("Discard changes?", "丢弃修改？"),
                    text("Close AI fleet settings and discard unapplied changes? Saved settings stay unchanged.",
                        "关闭 AI 舰队设置并丢弃未应用的修改？已经保存的设置不受影响。"),
                    text("Discard", "丢弃修改"), text("Keep editing", "继续编辑"), handle::close);
            });
    }

    /** 草稿会话：与框架的 ConfigEditor 同样的语义（草稿隔离、显式提交、冲突检测），
     *  但键名是舰队名，不符合 ConfigField 的键名规则，所以这里自带一份。 */
    private static final class Editor {
        private final Map<String, FleetPlan.Mode> modes = new LinkedHashMap<>();
        /** 文本框里的原始内容，允许是空串——玩家必须能先删干净再输入新数字。 */
        private final Map<String, String> counts = new LinkedHashMap<>();
        /** 每支舰队上一次有效的出场国家数：文本框被清空或输入非法时用它兜底，绝不写 0 或空值进配置。 */
        private final Map<String, Integer> fallback = new LinkedHashMap<>();
        /** 配置里存在、但本次没有加载出来的舰队设置；保留它们，MOD 临时关闭不会丢设置。 */
        private final Map<String, FleetPlan.Entry> preserved = new LinkedHashMap<>();
        private final List<String> known = new ArrayList<>();
        private ConfigSnapshot baseline;
        private String status;
        /** 搜索文本与页码：换页/搜索会重建窗口，但草稿留在同一个 Editor 里。 */
        private String filter = "";
        private int page;
        /** 重建窗口（换页、搜索、清除）时不还原草稿；玩家关窗仍走 cancel。 */
        boolean rebuilding;
        /** 舰队来源问题的只读扫描结果；开窗时算一次，换页不重复读盘。 */
        private FleetSources.Report report;

        FleetSources.Report report() {
            if (report == null) report = FleetSources.scan();
            return report;
        }

        String filterText() { return filter; }

        void setFilter(String value) {
            filter = value == null ? "" : value.trim();
            page = 0;
        }

        void clearFilter() { filter = ""; page = 0; }

        int page() { return page; }

        void setPage(int value) { page = Math.max(0, value); }

        String pageSummary(int shown, int total, int page, int pages) {
            return text("Showing " + shown + " of " + total + " AI fleets — page " + (page + 1) + "/" + pages,
                "共 " + total + " 支 AI 舰队，当前显示 " + shown + " 支 — 第 " + (page + 1) + "/" + pages + " 页");
        }

        /** 要显示的行：按显示名、ID 或来源 MOD 过滤；搜索框不受游戏自带输入白名单限制。 */
        List<ConstructionStrategy> visible(List<ConstructionStrategy> fleets) {
            if (filter.isEmpty()) return fleets;
            String needle = filter.toLowerCase(Locale.ROOT);
            List<ConstructionStrategy> result = new ArrayList<>();
            for (ConstructionStrategy fleet : fleets)
                if (displayName(fleet).toLowerCase(Locale.ROOT).contains(needle)
                        || fleet.name.toLowerCase(Locale.ROOT).contains(needle)
                        || sourceTag(fleet).toLowerCase(Locale.ROOT).contains(needle)) result.add(fleet);
            return result;
        }

        /** 行内提示：ID 冲突或名称打不出来时补一句，玩家才知道为什么某些舰队“找不到”。 */
        String tag(ConstructionStrategy fleet) {
            FleetSources.Report scanned = report();
            if (scanned.duplicateIds().contains(fleet.name))
                return text(" · duplicate ID: only the last loaded fleet with this ID is used",
                    " · ID 重复：只有最后加载的同 ID 舰队生效");
            if (scanned.untypeableIds().contains(fleet.name))
                return text(" · the game's own search box cannot type this name", " · 游戏自带搜索框打不出这个名字");
            return "";
        }

        Editor() {
            try { accept(config.load()); }
            catch (IOException ex) {
                throw new IllegalStateException("Cannot read ARC fleet settings / 无法读取 ARC 舰队配置", ex);
            }
        }

        private void accept(ConfigSnapshot snapshot) {
            baseline = snapshot;
            FleetPlan plan = FleetPlan.read(snapshot.data());
            modes.clear();
            counts.clear();
            fallback.clear();
            preserved.clear();
            known.clear();
            for (ConstructionStrategy fleet : loadedFleets()) {
                known.add(fleet.name);
                FleetPlan.Entry entry = plan.entry(fleet.name);
                int number = entry.mode() == FleetPlan.Mode.FORCE ? entry.count() : 1;
                modes.put(fleet.name, entry.mode());
                counts.put(fleet.name, Integer.toString(number));
                fallback.put(fleet.name, number);
            }
            for (Map.Entry<String, FleetPlan.Entry> entry : plan.entries().entrySet())
                if (!modes.containsKey(entry.getKey())) preserved.put(entry.getKey(), entry.getValue());
            status = text("New campaigns only; in a lobby the host's settings apply to everyone automatically.",
                "仅新战役生效；联机时以房主设置为准，其他玩家自动采用，不需要各自手动改成一样。");
        }

        FleetPlan.Mode mode(String name) { return modes.getOrDefault(name, FleetPlan.Mode.ALLOW); }

        void setMode(String name, FleetPlan.Mode value) {
            keepAllCounts();
            modes.put(name, value);
        }

        /**
         * 批量设置：全部强制禁用 / 全部强制启用。强制启用时沿用每支舰队当前（或上一个有效）的
         * 出场国家数，不会把别人刚填的数字抹掉。
         */
        void setAll(FleetPlan.Mode value) {
            if (!isValid()) return;
            keepAllCounts();
            for (String name : known) {
                modes.put(name, value);
                if (value == FleetPlan.Mode.FORCE) counts.put(name, Integer.toString(count(name)));
            }
            status = value == FleetPlan.Mode.FORCE
                ? text("Every loaded AI fleet is now force enabled. Apply to save.",
                    "所有已加载的 AI 舰队都改成强制启用。点击应用才会保存。")
                : text("Every loaded AI fleet is now force disabled. Apply to save.",
                    "所有已加载的 AI 舰队都改成强制禁用。点击应用才会保存。");
        }

        /** 实际生效的数字：文本框里是合法数字就用它，否则沿用上一次的有效值。 */
        int count(String name) {
            int value = parseCount(counts.get(name));
            return value > 0 ? value : fallback.getOrDefault(name, 1);
        }

        /** 文本框显示的内容，原样返回：空就是空，不让它自己弹回上一个数字，否则没法删掉重输。 */
        String countText(String name) { return counts.getOrDefault(name, ""); }

        void setCount(String name, String value) {
            keepOtherCounts(name);
            counts.put(name, value == null ? "" : value);
            int parsed = parseCount(counts.get(name));
            if (parsed > 0) fallback.put(name, parsed);
        }

        /** 清空的文本框不报错：那是玩家换数字的第一步。只有真的输入了非法内容才提示。 */
        String countError(String name) {
            String raw = counts.get(name);
            if (raw == null || raw.isBlank()) return "";
            return parseCount(raw) > 0 ? "" : text("Enter 1 to " + FleetPlan.MAX_COUNTRIES + ".",
                "请输入 1–" + FleetPlan.MAX_COUNTRIES + "。");
        }

        /** 玩家在别的地方动手了，就把所有还空着的数字框复原成删除前的数字。 */
        private void keepOtherCounts(String editing) {
            for (String name : known) {
                if (name.equals(editing)) continue;
                String raw = counts.get(name);
                if (raw == null || raw.isBlank()) counts.put(name, Integer.toString(count(name)));
            }
        }

        private void keepAllCounts() { keepOtherCounts(null); }

        private static int parseCount(String raw) {
            try {
                int value = Integer.parseInt(raw == null ? "" : raw.trim());
                return value >= 1 && value <= FleetPlan.MAX_COUNTRIES ? value : -1;
            } catch (RuntimeException ex) { return -1; }
        }

        /** 空的文本框不算无效：它按「保持上一个有效数字」处理，所以不会挡住保存。 */
        boolean isValid() {
            for (String name : known) {
                if (mode(name) != FleetPlan.Mode.FORCE) continue;
                String raw = counts.get(name);
                if (raw == null || raw.isBlank()) continue;
                if (parseCount(raw) < 0) return false;
            }
            return true;
        }

        boolean isDirty() {
            try { return !draft().equals(FleetPlan.read(baseline.data())); }
            catch (RuntimeException ex) { return true; }
        }

        private FleetPlan draft() {
            Map<String, FleetPlan.Entry> entries = new LinkedHashMap<>(preserved);
            for (String name : known) {
                FleetPlan.Mode mode = mode(name);
                entries.put(name, new FleetPlan.Entry(mode, mode == FleetPlan.Mode.FORCE ? count(name) : 0));
            }
            return FleetPlan.of(entries);
        }

        void apply(UiWindowHandle handle) {
            String failure = save();
            if (failure != null) handle.message(text("Could not save", "无法保存"), failure);
        }

        /** 提交草稿：返回 {@code null} 表示成功，否则是给玩家看的失败说明。集成检查直接调用这里。 */
        String save() {
            // 先复原还空着的数字框，玩家不会带着一个空框保存成功却在界面上看不到数字。
            keepAllCounts();
            if (!isValid()) return text("Every force-enabled fleet needs 1 to " + FleetPlan.MAX_COUNTRIES + " countries.",
                "每支强制启用的舰队都需要 1–" + FleetPlan.MAX_COUNTRIES + " 个出场国家。");
            ConfigSnapshot saved;
            // 配置文件顶层固定是 {"fleets": {...}}，FleetPlan.json() 只给出内层对象。
            try { saved = config.save(baseline, new JSONObject().put(FleetPlan.KEY, draft().json())); }
            catch (IOException | RuntimeException ex) {
                boolean conflict = ex instanceof ConfigException ce && ce.code() == ConfigException.Code.CONFLICT;
                System.err.println("[ARC fleet settings] " + ex);
                return conflict
                    ? text("The config file changed or is locked. Draft kept; reopen the window for current values.",
                        "配置文件已变化或被锁定。草稿已保留，请重新打开窗口读取当前值。")
                    : text("Could not save. Draft kept; check the config file and its permissions.",
                        "保存失败。草稿已保留，请检查配置文件与权限。");
            }
            accept(saved);
            try { ArcRules.publish(); }
            catch (RuntimeException ex) {
                System.err.println("[ARC fleet settings] saved, publish failed: " + ex);
                status = text("Saved, but the shared rules could not be updated; check the log.",
                    "已保存，但共享规则更新失败，详情见日志。");
                return status;
            }
            status = text("Saved. New campaigns will use these AI fleets.", "已保存。新战役将使用这套 AI 舰队规则。");
            return null;
        }

        void restoreDefaults() {
            for (String name : known) {
                modes.put(name, FleetPlan.Mode.ALLOW);
                counts.put(name, "1");
                fallback.put(name, 1);
            }
            preserved.clear();
            status = text("Vanilla defaults are in the draft. Apply to save.", "原版默认值已放入草稿，点击应用才会保存。");
        }

        void reload() {
            try { accept(config.reload()); }
            catch (IOException | RuntimeException ex) {
                System.err.println("[ARC fleet settings] " + ex);
                status = text("Reload failed. Draft kept; check the config file.", "重新读取失败。草稿已保留，请检查配置文件。");
            }
        }

        void cancel() { accept(baseline); }
    }
}
