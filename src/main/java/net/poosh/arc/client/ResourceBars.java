/* ResourceBars.java — 五种资源按实际数量排列，保持原生两行高度，耗尽和战损后仍保留原有资源项。 */
package net.poosh.arc.client;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.util.Clr;
import net.poosh.arc.conquest.StartingOptions;
import java.util.ArrayList;
import java.util.List;

public final class ResourceBars {
    private ResourceBars() { }
    public enum Kind {
        CREW("Crew_", "Crew", "人员"), AMMO("Ammo_", "Ammo", "弹药"),
        WATER("Water_", "Water", "水"), COAL("Coal_", "Coal", "煤炭"), REPAIR("Repair_supplies_", "Repair", "维修");
        final String key, english, chinese;
        Kind(String key, String english, String chinese) { this.key = key; this.english = english; this.chinese = chinese; }
        String label() { return StartingOptions.text(english, chinese); }
    }
    public record Entry(Kind kind, int amount, int capacity, Clr color) {
        public double fraction() { return Math.clamp((double) amount / Math.max(1, capacity), 0.0, 1.0); }
        public String quantity() { return amount + "/" + capacity; }
        public String tooltip() { return Lang._t(kind.key) + quantity(); }
    }
    public record Cell(int x, int y, int width, int height) { }
    public static List<Entry> entries(Airship ship) {
        List<Entry> entries = new ArrayList<>(5);
        add(entries, Kind.CREW, ship.getActiveCrewCount(), ship.getOriginalAllQuartered(), new Clr(110, 149, 17));
        add(entries, Kind.AMMO, ship.getTotalResource(Resource.AMMO), ship.getOriginalAmmoCapacity(), Airship.AMMO_FG);
        add(entries, Kind.WATER, ship.getTotalResource(Resource.WATER), ship.getOriginalWaterCapacity(), Airship.WATER_FG);
        add(entries, Kind.COAL, ship.getTotalResource(Resource.COAL), ship.getOriginalCoalCapacity(), Airship.COAL_REAL_FG);
        add(entries, Kind.REPAIR, ship.getTotalResource(Resource.REPAIR), ship.getOriginalRepairCapacity(), Airship.REPAIR_FG);
        return entries;
    }
    private static void add(List<Entry> entries, Kind kind, int amount, int capacity, Clr color) {
        if (capacity > 0 || amount > 0) entries.add(new Entry(kind, amount, capacity, color));
    }
    /** 两行以内动态分列，五项用三列；不增大原生命令/直控面板的高度。 */
    public static List<Cell> layout(int count, int width, int rowHeight, int gap) {
        if (count <= 0 || width <= 0 || rowHeight <= 0) return List.of();
        int columns = (count + 1) / 2;
        gap = Math.min(Math.max(0, gap), Math.max(0, (width - columns) / Math.max(1, columns - 1)));
        int content = Math.max(0, width - gap * (columns - 1));
        List<Cell> cells = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int col = i % columns;
            int left = content * col / columns + col * gap;
            int right = content * (col + 1) / columns + col * gap;
            cells.add(new Cell(left, i / columns * rowHeight, right - left, rowHeight));
        }
        return cells;
    }
    public static void draw(MyDraw draw, Airship ship, int x, int y, int availableWidth) {
        List<Entry> entries = entries(ship);
        List<Cell> cells = layout(entries.size(), availableWidth, AGame.FOUNT.lineHeight, MyDraw.UI_SPACING);
        int top = y + MyDraw.BUTTON_H + MyDraw.BUTTON_SPACING * 2 + AGame.FOUNT.height;
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            if (cell.width() <= 0) continue;
            Entry entry = entries.get(i);
            int xx = x + cell.x(), yy = top + cell.y();
            // 复用原生带边框进度条；与文字并排占用完整行高，不能按字体留白画细线。
            int gap = Math.min(MyDraw.BUTTON_SPACING, Math.max(0, cell.width() / 8));
            int reservedBar = Math.max(9, cell.width() * 2 / 5);
            int textWidth = Math.max(0, cell.width() - reservedBar - gap);
            String label = entry.kind().label() + ": " + entry.quantity();
            textWidth = Math.min(textWidth, (int) Math.ceil(draw.textSize(label, AGame.FOUNT).x));
            draw.text(fit(draw, label, textWidth), AGame.FOUNT, xx, yy);
            int barX = xx + textWidth + gap;
            int barWidth = xx + cell.width() - barX;
            int barHeight = Math.min(cell.height(), Math.max(9, AGame.FOUNT.height - MyDraw.BUTTON_SPACING));
            int barY = yy + (cell.height() - barHeight) / 2;
            if (barWidth >= 9 && barHeight >= 9) {
                draw.progressBar(barX, barY, barWidth, barHeight, entry.fraction(), entry.color());
            } else if (barWidth >= 3 && barHeight >= 3) {
                // 极小缩放的行高不足原生固定 4px 内边距，保留金色边框和可见填充，使用 1px 内边距。
                draw.rect(MyDraw.TITLE, barX, barY, barWidth, barHeight);
                draw.rect(MyDraw.DARK_BG, barX + 1, barY + 1, barWidth - 2, barHeight - 2);
                int filled = (int) Math.round((barWidth - 2) * entry.fraction());
                if (filled > 0) draw.rect(entry.color(), barX + 1, barY + 1, filled, barHeight - 2);
            }
            draw.tooltip(xx, yy, cell.width(), cell.height(), entry.tooltip());
        }
    }
    /** 超长数字只缩短行内显示；悬浮提示保留完整名称和精确数值。 */
    private static String fit(MyDraw draw, String text, int width) {
        if (draw.textSize(text, AGame.FOUNT).x <= width) return text;
        String suffix = "…";
        if (draw.textSize(suffix, AGame.FOUNT).x > width) return "";
        while (!text.isEmpty() && draw.textSize(text + suffix, AGame.FOUNT).x > width)
            text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        return text + suffix;
    }
}
