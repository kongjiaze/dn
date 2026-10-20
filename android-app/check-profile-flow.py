"""Check real profile storage, slot-independent routes, selected-role order and optional jobs."""
from pathlib import Path
import ast,subprocess,tempfile
ROOT=Path(__file__).resolve().parent;JAVA=Path('D:/JDK/openjdk-17/bin')
tree=ast.parse((ROOT/'check-combat-policy.py').read_text(encoding='utf8'))
stubs=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in n.targets)))
stubs.pop('cn/kongj/blackstone/CombatTest.java')
stubs['android/content/Context.java']='''package android.content;public class Context {java.util.Map<String,SharedPreferences> prefs=new java.util.HashMap<>();public SharedPreferences getSharedPreferences(String key,int mode){return prefs.computeIfAbsent(key,k->new SharedPreferences());}public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}}'''
stubs['android/content/res/AssetManager.java']='''package android.content.res;public class AssetManager {public static String root;public java.io.InputStream open(String name)throws java.io.IOException{return new java.io.FileInputStream(new java.io.File(root,name));}}'''
stubs['android/graphics/Paint.java']='package android.graphics;public class Paint{public Paint(int flags){}}'
stubs['android/graphics/BitmapFactory.java']='package android.graphics;public class BitmapFactory{public static Bitmap decodeStream(java.io.InputStream in){try{return javax.imageio.ImageIO.read(in)==null?null:new Bitmap();}catch(java.io.IOException e){return null;}}}'
stubs['android/view/View.java']='package android.view;public class View{public CharSequence description;public View(android.content.Context context){}public void setContentDescription(CharSequence value){description=value;}}'
stubs['android/text/TextUtils.java']='package android.text;public class TextUtils{public static String join(CharSequence delimiter,CharSequence[] values){return String.join(delimiter,values);}}'
# Compile the actual guide constructor without Android Canvas rendering.
guide=(ROOT/'src/cn/kongj/blackstone/SkillGuideView.java').read_text(encoding='utf-8')
stubs['cn/kongj/blackstone/SkillGuideView.java']=guide[:guide.index(' protected void onDraw(')]+'\n}'
stubs['cn/kongj/blackstone/ProfileTest.java']='''package cn.kongj.blackstone;
import java.util.*;
class SlotFlow extends GameFlow {
 List<String> events=new ArrayList<>();int fatigue;SlotFlow(RunnerService r){super(r);}
 void selectAndEnter(){events.add("select"+currentRole);fatigue=0;}
 int readFatigue(){return fatigue;}
 void recoverFatigue(){events.add("recover"+currentRole);fatigue+=300;}
 void repairEquipment(){events.add("repair"+currentRole);}
 void enterBlackstone(int before){events.add("enter"+currentRole);townFlow=new TownFlow(this){void run(){((SlotFlow)flow).events.add("town"+flow.currentRole);}};}
 void brushUntilEmpty(boolean resumed){events.add("battle"+currentRole);}
 void returnToRoles(){events.add("return"+currentRole);}
}
class RouteFlow extends GameFlow {RouteFlow(RunnerService r){super(r);}long combatClock(){return r.virtualTime;}}
class TraceRunner extends RunnerService {
 List<String> points=new ArrayList<>();boolean failUltimate;long readyAt;
 void combatTap(String label,int x,int y)throws Exception{points.add(x+","+y);super.combatTap(label,x,y);}
 void combatTap(String label,int x,int y,int gap)throws Exception{
  if(failUltimate&&x==1720){failUltimate=false;throw new Exception("ultimate dispatch unavailable");}
  points.add(x+","+y);super.combatTap(label,x,y,gap);
 }
 int ultimateState()throws Exception{return virtualTime<readyAt?SkillCooldown.COOLING:super.ultimateState();}
}
class JobFlow extends GameFlow {List<String> operations=new ArrayList<>();JobFlow(RunnerService r){super(r);currentRole=1;}void openMenu(){}void transition(String from,String target,String label,int x,int y,long timeout,String message){operations.add(label);}void require(String k,long timeout,String message){}}
class JobTown extends TownFlow {JobTown(GameFlow f){super(f);}void filters(){}void synthesize(){((JobFlow)flow).operations.add("craft");}void store(){((JobFlow)flow).operations.add("store");}}
public class ProfileTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static RolePlan plan(int profession){RolePlan p=new RolePlan();p.enabled=true;p.profession=profession;return p;}
 public static void main(String[] args)throws Exception {
  android.content.res.AssetManager.root=args[0];
  android.content.Context context=new android.content.Context();ScriptConfig cfg=new ScriptConfig(context);check(!cfg.configured()&&cfg.first()==7,"fresh install has no private default account or selected roles");
  RolePlan[] draft=cfg.roles();draft[0]=plan(RolePlan.SAGE);draft[5]=plan(RolePlan.SAGE);draft[5].recover=false;draft[5].craft=false;draft[5].store=false;draft[5].position=-2;cfg.saveRoles(draft);
  ScriptConfig reopened=new ScriptConfig(context);check(reopened.first()==1&&reopened.last()==6&&reopened.next(1)==6&&reopened.next(6)==7,"nonconsecutive role selection persists in order");check(!reopened.role(6).recover&&!reopened.role(6).craft&&!reopened.role(6).store&&reopened.role(6).forward()==2800,"all role settings persist");
  for(int i=0;i<30;i++)draft[0].movePosition(1);check(draft[0].position==10&&draft[0].forward()==4000,"arrow limit prevents extreme forward");for(int i=0;i<50;i++)draft[0].movePosition(-1);check(draft[0].position==-10&&draft[0].forward()==2000,"back arrow decreases movement from default");
  RolePlan[] invalid=new ScriptConfig(new android.content.Context()).roles();try{ScriptConfig.validate(invalid);throw new AssertionError("none selected");}catch(IllegalArgumentException expected){}invalid[0].enabled=true;try{ScriptConfig.validate(invalid);throw new AssertionError("profession missing");}catch(IllegalArgumentException expected){}
  android.content.Context old=new android.content.Context();old.getSharedPreferences("route",0).putInt("startRole",2).putInt("endRole",5).putInt("role4Forward",2400);ScriptConfig upgraded=new ScriptConfig(old);check(upgraded.first()==2&&upgraded.last()==5&&upgraded.role(2).profession==RolePlan.SAGE&&upgraded.role(4).forward()==2300,"existing device positions migrate with one sorceress correction without exposing names");check(upgraded.role(2).recover&&upgraded.role(2).craft&&upgraded.role(2).store,"default task switches checked");
  check(upgraded.role(3).forward()==2800&&new ScriptConfig(old).role(3).forward()==2800,"legacy elementalist moves 200ms less, exactly once");
  android.content.Context installed=new android.content.Context();android.content.SharedPreferences previous=installed.getSharedPreferences("profile",0);previous.putInt("revision",1).putInt("role1.profession",RolePlan.ELEMENTALIST).putInt("role1.baseForward",3200).putInt("role1.position",2).putBoolean("role1.enabled",true).putBoolean("role1.craft",false).putInt("role6.profession",RolePlan.SAGE).putInt("role6.baseForward",3300);
  ScriptConfig calibrated=new ScriptConfig(installed);check(calibrated.role(1).baseForward==3000&&calibrated.role(1).position==2&&calibrated.role(1).forward()==3200&&!calibrated.role(1).craft&&calibrated.role(6).forward()==3300,"elementalist correction preserves user arrow offset/jobs and other professions");check(new ScriptConfig(installed).role(1).forward()==3200,"reopening cannot repeatedly shorten elementalist movement");
  check(plan(RolePlan.ELEMENTALIST).forward()==2800,"fresh elementalist default matches corrected position");
  check(upgraded.role(4).forward()==2300&&new ScriptConfig(old).role(4).forward()==2300,"legacy sorceress moves 100ms less exactly once");
  android.content.Context mageDevice=new android.content.Context();android.content.SharedPreferences magePrefs=mageDevice.getSharedPreferences("profile",0);magePrefs.putInt("revision",1).putInt("elementalistPositionRevision",1).putInt("role1.profession",RolePlan.SORCERESS).putInt("role1.baseForward",2200).putInt("role1.position",-2).putBoolean("role1.enabled",true).putBoolean("role1.recover",false).putBoolean("role1.store",false).putInt("role4.profession",RolePlan.SORCERESS).putInt("role4.baseForward",0).putInt("role6.profession",RolePlan.ALCHEMIST).putInt("role6.baseForward",2400);
  ScriptConfig mageCorrected=new ScriptConfig(mageDevice);check(mageCorrected.role(1).baseForward==2100&&mageCorrected.role(1).position==-2&&mageCorrected.role(1).forward()==1900&&mageCorrected.role(1).enabled&&!mageCorrected.role(1).recover&&!mageCorrected.role(1).store,"sorceress correction preserves arrows, selection and optional jobs in any slot");check(new ScriptConfig(mageDevice).role(1).forward()==1900&&mageCorrected.role(4).baseForward==0&&mageCorrected.role(4).forward()==2100&&mageCorrected.role(6).forward()==2400,"reopening does not shorten again; fresh default and other professions preserved");
  check(plan(RolePlan.SORCERESS).forward()==2100&&plan(RolePlan.ALCHEMIST).forward()==2200,"fresh sorceress default corrected, alchemist unchanged");
  check(RolePlan.SAGE==1&&RolePlan.ELEMENTALIST==2&&RolePlan.SORCERESS==3&&RolePlan.ALCHEMIST==4&&RolePlan.PRIEST==5,"append priest without remapping old saved professions");
  RolePlan priest=plan(RolePlan.PRIEST);check(priest.supported()&&priest.center()&&!priest.controlBeforeUltimate()&&priest.forward()==2800&&priest.chestForward()==0&&priest.professionName().equals("祭祀"),"priest uses elementalist default position with no pre-ultimate skill and remains at chest position");
  SkillGuideView priestGuide=new SkillGuideView(context,RolePlan.PRIEST),elementGuide=new SkillGuideView(context,RolePlan.ELEMENTALIST);
  check(priestGuide.points.length==1&&Arrays.equals(priestGuide.points[0],elementGuide.points[0])&&priestGuide.labels[0].equals("天堂审判")&&priestGuide.description.toString().contains("祭祀"),"priest guide contains only 天堂审判 at the shared ultimate position");
  check(priestGuide.icons.length==1&&priestGuide.icons[0]!=null,"priest guide loads the actual captured ultimate icon");
  RolePlan[] priestRoles=new ScriptConfig(new android.content.Context()).roles();priestRoles[5]=priest.copy();priestRoles[5].position=-2;priestRoles[5].recover=false;priestRoles[5].craft=false;
  android.content.Context priestStore=new android.content.Context();ScriptConfig priestConfig=new ScriptConfig(priestStore);priestConfig.saveRoles(priestRoles);ScriptConfig priestAgain=new ScriptConfig(priestStore);
  RolePlan savedPriest=priestAgain.role(6);check(savedPriest.profession==RolePlan.PRIEST&&savedPriest.forward()==2600&&!savedPriest.recover&&!savedPriest.craft&&savedPriest.store&&priestAgain.summary().contains("角色6 · 祭祀"),"priest settings, arrow offset, task switches and summary persist after reopen");
  android.content.Context priestDevice=new android.content.Context();android.content.SharedPreferences priestPrefs=priestDevice.getSharedPreferences("profile",0);priestPrefs.putInt("revision",1).putInt("role1.profession",RolePlan.PRIEST).putInt("role1.baseForward",2800).putBoolean("role1.enabled",true);
  check(new ScriptConfig(priestDevice).role(1).forward()==2800&&new ScriptConfig(priestDevice).role(1).forward()==2800,"old elementalist calibration never shortens newly saved priest position");
  for(int profession=1;profession<=RolePlan.PRIEST;profession++)for(int slot:new int[]{1,6}){
   RunnerService r=new RunnerService();RolePlan p=plan(profession);r.plans.put(slot,p);r.params.put("ultimateCooldown",0);r.params.put("roleClearWait",5000);RouteFlow f=new RouteFlow(r);f.currentRole=slot;
   r.tapLabelHook=label->{if(professionFor(r,slot)==RolePlan.SAGE&&r.taps==6)r.vision.worldChest=true;};r.waitHook=()->{if(professionFor(r,slot)!=RolePlan.SAGE&&r.waited>=5000)r.vision.worldChest=true;};
   check(f.finishRoleCombat(1),"profession route reaches chest independent of slot");check(r.moves==1&&r.durations.get(0)==p.forward(),"default movement by profession");check(r.taps==(profession==1?6:p.controlBeforeUltimate()?2:1),"skill sequence by profession instead of role index");
  }
  for(int adjustment:new int[]{-2,0,3}){
   List<TraceRunner> traces=new ArrayList<>();
   for(int profession:new int[]{RolePlan.ELEMENTALIST,RolePlan.PRIEST}){
    TraceRunner trace=new TraceRunner();RolePlan p=plan(profession);p.position=adjustment;trace.plans.put(6,p);trace.readyAt=2000000000L;
    trace.waitHook=()->{if(trace.waited>=10000)trace.vision.worldChest=true;};RouteFlow route=new RouteFlow(trace);route.currentRole=6;
    List<String> expected=profession==RolePlan.PRIEST?Arrays.asList("1720,1290"):Arrays.asList("2190,805","1720,1290");
    check(route.finishRoleCombat(1)&&trace.points.equals(expected),"priest only casts ultimate; elementalist retains control first");traces.add(trace);
   }
   check(traces.get(0).durations.equals(traces.get(1).durations),"priest matches elementalist movement at each position offset");
  }
  TraceRunner interrupted=new TraceRunner();interrupted.failUltimate=true;interrupted.plans.put(1,plan(RolePlan.PRIEST));RouteFlow priestFlow=new RouteFlow(interrupted);priestFlow.currentRole=1;
  try{priestFlow.finishRoleCombat(1);throw new AssertionError("simulate failed priest ultimate");}catch(Exception e){check(e.getMessage().equals("ultimate dispatch unavailable")&&!priestFlow.preUltimateControlCast&&interrupted.points.isEmpty(),"no small skill dispatched before failed priest ultimate");}
  interrupted.waitHook=()->{if(interrupted.waited>=5000)interrupted.vision.worldChest=true;};check(priestFlow.finishRoleCombat(1)&&interrupted.moves==1&&interrupted.points.equals(Arrays.asList("1720,1290")),"Continue retries only priest ultimate without small skills or replaying entry movement");
  TraceRunner settlement=new TraceRunner();settlement.plans.put(6,plan(RolePlan.PRIEST));settlement.readyAt=5000000000L;settlement.retry=true;settlement.entry=false;RouteFlow priestSettlement=new RouteFlow(settlement);priestSettlement.currentRole=6;priestSettlement.battlePhase=4;
  settlement.tapLabelHook=label->{check(label.equals("再次挑战")&&settlement.virtualTime>=settlement.readyAt+700000000L,"priest challenge waits for stable observed ultimate readiness");settlement.stopped=true;};
  try{priestSettlement.brushUntilEmpty(true);throw new AssertionError("stop after priest challenge");}catch(Exception e){check(e.getMessage().equals("用户停止")&&settlement.taps==1&&settlement.automaticRetries==0,"priest cooldown wait does not cast or exhaust retries");}
  RunnerService r=new RunnerService();for(int slot=1;slot<=6;slot++){RolePlan p=plan(RolePlan.SAGE);p.enabled=slot==1||slot==6;p.recover=slot==1;r.plans.put(slot,p);}r.params.put("startRole",1);r.params.put("endRole",6);SlotFlow f=new SlotFlow(r);f.currentRole=1;f.phase=GameFlow.SELECT;f.runPhases();check(f.events.equals(Arrays.asList("select1","recover1","repair1","enter1","battle1","town1","return1","select6","return6")),"zero fatigue enabled recovery first, disabled recovery skips, unselected slots untouched");
  for(boolean craft:new boolean[]{false,true})for(boolean store:new boolean[]{false,true}){
   r=new RunnerService();RolePlan p=plan(RolePlan.SAGE);p.craft=craft;p.store=store;r.plans.put(1,p);r.screens.put("town",true);r.screens.put("warehouse",true);JobFlow jobs=new JobFlow(r);new JobTown(jobs).run();check(jobs.operations.contains("craft")==craft&&jobs.operations.contains("store")==store,"independent crafting and storage switches");
  }
  check(FlowPolicy.roleY(1)==258&&FlowPolicy.roleY(6)==1218,"all six selection positions");System.out.println("PASS: profile persistence, five professions, priest ultimate-only flow/real icon/Continue, legacy IDs and positions, six slots and optional jobs");
 }
 static int professionFor(RunnerService r,int slot){return r.rolePlan(slot).profession;}
}'''
with tempfile.TemporaryDirectory(prefix='blackstone-profile-') as folder:
 p=Path(folder);sources=[]
 for name,body in stubs.items():
  path=p/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body,encoding='utf8');sources.append(str(path))
 sources += [str(ROOT/'src/cn/kongj/blackstone'/name) for name in ('ScriptConfig.java','RolePlan.java','SkillCooldown.java','GameFlow.java','RepairFlow.java','MovementInterrupted.java','FlowPolicy.java','FlowControl.java','MovementModel.java','TownFlow.java','TownInventory.java')]
 subprocess.run([str(JAVA/'javac.exe'),'-encoding','UTF-8','-d',str(p),*sources],check=True)
 subprocess.run([str(JAVA/'java.exe'),'-cp',str(p),'cn.kongj.blackstone.ProfileTest',str(ROOT/'assets')],check=True)
