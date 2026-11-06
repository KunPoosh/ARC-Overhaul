/* StartValues.java — 不可变的新战役设置；-1 保留原版，独立字段不会互相隐式启用。 */
package net.poosh.arc.conquest;

import org.json.JSONObject;

public record StartValues(int cities, int towns, int cash, int research, int aiTowns, int aiCities) {
    public static final int MAX_CITIES = 4, MAX_TOWNS = 8, MAX_CASH = 1_000_000;
    /**
     * 研发点上限必须和游戏自身的技术成本同量级：{@code Tech.baseCost} 为
     * {@code BASE_RESEARCH_COST × RESEARCH_COST_MULTIPLIER × 5600 × 1.4^tier}，
     * 前几级科技就需要 1,680,000–2,352,000 点（引擎内实测）。上限过小会让进度条上
     * 出现一条和 0 无法区分的细线，看起来就像没有发放。
     */
    public static final int MAX_RESEARCH = 10_000_000;
    public StartValues {
        if (cities != -1 && (cities < 1 || cities > MAX_CITIES)
                || towns < -1 || towns > MAX_TOWNS || cash < -1 || cash > MAX_CASH
                || (aiCities != -1 && (aiCities < 1 || aiCities > MAX_CITIES))
                || research < 0 || research > MAX_RESEARCH || aiTowns < -1 || aiTowns > MAX_TOWNS)
            throw new IllegalArgumentException("Invalid ARC starting values / ARC 开局数值超出范围");
    }
    /** 忽略研发点的简写；{@code 0} 就是不发放，等同原版。 */
    public StartValues(int cities, int towns, int cash) { this(cities, towns, cash, 0); }
    /** 旧调用与旧战役均保留 AI 的原版城镇数。 */
    public StartValues(int cities, int towns, int cash, int research) { this(cities, towns, cash, research, -1); }
    /** 旧五参数调用保留 AI 默认城市数。 */
    public StartValues(int cities, int towns, int cash, int research, int aiTowns) { this(cities, towns, cash, research, aiTowns, -1); }
    /** 研发点默认 0：0 表示不发放，行为等同原版；旧配置没有该键时按 0 读取，不强制迁移。 */
    public static StartValues read(JSONObject json) {
        for (String key : new String[]{"cities", "towns", "cash"})
            if (!(json.get(key) instanceof Integer))
                throw new IllegalArgumentException("Expected integer / 必须为整数: " + key);
        Object points = json.has("research") ? json.get("research") : Integer.valueOf(0);
        if (!(points instanceof Integer))
            throw new IllegalArgumentException("Expected integer / 必须为整数: research");
        Object ai = json.has("aiTowns") ? json.get("aiTowns") : Integer.valueOf(-1);
        if (!(ai instanceof Integer))
            throw new IllegalArgumentException("Expected integer / 必须为整数: aiTowns");
        Object aiCities = json.has("aiCities") ? json.get("aiCities") : Integer.valueOf(-1);
        if (!(aiCities instanceof Integer)) throw new IllegalArgumentException("Expected integer / 必须为整数: aiCities");
        return new StartValues(json.getInt("cities"), json.getInt("towns"), json.getInt("cash"), (Integer) points, (Integer) ai, (Integer) aiCities);
    }
    public JSONObject json() {
        return new JSONObject().put("cities", cities).put("towns", towns).put("cash", cash).put("research", research).put("aiTowns", aiTowns).put("aiCities", aiCities);
    }
    public boolean changesHumanLand() { return cities != -1 || towns != -1; }
    public boolean changesLand() { return changesHumanLand() || aiTowns != -1 || aiCities != -1; }
    public boolean changesLand(boolean human) { return human ? changesHumanLand() : aiTowns != -1 || aiCities != -1; }
    /** 只有正数才发放：0 是默认值，必须保持原版的开局研发行为。 */
    public boolean grantsResearch() { return research > 0; }
    public int cityCount() { return cities == -1 ? 1 : cities; }
    public int cityCount(boolean human) { return human ? cityCount() : aiCities == -1 ? 1 : aiCities; }
    public int townCount(int original) { return towns == -1 ? original : towns; }
    public int aiTownCount(int original) { return aiTowns == -1 ? original : aiTowns; }
    public int extraSlots(int original) { return cityCount() - 1 + townCount(original); }
    public int extraSlots(boolean human, int original) { return human ? extraSlots(original) : cityCount(false) - 1 + aiTownCount(original); }
    public int capacity(int original) { return Math.max(original, Math.max(extraSlots(original), extraSlots(false, original))); }
}
