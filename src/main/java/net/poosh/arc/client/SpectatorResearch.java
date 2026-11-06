/* SpectatorResearch.java — 联机战役观战时访问自己的原生科技树；指令、同步与后台推进仍由原版负责。 */
package net.poosh.arc.client;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.Input;
import net.fabricacs.api.event.AirshipsClientEvents;
import net.poosh.arc.conquest.StartingOptions;

public final class SpectatorResearch extends UniScreen.Button {
    private static UniScreen returnTo;
    private static TechScreen opened;

    public static void initialize() { AirshipsClientEvents.CLIENT_TICK_END.register(game -> returnFromResearch((AirshipGame) game)); }

    public static StrategicScreen campaign(UniScreen screen) {
        if (screen == null || !(screen.intent instanceof MultiplayerCampaignCombatIntent intent)
                || !intent.spectate || intent.finished) return null;
        StrategicScreen ss = intent.ss;
        if (ss == null || ss.w == null || ss.w.player == null || !ss.w.player.playerControlled
                || ss.combatUniScreen != screen || ss.w.combatInfo == null || ss.w.combatInfo != intent.ci
                || screen.combat == null || ss.w.combatInfo.combat != screen.combat
                || ss.w.combatInfo.playerCombatSideIndex(ss.w) != -1) return null;
        return ss;
    }

    @Override public boolean visible(UniScreen screen) { return campaign(screen) != null; }
    @Override public String text(UniScreen screen) { return StartingOptions.text("Research", "科技研究"); }
    @Override public String tooltip(UniScreen screen) {
        return StartingOptions.text("Change your empire's research while spectating. Battle continues; close research to return.",
            "观战时调整自己国家的研究与队列。战斗继续运行；关闭科技树返回观战。");
    }
    @Override public String hotkey(UniScreen screen) { return "R"; }
    @Override public String hotkeyID(UniScreen screen) { return "strategic_research"; }
    @Override public void click(Input input, UniScreen screen) {
        StrategicScreen ss = campaign(screen);
        if (ss == null || screen.g == null || screen.g.s != screen) return;
        // TechScreen 始终读取 ss.w.player，并调用 CampaignWorld.giveCommand；不能在本机直接写研究字段。
        opened = new TechScreen(ss);
        returnTo = screen;
        screen.g.s = opened;
    }

    /** 原生 Esc 与关闭按钮均回到 ss；在输入结束事件统一返回观战，不覆盖断线/重同步/战斗结束界面。 */
    public static void returnFromResearch(AirshipGame game) {
        if (opened == null || returnTo == null || game != returnTo.g) return;
        if (game.s == opened) return;
        if (game.s == opened.ss && campaign(returnTo) == opened.ss) game.s = returnTo;
        opened = null;
        returnTo = null;
    }
}
