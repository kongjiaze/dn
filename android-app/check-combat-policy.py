"""Run actual GameFlow combat methods with a simulated screenshot/gesture runtime.

This checks bounded recovery and cancellation, not real in-game combat efficacy.
"""
from pathlib import Path
import tempfile, subprocess

ROOT = Path(__file__).resolve().parent
JAVA = Path('D:/JDK/openjdk-17/bin')
stubs = {
 'android/content/SharedPreferences.java': '''package android.content;
 public class SharedPreferences {public java.util.Map<String,Integer> values=new java.util.HashMap<>();
 public int getInt(String k,int d){return values.containsKey(k)?values.get(k):d;}
 public java.util.Map<String,Boolean> bools=new java.util.HashMap<>();public boolean contains(String k){return values.containsKey(k)||bools.containsKey(k);}public boolean getBoolean(String k,boolean d){return bools.getOrDefault(k,d);}public SharedPreferences putBoolean(String k,boolean b){bools.put(k,b);return this;}
 public Editor edit(){return new Editor(this);}public SharedPreferences putInt(String k,int n){values.put(k,n);return this;}public void apply(){}
 public static class Editor {SharedPreferences prefs;Editor(SharedPreferences p){prefs=p;}public Editor putInt(String k,int n){prefs.putInt(k,n);return this;}public Editor putBoolean(String k,boolean b){prefs.putBoolean(k,b);return this;}public void apply(){}}}''',
 'android/graphics/Bitmap.java': 'package android.graphics; public class Bitmap {public int getPixel(int x,int y){return 0;}public void recycle(){}}',
 'org/json/JSONObject.java': 'package org.json; public class JSONObject {public int getInt(String k){return 0;}}',
 'cn/kongj/blackstone/TouchService.java': '''package cn.kongj.blackstone;
 class TouchService {static TouchService instance=new TouchService(); String activePackage(){return "com.dn.lzgsj3";}}''',
 'cn/kongj/blackstone/Vision.java': '''package cn.kongj.blackstone;
 import android.graphics.Bitmap;import org.json.JSONObject;
 class Vision {int fatigueValue=100,remaining,checksBeforeSelected;boolean has(String k){return true;}boolean selectedRole(Bitmap b,int n){return checksBeforeSelected--<=0;}
 boolean worldChest;boolean worldChestVisible(Bitmap b){return worldChest;}
 boolean unknownArena;double px=211,py=222;int[] arenaPosition(Bitmap b){return unknownArena?null:new int[]{(int)Math.round(px),(int)Math.round(py)};}
 int[] arenaPosition(Bitmap b,int x,int y){return arenaPosition(b);}
 int[] arenaSample(Bitmap b){return new int[1];}int[] temporalArenaPosition(java.util.ArrayList<int[]> samples,int[] hint){return arenaPosition(null);}
 boolean dimHud;int modalFatigue=-2;
 int fatigue(Bitmap b){return dimHud?-1:fatigueValue;}int exchangeRemaining(Bitmap b){return remaining;}int recoveryFatigue(Bitmap b){return modalFatigue==-2?fatigueValue:modalFatigue;}
 int townGold(Bitmap b){return 1000;}int[] warehouseCapacity(Bitmap b){return new int[]{0,160};}
 boolean matches(Bitmap b,String k){return true;}int[] locateBlackstone(Bitmap b){return null;}JSONObject action(String k){return new JSONObject();}}''',
 'cn/kongj/blackstone/RunnerService.java': '''package cn.kongj.blackstone;
 import android.graphics.Bitmap;
 class RunnerService {
 java.util.Map<Integer,RolePlan> plans=new java.util.HashMap<>();
 RolePlan rolePlan(int slot){if(plans.containsKey(slot))return plans.get(slot);RolePlan p=new RolePlan();p.enabled=slot>=param("startRole",2)&&slot<=param("endRole",5);p.profession=slot>=2&&slot<=5?slot-1:0;p.baseForward=param("role"+slot+"Forward",p.defaultForward());return p;}
 int nextRole(int after){for(int i=after+1;i<=6;i++)if(rolePlan(i).enabled)return i;return 7;}
 String gamePackage="";Vision vision=new Vision();int taps,moves,cancels,chestAfterMoves=Integer.MAX_VALUE;
 boolean entry=true,retry=false,stopped=false;String failMessage="手势取消或无障碍服务不可用";
 java.util.Map<String,Boolean> screens=new java.util.HashMap<>();Runnable retryHook,tapHook,waitHook;int manualRetries,automaticRetries;long waited;
 java.util.function.Consumer<String> tapLabelHook;
 java.util.Map<String,Integer> params=new java.util.HashMap<>();
 java.util.Map<String,Boolean> cooldowns=new java.util.HashMap<>();int ignoredDebuffs,ignoredBuffs,ignoredUltimates,shortRetries;int ultimateLifetimeMs=-1;long ultimateExpiresAt=-1;
 void checkpoint()throws Exception{if(stopped)throw new Exception("用户停止");}
 void tap(String label,int x,int y)throws Exception{checkpoint();taps++;if(tapHook!=null)tapHook.run();if(tapLabelHook!=null)tapLabelHook.accept(label);if(cancels-->0)throw new Exception(failMessage);}
 long virtualTime;
 void combatTap(String label,int x,int y)throws Exception{tap(label,x,y);virtualTime+=600000000L;}void fastTap(String label,int x,int y)throws Exception{tap(label,x,y);}
 void combatTap(String label,int x,int y,int gap)throws Exception{
  tap(label,x,y);virtualTime+=gap*1000000L;
  if(label.endsWith("防顶飞先释放减益")&&ignoredDebuffs-->0)return;
  if(label.endsWith("防顶飞再释放增益")&&ignoredBuffs-->0)return;
  if(label.endsWith("释放大招一次")&&ignoredUltimates-->0)return;
  if(label.contains("防顶飞")||label.endsWith("释放大招一次"))cooldowns.put(x+","+y,true);
  if(label.endsWith("释放大招一次")&&ultimateLifetimeMs>=0)ultimateExpiresAt=virtualTime+ultimateLifetimeMs*1000000L;
 }
 void batchTap(String label,int x,int y)throws Exception{tap(label,x,y);}void doubleTap(String label,int x,int y)throws Exception{tap(label,x,y);}boolean threeMaterials(){return false;}
 int[] synthesisUi()throws Exception{return new int[]{match("synthesisResult")?1:0,threeMaterials()?3:0,0,0};}
 void resetBattleClear(){}void watchBattleClear()throws Exception{checkpoint();}boolean battleClearSeen()throws Exception{checkpoint();return false;}
 java.util.List<Integer> durations=new java.util.ArrayList<>();double speedX=28.6,speedY=12,crossXY=0,crossYX=0;boolean frozen;
 void move(String label,int x,int y,int ms)throws Exception{checkpoint();moves++;durations.add(ms);if(!frozen){double ux=(x-300)/220.,uy=(y-1148)/220.;vision.px+=(speedX*ux+crossXY*uy)*ms/1000.;vision.py+=(crossYX*ux+speedY*uy)*ms/1000.;}}
 void gesture(String label,int x,int y,int ex,int ey,long ms)throws Exception{checkpoint();}
 void waitMs(long ms)throws Exception{checkpoint();waited+=ms;virtualTime+=ms*1000000L;if(waitHook!=null)waitHook.run();}
 boolean match(String k)throws Exception{checkpoint();if(screens.containsKey(k))return screens.get(k);return k.equals("chest")?moves>=chestAfterMoves:k.equals("entry")?entry:k.equals("retry")?retry:false;}
 boolean waitMatch(String k,long ms)throws Exception{return match(k);}
 boolean battleReady()throws Exception{checkpoint();return entry&&!retry;}
 boolean combatFrameReady()throws Exception{return battleReady();}
 boolean skillCooling(int x,int y)throws Exception{checkpoint();if(x==1720&&y==1290&&ultimateExpiresAt>=0&&virtualTime>=ultimateExpiresAt)cooldowns.remove(x+","+y);return Boolean.TRUE.equals(cooldowns.get(x+","+y));}
 int ultimateState()throws Exception{checkpoint();if(!entry&&!retry)return SkillCooldown.UNKNOWN;return skillCooling(1720,1290)?SkillCooldown.COOLING:SkillCooldown.READY;}
 int param(String k,int d){return params.containsKey(k)?params.get(k):d;}void log(String s){}void snapshot(String s){}
 void awaitRetry(String msg)throws Exception{checkpoint();manualRetries++;if(retryHook!=null)retryHook.run();else throw new Exception(msg);}
 void retrySucceeded(){shortRetries=0;}
 void autoRetry(String msg)throws Exception{checkpoint();automaticRetries++;if(retryHook!=null)retryHook.run();else throw new Exception(msg);}
 void autoRetry(String msg,long gap)throws Exception{checkpoint();if(++shortRetries>param("maxRetries",20))throw new Exception("自动重试次数已用完："+msg);automaticRetries++;waitMs(gap);if(retryHook!=null)retryHook.run();}
 android.content.SharedPreferences progress=new android.content.SharedPreferences();
 Bitmap frame(){return new Bitmap();}android.content.SharedPreferences getSharedPreferences(String k,int mode){return progress;}
 }''',
 'cn/kongj/blackstone/CombatTest.java': '''package cn.kongj.blackstone;
 class FastFlow extends GameFlow {FastFlow(RunnerService r){super(r);}long combatClock(){return r.virtualTime;}}
 class OpeningRetryRunner extends RunnerService {
  int buffFailures=3;
  void combatTap(String label,int x,int y,int gap)throws Exception{
   if(label.endsWith("防顶飞再释放增益")&&buffFailures-->0)throw new Exception("手势取消或无障碍服务不可用");
   super.combatTap(label,x,y,gap);
  }
 }
 class UltimateRetryRunner extends RunnerService {
  int ultimateFailures=3;
  void combatTap(String label,int x,int y,int gap)throws Exception{
   if(label.endsWith("释放大招一次")&&ultimateFailures-->0)throw new Exception("手势取消或无障碍服务不可用");
   super.combatTap(label,x,y,gap);
  }
 }
 class DelayedCastRunner extends RunnerService {
  java.util.Map<String,Long> activatesAt=new java.util.HashMap<>(),confirmedAt=new java.util.HashMap<>();
  void combatTap(String label,int x,int y,int gap)throws Exception{
   tap(label,x,y);activatesAt.put(x+","+y,virtualTime+1800000000L);virtualTime+=gap*1000000L;
  }
  boolean skillCooling(int x,int y)throws Exception{
   checkpoint();String key=x+","+y;Long ready=activatesAt.get(key);boolean cooling=ready!=null&&virtualTime>=ready;
   if(cooling)confirmedAt.put(key,virtualTime);return cooling;
  }
 }
 class MissedDebuffRunner extends RunnerService {
  boolean hit,buffActivated,alwaysLocked;int cycleDebuffs,buffAttempts;
  java.util.List<String> labels=new java.util.ArrayList<>();java.util.List<Long> times=new java.util.ArrayList<>();
  void combatTap(String label,int x,int y,int gap)throws Exception{
   labels.add(label);times.add(virtualTime);super.combatTap(label,x,y,gap);
   if(label.endsWith("原地减益")&&++cycleDebuffs>=3)hit=true;
   if(x==1945&&y==1090){buffAttempts++;if(alwaysLocked||!hit)cooldowns.remove(x+","+y);else{buffActivated=true;cooldowns.put(x+","+y,true);vision.worldChest=true;}}
  }
 }
 class CycleRetryRunner extends RunnerService {
  int cycleFailures=3;
  void combatTap(String label,int x,int y,int gap)throws Exception{
   if(label.endsWith("原地减益")&&cycleFailures-->0)throw new Exception("手势取消或无障碍服务不可用");
   super.combatTap(label,x,y,gap);
  }
 }
 class PartialChestRunner extends RunnerService {
  java.util.ArrayDeque<Integer> outcomes=new java.util.ArrayDeque<>();int approachApplied;boolean failAfterComplete;
  void move(String label,int x,int y,int ms)throws Exception{
   if(!label.contains("开箱步骤1")){super.move(label,x,y,ms);return;}
   checkpoint();moves++;durations.add(ms);int applied=outcomes.isEmpty()?ms:Math.min(ms,Math.max(0,outcomes.removeFirst()));approachApplied+=applied;
   if(applied<ms||failAfterComplete){failAfterComplete=false;throw new MovementInterrupted(ms,applied,"post-cast movement interrupted");}
  }
 }
 class DistantChestRunner extends RunnerService {
  int approachMs;final int requiredMs;DistantChestRunner(int target){requiredMs=target;}
  void move(String label,int x,int y,int ms)throws Exception{super.move(label,x,y,ms);if(label.contains("靠近宝箱"))approachMs+=ms;}
  boolean match(String key)throws Exception{checkpoint();return key.equals("chest")?approachMs>=requiredMs:super.match(key);}
 }
 class PartialNudgeRunner extends RunnerService {
  java.util.ArrayDeque<Integer> outcomes=new java.util.ArrayDeque<>();int applied;boolean failAfterComplete;
  void move(String label,int x,int y,int ms)throws Exception{
   checkpoint();moves++;durations.add(ms);int elapsed=outcomes.isEmpty()?ms:Math.min(ms,Math.max(0,outcomes.removeFirst()));applied+=elapsed;
   if(elapsed<ms||failAfterComplete){failAfterComplete=false;throw new MovementInterrupted(ms,elapsed,"nudge interrupted");}
  }
 }
 class SlowClearRunner extends RunnerService {
  long clearAt=-1,approachAt=-1;
  void log(String message){if(message.contains("战斗步骤4")&&clearAt<0)clearAt=virtualTime;}
  boolean combatFrameReady()throws Exception{if(clearAt>=0)virtualTime+=2000000000L;return super.combatFrameReady();}
  void move(String label,int x,int y,int ms)throws Exception{if(label.contains("开箱步骤1"))approachAt=virtualTime;super.move(label,x,y,ms);}
 }
 class ResumeTestFlow extends GameFlow {ResumeTestFlow(RunnerService r){super(r);}void runPhases(){}}
 class RecoveryPhaseFlow extends GameFlow {
  boolean entered,repaired;RecoveryPhaseFlow(RunnerService r){super(r);}
  void repairEquipment(){repaired=true;}
  void enterBlackstone(int before)throws Exception{if(before!=200)throw new AssertionError("expected remaining fatigue 200");entered=true;r.stopped=true;throw new Exception("用户停止");}
 }
 class PillarRetryFlow extends FastFlow {
 java.util.List<Integer> visits=new java.util.ArrayList<>();boolean failed;
 PillarRetryFlow(RunnerService r){super(r);}
 void approachPillar(int p,int pass)throws Exception{visits.add(p);if(p==3&&!failed){failed=true;throw new Exception("third pillar unavailable");}}
 void timedCombat(boolean skills,int p){if(p==3&&!skills)r.retry=true;}
 }
 public class CombatTest {
 static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
 static void failure(GameFlow f,String expected)throws Exception{try{f.combatTap("skill",1,1);throw new AssertionError("expected failure");}catch(Exception e){check(expected.equals(e.getMessage()),e.getMessage());}}
 public static void main(String[] args)throws Exception {
 FlowControl extended=new FlowControl();extended.start();for(int i=0;i<20;i++)check(extended.failed("temporary"+i,20)&&extended.state==FlowControl.RUNNING,"twenty retries remain running");check(!extended.failed("still unavailable",20)&&extended.state==FlowControl.ERROR,"pause only after twenty retries exhausted");extended.resume();check(extended.failed("retry",20)&&extended.failures()==1,"continue renews extended retry budget");
 FlowControl lifecycle=new FlowControl();check(lifecycle.state==FlowControl.READY&&!lifecycle.resume(),"restart is ready and cannot resume old run");lifecycle.start();check(lifecycle.state==FlowControl.RUNNING,"start becomes continue");for(int i=0;i<3;i++)check(lifecycle.failed("step"+i,3)&&lifecycle.state==FlowControl.RUNNING,"changing error text must not reset retry budget");check(!lifecycle.failed("different error",3)&&lifecycle.state==FlowControl.ERROR,"only after three retries pause as error");check(lifecycle.resume()&&lifecycle.failed("step",3),"continue renews budget on same failed step");lifecycle.pause();check(lifecycle.state==FlowControl.PAUSED&&lifecycle.resume(),"manual pause resumes");lifecycle.reset();check(lifecycle.state==FlowControl.READY&&!lifecycle.resume(),"stop or configuration reset must start fresh");lifecycle.start();check(lifecycle.failed("next",1),"new run has clean budget");lifecycle.success();check(lifecycle.failed("next",1),"successful step clears failure count");
 RunnerService selected=new RunnerService();selected.params.put("startRole",3);selected.screens.put("roles",true);selected.progress.putInt("activeRole",2).putInt("resumeValid",1).putInt("phase",GameFlow.BATTLE);ResumeTestFlow restored=new ResumeTestFlow(selected);restored.runResume();check(restored.currentRole==3&&restored.phase==GameFlow.SELECT,"old role two must not resume when range starts at three");
 selected=new RunnerService();selected.params.put("startRole",3);selected.progress.putInt("activeRole",2).putInt("resumeValid",1);restored=new ResumeTestFlow(selected);try{restored.runResume();throw new AssertionError("range change outside role page must wait");}catch(Exception e){check(selected.taps==0&&e.getMessage().contains("角色范围"),"do not relabel active role two as role three");}
 selected=new RunnerService();selected.params.put("startRole",3);selected.screens.put("roles",true);selected.progress.putInt("activeRole",4).putInt("resumeValid",1).putInt("phase",GameFlow.SELECT).putInt("rangeStart",2).putInt("rangeEnd",5);restored=new ResumeTestFlow(selected);restored.runResume();check(restored.currentRole==3,"changed saved range must restart at new start even if old role is inside range");
 selected=new RunnerService();selected.params.put("startRole",3);selected.progress.putInt("activeRole",4).putInt("resumeValid",1).putInt("phase",GameFlow.BATTLE).putInt("battlePhase",2).putInt("rangeStart",3).putInt("rangeEnd",5);restored=new ResumeTestFlow(selected);restored.runResume();check(restored.currentRole==4&&restored.battlePhase==2,"unchanged range preserves later-role pickup progress");
 selected=new RunnerService();selected.params.put("startRole",3);selected.screens.put("roles",true);restored=new ResumeTestFlow(selected);restored.run();check(restored.currentRole==3&&selected.progress.getInt("rangeStart",0)==3,"fresh start uses saved role three");
 RunnerService r=new RunnerService();GameFlow f=new FastFlow(r);r.cancels=1;f.combatTap("skill",1,1);check(r.taps==2,"transient cancellation must retry");
 r=new RunnerService();f=new FastFlow(r);r.cancels=9;failure(f,"手势取消或无障碍服务不可用");check(r.taps==3,"retry must stop after three attempts");
 r=new RunnerService();f=new FastFlow(r);r.cancels=9;r.entry=false;failure(f,"手势取消或无障碍服务不可用");check(r.taps==1,"do not retry outside battle");
 r=new RunnerService();f=new FastFlow(r);r.cancels=9;r.failMessage="other";failure(f,"other");check(r.taps==1,"do not retry unrelated errors");
 r=new RunnerService();f=new FastFlow(r);r.cancels=1;r.stopped=true;failure(f,"用户停止");check(r.taps==0,"stop must prevent input");
 r=new RunnerService();f=new FastFlow(r);r.params.put("sweepPasses",2);check(!f.finishCombatAndApproachChest(false,1),"missing chest must finish a batch for retry");check(r.taps==204,"two rounds of three pillars with ten seconds skills and ten seconds attacks");
 for(double speed:new double[]{.4,1,2.5}) {r=new RunnerService();f=new FastFlow(r);r.vision.px=260;r.vision.py=275;r.speedX*=speed;r.speedY*=speed;f.approachPillar(1,0);check(Math.abs(r.vision.px-194)<=8.5&&Math.abs(r.vision.py-240)<=8.5&&r.taps==0,"different speeds must reach threshold before any skill");for(int ms:r.durations)check(ms<=2500,"full movement cap");check(r.durations.get(0)>700,"first movement must use full computed distance instead of old small pulse");check(f.movement.observations>0,"actual displacement updates movement calibration");}
 r=new RunnerService();f=new FastFlow(r);r.vision.px=245;r.vision.py=275;r.crossXY=-8;r.crossYX=4;f.approachPillar(1,0);check(Math.abs(r.vision.px-194)<=8.5&&Math.abs(r.vision.py-240)<=8.5&&f.movement.observations>0,"learn two-dimensional movement including cross-axis drift");int learned=f.movement.observations;f.resetCombatRoute();check(f.movement.observations==learned,"same role keeps movement model across rounds");f.currentRole=5;f.ensureMovementRole();check(f.movement.observations==0,"new role resets speed model");
 r=new RunnerService();f=new FastFlow(r);r.frozen=true;try{f.approachPillar(1,0);throw new AssertionError("no progress must fail");}catch(Exception e){check(r.taps==0&&e.getMessage().contains("未靠近"),"stalled navigation cannot start skills");}
 r=new RunnerService();PillarRetryFlow pillarRetry=new PillarRetryFlow(r);try{pillarRetry.finishCombatAndApproachChest(false,1);throw new AssertionError("third must fail once");}catch(Exception e){check(e.getMessage().contains("third"),"expected third pillar failure");}check(pillarRetry.finishCombatAndApproachChest(true,1)&&pillarRetry.visits.equals(java.util.Arrays.asList(1,2,3,3)),"retry third pillar without revisiting first and second");
 r=new RunnerService();f=new FastFlow(r);f.timedCombat(true,1);check(r.virtualTime>=10000000000L&&r.virtualTime<10600000000L,"skill phase lasts ten seconds within one input latency");int skillTaps=r.taps;long skillTime=r.virtualTime;f.timedCombat(false,1);check(r.virtualTime-skillTime>=10000000000L&&r.taps==skillTaps*2,"separate ten second basic attack phase");
 r=new RunnerService();f=new FastFlow(r);r.chestAfterMoves=2;r.vision.px=260;r.vision.py=275;check(f.finishCombatAndApproachChest(false,1),"chest during pillar approach");check(r.moves==2&&r.taps==0,"stop both remaining movement and skills when reward appears");
 r=new RunnerService();f=new FastFlow(r);f.combatDeadline=System.nanoTime()-1;try{f.fight(40);throw new AssertionError("expired time budget must stop");}catch(Exception e){check(e.getMessage().contains("时间上限")&&r.taps==0,"time limit prevents further input");}
 r=new RunnerService();f=new FastFlow(r);r.chestAfterMoves=0;check(f.finishCombatAndApproachChest(true,1),"resume existing completed battle");check(r.moves==0&&r.taps==0,"visible chest must bypass all movement and combat");
 r=new RunnerService();f=new FastFlow(r);r.chestAfterMoves=2;f.returnToCentre();check(r.moves==2,"chest during recenter must stop remaining route");
 r=new RunnerService();f=new FastFlow(r);r.retry=true;check(f.finishCombatAndApproachChest(true,1)&&r.moves==0&&r.taps==0,"settlement must bypass further motion and combat");
 r=new RunnerService();f=new FastFlow(r);r.vision.worldChest=true;check(f.finishCombatAndApproachChest(true,1)&&r.moves==0&&r.taps==0,"visible world chest without interaction button must stop combat");
 r=new RunnerService();f=new FastFlow(r);r.vision.unknownArena=true;f.navigationHint=new int[]{198,254};try{f.approachPillar(1,0);throw new AssertionError("unreliable minimap must fail before moving");}catch(Exception e){check(r.moves==0&&r.taps==0&&e.getMessage().contains("不可靠"),"no guessed route for unreadable minimap");check(f.navigationHint!=null&&f.navigationHint[0]==198&&f.navigationHint[1]==254,"stationary retry preserves accepted movement hint");}
 r=new RunnerService();f=new FastFlow(r);r.retryHook=()->{};r.chestAfterMoves=2;r.vision.px=260;r.vision.py=275;final RunnerService box=r;r.tapLabelHook=label->{check(label.equals("开启中央宝箱"),"no skill input during chest stage");if(box.taps==2)box.retry=true;};f.collectChest();check(r.taps==2&&r.moves==2,"not close enough must recalculate; ignored opening tap must retry until settlement");
 r=new RunnerService();f=new FastFlow(r);r.retry=true;f.collectChest();check(r.taps==0&&r.moves==0,"already opened chest must not repeat input");
 r=new RunnerService();f=new FastFlow(r);final RunnerService slowLoot=r;r.waitHook=()->{if(slowLoot.waited>=35000)slowLoot.retry=true;};check(f.waitForPickup(),"wait for delayed loot settlement beyond twenty seconds");check(r.waited>=35000&&r.moves==0&&r.taps==0,"pending pickup must never move or reclick");
 r=new RunnerService();f=new FastFlow(r);r.screens.put("chest",true);check(!f.waitForPickup()&&r.waited>=7000,"persistently available opening button allows retry after waiting");
 r=new RunnerService();f=new FastFlow(r);r.retry=true;r.vision.fatigueValue=0;f.battlePhase=3;f.brushUntilEmpty(true);check(r.taps==0,"zero fatigue must return without clicking challenge");
 r=new RunnerService();f=new FastFlow(r);r.retry=true;r.vision.fatigueValue=100;f.battlePhase=3;final RunnerService enoughFatigue=r;r.tapLabelHook=label->{check(label.equals("再次挑战"),"sufficient fatigue must click challenge");enoughFatigue.stopped=true;};try{f.brushUntilEmpty(true);throw new AssertionError("test must stop after challenge click");}catch(Exception e){check(r.taps==1&&e.getMessage().equals("用户停止"),"challenge at exactly 100 fatigue");}
 r=new RunnerService();f=new FastFlow(r);r.entry=false;try{f.fight(40);throw new AssertionError("must reject non-battle");}catch(Exception e){check(r.taps==0,"no combat outside dungeon");}
 r=new RunnerService();f=new FastFlow(r);r.screens.put("hud",true);f.selectAndEnter();check(r.taps==0,"already entered role must not enter again");
 r=new RunnerService();f=new FastFlow(r);r.screens.put("target",true);f.transition("from","target","open",1,1,10,"missing");check(r.taps==0,"observed success must not replay tap");
 r=new RunnerService();f=new FastFlow(r);r.screens.put("from",true);final RunnerService cancelled=r;r.cancels=1;r.tapHook=()->cancelled.screens.put("target",true);r.retryHook=()->{};
 f.transition("from","target","open",1,1,10,"missing");check(r.taps==1&&r.automaticRetries==1&&r.manualRetries==0,"cancelled callback after successful action must not replay");
 r=new RunnerService();f=new FastFlow(r);r.screens.put("from",true);final RunnerService ignored=r;r.retryHook=()->ignored.tapHook=()->ignored.screens.put("target",true);
 f.transition("from","target","open",1,1,10,"missing");check(r.taps==2&&r.automaticRetries==1&&r.manualRetries==0,"ignored click must retry automatically");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=2;r.screens.put("roles",true);r.screens.put("roleEnter",true);r.vision.checksBeforeSelected=20;final RunnerService slow=r;r.tapLabelHook=label->{if(label.equals("进入游戏"))slow.screens.put("hud",true);};f.selectAndEnter();check(r.taps==2&&r.automaticRetries==0,"six-second highlight delay must wait without stopping or reselecting");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=2;r.screens.put("roles",true);r.screens.put("roleEnter",true);r.vision.checksBeforeSelected=100;final RunnerService delayed=r;r.retryHook=()->delayed.vision.checksBeforeSelected=0;r.tapLabelHook=label->{if(label.equals("进入游戏"))delayed.screens.put("hud",true);};f.selectAndEnter();check(r.taps==3&&r.automaticRetries==1&&r.manualRetries==0,"highlight timeout must automatically reselect before entering");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=3;f.phase=GameFlow.BATTLE;f.battlePhase=6;f.battleRound=4;f.entranceFatigue=500;f.saveProgress();check(r.progress.getInt("battlePhase",-1)==6&&r.progress.getInt("battleRound",-1)==4&&r.progress.getInt("activeRole",-1)==3,"persist current battle step and role");
 r=new RunnerService();f=new FastFlow(r);final RunnerService exchange=r;final int[] confirmations={0};r.vision.fatigueValue=500;r.vision.remaining=1;r.screens.put("recovery",true);r.screens.put("recoveryConfirm",true);
 r.tapLabelHook=label->{if(label.startsWith("确认恢复体力")){confirmations[0]++;exchange.vision.remaining--;exchange.vision.fatigueValue+=100;exchange.cancels=1;}if(label.equals("关闭体力恢复窗口")){exchange.screens.put("hud",true);exchange.screens.put("recovery",false);}};
 try{f.recoverFatigue();throw new AssertionError("simulate cancelled successful exchange");}catch(Exception e){check(e.getMessage().equals("手势取消或无障碍服务不可用"),e.getMessage());}
 f.recoverFatigue();check(confirmations[0]==1&&r.vision.fatigueValue==600,"resume must not repeat a successful fatigue exchange");
 for(int modal:new int[]{200,-1}){
  r=new RunnerService();r.vision.fatigueValue=200;r.vision.dimHud=true;r.vision.modalFatigue=modal;r.vision.remaining=0;r.screens.put("hud",true);r.screens.put("recovery",true);
  final RunnerService recoveryModal=r;r.tapLabelHook=label->{check(!label.startsWith("确认恢复体力"),"0/3 cannot trigger another exchange");if(label.equals("关闭体力恢复窗口")){recoveryModal.screens.put("recovery",false);recoveryModal.vision.dimHud=false;}};
  RecoveryPhaseFlow recoveryFlow=new RecoveryPhaseFlow(r);recoveryFlow.currentRole=3;recoveryFlow.phase=GameFlow.RECOVER;r.params.put("endRole",3);
  try{recoveryFlow.runPhases();throw new AssertionError("stop at dungeon entry");}catch(Exception error){check(error.getMessage().equals("用户停止"),error.getMessage());}
  check(recoveryFlow.repaired&&recoveryFlow.entered&&r.taps==2&&r.automaticRetries==0,"resume recovery modal skips dimmed HUD; exhausted exchanges close and use actual stable fatigue before repair and entry");
 }
 r=new RunnerService();f=new FastFlow(r);r.screens.put("recovery",true);r.vision.remaining=1;r.vision.modalFatigue=-1;
 try{f.recoverFatigue();throw new AssertionError("unreadable modal with available exchanges must fail");}catch(Exception error){check(error.getMessage().contains("恢复窗口兑换次数或疲劳值尚未稳定")&&r.taps==1,"do not spend an exchange without readable fatigue");}
 r=new RunnerService();f=new FastFlow(r);r.screens.put("recovery",true);r.screens.put("recoveryConfirm",true);r.screens.put("hud",true);r.vision.remaining=1;r.vision.modalFatigue=-1;r.vision.fatigueValue=200;r.vision.dimHud=true;
 final RunnerService transientRecovery=r;r.waitHook=()->{if(transientRecovery.waited>=1000)transientRecovery.vision.modalFatigue=-2;};
 r.tapLabelHook=label->{if(label.startsWith("确认恢复体力")){transientRecovery.vision.fatigueValue+=100;transientRecovery.vision.remaining--;}if(label.equals("关闭体力恢复窗口")){transientRecovery.screens.put("recovery",false);transientRecovery.vision.dimHud=false;}};
 f.recoverFatigue();check(r.taps==3&&r.vision.fatigueValue==300&&r.automaticRetries==0,"transient modal OCR waits inside recovery instead of reading the dimmed HUD or duplicating an exchange");
 r=new RunnerService();f=new FastFlow(r);r.vision.fatigueValue=200;r.screens.put("hud",true);r.screens.put("recovery",true);r.retryHook=()->{};
 final RunnerService cancelledClose=r;r.tapLabelHook=label->{cancelledClose.screens.put("recovery",false);cancelledClose.cancels=1;};
 f.closeRecovery(200);check(r.taps==1&&r.automaticRetries==1,"successful modal close with cancelled callback must not reopen recovery or re-exchange");
 // Personal routes run actual GameFlow methods, with no minimap dependency.
 for(int role:new int[]{2,3,4,5}) {
  r=new RunnerService();f=new FastFlow(r);f.currentRole=role;r.params.put("ultimateCooldown",0);
  final RunnerService personal=r;final int personalRole=role;
  java.util.List<String> labels=new java.util.ArrayList<>();java.util.List<Long> times=new java.util.ArrayList<>();
  r.tapLabelHook=label->{labels.add(label);times.add(personal.virtualTime);if(personalRole==2&&personal.taps==6)personal.vision.worldChest=true;};
  if(role!=2)r.waitHook=()->{if(personal.waited>=5000)personal.vision.worldChest=true;};
  check(f.finishRoleCombat(1),"personal route must reach chest");
  check(r.moves==1&&r.durations.get(0)==(role==2?3000:role==3?2800:role==4?2100:2200),"one complete role-specific forward movement");
  check(r.taps==(role==2?6:role==3?2:1),"role-specific spells before and after one ultimate");
  if(role==3){
   check(labels.get(0).endsWith("大招前先释放三道冰锥控制")&&labels.get(1).endsWith("释放大招一次"),"role three must control before ultimate");
   check(times.get(1)-times.get(0)==600000000L,"control uses configured skill gap before ultimate");
  }
  if(role==2){
   check(labels.get(1).endsWith("防顶飞先释放减益")&&labels.get(2).endsWith("防顶飞再释放增益"),"prerequisite debuff before buff");
   check(times.get(1)-times.get(0)>=300000000L&&times.get(2)-times.get(1)==200000000L,"confirmed ultimate precedes small skills; debuff-to-buff uses the new fast gap");
   check(labels.get(3).endsWith("控制")&&labels.get(4).endsWith("减益")&&labels.get(5).endsWith("增益")&&times.get(3)-times.get(2)<1000000000L,"control follows optional buff without an idle spawn wait; cycle preserves debuff before buff");
  }
 }
 OpeningRetryRunner interrupted=new OpeningRetryRunner();f=new FastFlow(interrupted);f.currentRole=2;
 try{f.finishRoleCombat(1);throw new AssertionError("buff fails three times");}catch(Exception expected){check(interrupted.taps==2&&f.openingSkill==1,"retain completed ultimate/debuff before failed buff");}
 interrupted.tapLabelHook=label->{check(label.endsWith("防顶飞再释放增益"),"resume only the failed protective buff");interrupted.vision.worldChest=true;};
 check(f.finishRoleCombat(1)&&interrupted.moves==1&&interrupted.taps==3,"continue never replays entrance, ultimate, or successful debuff");
 f.resetRoleRoute();check(f.openingSkill==0,"new round resets protective sequence");
 // Accepted Android taps may still be ignored by the game during an animation.
 r=new RunnerService();f=new FastFlow(r);f.currentRole=2;r.ignoredDebuffs=3;r.ignoredBuffs=2;
 final RunnerService ignoredSpells=r;java.util.List<String> protectionLabels=new java.util.ArrayList<>();java.util.List<Long> protectionTimes=new java.util.ArrayList<>();
 r.tapLabelHook=label->{protectionLabels.add(label);protectionTimes.add(ignoredSpells.virtualTime);if(label.endsWith("原地增益"))ignoredSpells.vision.worldChest=true;};
 check(f.finishRoleCombat(1)&&r.moves==1&&r.taps==6&&r.automaticRetries==0&&f.openingSkill==2,"ignored initial debuff and buff never hold the fast three-skill cycle or consume retries");
 check(protectionLabels.get(1).endsWith("减益")&&protectionLabels.get(2).endsWith("增益")&&protectionTimes.get(2)-protectionTimes.get(1)==200000000L,"buff follows the ignored debuff after 200ms, without waiting for cooldown confirmation");
 // Delayed cooldown can represent an animation still winding up. Re-tapping after
 // the old 600ms observation would reset this simulated wind-up forever.
 DelayedCastRunner delayedCast=new DelayedCastRunner();f=new FastFlow(delayedCast);f.currentRole=2;
 java.util.List<String> delayedLabels=new java.util.ArrayList<>();java.util.List<Long> delayedTimes=new java.util.ArrayList<>();
 delayedCast.tapLabelHook=label->{delayedLabels.add(label);delayedTimes.add(delayedCast.virtualTime);if(label.endsWith("防顶飞再释放增益"))delayedCast.vision.worldChest=true;};
 check(f.finishRoleCombat(1)&&delayedCast.taps==3&&delayedCast.automaticRetries==0,"1.8-second delayed ultimate still confirms; neither small skill waits for cooldown");
 check(delayedLabels.get(0).endsWith("释放大招一次")&&delayedLabels.get(1).endsWith("先释放减益")&&delayedLabels.get(2).endsWith("再释放增益"),"slow skill sequence preserves ultimate-debuff-buff order");
 check(delayedTimes.get(1)>=delayedCast.confirmedAt.get("1720,1290")&&!delayedCast.confirmedAt.containsKey("1900,1290")&&delayedTimes.get(2)-delayedTimes.get(1)==200000000L,"slow ultimate does not block small skills once confirmed; no small-skill cooldown polling");
 MissedDebuffRunner missedTarget=new MissedDebuffRunner();f=new FastFlow(missedTarget);f.currentRole=2;
 check(f.finishRoleCombat(1)&&missedTarget.buffActivated&&missedTarget.cycleDebuffs==3&&missedTarget.buffAttempts==4&&missedTarget.automaticRetries==0&&missedTarget.moves==1,"initial debuff cooldown without a hit cannot block other skills; later hit unlocks buff and reaches chest");
 for(int i=3;i<missedTarget.labels.size();i+=3){
  check(missedTarget.labels.get(i).endsWith("原地控制")&&missedTarget.labels.get(i+1).endsWith("原地减益")&&missedTarget.labels.get(i+2).endsWith("原地增益"),"each subsequent cycle tries all three skills and keeps prerequisite order");
  check(missedTarget.times.get(i+1)-missedTarget.times.get(i)==200000000L&&missedTarget.times.get(i+2)-missedTarget.times.get(i+1)==200000000L,"each 1-2-3 cycle uses a 200ms gap even when skills cannot cast");
 }
 check(missedTarget.times.get(3)-missedTarget.times.get(2)<1000000000L,"unavailable buff proceeds directly to control instead of waiting 2.4 seconds or five-second idle spawn delay");
 MissedDebuffRunner lockedBuff=new MissedDebuffRunner();lockedBuff.alwaysLocked=true;lockedBuff.params.put("roleClearWait",5000);lockedBuff.params.put("maxRetries",1);f=new FastFlow(lockedBuff);f.currentRole=2;
 try{f.finishRoleCombat(1);throw new AssertionError("missing chest must end batch");}catch(Exception error){check(error.getMessage().contains("尚未出现宝箱")&&f.roleStage==3&&lockedBuff.cycleDebuffs>=2&&lockedBuff.buffAttempts>=3&&lockedBuff.automaticRetries==0,"permanently unavailable buff never exhausts the input retry budget or prevents control/debuff attempts");}
 CycleRetryRunner interruptedCycle=new CycleRetryRunner();f=new FastFlow(interruptedCycle);f.currentRole=2;
 try{f.finishRoleCombat(1);throw new AssertionError("cycle gesture must fail");}catch(Exception error){check(f.roleStage==3&&f.stationarySkill==1&&interruptedCycle.taps==4,"real cancelled gesture retains the current cycle skill");}
 java.util.List<String> resumedCycle=new java.util.ArrayList<>();interruptedCycle.tapLabelHook=label->{resumedCycle.add(label);if(label.endsWith("原地增益"))interruptedCycle.vision.worldChest=true;};
 check(f.finishRoleCombat(1)&&interruptedCycle.moves==1&&resumedCycle.size()==2&&resumedCycle.get(0).endsWith("原地减益")&&resumedCycle.get(1).endsWith("原地增益"),"Continue resumes failed cycle skill without repeating movement, ultimate or opening sequence");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=2;r.ignoredDebuffs=100;final RunnerService stopProtection=r;r.tapLabelHook=label->{if(label.endsWith("先释放减益"))stopProtection.stopped=true;};
 try{f.finishRoleCombat(1);throw new AssertionError("stop must interrupt fast small skills");}catch(Exception error){check(error.getMessage().equals("用户停止")&&r.taps==2&&f.openingSkill==1,"stop after the first attempted small skill prevents further input");}
 r=new RunnerService();f=new FastFlow(r);f.currentRole=2;r.entry=false;
 try{f.protectOpening();throw new AssertionError("wrong screen must reject protection");}catch(Exception error){check(error.getMessage().contains("战斗画面不符")&&r.taps==0,"no protective input outside battle");}
 UltimateRetryRunner interruptedUltimate=new UltimateRetryRunner();f=new FastFlow(interruptedUltimate);f.currentRole=3;
 try{f.finishRoleCombat(1);throw new AssertionError("ultimate fails three times");}catch(Exception expected){check(interruptedUltimate.taps==1&&f.preUltimateControlCast&&f.roleStage==1,"retain successful control while ultimate fails");}
 interruptedUltimate.tapLabelHook=label->{check(label.endsWith("释放大招一次"),"resume only failed ultimate after control");interruptedUltimate.vision.worldChest=true;};
 check(f.finishRoleCombat(1)&&interruptedUltimate.moves==1&&interruptedUltimate.taps==2,"continue cannot repeat successful control or entrance movement");
 f.resetRoleRoute();check(!f.preUltimateControlCast,"new round resets pre-ultimate control progress");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=3;r.ignoredUltimates=4;
 final RunnerService ignoredUltimate=r;r.tapLabelHook=label->{if(label.endsWith("释放大招一次")&&ignoredUltimate.ignoredUltimates==0)ignoredUltimate.vision.worldChest=true;};
 check(f.finishRoleCombat(1)&&r.taps==6&&r.moves==1&&r.automaticRetries==4,"control animation can ignore four ultimate taps, then cooldown confirms one successful cast without replaying control");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=3;r.params.put("maxRetries",2);r.ignoredUltimates=100;
 try{f.finishRoleCombat(1);throw new AssertionError("unaccepted ultimate must fail");}catch(Exception error){check(error.getMessage().contains("自动重试次数")&&r.taps==4&&f.roleStage==1&&f.preUltimateControlCast&&!r.skillCooling(1720,1290),"failed ultimate must remain pending without fabricating cooldown or repeating control");}
 r.ignoredUltimates=0;r.retrySucceeded();final RunnerService continuedUltimate=r;r.tapLabelHook=label->{check(label.endsWith("释放大招一次"),"Continue retries only the unaccepted ultimate");continuedUltimate.vision.worldChest=true;};
 check(f.finishRoleCombat(1)&&r.taps==5&&r.moves==1&&r.skillCooling(1720,1290),"Continue confirms actual ultimate cooldown");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=3;r.cooldowns.put("1720,1290",true);
 final RunnerService oldCooldown=r;r.waitHook=()->{if(oldCooldown.taps==0&&oldCooldown.waited>=2500)oldCooldown.cooldowns.remove("1720,1290");};r.tapLabelHook=label->{if(label.endsWith("释放大招一次"))oldCooldown.vision.worldChest=true;};
 check(f.finishRoleCombat(1)&&r.taps==2&&r.automaticRetries==0&&r.waited>=3200,"old cooldown waits for actual readiness without consuming retries or counting as a new cast");
 f.resetRoleRoute();check(f.pendingUltimateTapAt<0,"new round resets pending confirmation");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=3;r.ultimateLifetimeMs=1000;r.params.put("roleClearWait",5000);
 java.util.List<String> repeatedSpells=new java.util.ArrayList<>();r.tapLabelHook=repeatedSpells::add;
 for(int attempt=0;attempt<2;attempt++){r.cooldowns.clear();try{f.finishRoleCombat(1);throw new AssertionError("missing chest must fail");}catch(Exception error){check(error.getMessage().contains("尚未出现宝箱"),"expected role-three clear retry");}}
 check(r.moves==1&&r.taps==4&&repeatedSpells.get(0).endsWith("三道冰锥控制")&&repeatedSpells.get(2).endsWith("三道冰锥控制"),"every fresh ultimate recast must have control first without repeating movement");
 for(int previous:new int[]{0,5000,130000,140000,150000}){
  android.content.SharedPreferences prefs=new android.content.SharedPreferences();prefs.putInt("ultimateCooldown",previous);
  MainActivity.upgradeCombatPreferences(prefs);check(prefs.getInt("ultimateCooldown",-1)==previous,"obsolete cooldown setting no longer gets shortened");
  MainActivity.upgradeCombatPreferences(prefs);check(prefs.getInt("ultimateCooldown",-1)==previous,"reopening assistant does not alter obsolete cooldown setting");
 }
 android.content.SharedPreferences emptyPrefs=new android.content.SharedPreferences();MainActivity.upgradeCombatPreferences(emptyPrefs);check(!emptyPrefs.contains("ultimateCooldown"),"new install has no fixed ultimate cooldown setting");
 check(emptyPrefs.getInt("craftGap",0)==600,"new install uses paced synthesis input");
 android.content.SharedPreferences oldCraft=new android.content.SharedPreferences();oldCraft.putInt("craftGapRevision",1);oldCraft.putInt("craftGap",300);MainActivity.upgradeCombatPreferences(oldCraft);check(oldCraft.getInt("craftGap",0)==600,"old fast synthesis input migrates to600ms");oldCraft.putInt("craftGap",900);MainActivity.upgradeCombatPreferences(oldCraft);check(oldCraft.getInt("craftGap",0)==900,"slower custom synthesis pace preserved");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=4;r.ultimateLifetimeMs=1000;r.params.put("roleClearWait",5000);
 for(int attempt=0;attempt<2;attempt++)try{f.finishRoleCombat(1);throw new AssertionError("missing chest must fail");}catch(Exception error){check(error.getMessage().contains("宝箱")||error.getMessage().contains("开箱"),"expected clear/approach retry");}
 check(r.moves==4&&r.durations.get(1)==500&&r.durations.get(2)==200&&r.durations.get(3)==200&&r.taps==2,"missed chest approaches once, then nudges without repeating entrance; ready ultimate still recasts");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=2;r.params.put("ultimateCooldown",0);r.params.put("roleClearWait",5000);
 for(int attempt=0;attempt<2;attempt++)try{f.finishRoleCombat(1);}catch(Exception error){check(error.getMessage().contains("尚未出现宝箱"),"stationary clear retry");}
 check(r.moves==1,"stationary skill retry must never move again");
 for(int role:new int[]{2,3,4,5}) {
  r=new RunnerService();f=new FastFlow(r);f.currentRole=role;r.vision.worldChest=true;r.retryHook=()->{};
  final RunnerService opened=r;
  if(role<=3)r.screens.put("chest",true);else r.chestAfterMoves=1;
  r.tapHook=()->opened.retry=true;
  f.collectChest();check(r.taps==1&&r.moves==(role<=3?0:1),"centre roles open in place; other roles move forward once to open");
 }
 for(int profession:new int[]{RolePlan.SORCERESS,RolePlan.ALCHEMIST}){
  r=new RunnerService();RolePlan approachPlan=new RolePlan();approachPlan.profession=profession;approachPlan.enabled=true;r.plans.put(6,approachPlan);r.params.put("roleClearWait",5000);r.chestAfterMoves=2;f=new FastFlow(r);f.currentRole=6;
  check(f.finishRoleCombat(1)&&!r.vision.worldChest&&r.moves==2&&r.durations.get(1)==500&&r.taps==1&&f.roleChestMoved&&f.roleChestConfirmed&&r.waited>=11000,"mage/alchemist approach after clearing wait even when chest body is never recognized; no movement during initial cast");
  final RunnerService fallbackOpened=r;r.tapHook=()->fallbackOpened.retry=true;f.collectChest();check(r.moves==2&&r.taps==2,"collect opens the newly visible button without repeating post-cast approach");
 }
 PartialChestRunner partialChest=new PartialChestRunner();RolePlan partialPlan=new RolePlan();partialPlan.profession=RolePlan.SORCERESS;partialPlan.enabled=true;partialChest.plans.put(6,partialPlan);partialChest.params.put("roleClearWait",5000);partialChest.chestAfterMoves=4;partialChest.outcomes.add(200);partialChest.outcomes.add(0);f=new FastFlow(partialChest);f.currentRole=6;
 try{f.finishRoleCombat(1);throw new AssertionError("partial chest approach");}catch(MovementInterrupted expected){check(f.roleChestMoveRemaining==300&&f.roleStage==3,"retain only uncompleted post-cast movement");}long firstWait=partialChest.waited;
 try{f.finishRoleCombat(1);throw new AssertionError("rejected chest approach");}catch(MovementInterrupted expected){check(f.roleChestMoveRemaining==300&&partialChest.waited==firstWait,"Continue resumes movement immediately without another clear wait");}
 check(f.finishRoleCombat(1)&&partialChest.approachApplied==500&&partialChest.durations.equals(java.util.Arrays.asList(2100,500,300,300))&&partialChest.taps==1,"partial, rejected and continued approaches total 500ms accepted movement without another ultimate");
 partialChest=new PartialChestRunner();partialChest.plans.put(6,partialPlan);partialChest.params.put("roleClearWait",5000);partialChest.failAfterComplete=true;f=new FastFlow(partialChest);f.currentRole=6;
 try{f.finishRoleCombat(1);throw new AssertionError("completed approach with lost check");}catch(MovementInterrupted expected){check(f.roleChestMoveRemaining==0,"completed movement recorded before failed callback/check");}
 try{f.finishRoleCombat(1);throw new AssertionError("missing chest still waits");}catch(Exception expected){check(expected.getMessage().contains("开箱按钮未出现")&&partialChest.moves==3&&partialChest.approachApplied==500&&partialChest.durations.get(2)==200&&f.roleChestMoved,"full approach cannot repeat after failed later check; only a short nudge follows");}
 f.resetRoleRoute();check(f.roleChestMoveRemaining==-1&&!f.roleChestMoved,"new round resets approach budget");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=5;r.vision.worldChest=true;
 check(f.finishRoleCombat(1)&&r.taps==0&&r.moves==0&&f.roleChestConfirmed,"spawned chest must advance role five from combat to approach without another ultimate");
 r.vision.worldChest=false;r.chestAfterMoves=2;final RunnerService approaching=r;r.tapHook=()->approaching.retry=true;
 f.collectChest();check(r.moves==2&&r.durations.get(0)==500&&r.durations.get(1)==200&&r.taps==1,"role five approaches five hundred ms, then nudges until button appears even when chest body is hidden");
 f.resetCombatRoute();r.retry=false;r.chestAfterMoves=Integer.MAX_VALUE;
 check(!f.roleChestConfirmed&&!f.rewardReady(),"new round cannot inherit a confirmed chest");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=5;r.vision.worldChest=true;final RunnerService transientChest=r;r.waitHook=()->transientChest.vision.worldChest=false;
 check(!f.rewardReady()&&!f.roleChestConfirmed,"one-frame detection cannot latch a chest");
 r=new RunnerService();f=new FastFlow(r);f.currentRole=4;r.vision.worldChest=true;f.roleChestMoved=true;
 // More than the old three probes are allowed; the cap persists across Continue.
 final RunnerService missingButton=r;r.retryHook=()->{throw new RuntimeException("end test");};
 try{f.collectChest();}catch(Exception error){}
 check(r.moves==1&&f.chestNudges==1,"one nudge before retry");
 for(int retry=0;retry<22;retry++)try{f.collectChest();}catch(Exception error){}
 check(r.moves==20&&f.chestNudges==20,"retry keeps the twenty-nudge cap across calls");
 DistantChestRunner distant=new DistantChestRunner(1900);RolePlan distantPlan=new RolePlan();distantPlan.profession=RolePlan.SORCERESS;distantPlan.enabled=true;distant.plans.put(6,distantPlan);distant.params.put("roleClearWait",5000);f=new FastFlow(distant);f.currentRole=6;boolean reached=false;long waitedOnce=-1;
 for(int attempt=0;attempt<10&&!reached;attempt++){try{reached=f.finishRoleCombat(1);}catch(Exception error){check(error.getMessage().contains("宝箱")||error.getMessage().contains("开箱"),"missing interaction retains approach step");}if(attempt==0)waitedOnce=distant.waited;else check(distant.waited==waitedOnce,"approach retries never wait through another clearing batch");}
 check(reached&&distant.approachMs==1900&&f.chestNudges==7&&distant.taps==1&&!distant.vision.worldChest,"a farther chest is reached with seven short probes despite missed body recognition, without repeating ultimate");
 final DistantChestRunner distantOpened=distant;distant.tapHook=()->distantOpened.retry=true;int beforeOpenMoves=distant.moves;f.collectChest();check(distant.moves==beforeOpenMoves&&distant.taps==2,"open immediately once within range without extra walking");
 SlowClearRunner slowClear=new SlowClearRunner();slowClear.plans.put(6,distantPlan);slowClear.chestAfterMoves=2;f=new FastFlow(slowClear);f.currentRole=6;check(f.finishRoleCombat(1)&&slowClear.approachAt-slowClear.clearAt>=30000000000L&&slowClear.approachAt-slowClear.clearAt<33000000000L,"frame-check overhead counts toward actual thirty-second clearing wait instead of inflating it to ninety seconds");
 PartialNudgeRunner nudge=new PartialNudgeRunner();nudge.outcomes.add(100);nudge.outcomes.add(0);f=new FastFlow(nudge);f.currentRole=4;
 for(int attempt=0;attempt<2;attempt++)try{f.nudgeRoleChest();throw new AssertionError("partial/rejected nudge");}catch(MovementInterrupted expected){check(f.chestNudgeRemaining==100&&f.chestNudges==0,"partial nudge retains remaining movement and does not consume a full probe count");}
 f.nudgeRoleChest();check(nudge.applied==200&&nudge.durations.equals(java.util.Arrays.asList(200,100,100))&&f.chestNudges==1,"continued short movement never exceeds one nudge budget");
 nudge=new PartialNudgeRunner();nudge.failAfterComplete=true;f=new FastFlow(nudge);f.currentRole=4;try{f.nudgeRoleChest();throw new AssertionError("completed nudge with lost callback");}catch(MovementInterrupted expected){}f.nudgeRoleChest();check(nudge.moves==1&&nudge.applied==200&&f.chestNudges==1,"completed nudge is not replayed after callback failure");f.resetRoleRoute();check(f.chestNudgeRemaining==-1&&f.chestNudges==0,"new round resets short probe cursor");
 android.content.SharedPreferences oldApproach=new android.content.SharedPreferences();oldApproach.putInt("chestNudgeLimit",3);MainActivity.upgradeCombatPreferences(oldApproach);check(oldApproach.getInt("chestNudgeLimit",0)==20,"old three-probe limit upgrades to twenty");MainActivity.upgradeCombatPreferences(oldApproach);check(oldApproach.getInt("chestNudgeLimit",0)==20,"probe upgrade applies once");
 for(int role:new int[]{2,3,4,5}){r=new RunnerService();f=new FastFlow(r);f.currentRole=role;r.params.put("ultimateCooldown",999999);check(f.waitUltimate(false)&&r.waited==700&&r.taps==0&&r.automaticRetries==0,"ready skill checks three frames without waiting an obsolete timer");}
 r=new RunnerService();f=new FastFlow(r);f.currentRole=3;r.params.put("roleClearWait",5000);try{f.finishRoleCombat(1);}catch(Exception expected){}
 check(r.moves==1&&r.taps==2&&r.waited==11000,"first entry only waits for readiness confirmation, spawn and clear checks");check(r.skillCooling(1720,1290),"actual ultimate cast is confirmed by cooldown");
 r=new RunnerService();f=new FastFlow(r);r.screens.put("menuSettings",true);r.entry=false;f.openMenu();check(r.taps==0,"already open settings entry bypasses hud and does not reopen menu");
 r=new RunnerService();f=new FastFlow(r);r.screens.put("menu",true);r.entry=false;f.openMenu();check(r.taps==0,"already open dungeon menu advances without hud guard");
 r=new RunnerService();f=new FastFlow(r);r.entry=false;r.screens.put("menuSettings",true);final RunnerService returning=r;
 java.util.List<String> returnLabels=new java.util.ArrayList<>();r.tapLabelHook=label->{returnLabels.add(label);if(label.equals("打开设置"))returning.screens.put("settingsReturn",true);if(label.equals("返回角色选择"))returning.screens.put("returnConfirm",true);if(label.equals("确认返回角色")){returning.screens.put("roles",true);returning.screens.put("roleEnter",true);}};
 f.returnToRoles();check(returnLabels.equals(java.util.Arrays.asList("打开设置","返回角色选择","确认返回角色")),"open-menu return flow clicks settings then return then confirm only");
 System.out.println("PASS: delayed highlight, automatic reselect/transition retry, no duplicate successful action, combat limits and saved progress");
 }}'''
}
# Exercise the actual new migration block without requiring Android UI classes.
activity=(ROOT/'src/cn/kongj/blackstone/MainActivity.java').read_text(encoding='utf8')
start=activity.index(' static void upgradeCombatPreferences(')
migration=activity[start:activity.rfind('\n}')]
stubs['cn/kongj/blackstone/MainActivity.java']='package cn.kongj.blackstone;import android.content.SharedPreferences;class MainActivity{'+migration+'}'
with tempfile.TemporaryDirectory(prefix='blackstone-combat-test-') as folder:
 path=Path(folder); sources=[]
 for name,body in stubs.items():
  file=path/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(body,encoding='utf8');sources.append(str(file))
 sources += [str(ROOT/'src/cn/kongj/blackstone'/name) for name in ('GameFlow.java','RepairFlow.java','MovementInterrupted.java','FlowPolicy.java','RolePlan.java','SkillCooldown.java','FlowControl.java','MovementModel.java','TownFlow.java','TownInventory.java')]
 subprocess.run([str(JAVA/'javac.exe'),'-encoding','UTF-8','-d',str(path),*sources],check=True)
 subprocess.run([str(JAVA/'java.exe'),'-cp',str(path),'cn.kongj.blackstone.CombatTest'],check=True)
