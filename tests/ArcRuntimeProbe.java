/* ArcRuntimeProbe.java — 隔离 Fabric 内验证注入、生成阶段和原生保存恢复；资源绘制不接 OpenGL。 */
package regression;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.util.Utils;
import net.poosh.arc.conquest.*;
import net.poosh.arc.mixin.*;
import net.poosh.arc.speed.*;
import net.fabricacs.api.rules.SharedRules;
import net.fabricacs.api.config.*;
import net.fabricacs.api.ui.*;
import org.json.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public final class ArcRuntimeProbe {
    public static int generatedLand, generatedCityLand;
    static int checks;
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; System.out.println("PASS ARC: " + label); }
    static void reject(Runnable action, String label) { try { action.run(); } catch (IllegalArgumentException expected) { check(true,label); return; } throw new AssertionError(label); }
    static Object unsafe(Class<?> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return ((sun.misc.Unsafe)field.get(null)).allocateInstance(type);
    }
    static Object field(Object object, Class<?> type, String name) throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object); }
    static void set(Object object, Class<?> type, String name, Object value) throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value); }
    static Object invoke(Object object,String name,Object...args) throws Exception {
        for(Method method:object.getClass().getDeclaredMethods()) if(method.getName().contains(name)) {
            method.setAccessible(true);try{return method.invoke(object,args);}catch(InvocationTargetException ex){throw (Exception)ex.getCause();}
        }
        throw new NoSuchMethodException(name);
    }
    static void entry(Class<? extends Loadable> type,String name) throws Exception {
        Object value=unsafe(type);set(value,Loadable.class,"name",name);Loadable.map.computeIfAbsent(type,k->new HashMap<>()).put(name,value);
    }
    static JSONObject grid(String text){return new JSONObject().put("yl",2).put("xl",2).put("data",text);}
    static JSONObject mapData(){return new JSONObject().put("worldID","arc-probe").put("lang","en").put("empires",new JSONArray()).put("terrainFeatures",new JSONArray())
        .put("water",grid("1111")).put("roads2",grid("0000")).put("connections",grid("0000")).put("cityOwnership",grid("-1 -1 -1 -1 "))
        .put("height",new JSONArray().put(0).put(0).put(0).put(0));}
    static MapSize base;
    static SharedRules rules;
    /** 共享规则是「开局设置 + AI 舰队规则」合成的同一份 JSON，发布先后决定生成期读到什么。 */
    static void publish(StartValues values, FleetPlan plan) {
        JSONObject start=values.json();JSONObject merged=new JSONObject();
        for(java.util.Iterator<String> keys=start.keys();keys.hasNext();){String key=keys.next();merged.put(key,start.get(key));}
        rules.update(merged.put(FleetPlan.KEY,plan.json()));
    }
    static CampaignWorld newWorld(StartValues values) throws Exception { return newWorld(values,FleetPlan.empty()); }
    static CampaignWorld newWorld(StartValues values,FleetPlan plan) throws Exception {
        publish(values,plan);
        WorldMap map=new WorldMap(mapData(),null,null);
        CampaignWorld world=new CampaignWorld(map,null,null);world.playerEmpireIndex=-1;
        new WorldGenScreen(world,null);
        // 只调用实际注入的生成准备函数，保留原生阶段对象用于后面的定向执行。
        invoke(map,"arc$prepare",new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>("test",false));
        return world;
    }
    static Empire empire(boolean human,int id) throws Exception {
        Empire empire=(Empire)unsafe(Empire.class);empire.playerControlled=human;set(empire,Empire.class,"cities",new ArrayList<City>());
        empire.cities.add(new City(id,35+id*25,35+id*25,"capital",false,30,null,1));empire.setMoney(777);
        return empire;
    }
    static Object stage(WorldMap map,int index) throws Exception {return ((Object[])field(map,WorldMap.class,"setupStages"))[index];}
    static boolean runStage(Object stage,int index,WorldMap map) throws Exception {return (Boolean)invoke(stage,"run",index,map);}
    static String placeAll() throws Exception {
        CampaignWorld world=newWorld(new StartValues(3,3,100));WorldMap map=world.map;
        map.water=new boolean[128][128];map.setupCityNames=new ArrayList<>(List.of("a","b","c","d","e","f"));
        map.empires.add(empire(true,0));map.empires.add(empire(false,1));map.r=new GuardedRandom(89412);
        Object stage=stage(map,2);int count=(Integer)invoke(stage,"getSize");check(count==10,"native placement stage uses expanded capacity");
        for(int i=0;i<count;i++)runStage(stage,i,map);
        check(map.empires.get(0).cities.size()==6&&map.empires.get(0).cities.stream().filter(c->!c.isTown).count()==3,"native placement creates exactly three cities and three towns for human");
        check(map.empires.get(1).cities.size()==3&&map.empires.get(1).cities.stream().filter(c->!c.isTown).count()==1,"native placement keeps AI at one city and two towns");
        Set<Integer> ids=new HashSet<>();StringBuilder result=new StringBuilder();
        for(Empire empire:map.empires)for(City city:empire.cities){check(ids.add(city.id),"unique native settlement ID "+city.id);result.append(city.id).append(':').append(city.x).append(',').append(city.y).append(',').append(city.isTown).append(',').append(city.income).append(';');}
        check(java.util.stream.IntStream.range(0,9).allMatch(id -> map.getCity(id)!=null)
            && map.getCity(9)==null,"native city cache contains contiguous IDs 0 through 8 only");
        return result.toString();
    }
    /** 连通陆地大小：直接调用被注入的 ARC 检查函数本体，避免在测试里重写一遍算法。 */
    static int landMass(Object stage,WorldMap map,int x,int y,int cap) throws Exception {
        return (Integer)invoke(stage,"arc$landMass",map,x,y,cap);
    }
    static int landMinimum(Object stage,WorldMap map) throws Exception {
        return (Integer)invoke(stage,"arc$landMinimum",map);
    }
    /**
     * 孤立陆地保护：造一张「两块大陆 + 大量孤立小格」的水域图。
     * 原生 findMapLocationSpot 只要求落点不是水，孤立的一格陆点完全合格，于是新增城镇会落在
     * 与任何地块都不相连的小岛上；ARC 的额外定居点会重新选址直到连在一块像样的陆地上。
     */
    static void checkLandPlacement() throws Exception {
        base=new MapSize(new JSONObject().put("name","SMALLISH").put("gridSize",4).put("empires",2).put("nests",0));
        Loadable.map.get(MapSize.class).put(base.name,base);
        CampaignWorld world=newWorld(new StartValues(2,1,0,0));
        WorldMap map=world.map;
        int grid=map.size.gridSize;
        map.water=new boolean[grid][grid];
        for(boolean[] row:map.water)Arrays.fill(row,true);
        for(int y=21;y<50;y++)for(int x=21;x<50;x++)map.water[y][x]=false;   // 玩家首都所在大陆 (35,35)
        for(int y=52;y<81;y++)for(int x=52;x<81;x++)map.water[y][x]=false;   // AI 首都所在大陆 (60,60)
        for(int y=1;y<grid-1;y+=3)for(int x=1;x<grid-1;x+=3)if(map.water[y][x])map.water[y][x]=false;
        map.setupCityNames=new ArrayList<>(List.of("a","b","c","d"));
        map.empires.add(empire(true,0));map.empires.add(empire(false,1));
        map.r=new GuardedRandom(4711);
        Object stage=stage(map,2);
        int minimum=landMinimum(stage,map);
        check(minimum>=32,"ARC landmass minimum scales with the map ("+minimum+" tiles)");
        check(landMass(stage,map,35,35,minimum)>=minimum,"continent around the capital is accepted");
        check(landMass(stage,map,100,100,minimum)<minimum,"an isolated single-tile island is below the minimum");
        // 对照实验：直接调用原生选址函数，确认孤立小格确实是它的合格结果（这就是缺陷的来源）。
        WorldMapAccess access=(WorldMapAccess)map;
        int specks=0,demoSeed=-1;
        for(int seed=0;seed<16;seed++){
            Utils.Pair<Integer,Integer> raw=access.arc$findMapLocationSpot(new GuardedRandom(seed),7,map.empires.get(0));
            if(raw!=null&&!map.water[raw.b][raw.a]&&landMass(stage,map,raw.a,raw.b,minimum)<minimum){specks++;if(demoSeed<0)demoSeed=seed;}
        }
        check(specks>0,"native spot finder accepts isolated single-tile land ("+specks+"/16 seeds, no ARC guard)");
        // A/B 对照：同一个种子、同一个原生选址函数，唯一差别是中间有没有 ARC 的陆地检查。
        Utils.Pair<Integer,Integer> rawSpot=access.arc$findMapLocationSpot(new GuardedRandom(demoSeed),7,map.empires.get(0));
        check(rawSpot!=null&&landMass(stage,map,rawSpot.a,rawSpot.b,minimum)<minimum,
            "control without the guard: seed "+demoSeed+" places the settlement on an isolated island");
        @SuppressWarnings("unchecked") Utils.Pair<Integer,Integer> guarded=(Utils.Pair<Integer,Integer>)invoke(stage,"arc$landSpot",map,new GuardedRandom(demoSeed),7,map.empires.get(0),1,map);
        check(guarded!=null&&landMass(stage,map,guarded.a,guarded.b,minimum)>=minimum,
            "with the ARC guard: the same seed is retried onto a real landmass ("+guarded.a+","+guarded.b+")");
        int count=(Integer)invoke(stage,"getSize");
        for(int i=0;i<count;i++)runStage(stage,i,map);
        for(City city:map.empires.get(0).cities)check(!map.water[city.y][city.x]&&landMass(stage,map,city.x,city.y,minimum)>=minimum,
            "player settlement "+city.id+" at "+city.x+","+city.y+" sits on a real landmass");
    }
    /** 在真实放置后验证领土描边；只构造小块归属网格，不冒充完整地形或 GPU 验收。 */
    static void checkTerritoryIds() throws Exception {
        StartValues[] settings = {
            new StartValues(-1,-1,-1), new StartValues(-1,-1,42), new StartValues(1,2,-1),
            new StartValues(2,8,-1), new StartValues(1,0,-1), new StartValues(4,8,-1),
            new StartValues(2,0,-1), new StartValues(-1,1,-1)
        };
        for (int empireCount : new int[]{2,4}) {
            for (int humanParity=0; humanParity<2; humanParity++) {
                String defaults = null;
                for (int i=0; i<settings.length; i++) {
                    String snapshot = placeAndTrace(empireCount,humanParity,settings[i],false);
                    if (i==0) defaults=snapshot;
                    if (i==1 || i==2) check(snapshot.equals(defaults),
                        "cash-only/explicit defaults preserve IDs, placement, types, income and RNG: "
                            + empireCount + "/" + humanParity + "/" + settings[i]);
                }
            }
        }
        // 人类首槽之后强制 AI 选址失败，随后恢复陆地；失败不应消耗连续 ID。
        placeAndTrace(2,0,new StartValues(2,8,-1),true);
    }
    static String placeAndTrace(int empireCount,int humanParity,StartValues values,boolean failAI) throws Exception {
        base=new MapSize(new JSONObject().put("name","SMALLISH").put("gridSize",8)
            .put("empires",empireCount).put("nests",0));
        Loadable.map.get(MapSize.class).put(base.name,base);
        WorldMap map=newWorld(values).map;
        int gridSize=map.size.gridSize;
        map.water=new boolean[gridSize][gridSize];
        map.setupCityNames=new ArrayList<>(List.of("a","b","c","d","e","f"));
        for (int i=0; i<empireCount; i++) map.empires.add(empire(i%2==humanParity,i));
        map.r=new GuardedRandom(89412+humanParity);
        Object placement=stage(map,2);
        int slots=(Integer)invoke(placement,"getSize");
        for (int i=0; i<slots; i++) {
            if (failAI && i==1) for (boolean[] row:map.water) Arrays.fill(row,true);
            runStage(placement,i,map);
            if (failAI && i==1) for (boolean[] row:map.water) Arrays.fill(row,false);
        }
        String label=empireCount+"/"+humanParity+"/"+values+"/failAI="+failAI;
        Set<Integer> ids=new TreeSet<>();
        ArrayList<City> settlements=new ArrayList<>();
        StringBuilder snapshot=new StringBuilder();
        for (int i=0; i<empireCount; i++) {
            Empire empire=map.empires.get(i);
            int expected=empire.playerControlled ? values.cityCount()+values.townCount(base.townsPerEmpire)
                : 1+base.townsPerEmpire;
            if (failAI && i==1) expected--;
            check(empire.cities.size()==expected,label+" settlement count for empire "+i);
            check(empire.cities.stream().filter(city -> !city.isTown).count()
                ==(empire.playerControlled ? values.cityCount() : 1),label+" city types for empire "+i);
            for (City city:empire.cities) {
                settlements.add(city);
                ids.add(city.id);
                snapshot.append(i).append(':').append(city.id).append(':').append(city.x).append(',')
                    .append(city.y).append(',').append(city.isTown).append(',').append(city.income).append(';');
            }
        }
        check(ids.size()==settlements.size(),label+" unique IDs");
        check(java.util.stream.IntStream.range(0,settlements.size()).allMatch(ids::contains),label+" contiguous IDs");
        check(settlements.stream().allMatch(city -> map.getCity(city.id)==city)
            && map.getCity(settlements.size())==null,label+" city cache matches settlement identities");
        // 保留真实放置得到的 ID，把测试领土排列为隔开的单格，避免高数量场景夹具重叠。
        // 这里不模拟影响力分配；直接调用游戏字节码，检查 ID 空洞是否截断领土描边。
        int[][] ownership=new int[3][settlements.size()*3+2];
        for (int[] row:ownership) Arrays.fill(row,-1);
        for (int i=0; i<settlements.size(); i++) ownership[1][i*3+1]=settlements.get(i).id;
        ArrayList<ShapeUtils.Area> areas=new ArrayList<>();
        ShapeUtils.cityOwnershipAreas(ownership,new HashMap<>(),new ArrayList<>(),areas);
        Set<Integer> tracedIds=new HashSet<>();
        for (ShapeUtils.Area area:areas) tracedIds.add(area.identifier);
        check(areas.size()==settlements.size() && tracedIds.equals(ids),label+" native territory area for every settlement");
        return snapshot.append("rng=").append(map.r.nextInt()).toString();
    }
    /** 合成一支 AI 舰队登记进原生 Loadable 注册表；真实游戏里这些对象来自 ConstructionStrategy 数据目录。 */
    static ConstructionStrategy fleet(String name) throws Exception {
        ConstructionStrategy strategy=(ConstructionStrategy)unsafe(ConstructionStrategy.class);
        set(strategy,Loadable.class,"name",name);strategy.displayName=name;
        // 原生 forCharge 会读 enabled 与 charges；Unsafe 分配出来的对象这两处是空的。
        strategy.enabled=true;strategy.charges=new HashSet<>();strategy.requiredCharge=null;
        Loadable.map.computeIfAbsent(ConstructionStrategy.class,k->new HashMap<>()).put(name,strategy);
        return strategy;
    }
    /** 只填 isPlayerEmpire 会读的字段：谁声明了这个势力索引，这个势力就归玩家。 */
    static StrategicSetupInfo setupInfo(int... claimed) throws Exception {
        StrategicSetupInfo info=(StrategicSetupInfo)unsafe(StrategicSetupInfo.class);
        ArrayList<StrategicPlayerInfo> players=new ArrayList<>();
        for(int index:claimed){StrategicPlayerInfo player=(StrategicPlayerInfo)unsafe(StrategicPlayerInfo.class);player.claimedEmpireIndex=index;players.add(player);}
        set(info,StrategicSetupInfo.class,"strategicPlayerInfos",players);
        return info;
    }
    /** 一张已经固化好舰队规则的地图；共享规则在 WorldGenScreen 里冻结，与真实生成路径一致。 */
    static WorldMap fleetMap(FleetPlan plan) throws Exception {
        publish(new StartValues(-1,-1,-1),plan);
        WorldMap map=new WorldMap(mapData(),null,null);
        CampaignWorld world=new CampaignWorld(map,null,null);world.playerEmpireIndex=-1;
        new WorldGenScreen(world,null);
        map.r=new GuardedRandom(24680);
        map.setupInfos=new ArrayList<>(List.of(setupInfo(0)));
        return map;
    }
    static FleetPlan planOf(Object... spec) {
        Map<String,FleetPlan.Entry> entries=new LinkedHashMap<>();
        for(int i=0;i<spec.length;i+=2)entries.put((String)spec[i],(FleetPlan.Entry)spec[i+1]);
        return FleetPlan.of(entries);
    }
    /**
     * AI 舰队设置：规则解析与校验、设置窗口、以及生成期真实选择函数。
     * 夹具里没有游戏自带的 ConstructionStrategy 数据，舰队目录用登记进 Loadable 的合成条目代替；
     * 选择逻辑、共享规则读取、原生随机数都是真实的。
     */
    static void checkFleetOptions() throws Exception {
        ConstructionStrategy alpha=fleet("arc-alpha"),beta=fleet("arc-beta"),gamma=fleet("arc-gamma");
        check(Loadable.hasOfName(ConstructionStrategy.class,"arc-alpha")&&Loadable.all(ConstructionStrategy.class).size()>=3,
            "AI fleets are enumerated through the native Loadable registry");
        FleetPlan stored=planOf("arc-alpha",new FleetPlan.Entry(FleetPlan.Mode.FORCE,3),"arc-beta",new FleetPlan.Entry(FleetPlan.Mode.BAN,0));
        check(FleetPlan.read(new JSONObject().put(FleetPlan.KEY,stored.json())).equals(stored),"AI fleet rules round-trip through JSON");
        check(FleetPlan.read(new JSONObject()).isVanilla(),"missing fleet key reads as vanilla");
        check(FleetPlan.of(Map.of("arc-alpha",FleetPlan.Entry.ALLOW)).isVanilla(),"an explicit allow entry is normalised away");
        check(FleetPlan.of(Map.of()).json().length()==0,"empty rules serialise to an empty object");
        reject(()->new FleetPlan.Entry(FleetPlan.Mode.FORCE,0),"force enable without a country count rejected");
        reject(()->new FleetPlan.Entry(FleetPlan.Mode.ALLOW,FleetPlan.MAX_COUNTRIES+1),"country count above the cap rejected");
        reject(()->FleetPlan.of(Map.of("",new FleetPlan.Entry(FleetPlan.Mode.BAN,0))),"empty fleet name rejected");
        reject(()->FleetPlan.read(new JSONObject().put(FleetPlan.KEY,new JSONObject().put("arc-alpha",new JSONObject().put("mode","maybe").put("count",1)))),"unknown fleet mode rejected");
        reject(()->FleetPlan.read(new JSONObject().put(FleetPlan.KEY,new JSONObject().put("arc-alpha","ban"))),"non-object fleet entry rejected");
        reject(()->FleetPlan.read(new JSONObject().put(FleetPlan.KEY,new JSONObject().put("arc-alpha",new JSONObject().put("mode","ban").put("count","2")))),"string country count rejected");
        reject(()->FleetPlan.validate(new JSONObject().put("cities",1)),"unknown key in the fleet config rejected");
        publish(new StartValues(-1,-1,-1),FleetPlan.empty());
        check(rules.current().values().has("cities")&&rules.current().values().has(FleetPlan.KEY),
            "one shared rule declaration carries starting values and fleet rules together");
        Lang.currentLocale=Locale.forLanguageTag("chi");check(FleetOptions.title().contains("AI 舰队"),"fleet settings title follows Chinese");
        Lang.currentLocale=Locale.ENGLISH;check(FleetOptions.title().contains("AI fleets"),"fleet settings title follows English");
        check(FleetOptions.window()!=null,"AI fleet settings window builds on the real config handle");
        List<ConstructionStrategy> pool=List.of(alpha,beta,gamma);
        // 默认规则完全不介入：连返回的对象身份都和原版一致，也不消耗随机数。
        WorldMap untouched=fleetMap(FleetPlan.empty());
        check(FleetOptions.choose(pool,beta,1,untouched)==beta&&untouched.r.nextInt()==new GuardedRandom(24680).nextInt(),
            "vanilla rules return the native pick and leave the map random stream untouched");
        // 强制禁用：谁都可能被选中，唯独被禁的那支不会。
        WorldMap banned=fleetMap(planOf("arc-beta",new FleetPlan.Entry(FleetPlan.Mode.BAN,0)));
        Set<ConstructionStrategy> seen=new HashSet<>();
        for(int i=0;i<64;i++)seen.add(FleetOptions.choose(pool,beta,1,banned));
        check(!seen.contains(beta)&&seen.contains(alpha)&&seen.contains(gamma),"a force-disabled AI fleet never appears");
        check(FleetOptions.choose(pool,beta,1,fleetMap(planOf("arc-alpha",new FleetPlan.Entry(FleetPlan.Mode.BAN,0),
            "arc-beta",new FleetPlan.Entry(FleetPlan.Mode.BAN,0),"arc-gamma",new FleetPlan.Entry(FleetPlan.Mode.BAN,0))))==beta,
            "banning every candidate falls back to the native pick instead of failing");
        // 强制启用：恰好这么多国家，用完就停，也不会再被随机抽到。
        WorldMap forcedMap=fleetMap(planOf("arc-alpha",new FleetPlan.Entry(FleetPlan.Mode.FORCE,3)));
        int forced=0;
        for(int i=1;i<=3;i++)if(FleetOptions.choose(pool,beta,i,forcedMap)==alpha)forced++;
        check(forced==3,"a force-enabled AI fleet reaches exactly its country count ("+forced+"/3)");
        // 名额用完之后的势力继续随机，但被强制占用的舰队不会重新回到随机池里。
        Set<ConstructionStrategy> rest=new HashSet<>();
        for(int i=4;i<=64;i++)rest.add(FleetOptions.choose(pool,beta,i,forcedMap));
        check(!rest.contains(alpha),"a force-enabled AI fleet is not also handed out at random");
        check(rest.contains(beta)&&rest.contains(gamma),"the remaining AI fleets still cover the random pool ("+rest.size()+" of 2)");
        // 玩家自己的国家不参与替换，也不占用名额。
        WorldMap playerFirst=fleetMap(planOf("arc-alpha",new FleetPlan.Entry(FleetPlan.Mode.FORCE,1)));
        check(FleetOptions.choose(pool,beta,0,playerFirst)==beta,"the player's own country keeps the native fleet");
        check(FleetOptions.choose(pool,beta,1,playerFirst)==alpha,"the first AI country still takes the forced fleet");
        // 混合三态：强制优先于随机池，禁用的永远不出现。
        WorldMap mixed=fleetMap(planOf("arc-alpha",new FleetPlan.Entry(FleetPlan.Mode.FORCE,1),"arc-beta",new FleetPlan.Entry(FleetPlan.Mode.BAN,0)));
        check(FleetOptions.choose(pool,beta,1,mixed)==alpha,"force enable outranks the random pool");
        check(FleetOptions.choose(pool,beta,2,mixed)==gamma,"once the forced quota is used up the banned fleet is still skipped");
        // MOD 卸载后配置里还留着名字：只清掉名额，不能让生成失败。
        WorldMap removed=fleetMap(planOf("arc-removed",new FleetPlan.Entry(FleetPlan.Mode.FORCE,2),"arc-beta",new FleetPlan.Entry(FleetPlan.Mode.BAN,0)));
        check(FleetOptions.choose(pool,beta,1,removed)==gamma,"an unloaded force-enabled fleet is skipped instead of failing generation");
        // 真实配置文件读写。
        ModConfig fleetConfig=(ModConfig)field(null,FleetOptions.class,"config");
        fleetConfig.save(fleetConfig.read(),new JSONObject().put(FleetPlan.KEY,stored.json()));fleetConfig.reload();
        check(FleetPlan.read(fleetConfig.read().data()).equals(stored),"AI fleet settings persist through the real config file");
        fleetConfig.save(fleetConfig.read(),new JSONObject().put(FleetPlan.KEY,FleetPlan.empty().json()));fleetConfig.reload();
        check(FleetPlan.read(fleetConfig.read().data()).isVanilla(),"emptying the fleet config restores vanilla");
    }
    /** 精确按名字与参数个数调用，避免 invoke() 的子串匹配在重载方法上选错。 */
    static Object call(Object object,String name,Object...args) throws Exception {
        for(Method method:object.getClass().getDeclaredMethods()){
            if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;
            method.setAccessible(true);
            try{return method.invoke(object,args);}catch(InvocationTargetException ex){throw (Exception)ex.getCause();}
        }
        throw new NoSuchMethodException(name);
    }
    @SuppressWarnings("unchecked") static List<UiNode> kids(UiNode node) throws Exception {
        return (List<UiNode>)field(node,UiNode.class,"children");
    }
    static String kindOf(UiNode node) throws Exception { return ((Enum<?>)field(node,UiNode.class,"kind")).name(); }
    static String textOf(UiNode node) throws Exception {
        Object value=field(node,UiNode.class,"text");
        return value==null?"":String.valueOf(((java.util.function.Supplier<?>)value).get());
    }
    static boolean enabledOf(UiNode node) throws Exception {
        return ((java.util.function.BooleanSupplier)field(node,UiNode.class,"enabled")).getAsBoolean();
    }
    /**
     * 设置窗口的形状：直接遍历真实 UiWindow/UiNode 树，核对每行确实是「名字 + 三态选择 + 出场国家数」，
     * 而不是只看窗口能不能构造出来。组件字段是包内可见的，这里用反射读取。
     */
    static void checkFleetWindowShape() throws Exception {
        Lang.currentLocale=Locale.ENGLISH;
        List<ConstructionStrategy> fleets=FleetOptions.loadedFleets();
        check(!fleets.isEmpty(),"the fleet catalogue is non-empty for the window check");
        UiWindow window=FleetOptions.window();
        check(window.title().equals(FleetOptions.title())&&window.modal()&&window.footer()!=null,
            "fleet window is a modal window with the bilingual title and an action footer");
        List<UiNode> body=kids(window.content());
        check(body.size()==3&&kindOf(body.get(0)).equals("LABEL")&&kindOf(body.get(2)).equals("LABEL"),
            "window body is explanation + list + status");
        check(kindOf(body.get(1)).equals("SCROLL"),"the catalogue sits in a scroll area so long fleet lists stay reachable");
        List<UiNode> rows=kids(kids(body.get(1)).get(0));
        check(rows.size()==fleets.size(),"one row per loaded AI fleet ("+rows.size()+"/"+fleets.size()+")");
        ConstructionStrategy first=fleets.get(0);
        List<UiNode> row=kids(rows.get(0));
        check(row.size()==3&&kindOf(row.get(0)).equals("LABEL")&&kindOf(row.get(1)).equals("BUTTON")&&kindOf(row.get(2)).equals("COLUMN"),
            "each row is a label, a three-state chooser and a country-count field");
        check(textOf(row.get(0)).equals(FleetOptions.displayName(first)),"row label is the fleet display name ("+textOf(row.get(0))+")");
        check(FleetOptions.sourceTag(first).startsWith(first.name),"the row tooltip names the fleet internally and where it came from ("+FleetOptions.sourceTag(first)+")");
        check(textOf(row.get(1)).equals(FleetOptions.text("Allow (random)","允许（随机出现）")),"the chooser shows the current state ("+textOf(row.get(1))+")");
        List<UiNode> countField=kids(row.get(2));
        check(countField.size()==3&&kindOf(countField.get(0)).equals("SPACE")&&kindOf(countField.get(1)).equals("TEXT"),
            "the country count cell is a spacer, an editable field and its hint line");
        check(((Integer)field(countField.get(0),UiNode.class,"size"))>0,
            "the country count field is nudged down by a spacer of "+field(countField.get(0),UiNode.class,"size")+" px");
        check(textOf(countField.get(1)).equals("1"),"the country count starts at a valid 1, never 0");
        check(!enabledOf(row.get(2)),"the country count stays disabled while the fleet is only allowed");
        check(((java.util.function.Supplier<?>)field(countField.get(2),UiNode.class,"text")).get().equals(""),
            "an untouched country count shows no error line");
        List<UiNode> footer=kids(window.footer());
        check(footer.size()==4,"the footer is status plus three action rows");
        List<UiNode> bulk=kids(footer.get(2));
        check(bulk.size()==2&&textOf(bulk.get(0)).equals(FleetOptions.text("Force disable all","全部强制禁用"))
            &&textOf(bulk.get(1)).equals(FleetOptions.text("Force enable all","全部强制启用")),
            "the footer carries force-disable-all and force-enable-all buttons");
    }
    /**
     * 草稿会话：驱动窗口背后的真实 Editor，覆盖校验、提交写盘、未加载条目的保留与丢弃。
     */
    static void checkFleetEditor() throws Exception {
        fleet("arc-alpha");
        ModConfig fleetConfig=(ModConfig)field(null,FleetOptions.class,"config");
        Class<?> editorType=Class.forName("net.poosh.arc.conquest.FleetOptions$Editor");
        Constructor<?> constructor=editorType.getDeclaredConstructor();constructor.setAccessible(true);
        // 先放一条来自已卸载 MOD 的规则，确认窗口不会把它丢掉。
        FleetPlan ghost=FleetPlan.of(Map.of("arc-ghost",new FleetPlan.Entry(FleetPlan.Mode.BAN,0)));
        fleetConfig.save(fleetConfig.read(),new JSONObject().put(FleetPlan.KEY,ghost.json()));fleetConfig.reload();
        Object editor=constructor.newInstance();
        check(!(Boolean)call(editor,"isDirty")&&(Boolean)call(editor,"isValid"),"a freshly opened draft is clean and valid");
        check(((FleetPlan)call(editor,"draft")).entry("arc-ghost").equals(new FleetPlan.Entry(FleetPlan.Mode.BAN,0)),
            "a rule for a fleet that is no longer loaded is carried through the draft");
        call(editor,"setMode","arc-alpha",FleetPlan.Mode.FORCE);
        check((Boolean)call(editor,"isDirty"),"changing a fleet state marks the draft dirty");
        check(((Integer)call(editor,"count","arc-alpha"))==1,"force enable starts from a valid count of 1");
        call(editor,"setCount","arc-alpha","0");
        check(!(Boolean)call(editor,"isValid")&&call(editor,"save")!=null,"a force-enabled fleet with count 0 refuses to save");
        call(editor,"setCount","arc-alpha","33");
        check(!(Boolean)call(editor,"isValid"),"a country count above the cap refuses to save");
        call(editor,"setCount","arc-alpha","abc");
        check(!(Boolean)call(editor,"isValid"),"a non-numeric country count refuses to save");
        call(editor,"setCount","arc-alpha","3");
        check((Boolean)call(editor,"isValid"),"a valid country count unblocks saving");
        check(call(editor,"save")==null,"saving a valid draft reports success");
        FleetPlan written=FleetPlan.read(fleetConfig.read().data());
        check(written.entry("arc-alpha").equals(new FleetPlan.Entry(FleetPlan.Mode.FORCE,3))
            &&written.entry("arc-ghost").equals(new FleetPlan.Entry(FleetPlan.Mode.BAN,0)),
            "the saved file holds the new rule and keeps the unloaded one");
        check(((FleetPlan)call(editor,"draft")).equals(written),"the draft matches what was written");
        check(!(Boolean)call(editor,"isDirty"),"a saved draft is no longer dirty");
        call(editor,"setMode","arc-alpha",FleetPlan.Mode.BAN);
        call(editor,"reload");
        check(((FleetPlan)call(editor,"draft")).entry("arc-alpha").equals(new FleetPlan.Entry(FleetPlan.Mode.FORCE,3)),
            "reload discards the draft and re-reads the file");
        call(editor,"restoreDefaults");
        check(((FleetPlan)call(editor,"draft")).isVanilla(),"restoring defaults clears every fleet rule, including unloaded ones");
        check(call(editor,"save")==null&&FleetPlan.read(fleetConfig.read().data()).isVanilla(),"the saved file is vanilla again");
        // 数字框的人机工效：先删干净再输入。
        Object taps=constructor.newInstance();
        call(taps,"setMode","arc-alpha",FleetPlan.Mode.FORCE);
        call(taps,"setCount","arc-alpha","");
        check(((String)call(taps,"countText","arc-alpha")).isEmpty(),"clearing the country count leaves the box empty instead of snapping back");
        check(((String)call(taps,"countError","arc-alpha")).isEmpty(),"an empty country count shows no error while you retype");
        check((Boolean)call(taps,"isValid"),"an empty country count does not block saving");
        check(((Integer)call(taps,"count","arc-alpha"))==1,"an empty country count still means the previous number");
        call(taps,"setCount","arc-alpha","3");
        check(((String)call(taps,"countText","arc-alpha")).equals("3"),"a new number can be typed straight after clearing");
        call(taps,"setCount","arc-alpha","33");
        check(!((String)call(taps,"countError","arc-alpha")).isEmpty()&&!(Boolean)call(taps,"isValid"),
            "a country count above the cap is reported and blocks saving");
        call(taps,"setCount","arc-alpha","abc");
        check(!((String)call(taps,"countError","arc-alpha")).isEmpty(),"a non-numeric country count is reported");
        call(taps,"setCount","arc-alpha","2");
        call(taps,"setCount","arc-alpha","");
        call(taps,"setCount","arc-beta","5");
        check(((String)call(taps,"countText","arc-alpha")).equals("2"),
            "typing in another box restores the number deleted from the first one");
        call(taps,"setCount","arc-alpha","");
        call(taps,"setAll",FleetPlan.Mode.BAN);
        check(((String)call(taps,"countText","arc-alpha")).equals("2"),"pressing a button also restores the deleted number");
        call(taps,"setMode","arc-alpha",FleetPlan.Mode.FORCE);
        call(taps,"setCount","arc-alpha","");
        check(call(taps,"save")==null,"an empty box can still be saved");
        check(FleetPlan.read(fleetConfig.read().data()).entry("arc-alpha").equals(new FleetPlan.Entry(FleetPlan.Mode.FORCE,2)),
            "an empty box saves the previous number, never 0");
        // 批量按钮。
        Object bulkEditor=constructor.newInstance();
        call(bulkEditor,"setAll",FleetPlan.Mode.BAN);
        FleetPlan banned=(FleetPlan)call(bulkEditor,"draft");
        check(banned.entries().size()==FleetOptions.loadedFleets().size()
            &&banned.entries().values().stream().allMatch(e->e.mode()==FleetPlan.Mode.BAN),
            "force disable all marks every loaded AI fleet ("+banned.entries().size()+" of "+FleetOptions.loadedFleets().size()+")");
        call(bulkEditor,"setAll",FleetPlan.Mode.FORCE);
        FleetPlan allForced=(FleetPlan)call(bulkEditor,"draft");
        check(allForced.entries().size()==FleetOptions.loadedFleets().size()
            &&allForced.entries().values().stream().allMatch(e->e.mode()==FleetPlan.Mode.FORCE&&e.count()>=1),
            "force enable all marks every loaded AI fleet with a usable country count");
        check(((FleetPlan)call(bulkEditor,"draft")).entries().size()!=0,"bulk rules survive into the draft");
    }
    /**
     * 重定向落点检查：解析原生 {@code WorldMap$2.run} 的字节码，确认 {@code ordinal = 1} 指的就是
     * 「取一支舰队交给 Empire 构造器」那个 {@code ArrayList.get}。
     *
     * <p>原生方法里有四个 {@code ArrayList.get}：城市名、舰队、科技选项、英雄。只有舰队那个的结果会被
     * {@code CHECKCAST ConstructionStrategy}，所以「invokevirtual ArrayList.get 紧跟 checkcast 到
     * ConstructionStrategy」这条指令序列能唯一定位它；断言它的序号恰好是 1，就等于断言注入配置正确。
     * 类加载器给出的是归档里的原始字节（资源不会经过 Mixin 改写），这正是要对着游戏发行版本核对的份。</p>
     */
    static void checkFleetHookTarget() throws Exception {
        byte[] code;
        Class<?> type=Class.forName("com.zarkonnen.airships.WorldMap$2");
        try(java.io.InputStream in=type.getResourceAsStream("/com/zarkonnen/airships/WorldMap$2.class")) {
            check(in!=null,"WorldMap$2 class file is readable from the game archive");code=in.readAllBytes();
        }
        // 常量池一遍：Utf8 文本、Class 的名字索引、NameAndType 的名字索引、成员引用的 (类,名字) 索引。
        Map<Integer,String> utf=new HashMap<>();
        Map<Integer,Integer> classNames=new HashMap<>();
        Map<Integer,Integer> nameAndTypes=new HashMap<>();
        Map<Integer,int[]> refs=new HashMap<>();
        int p=8,count=readU2(code,p);p+=2;
        for(int index=1;index<count;index++){
            int tag=code[p++]&0xff;
            switch(tag){
                case 1->{int length=readU2(code,p);p+=2;utf.put(index,new String(code,p,length,java.nio.charset.StandardCharsets.UTF_8));p+=length;}
                case 7->{classNames.put(index,readU2(code,p));p+=2;}
                case 8->p+=2;
                case 9,10,11->{refs.put(index,new int[]{readU2(code,p),readU2(code,p+2)});p+=4;}
                case 12->{nameAndTypes.put(index,readU2(code,p));p+=4;}
                case 3,4->p+=4;
                case 5,6->{p+=8;index++;}
                case 15->p+=3;
                case 16,19,20->p+=2;
                case 17,18->p+=4;
                default->throw new IllegalStateException("Unknown constant pool tag "+tag);
            }
        }
        int strategyClass=-1,listGet=-1;
        for(Map.Entry<Integer,Integer> entry:classNames.entrySet())
            if("com/zarkonnen/airships/ConstructionStrategy".equals(utf.get(entry.getValue())))strategyClass=entry.getKey();
        for(Map.Entry<Integer,int[]> entry:refs.entrySet()){
            String owner=utf.get(classNames.getOrDefault(entry.getValue()[0],-1));
            String member=utf.get(nameAndTypes.getOrDefault(entry.getValue()[1],-1));
            // ArrayList 上带这个名字的成员只有 get(int)，常量池里也就是那一个引用。
            if("java/util/ArrayList".equals(owner)&&"get".equals(member))listGet=entry.getKey();
        }
        check(strategyClass>0&&listGet>0,"WorldMap$2.bytecode references ArrayList.get and ConstructionStrategy");
        byte[] run=codeAttribute(code,utf,p,"run","(ILcom/zarkonnen/airships/WorldMap;)Z");
        check(run!=null,"native WorldMap$2.run bytecode is parsed from the class file");
        List<Integer> sites=new ArrayList<>();
        for(int i=0;i+2<run.length;i++)if((run[i]&0xff)==0xb6&&readU2(run,i+1)==listGet)sites.add(i);
        check(sites.size()==4,"native run() has four ArrayList.get call sites ("+sites.size()+")");
        int fleetOrdinal=-1;
        for(int index=0;index<sites.size();index++){
            int at=sites.get(index);
            if(at+6<run.length&&(run[at+3]&0xff)==0xc0&&readU2(run,at+4)==strategyClass)fleetOrdinal=index;
        }
        check(fleetOrdinal==1,"the strategy pick is ArrayList.get call site #1, matching the injected ordinal ("+fleetOrdinal+")");
    }
    /** 取出某个方法的 Code 属性字节；属性长度是 u4，其余按 JVM 规范逐段跳过。 */
    static byte[] codeAttribute(byte[] code,Map<Integer,String> utf,int from,String name,String descriptor){
        int p=from+6;                                  // access_flags, this_class, super_class
        int interfaces=readU2(code,p);p+=2+2*interfaces;
        int fields=readU2(code,p);p+=2;
        for(int i=0;i<fields;i++){int attrs=readU2(code,p+6);p+=8;for(int a=0;a<attrs;a++)p+=6+(int)readU4(code,p+2);}
        int methods=readU2(code,p);p+=2;
        for(int i=0;i<methods;i++){
            String methodName=utf.get(readU2(code,p+2)),methodDescriptor=utf.get(readU2(code,p+4));
            int attrs=readU2(code,p+6);int q=p+8;
            for(int a=0;a<attrs;a++){
                String attribute=utf.get(readU2(code,q));long length=readU4(code,q+2);
                if(name.equals(methodName)&&descriptor.equals(methodDescriptor)&&"Code".equals(attribute)){
                    int size=(int)readU4(code,q+10);        // max_stack, max_locals, code_length
                    return Arrays.copyOfRange(code,q+14,q+14+size);
                }
                q+=6+(int)length;
            }
            p=q;
        }
        return null;
    }
    static int readU2(byte[] code,int at){return ((code[at]&0xff)<<8)|(code[at+1]&0xff);}
    static long readU4(byte[] code,int at){return ((long)(code[at]&0xff)<<24)|((code[at+1]&0xff)<<16)|((code[at+2]&0xff)<<8)|(code[at+3]&0xff);}
    /**
     * 相对坐标航点：在真实内核类上直接调用注入后的处理函数。
     *
     * <p>不启动战斗。注入只读写 {@code strafeTo}、{@code attackTarget} 与三个 {@code @Unique}
     * 锚点字段，因此用 Unsafe 造一个 Crewman、把目标 x 直接写进 PhysicsRect 就足以验证行为。
     * 核心不变量是「航点与目标的偏移量恒定」：原生正是靠比较航点与目标包围盒判断这一趟是否作废
     * （第 2562 行），偏移量恒定就意味着目标移动再也无法作废航点。</p>
     */
    static void checkAircraftStrafe() throws Exception {
        Class<?> crewman=Class.forName("com.zarkonnen.airships.Crewman");
        StringBuilder members=new StringBuilder();
        Method follow=null;
        // 处理器名可能被 Mixin 修饰，按参数签名（int, Combat, Combat$Side, boolean, CallbackInfoReturnable）定位更稳。
        for(Method m:crewman.getDeclaredMethods()){
            if(m.getName().contains("arc$")) members.append(m.getName()).append('/').append(m.getParameterCount()).append(' ');
            Class<?>[] ps=m.getParameterTypes();
            if(ps.length==5&&ps[0]==int.class&&ps[4].getName().endsWith("CallbackInfoReturnable")) follow=m;
        }
        check(follow!=null,"real mixin transformation: Crewman carries the ARC strafe handler ["+members+"]");
        follow.setAccessible(true);
        Class<?> airship=Class.forName("com.zarkonnen.airships.Airship");
        Class<?> physics=Class.forName("com.zarkonnen.airships.PhysicsRect");
        Class<?> pointType=Class.forName("com.zarkonnen.catengine.util.Pt");
        Constructor<?> newPoint=pointType.getDeclaredConstructor(double.class,double.class);
        Field pointX=pointType.getField("x");
        Object callback=Class.forName("org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable")
            .getDeclaredConstructor(String.class,boolean.class).newInstance("arc-probe",false);
        Object crew=unsafe(crewman);
        Object target=unsafe(airship);
        Object first=newPoint.newInstance(1000.0,100.0);
        set(target,physics,"x",500.0);
        set(crew,crewman,"strafeTo",first);
        set(crew,crewman,"attackTarget",target);
        follow.invoke(crew,16,null,null,false,callback);
        check(field(crew,crewman,"strafeTo")==first,"the first frame only records the anchor and leaves the point alone");
        set(target,physics,"x",520.0);follow.invoke(crew,16,null,null,false,callback);
        Object shifted=field(crew,crewman,"strafeTo");
        check(shifted!=first&&pointX.getDouble(shifted)==1020.0,
            "the aim point follows the target's +20 px move and is a new immutable Pt instance");
        follow.invoke(crew,16,null,null,false,callback);
        check(pointX.getDouble(field(crew,crewman,"strafeTo"))==1020.0,"a stationary target does not keep shifting the point");
        // 目标连续移动 1000px：原生在 2×strafeOvershoot=400px 处就会作废航点，这里偏移量必须恒定。
        for(int step=1;step<=50;step++){
            set(target,physics,"x",500.0+20*step);follow.invoke(crew,16,null,null,false,callback);
        }
        double moved=pointX.getDouble(field(crew,crewman,"strafeTo"))-1500.0;
        check(moved==500.0,"the target-relative offset survives 1000 px of target movement (native discards it after 400)");
        // 原版重选航点（目标离开原点位后的常见结果）→ 本帧只做对齐，不平移。
        Object repick=newPoint.newInstance(4000.0,50.0);
        set(crew,crewman,"strafeTo",repick);set(target,physics,"x",1600.0);
        follow.invoke(crew,16,null,null,false,callback);
        check(field(crew,crewman,"strafeTo")==repick,"a native re-pick is adopted without shifting on the same frame");
        set(target,physics,"x",1610.0);follow.invoke(crew,16,null,null,false,callback);
        check(pointX.getDouble(field(crew,crewman,"strafeTo"))==4010.0,"the adopted point follows from the next frame on");
        // 换目标（母舰 fireAt 改写、原目标阵亡后重选最近邻）→ 不平移，交还原版重新决策。
        Object other=unsafe(airship);set(other,physics,"x",9000.0);
        set(crew,crewman,"attackTarget",other);set(target,physics,"x",2000.0);
        follow.invoke(crew,16,null,null,false,callback);
        check(pointX.getDouble(field(crew,crewman,"strafeTo"))==4010.0,"a new target re-anchors instead of dragging the old run");
        // 无目标/无航点：原版巡逻分支与一切非攻击状态必须完全不受影响。
        set(crew,crewman,"attackTarget",null);set(target,physics,"x",2100.0);
        follow.invoke(crew,16,null,null,false,callback);
        check(pointX.getDouble(field(crew,crewman,"strafeTo"))==4010.0,"no target leaves the patrol point untouched");
        set(crew,crewman,"strafeTo",null);set(crew,crewman,"attackTarget",target);
        follow.invoke(crew,16,null,null,false,callback);
        check(field(crew,crewman,"strafeTo")==null,"no aim point is not an error");
    }
    public static void main(String[] args) throws Exception {
        for(String name:List.of("WorldMap","WorldMap$2","WorldMap$3","WorldMap$4","GameSetupScreen","CampaignWorld","WorldGenScreen","Crewman")) {
            Class<?> type=Class.forName("com.zarkonnen.airships."+name);
            check(Arrays.stream(type.getDeclaredMethods()).anyMatch(m->m.getName().contains("arc$")||m.getName().contains("acbric$")),"real mixin transformation: "+name);
        }
        rules=(SharedRules)field(null,ArcRules.class,"rules");check(rules!=null,"real ARC initializer declared rules");
        Lang.currentLocale=Locale.forLanguageTag("chi");check(StartingOptions.title().contains("玩家"),"Chinese follows game");
        Lang.currentLocale=Locale.ENGLISH;check(StartingOptions.title().contains("Player"),"English follows game");
        check(StartingOptions.window()!=null,"settings window builds using real config editor");
        reject(()->new StartValues(0,0,0),"zero cities rejected");reject(()->new StartValues(5,0,0),"cities upper bound");
        reject(()->new StartValues(1,9,0),"town upper bound");reject(()->new StartValues(1,0,-2),"cash lower bound");
        reject(()->StartValues.read(new JSONObject().put("cities","2").put("towns",0).put("cash",0)),"string values rejected");
        reject(()->new StartValues(1,0,0,-1),"negative research rejected");
        reject(()->new StartValues(1,0,0,StartValues.MAX_RESEARCH+1),"research upper bound");
        check(StartValues.read(new JSONObject().put("cities",-1).put("towns",-1).put("cash",-1)).research()==0,"missing research key reads as zero");
        check(!new StartValues(-1,-1,-1,0).grantsResearch()&&new StartValues(-1,-1,-1,1).grantsResearch(),"zero research preserves vanilla");
        // 研发规模诊断：这个游戏里 1 点研发到底值多少，直接读原生统计值而不是猜。
        try {
            Object bonuses=Class.forName("com.zarkonnen.airships.BonusSet").getDeclaredConstructor().newInstance();
            if(EmpireStat.BASE_RESEARCH_COST!=null&&EmpireStat.RESEARCH_COST_MULTIPLIER!=null){
                int scaleBase=(Integer)EmpireStat.BASE_RESEARCH_COST.get((BonusSet)bonuses);
                double scaleMult=(Double)EmpireStat.RESEARCH_COST_MULTIPLIER.get((BonusSet)bonuses);
                long tier0=(long)(scaleBase*scaleMult*5600.0*StrictMath.pow(EmpireStat.RESEARCH_COST_EXPONENT,0));
                System.out.println("INFO ARC: tier0 tech cost = "+tier0+" research points, tier1 = "
                    +((long)(scaleBase*scaleMult*5600.0*StrictMath.pow(EmpireStat.RESEARCH_COST_EXPONENT,1))));
                // 开局研发点的上限必须和游戏自身的技术成本同量级，否则进度条上根本看不出变化。
                check(StartValues.MAX_RESEARCH>=tier0/4,
                    "starting research range is within scale of a real technology ("+StartValues.MAX_RESEARCH+" vs tier0 "+tier0+")");
            }
        } catch(Exception scaleFailure) { System.out.println("INFO ARC: research scale unavailable: "+scaleFailure); }
        Loadable.map=new HashMap<>();Loadable.alls=null;
        entry(DifficultyLevel.class,"NORMAL");entry(MonsterSetting.class,"DEFAULT");entry(SeaLevelSetting.class,"MIXED");
        entry(FrequencySetting.class,"DEFAULT");entry(TechSpeedSetting.class,"NORMAL");entry(EraModifier.class,"NO_BONUS");entry(StrategicEra.class,"INITIAL");
        entry(HeraldicStyle.class,"city");
        Loadable.map.put(LoadingQuote.class,new HashMap<>());
        base=new MapSize(new JSONObject().put("name","SMALLISH").put("gridSize",4).put("empires",2).put("nests",0));
        Loadable.map.computeIfAbsent(MapSize.class,k->new HashMap<>()).put(base.name,base);
        CampaignWorld vanilla=newWorld(new StartValues(-1,-1,-1));
        check(vanilla.map.size==base,"defaults preserve global size identity");
        CampaignWorld cashOnly=newWorld(new StartValues(-1,-1,0));check(cashOnly.map.size==base,"cash only does not alter map");
        CampaignWorld world=newWorld(new StartValues(3,3,12345,777));WorldMap map=world.map;
        check(map.size!=base&&map.size.townsPerEmpire==5&&base.townsPerEmpire==2,"per-map capacity expanded, global unchanged");
        check(map.size.gridSize==base.gridSize&&map.size.empires==base.empires,"geometry and empire count unchanged");
        rules.update(new StartValues(1,0,0,0).json());check(StartingOptions.forMap(map).equals(new StartValues(3,3,12345,777)),"candidate change cannot change frozen map rules");
        check(newWorld(new StartValues(-1,-1,-1)).map.size==base,"next vanilla campaign not polluted");
        reject(()->MapLayout.read(base,new JSONObject().put(MapLayout.KEY,new JSONObject().put("version",2).put("slots",5))),"unknown layout version rejected");
        reject(()->MapLayout.read(base,new JSONObject().put(MapLayout.KEY,new JSONObject().put("version",1).put("slots",500))),"invalid layout capacity rejected");
        SavedStateOutPipe initialState=new SavedStateOutPipe();JSONObject saved=map.toJSON(initialState);initialState.compileAndGetHash();
        check(saved.getJSONObject(MapLayout.KEY).getInt("slots")==5,"real native serializer includes capacity");
        WorldMap read=new WorldMap(saved,null,new JSONObjectInPipe(initialState.toJSON()));check(read.size.townsPerEmpire==5&&base.townsPerEmpire==2,"real native JSON constructor restores capacity without global mutation");
        check(((GenerationAccess)read).arc$options()==null,"load does not arm generation logic");
        SavedStateOutPipe state=new SavedStateOutPipe();JSONObject stateMap=read.toJSON(state);state.compileAndGetHash();
        WorldMap restored=new WorldMap(stateMap,null,new JSONObjectInPipe(state.toJSON()));
        check(restored.size.townsPerEmpire==5,"native binary state reconstruction retains capacity");
        check(StartingOptions.forMap(restored).equals(new StartValues(3,3,12345,777)),"native reconstruction retains shared rules");
        Path saveDir=Files.createDirectories(AGame.getGameDirectory().toPath().resolve("saves")).resolve("arc-test.json");
        IODirectory output=new IODirectory(saveDir.toFile(),map.worldID);JSONObject worldJson=world.toJSON(output);
        output.registerWithoutVersion(id->worldJson,"world");output.write();
        var input=OpenGameMission.load(saveDir.toFile());CampaignWorld loaded=new CampaignWorld(input.a,null,true,input.b);
        check(loaded.map.size.townsPerEmpire==5,"native disk save/load retains layout");
        check(StartingOptions.forMap(loaded.map).equals(new StartValues(3,3,12345,777)),"native disk save/load uses saved values");
        map.empires.add(empire(true,0));map.empires.add(empire(false,1));
        Object townStage=stage(map,2);map.r=new GuardedRandom(123);
        check(runStage(townStage,5,map),"AI expanded slot skipped by actual native stage");
        check(map.empires.get(1).cities.size()==1&&map.r.nextInt()==new GuardedRandom(123).nextInt(),"skipped AI slot does not mutate RNG or cities");
        City extra=new City(2,40,40,"extra",true,1,null,0);
        set(map.difficulty,DifficultyLevel.class,"playerIncome",33);
        int income=(Integer)invoke(townStage,"arc$settlementIncome",map,true,extra,map.r,0,map);
        check(!extra.isTown&&income==33,"extra city typed before land generation and uses native player income");
        City town=new City(6,50,50,"town",true,1,null,0);
        income=(Integer)invoke(townStage,"arc$settlementIncome",map,true,town,map.r,4,map);
        check(town.isTown&&income>=5&&income<=17,"remaining player slots keep native town income");
        City aiTown=new City(3,60,60,"AI",true,1,null,0);
        invoke(townStage,"arc$settlementIncome",map,false,aiTown,map.r,1,map);check(aiTown.isTown,"AI town never upgraded");
        Object rituals=stage(map,3);check((Integer)invoke(rituals,"arc$boundedRituals",2,1,0,map)==0,"zero towns has zero ritual sites");
        map.empires.get(1).cities.add(aiTown);check((Integer)invoke(rituals,"arc$boundedRituals",2,1,0,map)==1,"ritual count capped to available towns");
        // 全水地图确保原生选址失败；应在首次保存前给出可操作错误，不能假装达到设置数量。
        map.water=new boolean[128][128];for(boolean[] row:map.water)Arrays.fill(row,true);
        try{runStage(townStage,0,map);throw new AssertionError("missing placement accepted");}
        catch(IllegalStateException expected){check(expected.getMessage().contains("ARC"),"native placement failure produces explicit ARC error");}
        Empire human=map.empires.get(0);human.cities.add(extra);human.cities.add(new City(4,1,1,"city",false,30,null,0));
        human.cities.add(town);human.cities.add(new City(8,1,1,"town2",true,8,null,0));human.cities.add(new City(10,1,1,"town3",true,8,null,0));
        map.campaignWorldDuringGen=world;world.setupPlayer();
        check(human.getMoney()==12345&&map.empires.get(1).getMoney()==777,"actual CREATED hook sets human cash only");
        // 开局没有选中研究时，原生「选择科技」命令（CampaignWorld 的 set-research 执行器，字节码
        // 223-312）会先把 researchPoints 覆盖成 partialResearchPoints 里新研究的值（新研究通常没有
        // 记录，即 0），然后才把 unassignedResearchPoints 整池加进去并清零。所以直接写
        // researchPoints 会在玩家第一次点科技时被清掉——这就是"发了研发点却拿不到"的原因。
        check(human.research==null,"campaign starts without a selected research");
        check(human.unassignedResearchPoints==777&&human.researchPoints==0&&map.empires.get(1).unassignedResearchPoints==0,
            "actual CREATED hook banks human research in the native unassigned pool only");
        set(human,Empire.class,"partialResearchPoints",new HashMap<>());
        human.researchPoints=777;human.unassignedResearchPoints=0;
        human.researchPoints=human.partialResearchPoints.containsKey(null)?human.partialResearchPoints.get(null):0;
        check(human.researchPoints==0,"direct researchPoints grant is wiped by the native selection command (old bug)");
        human.researchPoints=0;human.unassignedResearchPoints=777;
        human.researchPoints+=human.unassignedResearchPoints;human.unassignedResearchPoints=0;
        check(human.researchPoints==777&&human.unassignedResearchPoints==0,
            "banked pool is poured into the first selected tech by the native transfer");
        human.setMoney(2);human.unassignedResearchPoints=0;human.researchPoints=0;world.setupPlayer();
        check(human.getMoney()==2&&human.unassignedResearchPoints==0&&human.researchPoints==0,
            "second setupPlayer cannot regrant cash or research");
        map.campaignWorldDuringGen=null;
        CampaignWorld zero=newWorld(new StartValues(1,0,0));zero.map.empires.add(empire(true,0));zero.map.empires.add(empire(false,1));
        check(runStage(stage(zero.map,2),0,zero.map),"zero player towns skips first slot");
        zero.map.campaignWorldDuringGen=zero;zero.setupPlayer();check(zero.map.empires.get(0).getMoney()==0,"zero cash is valid");
        check(zero.map.empires.get(0).researchPoints==0&&zero.map.empires.get(0).unassignedResearchPoints==0,
            "zero research leaves both native pools untouched");
        ModConfig config=(ModConfig)field(null,StartingOptions.class,"config");
        config.save(config.read(),new StartValues(2,1,456,900).json());config.reload();check(StartValues.read(config.read().data()).equals(new StartValues(2,1,456,900)),"real config persistence");
        check(placeAll().equals(placeAll()),"same seed and settings produce identical native placements with isolated asset fixtures");
        check(generatedLand==14&&generatedCityLand==4,"actual native placement reaches land hook with correct city types");
        checkTerritoryIds();
        checkLandPlacement();
        checkEffectiveSpeed();
        checkGroundLoad();
        checkAircraftStrafe();
        checkFleetOptions();
        checkFleetWindowShape();
        checkFleetEditor();
        checkFleetHookTarget();
        System.out.println("ARC RUNTIME PASS: "+checks+" checks");
    }
    /**
     * 有效速度：面板速度必须计入弹簧的接地摩擦 λ。
     * 原版 {@code getSpeed()} 只减二次空气阻力，陆行舰面板速度因此比实际战斗稳态速度高 2–6 倍
     * （无头实测数据见 tools/arc/analysis/landship-speed-analysis.md）。
     */
    static void checkEffectiveSpeed() throws Exception {
        Class<?> panelClass=Class.forName("com.zarkonnen.airships.ShipEditorUtils");
        Method handler=null;
        StringBuilder injected=new StringBuilder();
        for(Method m:panelClass.getDeclaredMethods()) {
            if(m.getName().contains("$")) injected.append(m.getName()).append(' ');
            if(m.getName().contains("arc$groundedSpeed")) handler=m;
        }
        System.out.println("ARC speed mixin methods on ShipEditorUtils: "+injected);
        check(handler!=null,"real mixin transformation: ShipEditorUtils carries the ARC effective-speed handler");
        double mass=898, air=0.0025716827585966387, force=1.1, vanillaSpeed=0.6901595115018447;
        check(EffectiveSpeed.steadySpeed(force,898,air,0)==StrictMath.sqrt(force/mass/air),
            "zero ground friction reproduces the native terminal speed exactly");
        check(Math.abs(EffectiveSpeed.steadySpeed(force,898,air,0)-vanillaSpeed)<1e-12,
            "panel formula still matches the measured walker getSpeed ("+vanillaSpeed+")");
        double one=EffectiveSpeed.perTickFactor(new double[]{0.004},EffectiveSpeed.TICK_MS);
        double four=EffectiveSpeed.perTickFactor(new double[]{0.004,0.004,0.004,0.004},EffectiveSpeed.TICK_MS);
        check(Math.abs(one-StrictMath.pow(0.996,16))<1e-12,"one grounded spring decays speed by the native (1-xFriction)^ms");
        check(Math.abs(four-one*StrictMath.pow(0.9996,16)*StrictMath.pow(0.9996,16)*StrictMath.pow(0.9996,16))<1e-12,
            "further grounded springs only contribute 10% of xFriction");
        double lambda=EffectiveSpeed.groundFrictionRate(new double[]{0.004,0.004,0.004,0.004},EffectiveSpeed.TICK_MS);
        check(lambda>0.004&&lambda<0.006,"four leg springs land near the native xFriction ("+lambda+")");
        check(EffectiveSpeed.groundFrictionRate(new double[0],EffectiveSpeed.TICK_MS)==0,"no springs means no ground friction");
        double grounded=EffectiveSpeed.steadySpeed(force,898,air,lambda);
        check(Math.abs(grounded-0.2347)<0.03,"grounded speed lands on the measured combat value ("+grounded+" vs 0.2347)");
        check(grounded<0.5*vanillaSpeed,"ground friction more than halves the reported landship speed");
        ModuleType legType=moduleType(1.1,List.of(legSpec(0.004),legSpec(0.004)),List.of());
        ModuleType trackType=moduleType(0.0,List.of(),List.of(new Spring(0,110,85,0.06,0.002,0.003)));
        Airship walker=(Airship)unsafe(Airship.class);walker.type=ShipType.LANDSHIP;
        walker.modules=new ArrayList<com.zarkonnen.airships.Module>(List.of(module(legType),module(trackType)));
        check(EffectiveSpeed.springFrictions(walker).length==3,"leg and module springs are both collected from a landship");
        double vanilla=walker.getMainMapSpeed(BonusSet.empty()),reported=EffectiveSpeed.mainMapSpeed(walker,BonusSet.empty());
        check(vanilla>0&&reported>0&&reported<vanilla,"reported panel speed is strictly lower once ground friction is counted");
        Airship flyer=(Airship)unsafe(Airship.class);flyer.type=ShipType.AIRSHIP;flyer.modules=walker.modules;
        check(EffectiveSpeed.mainMapSpeed(flyer,BonusSet.empty())==flyer.getMainMapSpeed(BonusSet.empty()),
            "airships keep the native panel speed because their springs never rest on the ground");
    }
    static ModuleType moduleType(double propulsion,List<Leg.Spec> legs,List<Spring> springs) throws Exception {
        ModuleType type=(ModuleType)unsafe(ModuleType.class);
        set(type,ModuleType.class,"propulsion",BonusableValue.of(Double.valueOf(propulsion)));
        set(type,ModuleType.class,"canResupplyInCombat",BonusableValue.of(Boolean.TRUE));
        set(type,ModuleType.class,"maxXSpeed",BonusableValue.of(Double.valueOf(10000.0)));
        set(type,ModuleType.class,"legSpecs",new ArrayList<Leg.Spec>(legs));
        set(type,ModuleType.class,"springs",new ArrayList<Spring>(springs));
        return type;
    }
    static com.zarkonnen.airships.Module module(ModuleType type) throws Exception {
        com.zarkonnen.airships.Module m=(com.zarkonnen.airships.Module)unsafe(com.zarkonnen.airships.Module.class);m.type=type;return m;
    }
    static Leg.Spec legSpec(double friction) {
        return new Leg.Spec(false,1.5,-0.5,70,70,70,18,0,80,1000,false,new Spring(0,110,85,0.06,friction,0.005),null,null,null,null,null,null,0,0);
    }

    /**
     * 接地载荷：悬浮石与龟甲（Shell Armour，每格 lift 35）提供的升力抵消掉的重力不该再产生摩擦。
     *
     * <p>无头实测（tools/arc/.work/speedprobe/SpeedProbe10，真实游戏数据）：把 Strider 的 27 格装甲全换成龟甲后
     * 升力 945、质量 1008（升力已超过重量），可测得的接地摩擦速率仍是 0.00476，裸舰是 0.00499 ——
     * 原版完全无视升力，速度没有任何提升，只有额外重量让舰船更慢。本注入把摩擦按载荷比例缩放：
     * 载荷 0 时弹簧不再夺走速度，载荷 1（无升力）时返回值与原版逐位相同。</p>
     */
    static void checkGroundLoad() throws Exception {
        check(GroundLoad.loadFraction(1.0,0.0)==1.0,"no lift means the whole weight rests on the ground");
        check(GroundLoad.loadFraction(1.0,0.4)==0.6,"partial lift removes exactly its share of the ground load");
        check(GroundLoad.loadFraction(1.0,2.0)==0.0,"lift beyond the weight cannot make the load negative");
        check(GroundLoad.loadFraction(0.0,0.0)==1.0,"a weightless ship keeps the vanilla load fraction");
        Method moduleHandler=null,legHandler=null;
        StringBuilder handlers=new StringBuilder();
        for(Method m:Class.forName("com.zarkonnen.airships.Module").getDeclaredMethods())
            if(m.getName().contains("arc$loadScaledFriction")){moduleHandler=m;handlers.append("Module=").append(m.getName()).append(' ');}
        for(Method m:Class.forName("com.zarkonnen.airships.Leg").getDeclaredMethods())
            if(m.getName().contains("arc$loadScaledFriction")){legHandler=m;handlers.append("Leg=").append(m.getName()).append(' ');}
        System.out.println("ARC ground-load handlers: "+handlers);
        check(moduleHandler!=null,"real mixin transformation: Module carries the ARC ground-load handler");
        check(legHandler!=null,"real mixin transformation: Leg carries the ARC ground-load handler");
        Airship plain=liftShip(1000,0);
        check(GroundLoad.loadFraction(plain,null)==1.0,"a spring-only landship still rests its full weight on the ground");
        check(GroundLoad.scaleFrictionBase(1.0-0.004,GroundLoad.loadFraction(plain,null))==1.0-0.004,
            "a ship without lift keeps the vanilla friction base bit for bit");
        Airship turtle=liftShip(1000,20);
        check(turtle.getLift()==700,"turtle-shell tiles contribute their native lift ("+turtle.getLift()+")");
        check(GroundLoad.loadFraction(turtle,null)==0.0,"lift above the weight takes the landship off its springs");
        check(GroundLoad.scaleFrictionBase(1.0-0.004,GroundLoad.loadFraction(turtle,null))==1.0,
            "a fully lifted landship has no ground friction left to apply");
        Airship half=liftShip(1000,7);
        double load=GroundLoad.loadFraction(half,null);
        check(load>0.4&&load<0.6,"partial turtle armour leaves roughly half the weight on the ground ("+load+")");
        double halfBase=GroundLoad.scaleFrictionBase(1.0-0.004,load);
        check(halfBase>1.0-0.004&&halfBase<1.0,"half load halves the friction base ("+halfBase+")");
        double[] frictions={0.004};
        check(StrictMath.abs(EffectiveSpeed.perTickFactor(frictions,EffectiveSpeed.TICK_MS,load)
            -StrictMath.pow(GroundLoad.scaleFrictionBase(1.0-0.004,load),EffectiveSpeed.TICK_MS))<1e-15,
            "panel per-tick factor is the physics base scaled by the same load");
        check(EffectiveSpeed.groundFrictionRate(frictions,EffectiveSpeed.TICK_MS,0.0)==0.0,
            "zero load means zero ground friction rate in the panel too");
    }
    /** 造一艘只有装甲升力的陆行舰：装甲格全用龟甲（lift 35/格），不带模块与弹簧。 */
    static Airship liftShip(int weight,int shellTiles) throws Exception {
        Airship ship=(Airship)unsafe(Airship.class);
        ship.type=ShipType.LANDSHIP;
        ship.modules=new ArrayList<com.zarkonnen.airships.Module>();
        ship.tiles=new ArrayList<Tile>();
        set(ship,Airship.class,"weight",Integer.valueOf(weight));
        if(shellTiles>0){
            ArmourType shell=(ArmourType)unsafe(ArmourType.class);
            putFinal(shell,ArmourType.class,"lift",BonusableValue.of(Integer.valueOf(35)));
            for(int i=0;i<shellTiles;i++){
                ArmourPlate plate=(ArmourPlate)unsafe(ArmourPlate.class);
                plate.type=shell;plate.hp=60;
                Tile tile=(Tile)unsafe(Tile.class);
                tile.armour=plate;
                ship.tiles.add(tile);
            }
        }
        return ship;
    }
    /** final 字段只能用 Unsafe 直接写（ArmourType.lift 是 final）。 */
    static void putFinal(Object target,Class<?> type,String name,Object value) throws Exception {
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        sun.misc.Unsafe u=(sun.misc.Unsafe)unsafeField.get(null);
        u.putObject(target,u.objectFieldOffset(type.getDeclaredField(name)),value);
    }
}
