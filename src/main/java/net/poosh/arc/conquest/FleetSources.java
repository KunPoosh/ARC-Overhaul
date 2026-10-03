/* FleetSources.java — 只读扫描已启用原生 MOD 的舰队声明：重复 ID、缺失 ID 与游戏搜索框打不出的名称。 */
package net.poosh.arc.conquest;

import com.zarkonnen.airships.AGame;
import com.zarkonnen.airships.Mod;
import net.fabricacs.api.util.AcbricLanguage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 游戏把每支 AI 舰队按 {@code ConstructionStrategy.name}（舰队编辑器里那个字段就叫 ID）注册进一张
 * 全局表；跨 MOD 重复的 ID 由后加载者静默顶替（{@code Loadable.load} 只做 {@code putAll}，
 * 没有冲突检查），缺少 {@code name} 的声明连失败记录都不会留。被顶替或被跳过的舰队在游戏列表里
 * 根本不存在，所以「检索不到」有时不是搜索的问题，而是这条舰队早就没被加载。
 *
 * <p>这里在打开设置页时只读扫描已启用 MOD 的 {@code ConstructionStrategy/*.json}，把上面这些
 * 看不见的丢件原因变成玩家能读的提示。扫描失败不影响设置页打开：读不到的文件交给游戏自己的加载
 * 逻辑处理，本类不拦、不改、不写任何东西。</p>
 */
final class FleetSources {
    /** 单个 MOD 里的一条舰队声明。 */
    private record Declaration(String id, String displayName, String origin) { }

    /** 扫描结果：给玩家看的提示，以及需要逐行标注的 ID 集合。 */
    record Report(List<String> messages, Set<String> duplicateIds, Set<String> untypeableIds) {
        static final Report EMPTY = new Report(List.of(), Set.of(), Set.of());
        boolean isEmpty() { return messages.isEmpty(); }
    }

    /** 上限只用于防止异常目录结构拖慢设置页；正常舰队包远低于此值。 */
    private static final int MAX_FILES = 600;
    private static final long MAX_BYTES = 4L * 1024 * 1024;

    private FleetSources() { }

    static Report scan() {
        Map<String, File> roots = new LinkedHashMap<>();
        try {
            for (Mod mod : Mod.getEnabledMods()) {
                if (mod == null || mod.dir == null) continue;
                roots.put(mod.dir.getName(), new File(mod.dir, "ConstructionStrategy"));
            }
        } catch (RuntimeException ex) {
            System.err.println("[ARC fleet settings] fleet source roots unavailable: " + ex);
            return Report.EMPTY;
        }
        return scan(roots);
    }

    /** 只读扫描给定的「来源 MOD 名 → 舰队声明目录」；集成检查用临时目录直接调用这里。 */
    static Report scan(Map<String, File> roots) {
        Map<String, List<Declaration>> byId = new LinkedHashMap<>();
        List<String> messages = new ArrayList<>();
        int files = 0, skipped = 0, missingNames = 0, unreadable = 0;
        try {
            for (var root : roots.entrySet()) {
                File dir = root.getValue();
                if (dir == null || !dir.isDirectory()) continue;
                File[] list = dir.listFiles((parent, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
                if (list == null) continue;
                Arrays.sort(list, Comparator.comparing(File::getName));
                for (File file : list) {
                    if (files++ >= MAX_FILES) { skipped++; continue; }
                    JSONArray rows = read(file);
                    if (rows == null) { if (file.length() > 0) unreadable++; continue; }
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row == null) continue;
                        Object raw = row.opt("name");
                        if (!(raw instanceof String id) || id.isBlank()) { missingNames++; continue; }
                        String display = row.optString("displayName", id);
                        byId.computeIfAbsent(id, key -> new ArrayList<>())
                                .add(new Declaration(id, display, root.getKey() + "/" + file.getName()));
                    }
                }
            }
        } catch (RuntimeException ex) {
            System.err.println("[ARC fleet settings] source scan failed: " + ex);
            return Report.EMPTY;
        }

        Set<String> duplicates = new LinkedHashSet<>(), untypeable = new LinkedHashSet<>();
        for (var entry : byId.entrySet()) {
            List<Declaration> declared = entry.getValue();
            if (declared.size() > 1) {
                duplicates.add(entry.getKey());
                List<String> origins = new ArrayList<>();
                for (Declaration declaration : declared) origins.add(declaration.origin());
                messages.add(text("ID \"" + entry.getKey() + "\" is declared by " + declared.size()
                                + " AI fleets; only the one loaded last is used and the others are replaced: "
                                + String.join(", ", origins) + ".",
                        "ID「" + entry.getKey() + "」被 " + declared.size() + " 支 AI 舰队共用：只有最后加载的那一支生效，其余被顶替。来源："
                                + String.join("、", origins) + "。"));
            }
            String display = declared.get(0).displayName();
            if (display != null && !display.isEmpty() && !display.equals(AGame.makeFileSafe(display))) {
                untypeable.add(entry.getKey());
                messages.add(text("\"" + display + "\" contains characters that cannot be typed into the game's own search box, "
                                + "so that fleet cannot be found there by name. Rename it with typeable characters, or find it by scrolling.",
                        "「" + display + "」含游戏自带搜索框无法输入的字符：游戏里打不出这些字，按名称搜不到这支舰队。"
                                + "请改用可输入的字符命名，或在列表里滚动查找。"));
            }
        }
        if (missingNames > 0) messages.add(text(missingNames + " AI fleet declaration(s) have no \"name\" (ID); the game skips them silently.",
                "有 " + missingNames + " 条 AI 舰队声明缺少 name（ID），游戏会静默跳过它们。"));
        if (unreadable > 0) messages.add(text(unreadable + " AI fleet file(s) could not be read as a JSON array and were not checked.",
                "有 " + unreadable + " 个 AI 舰队文件不是可读的 JSON 数组，未参与检查。"));
        if (skipped > 0) messages.add(text("Only the first " + MAX_FILES + " AI fleet files were checked.", "只检查了前 " + MAX_FILES + " 个 AI 舰队文件。"));
        return new Report(List.copyOf(messages), Set.copyOf(duplicates), Set.copyOf(untypeable));
    }

    private static JSONArray read(File file) {
        try {
            if (!file.isFile() || file.length() <= 0 || file.length() > MAX_BYTES) return null;
            return new JSONArray(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(String en, String zh) { return AcbricLanguage.text(en, zh); }
}
