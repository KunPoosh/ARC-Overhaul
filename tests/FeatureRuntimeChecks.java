/* FeatureRuntimeChecks.java — 五资源、履带分层、观战研究与 AI 城镇的真实注入及回归检查；不启动 GPU/网络会话。 */
package regression;

import com.zarkonnen.airships.*;
import com.zarkonnen.airships.Module;
import com.zarkonnen.catengine.*;
import com.zarkonnen.catengine.util.*;
import net.poosh.arc.client.*;
import net.poosh.arc.mixin.WorldMapAccess;
import net.poosh.arc.conquest.*;
import net.fabricacs.api.config.ModConfig;
import org.json.JSONObject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.*;
import java.util.*;
import static regression.ArcRuntimeProbe.*;

final class FeatureRuntimeChecks {
    static void run() throws Exception {
        aiCities(); aiTowns(); resources(); tracks(); spectatorResearch();
    }

    static void aiCities() throws Exception {
        JSONObject legacy=new StartValues(-1,-1,-1).json();legacy.remove("aiCities");
        check(StartValues.read(legacy).aiCities()==-1,"old rules preserve one AI city");
        for(Object invalid:new Object[]{0,5,-2,1.5,"2"})
            reject(()->StartValues.read(legacy.put("aiCities",invalid)),"invalid AI city count rejected: "+invalid);
        for(int parity=0;parity<2;parity++)for(int cities:new int[]{1,2,4})for(int towns:new int[]{0,8})
            placeAndTrace(4,parity,new StartValues(2,1,-1,0,towns,cities),false);
        StartValues values=new StartValues(-1,-1,-1,0,8,4);
        WorldMap map=newWorld(values).map;
        check(map.size.townsPerEmpire==11&&base.townsPerEmpire==2,"AI cities plus towns allocate private capacity");
        SavedStateOutPipe state=new SavedStateOutPipe();JSONObject data=map.toJSON(state);state.compileAndGetHash();
        WorldMap restored=new WorldMap(data,null,new JSONObjectInPipe(state.toJSON()));
        check(restored.size.townsPerEmpire==11&&StartingOptions.forMap(restored).equals(values),"AI city rules and capacity survive native save restore");
        City city=(City)unsafe(City.class);city.isTown=true;
        int income=(Integer)invoke(stage(map,2),"arc$settlementIncome",map,false,city,new GuardedRandom(123),0,map);
        int expected=((WorldMapAccess)map).arc$cityIncome(false,city,new GuardedRandom(123));
        check(!city.isTown&&income==expected,"AI city conversion uses native AI income rather than human income");
    }

    static void aiTowns() throws Exception {
        JSONObject old = new StartValues(-1,-1,-1).json(); old.remove("aiTowns");
        check(StartValues.read(old).aiTowns()==-1,"old config/rules preserve vanilla AI towns");
        reject(()->StartValues.read(new StartValues(-1,-1,-1).json().put("aiTowns",1.5)),"AI towns reject fractions");
        reject(()->StartValues.read(new StartValues(-1,-1,-1).json().put("aiTowns","3")),"AI towns reject strings");
        reject(()->new StartValues(-1,-1,-1,0,-2),"AI towns lower bound");
        reject(()->new StartValues(-1,-1,-1,0,9),"AI towns upper bound");
        for (int parity=0;parity<2;parity++) {
            for (int towns:new int[]{0,1,8})
                placeAndTrace(4,parity,new StartValues(-1,-1,-1,0,towns),false);
            placeAndTrace(4,parity,new StartValues(4,8,-1,0,0),false);
            placeAndTrace(4,parity,new StartValues(1,0,-1,0,8),false);
        }
        StartValues settings=new StartValues(-1,-1,-1,0,8);
        WorldMap map=newWorld(settings).map;
        check(map.size.townsPerEmpire==8&&base.townsPerEmpire==2,"AI-only expansion uses private capacity");
        publish(new StartValues(-1,-1,-1,0,0),FleetPlan.empty());
        check(StartingOptions.forMap(map).equals(settings),"host candidate changes cannot mutate frozen AI towns");
        SavedStateOutPipe state=new SavedStateOutPipe(); JSONObject data=map.toJSON(state);state.compileAndGetHash();
        WorldMap restored=new WorldMap(data,null,new JSONObjectInPipe(state.toJSON()));
        check(restored.size.townsPerEmpire==8&&StartingOptions.forMap(restored).equals(settings),"native save/restore keeps AI rules and capacity");
        check(((GenerationAccess)restored).arc$options()==null,"restoring AI options never arms generation");
        CampaignWorld zero=newWorld(new StartValues(1,0,-1,0,0));
        for(int i=0;i<zero.map.size.empires;i++)zero.map.empires.add(empire(i%2==0,i));
        for(int i=0;i<(Integer)invoke(stage(zero.map,2),"getSize");i++)runStage(stage(zero.map,2),i,zero.map);
        check((Integer)invoke(stage(zero.map,3),"arc$boundedRituals",2,1,0,zero.map)==0,"zero human and AI towns protects ritual placement");
        StartingOptions.finish(zero);
        check(zero.map.empires.stream().allMatch(e->e.getMoney()==777),"AI towns never grant human or AI cash");
        CampaignWorld failed=newWorld(new StartValues(-1,-1,-1,0,1));
        for(int i=0;i<failed.map.size.empires;i++)failed.map.empires.add(empire(i%2==0,i));
        failed.map.water=new boolean[base.gridSize][base.gridSize];for(boolean[] row:failed.map.water)Arrays.fill(row,true);
        failed.map.setupCityNames=new ArrayList<>(List.of("a","b"));failed.map.r=new GuardedRandom(123);
        try { runStage(stage(failed.map,2),1,failed.map);throw new AssertionError("missing configured AI settlement accepted"); }
        catch(IllegalStateException expected){check(expected.getMessage().contains("settlements"),"explicit AI count cannot silently under-generate");}
        check(newWorld(new StartValues(-1,-1,-1)).map.size==base,"next default campaign is not polluted by AI capacity");
    }

    static final class Quantities extends Airship {
        int[] amounts, capacities;
        Quantities(){super(ShipType.AIRSHIP);}
        @Override public int getActiveCrewCount(){return amounts[0];}
        @Override public int getOriginalAllQuartered(){return capacities[0];}
        @Override public int getOriginalAmmoCapacity(){return capacities[1];}
        @Override public int getOriginalWaterCapacity(){return capacities[2];}
        @Override public int getOriginalCoalCapacity(){return capacities[3];}
        @Override public int getOriginalRepairCapacity(){return capacities[4];}
        @Override public int getTotalResource(Resource r){return amounts[switch(r){case AMMO->1;case WATER->2;case COAL->3;case REPAIR->4;default->throw new AssertionError(r);}];}
    }
    record Mark(String type,String text,double x,double y,double w,double h) { }
    static final class RecordingDraw extends MyDraw {
        List<Mark> marks;
        RecordingDraw(){super(null,null,null);}
        @Override public Pt textSize(String text,Fount font){return new Pt(text.codePointCount(0,text.length())*font.displayWidth,font.height);}
        @Override public Draw text(String text,Fount font,double x,double y){marks.add(new Mark("text",text,x,y,textSize(text,font).x,font.height));return this;}
        @Override public Draw rect(Clr color,double x,double y,double w,double h){marks.add(new Mark("bar","",x,y,w,h));return this;}
        @Override public void progressBar(int x,int y,int w,int h,double fraction,Clr color){marks.add(new Mark("nativeBar",Double.toString(fraction),x,y,w,h));}
        @Override public void tooltip(double x,double y,double w,double h,String text){marks.add(new Mark("tip",text,x,y,w,h));}
    }
    static void resources() throws Exception {
        Quantities ship=(Quantities)unsafe(Quantities.class); ship.amounts=new int[]{10,20,30,40,50};ship.capacities=new int[]{100,100,100,100,100};
        check(ResourceBars.entries(ship).size()==5&&ResourceBars.entries(ship).get(4).kind()==ResourceBars.Kind.REPAIR,"all five supplies include repair without replacing another resource");
        ship.amounts[4]=0;
        check(ResourceBars.entries(ship).get(4).fraction()==0,"exhausted repair supplies remain visible");
        ship.capacities[1]=0;ship.amounts[1]=0;
        check(ResourceBars.entries(ship).size()==4,"absent resource does not occupy a slot");
        ship.capacities[1]=100;ship.amounts[1]=200;
        check(ResourceBars.entries(ship).get(1).fraction()==1,"over-capacity progress is bounded");
        RecordingDraw draw=(RecordingDraw)unsafe(RecordingDraw.class);draw.marks=new ArrayList<>();
        for(Locale locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("chi"))) {
            Lang.currentLocale=locale;
            for(int width:new int[]{72,250,600,1050}) {
                draw.marks.clear();
                // 直接调用真实被 Mixin 替换的入口，覆盖取消原版四槽绘制的行为。
                CommandButtonsPanel.renderShipQuantities(draw,ship,20,30,width);
                List<Mark> tips=draw.marks.stream().filter(m->m.type().equals("tip")).toList();
                check(tips.size()==5&&tips.get(4).text().endsWith("0/100"),"rendered repair tooltip keeps exact depleted count: "+locale+"/"+width);
                int top=30+MyDraw.BUTTON_H+MyDraw.BUTTON_SPACING*2+AGame.FOUNT.height;
                check(draw.marks.stream().allMatch(m->m.x()>=20&&m.x()+m.w()<=20+width&&m.y()>=top&&m.y()+m.h()<=top+2*AGame.FOUNT.lineHeight&&m.w()>=0&&m.h()>0),
                    "rendered text/bars/tooltips stay inside two-row resource region: "+locale+"/"+width);
                for(int i=0;i<tips.size();i++)for(int j=i+1;j<tips.size();j++) {
                    Mark a=tips.get(i),b=tips.get(j);
                    if(a.x()<b.x()+b.w()&&b.x()<a.x()+a.w()&&a.y()<b.y()+b.h()&&b.y()<a.y()+a.h())throw new AssertionError("overlapping resource cells");
                }
                check(true,"resource cells do not overlap");
                check(draw.marks.stream().filter(m->m.type().equals("nativeBar")).count()==5
                    &&draw.marks.stream().filter(m->m.type().equals("nativeBar")).allMatch(m->m.h()>=9&&m.w()>=9),
                    "all five use native framed bars with nonempty fill area");
            }
        }
        Lang.currentLocale=Locale.ENGLISH;
        AirshipGame game=(AirshipGame)unsafe(AirshipGame.class);game.currentGUIScale=GUIScale.SMALL;
        for(int percent:new int[]{50,100,125,150,200}) {
            net.fabricacs.api.ui.UiScale.setPercent(percent);
            net.fabricacs.api.impl.UiScaleBridge.beforeRender(game);
            UiScaleRuntimeChecks.RecordingFrame frame=new UiScaleRuntimeChecks.RecordingFrame();
            MyDraw.State state=new MyDraw.State();state.tick(16,new Pt(-100,-100));
            ResourceBars.draw(new MyDraw(frame,new Hooks(),state,null),ship,20,30,1050);
            draw.marks.clear();ResourceBars.draw(draw,ship,20,30,1050);
            check(AGame.FOUNT.lineHeight < 9
                ? draw.marks.stream().filter(m->m.type().equals("bar")&&m.h()==AGame.FOUNT.lineHeight).count()==5
                : draw.marks.stream().filter(m->m.type().equals("nativeBar")).count()==5
                    && draw.marks.stream().filter(m->m.type().equals("nativeBar")).allMatch(m->m.h()>=9),
                "native progress bar drawing and readable fill at scale "+percent);
        }
        net.fabricacs.api.ui.UiScale.useVanilla();net.fabricacs.api.impl.UiScaleBridge.beforeRender(game);GUIScale.SMALL.activate();
        String call="M com/zarkonnen/airships/CommandButtonsPanel.renderShipQuantities(Lcom/zarkonnen/airships/MyDraw;Lcom/zarkonnen/airships/Airship;III)V";
        String desc="(Lcom/zarkonnen/airships/MyDraw;Lcom/zarkonnen/catengine/util/Pt;Lcom/zarkonnen/catengine/util/ScreenMode;Lcom/zarkonnen/catengine/Hooks;Lcom/zarkonnen/airships/UniScreen;)V";
        check(sitesOf(events(CommandButtonsPanel.class,"draw",desc),call).size()==1&&sitesOf(events(DirectControlPanel.class,"draw",desc),call).size()==1,"both native command and direct-control panels call the replaced resource renderer");
    }

    static void tracks() throws Exception {
        check(DisplayOptions.window()!=null,"display settings use real framework editor");
        int armour=-1,extra=-1,front=-1,back=-1;
        for(int i=0;i<ShipLayers.ALL.size();i++) {
            UniScreen.ShipLayer layer=ShipLayers.ALL.get(i);
            if(layer instanceof ShipLayers.Armour)armour=i;
            if(layer instanceof ShipLayers.TracksAndLegs t) {
                if(t.backLegs)back=i;else if(extra<0)extra=i;else front=i;
            }
        }
        check(back==0&&extra==armour-1&&front>armour,"dedicated track pass inserted before armour, native front and back leg passes stay in place");
        ShipLayers.TracksAndLegs behind=(ShipLayers.TracksAndLegs)ShipLayers.ALL.get(extra);
        ShipLayers.TracksAndLegs foreground=(ShipLayers.TracksAndLegs)ShipLayers.ALL.get(front);
        Module module=(Module)unsafe(Module.class);module.wheels=new ArrayList<>();module.legs=new ArrayList<>();
        module.wheels.add((Wheel)unsafe(Wheel.class));module.legs.add((Leg)unsafe(Leg.class));
        for(boolean enabled:new boolean[]{false,true,false}) {
            set(null,DisplayOptions.class,"tracksBehindArmour",enabled);
            List<?> rear=(List<?>)invoke(behind,"arc$trackWheels",module),fore=(List<?>)invoke(foreground,"arc$trackWheels",module);
            check((rear==module.wheels)==enabled&&(fore==module.wheels)!=enabled&&rear.size()+fore.size()==1,"tracks appear in exactly one pass: "+enabled);
            check(((List<?>)invoke(behind,"arc$trackLegs",module)).isEmpty()&&invoke(foreground,"arc$trackLegs",module)==module.legs,"front legs keep original rendering pass: "+enabled);
            check(behind.doDraw(1.0)==enabled&&foreground.doDraw(1.0),"unused track pass is disabled: "+enabled);
        }
        check(module.wheels.size()==1&&module.legs.size()==1,"render routing never mutates physical lists");
        String descriptor="(Lcom/zarkonnen/airships/Airship;Lcom/zarkonnen/airships/WeatherEffect;ILcom/zarkonnen/airships/MyDraw;ZDDDIZZLcom/zarkonnen/airships/CoatOfArms;Lcom/zarkonnen/airships/CoatOfArms;Z[Lorg/newdawn/slick/Image;FLorg/newdawn/slick/Color;FLcom/zarkonnen/catengine/util/Clr;Lcom/zarkonnen/airships/SpritesheetBundle;Ljava/util/HashSet;Lcom/zarkonnen/airships/SpritesheetBundle;Ljava/util/HashSet;)V";
        List<String> nativeDraw=events(ShipLayers.TracksAndLegs.class,"draw",descriptor);
        check(sitesOf(nativeDraw,"F com/zarkonnen/airships/Module.wheels").size()==6&&sitesOf(nativeDraw,"F com/zarkonnen/airships/Module.legs").size()==2,"track routing field sites verified in shipped bytecode");
    }

    static final class MessageGame extends AirshipGame {
        JSONObject sent;
        @Override public void sendMessage(JSONObject message){sent=message;}
    }
    static void spectatorResearch() throws Exception {
        Tech[][] previousLayout=Tech.layout;Tech.layout=new Tech[1][1];
        MessageGame game=(MessageGame)unsafe(MessageGame.class);
        WorldMap map=newWorld(new StartValues(-1,-1,-1)).map;
        CampaignWorld world=new CampaignWorld(map,null,game);
        Empire player=empire(true,0);world.player=player;map.empires.add(player);
        StrategicScreen ss=(StrategicScreen)unsafe(StrategicScreen.class);putObject(ss,StrategicScreen.class,"w",world);ss.g=game;
        UniScreen us=(UniScreen)unsafe(UniScreen.class);us.g=game;ss.combatUniScreen=us;game.s=us;
        CombatInfo info=(CombatInfo)unsafe(CombatInfo.class);info.attackingFleets=new ArrayList<>();info.defendingFleets=new ArrayList<>();
        info.combat=(Combat)unsafe(Combat.class);us.combat=info.combat;world.combatInfo=info;
        MultiplayerCampaignCombatIntent intent=new MultiplayerCampaignCombatIntent(ss,info,true);us.intent=intent;world.multiplayerCampaignCombatIntent=intent;
        SpectatorResearch button=new SpectatorResearch();
        check(button.visible(us),"nonparticipant with own empire can open research while spectating");
        us.buttons=new ArrayList<>();invoke(us,"arc$clientButtons",game,intent,new CallbackInfo("test",false));
        check(us.buttons.stream().anyMatch(b->b instanceof SpectatorResearch),"real UniScreen constructor handler registers research in native toolbar");
        intent.spectate=false;check(!button.visible(us),"active combatants do not gain spectator research");intent.spectate=true;
        world.player=null;check(!button.visible(us),"empire-less spectators cannot access another player's research");world.player=player;
        button.click(null,us);check(game.s instanceof TechScreen&&((TechScreen)game.s).ss==ss,"button opens native technology screen for own campaign");
        TechScreen techScreen=(TechScreen)game.s;
        Tech tech=(Tech)unsafe(Tech.class);set(tech,Loadable.class,"name","ARC_TEST_TECH");
        Tech.Choice choice=(Tech.Choice)unsafe(Tech.Choice.class);putObject(choice,Tech.Choice.class,"tech",tech);putObject(choice,Tech.Choice.class,"name","ARC_TEST_CHOICE");
        world.mpClient=(Client)unsafe(Client.class);
        // 驱动原生科技格子的点击钩子，确认 giveCommand 仍交给游戏联机发送而非直接写字段。
        Class<?> hookClass=Class.forName("com.zarkonnen.airships.TechScreen$2");
        Constructor<?> constructor=hookClass.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Hook hook=(Hook)constructor.newInstance(techScreen,"setResearch",new Hook.Type[]{Hook.Type.MOUSE_1_CLICKED},choice);
        hook.run(null,new Pt(0,0),Hook.Type.MOUSE_1_CLICKED);
        check(game.sent!=null&&game.sent.getString("type").equals("setResearch")&&game.sent.getInt("empire")==player.id
            &&game.sent.getString("choice").equals("ARC_TEST_CHOICE")&&player.research==null,"native research click sends own empire command through multiplayer transport without local mutation");
        game.s=ss;SpectatorResearch.returnFromResearch(game);check(game.s==us,"closing research returns to same active spectator battle");
        button.click(null,us);world.combatInfo=null;game.s=ss;SpectatorResearch.returnFromResearch(game);
        check(game.s==ss,"battle ending while researching does not restore stale combat screen");
        world.combatInfo=info;game.s=us;button.click(null,us);Screen unrelated=(Screen)unsafe(UniScreen.class);game.s=unrelated;
        SpectatorResearch.returnFromResearch(game);check(game.s==unrelated,"disconnect/resync or other screen is never overwritten");
        String signature="(Lcom/zarkonnen/catengine/Input;Lcom/zarkonnen/airships/MyDraw$State;Lcom/zarkonnen/catengine/util/Pt;Lcom/zarkonnen/catengine/util/Pt;I)V";
        check(sitesOf(events(TechScreen.class,"input",signature),"M com/zarkonnen/airships/StrategicScreen.backgroundTick(I)Z").size()==1,"native research screen continues campaign and combat background ticking");
        Tech.layout=previousLayout;
    }
}
