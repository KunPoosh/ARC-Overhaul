/* EditorDisplayChecks.java — 真正打开显示窗口并将框架遮蔽输入送入原生 UniScreen.input，覆盖持续帧和关闭释放帧。 */
package regression;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.*;
import com.zarkonnen.catengine.util.*;
import net.fabricacs.api.impl.UiBridge;
import net.poosh.arc.client.DisplayOptions;
import java.lang.reflect.*;
import java.util.*;
import static regression.ArcRuntimeProbe.*;

final class EditorDisplayChecks {
    static final class Editor extends StandaloneEditShipIntent {
        int ticks;
        @Override public void tick(Input input,int ms,UniScreen screen){ticks++;}
    }
    static final class Tool extends UniScreen.Tool {
        int ticks,clicks;
        public void draw(MyDraw d,Pt cursor,ScreenMode mode,UniScreen screen){}
        public boolean click(Input input,Pt click,ScreenMode mode,UniScreen screen){clicks++;return true;}
        public boolean rightClick(Input input,Pt click,ScreenMode mode,UniScreen screen){return false;}
        public void tick(Input input,int ms,UniScreen screen){ticks++;}
        public String getLabel(){return "test";}
    }
    static void run() throws Exception {
        AirshipGame game=(AirshipGame)unsafe(AirshipGame.class);
        UniScreen screen=(UniScreen)unsafe(UniScreen.class);
        for(Field f:UniScreen.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())) {
            f.setAccessible(true);
            if(List.class.isAssignableFrom(f.getType()))f.set(screen,new ArrayList<>());
            if(f.getType()==HashSet.class)f.set(screen,new HashSet<>());
        }
        Editor editor=(Editor)unsafe(Editor.class);Tool tool=new Tool();
        screen.g=game;screen.intent=editor;screen.tool=tool;screen.zoom=2;screen.overrideCamera=true;
        game.s=screen;game.prevScreen=screen;screen.setupTime=(TimeOfDay)unsafe(TimeOfDay.class);
        Pt pointer=new Pt(200,MyDraw.TOP_BAR_H+80);
        Input raw=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(proxy,method,args)-> {
            return switch(method.getName()) {
                case "mode" -> new ScreenMode(1280,720,false);
                case "cursor","clicked" -> pointer;
                case "clickButton" -> 1;
                default -> method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:
                    method.getReturnType()==char.class?'\0':method.getReturnType()==Input.class?proxy:null;
            };
        });
        UiBridge.created(game);
        // 使用真实按钮、真实窗口及真实 filter；点击被屏蔽，但编辑器 tick 不应停止。
        DisplayOptions.Button button=new DisplayOptions.Button();button.click(raw,screen);
        check(UiBridge.openedDuringNativeInput(game),"ARC display button opens modal on the existing editor");
        MyDraw.State state=new MyDraw.State();
        for(int frame=0;frame<3;frame++) {
            Input masked=UiBridge.filter(game,raw);
            check(masked.cursor()==null&&masked.clicked()==null,"display modal captures native pointer input");
            screen.input(masked,state,masked.cursor(),masked.clicked(),16);
            check(game.s==screen&&tool.clicks==0,"modal frame retains editor and never places a module");
        }
        check(editor.ticks==3&&tool.ticks==3,"native editor tick continues while modal is open");
        UiBridge.exit(game);
        Input released=UiBridge.filter(game,raw);
        screen.input(released,state,released.cursor(),released.clicked(),16);
        check(game.s==screen&&tool.clicks==0,"modal close release frame stays in editor without click-through");
        // 普通原生鼠标输入仍可编辑，不能用取消整个 input 掩盖异常。
        state.tick(200,pointer);screen.input(raw,state,pointer,pointer,16);
        check(tool.clicks==1,"normal editor click resumes after modal closes");
        String desc="(Lcom/zarkonnen/catengine/Input;Lcom/zarkonnen/airships/MyDraw$State;Lcom/zarkonnen/catengine/util/Pt;Lcom/zarkonnen/catengine/util/Pt;I)V";
        var events=events(UniScreen.class,"input",desc);
        var ys=sitesOf(events,"F com/zarkonnen/catengine/util/Pt.y");
        var scroll=sitesOf(events,"M com/zarkonnen/catengine/Input.scrollAmount()I");
        check(scroll.get(0)<ys.get(10)&&ys.get(9)<scroll.get(0),"first unsafe y read follows first scrollAmount call in shipped bytecode");
        check(scroll.get(5)<ys.get(13)&&ys.get(13)<scroll.get(6),"second unsafe y read belongs to native toolbar scrolling");
    }
}
