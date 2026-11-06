/* UiScaleRuntimeChecks.java — 在真实 Knot 内验证百分比度量、原生绘制/命中、档位入口及本机配置事务。 */
package regression;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.*;
import com.zarkonnen.catengine.util.*;
import net.fabricacs.api.config.*;
import net.fabricacs.api.ui.*;
import net.fabricacs.api.impl.*;
import net.poosh.arc.ui.UiScaleOptions;
import org.json.JSONObject;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import java.util.*;
import java.util.function.Consumer;
import java.nio.file.Files;
import static regression.ArcRuntimeProbe.*;

public final class UiScaleRuntimeChecks {
    public static void run() throws Exception {
        check(Arrays.stream(Draw.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("acbric$scaleImages")), "Draw constructor transformed by real Knot");
        check(Arrays.stream(SettingsScreen.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("arc$scaleEntry")), "native SettingsScreen transformed by real Knot");
        checkTargets();
        checkSettings();
        AirshipGame game = (AirshipGame) unsafe(AirshipGame.class);
        game.currentGUIScale = GUIScale.SMALL;
        game.guiScaleOverride = GUIScale.MEDIUM;
        GUIScale.SMALL.activate();
        int baseHeight = MyDraw.BUTTON_H;
        Fount baseFont = AGame.FOUNT;
        Fount baseTitle = AGame.HUGE_FOUNT;
        Img baseBorder = MyDraw.BUTTON_START;
        check(baseFont != null && baseFont.get('A') != null, "real game font metrics available");
        for (int percent : new int[]{50, 100, 125, 137, 200, 125}) {
            UiScale.setPercent(percent);
            // 运行真实 AirshipGame 注入处理器，顺序与 render HEAD 完全相同。
            handler(AirshipGame.class,"acbric$prepareUiRender",Frame.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class)
                .invoke(game,null,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("render",false));
            check(MyDraw.BUTTON_H == Math.max(1,Math.round(baseHeight * percent / 100f)), "native button metric at " + percent + "%");
            check(AGame.FOUNT.getWidth('A') == Math.max(1,Math.round(baseFont.getWidth('A') * percent / 100f)), "font measurement at " + percent + "%");
            check(AGame.HUGE_FOUNT == baseTitle, "native fixed-size title preserved at " + percent + "%");
            check(game.guiScaleOverride == GUIScale.MEDIUM && AirshipGame.scaleFrom == null, "native preference and render resolution untouched at " + percent + "%");
            RecordingFrame frame = new RecordingFrame();
            Draw draw = new Draw(frame);
            check(draw.frame() instanceof UiScaleFrame, "real Draw constructor wraps frame");
            draw.blit(MyDraw.BUTTON_START,17,29);
            check(frame.lastImage == baseBorder && frame.x == 17 && frame.y == 29, "native draw retains crop and screen position");
            check(percent == 100 ? frame.w == 0 : frame.w == MyDraw.BUTTON_START.srcWidth, "display width matches logical border");
            draw.text("A",AGame.FOUNT,30,40);
            check(frame.lastImage == baseFont.get('A'), "font raster uses original glyph crop");
            Hooks hooks = new Hooks();
            MyDraw.State state = new MyDraw.State();state.tick(16,new Pt(-100,-100));
            MyDraw nativeDraw = new MyDraw(frame,hooks,state,null);
            check(nativeDraw.frame() instanceof UiScaleFrame, "real MyDraw constructor wraps frame with hooks");
            nativeDraw.button(100,80,240,"Apply",()->{},true);
            var bounds = hooks.list.get(0).a;
            check(bounds.width == 242 && bounds.height == MyDraw.BUTTON_H + 2 && bounds.x == 99 && bounds.y == 79,
                    "native button click rectangle matches new geometry at " + percent + "%");
            Fount same = AGame.FOUNT;
            UiScaleBridge.beforeRender(game);
            check(AGame.FOUNT == same, "steady frame reuses font and glyph objects");
        }
        GUIScale.MEDIUM.activate();
        game.currentGUIScale = GUIScale.MEDIUM;
        Fount medium = AGame.FOUNT;int mediumHeight = MyDraw.BUTTON_H;
        UiScaleBridge.beforeRender(game);
        check(AGame.FOUNT != medium && MyDraw.BUTTON_H == Math.round(baseHeight*1.25f), "native scale activation is followed by requested percentage");
        UiScale.useVanilla();UiScaleBridge.beforeRender(game);
        check(AGame.FOUNT == medium && MyDraw.BUTTON_H == mediumHeight, "reset restores native current preset exactly");
        check(MyDraw.SMALL_BUTTON_START == baseBorder && AGame.SMALL_FOUNT[0] == baseFont, "baseline assets and fonts remain unchanged");
        checkNativeControls(game);
        UiScale.useVanilla();UiScaleBridge.beforeRender(game);GUIScale.SMALL.activate();
    }

    private static void checkTargets() throws Exception {
        List<String> code = events(SettingsScreen.class,"render","(Lcom/zarkonnen/airships/MyDraw;Lcom/zarkonnen/catengine/util/ScreenMode;Lcom/zarkonnen/catengine/Hooks;Lcom/zarkonnen/catengine/util/Pt;)V");
        List<Integer> toggles = sitesOf(code,"M com/zarkonnen/airships/MyDraw.toggle(IIILjava/lang/String;Ljava/lang/String;Lcom/zarkonnen/airships/InputRunnable;ZZ)V");
        check(toggles.size() >= 3 && fieldBefore(code,toggles.get(1)).endsWith(".guiScaleOverride") && fieldBefore(code,toggles.get(2)).endsWith(".guiScaleOverride"),
                "native toggle ordinals 1 and 2 both read guiScaleOverride");
        check(sitesOf(code,"M com/zarkonnen/airships/ResChooserWidget.render(IIIILcom/zarkonnen/airships/MyDraw;Lcom/zarkonnen/catengine/util/ScreenMode;Lcom/zarkonnen/catengine/Hooks;Lcom/zarkonnen/catengine/util/Pt;)V").size()==1,
                "one native resolution widget is the scale-entry insertion point");
    }

    @SuppressWarnings("unchecked")
    private static void checkSettings() throws Exception {
        ModConfig config=(ModConfig)field(null,UiScaleOptions.class,"config");
        check(config != null && UiScale.percent()==0 && !Files.exists(config.path()), "ARC defaults neither change UI nor create a config file");
        Lang.currentLocale=Locale.forLanguageTag("chi");check(UiScaleOptions.window().title().contains("界面大小"), "Chinese scale settings");
        Lang.currentLocale=Locale.FRENCH;check(UiScaleOptions.window().title().contains("UI scale"), "other languages fall back to English");
        Lang.currentLocale=Locale.ENGLISH;
        UiWindow window=UiScaleOptions.window();
        List<UiNode> all=new ArrayList<>();collect(window.content(),all);collect(window.footer(),all);
        UiNode toggle=all.stream().filter(n->{try{return kindOf(n).equals("TOGGLE");}catch(Exception ex){throw new RuntimeException(ex);}}).findFirst().orElseThrow();
        UiNode input=all.stream().filter(n->{try{return kindOf(n).equals("TEXT");}catch(Exception ex){throw new RuntimeException(ex);}}).findFirst().orElseThrow();
        UiNode apply=button(all,"Apply"),defaults=button(all,"Defaults");
        Consumer<Boolean> change=(Consumer<Boolean>)field(toggle,UiNode.class,"change");
        Consumer<String> edit=(Consumer<String>)field(input,UiNode.class,"edit");
        change.accept(true);edit.accept("137");
        check(UiScale.percent()==0 && !config.read().data().getBoolean("enabled"), "editing remains a draft");
        for(String invalid:List.of("49","201","125.5","abc","")) {
            edit.accept(invalid);check(!enabledOf(apply), "invalid percentage disables Apply: " + invalid);
        }
        edit.accept("137");
        ((Consumer<UiWindowHandle>)field(apply,UiNode.class,"action")).accept(null);
        check(UiScale.percent()==137 && config.read().data().getInt("percent")==137 && Files.exists(config.path()), "Apply saves and requests exact integer percentage");
        check(!ArcRulesData().has("percent") && !ArcRulesData().has("enabled"), "display settings stay out of shared campaign rules");
        config.reload();check(config.read().data().getInt("percent")==137, "percentage survives disk reload");
        // Defaults 是草稿操作，需要新窗口拿到 reload 后的配置基线。
        window=UiScaleOptions.window();all.clear();collect(window.content(),all);collect(window.footer(),all);
        ((Consumer<UiWindowHandle>)field(button(all,"Defaults"),UiNode.class,"action")).accept(null);
        check(UiScale.percent()==137, "Defaults does not apply before explicit confirmation");
        ((Consumer<UiWindowHandle>)field(button(all,"Apply"),UiNode.class,"action")).accept(null);
        check(UiScale.percent()==0 && !config.read().data().getBoolean("enabled"), "applying defaults restores vanilla preference");
        ConfigSnapshot baseline=config.read();
        boolean bad=false;try{config.save(baseline,new JSONObject().put("enabled",true).put("percent","125"));}catch(ConfigException expected){bad=true;}
        check(bad && config.read()==baseline, "malformed persisted types cannot replace config");
    }
    private static JSONObject ArcRulesData() throws Exception {
        var method=net.poosh.arc.conquest.ArcRules.class.getDeclaredMethod("combine");
        method.setAccessible(true);
        return (JSONObject)method.invoke(null);
    }
    private static void collect(UiNode node,List<UiNode> all) throws Exception {if(node==null)return;all.add(node);for(UiNode child:kids(node))collect(child,all);}
    private static UiNode button(List<UiNode> nodes,String label) throws Exception {for(UiNode n:nodes)if(kindOf(n).equals("BUTTON")&&textOf(n).equals(label))return n;throw new AssertionError(label);}

    private static void checkNativeControls(AirshipGame game) throws Exception {
        SettingsScreen screen=(SettingsScreen)unsafe(SettingsScreen.class);
        putObject(screen,SettingsScreen.class,"g",game);
        EntryDraw draw=(EntryDraw)unsafe(EntryDraw.class);
        int row=MyDraw.BUTTON_H+MyDraw.BUTTON_SPACING;
        TestArgs args=new TestArgs(11,22,250,row*6,draw,new ScreenMode(1920,1080,false),new Hooks(),new Pt(0,0));
        handler(SettingsScreen.class,"arc$scaleEntry",Args.class).invoke(screen,args);
        check(draw.called && draw.x==11 && draw.y==22 && draw.w==250, "native entry stays inside the resolution column");
        check((int)args.get(1)==22+row && (int)args.get(3)==row*5, "resolution widget moves down without moving its bottom edge");
        draw.called=false;TestArgs tiny=new TestArgs(11,22,250,row,draw,null,null,null);
        handler(SettingsScreen.class,"arc$scaleEntry",Args.class).invoke(screen,tiny);
        check(!draw.called && (int)tiny.get(1)==22, "small native settings layout keeps its resolution controls");
        for(String method:List.of("arc$autoChoice","arc$presetChoice")) {
            ModConfig config=(ModConfig)field(null,UiScaleOptions.class,"config");
            config.save(config.read(),new JSONObject().put("enabled",true).put("percent",137));UiScale.setPercent(137);
            int[] clicks={0};InputRunnable original=input->clicks[0]++;
            TestArgs toggle=new TestArgs(0,0,100,"Small",null,original,true,true);
            handler(SettingsScreen.class,method,Args.class).invoke(screen,toggle);
            check(!(boolean)toggle.get(6), "native preset not marked selected during custom mode");
            ((InputRunnable)toggle.get(5)).run(null);
            check(clicks[0]==1 && UiScale.percent()==0 && !config.read().data().getBoolean("enabled"), "native preset clears saved override and invokes original action once");
        }
    }
    private static final class TestArgs extends Args {
        TestArgs(Object... values){super(values);}
        public <T> void set(int index,T value){values[index]=value;}
        public void setAll(Object... args){System.arraycopy(args,0,values,0,args.length);}
    }
    private static class EntryDraw extends MyDraw {
        boolean called;int x,y,w;
        EntryDraw(){super(null,(MyDraw.State)null,null);}
        @Override public void button(int x,int y,int w,String text,Runnable action,boolean enabled){called=true;this.x=x;this.y=y;this.w=w;}
    }
    static final class RecordingFrame implements Frame {
        Img lastImage;double x,y,w,h;
        public ScreenMode mode(){return new ScreenMode(1920,1080,false);}
        public int fps(){return 60;}
        public Object nativeRenderer(){return null;}
        public Pt cursor(){return new Pt(-100,-100);}
        public void rect(Clr c,double x,double y,double w,double h,double angle){}
        public void blit(Img image,Clr c,double alpha,double x,double y,double w,double h,double angle){lastImage=image;this.x=x;this.y=y;this.w=w;this.h=h;}
        public double getWidth(Img image){return image.srcWidth;}
        public double getHeight(Img image){return image.srcHeight;}
        public void shift(double x,double y){}
        public void scale(double x,double y){}
        public void rotate(double a){}
        public void resetTransforms(){}
    }
}
