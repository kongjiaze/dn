"""Check partial entrance movement with the actual GameFlow and native stroke accounting.

Host replay only: no live game gestures or interruption of the paused runner.
"""
from pathlib import Path
import ast, subprocess, tempfile
ROOT=Path(__file__).resolve().parent;JAVA=Path('D:/JDK/openjdk-17/bin');SRC=ROOT/'src/cn/kongj/blackstone'
tree=ast.parse((ROOT/'check-combat-policy.py').read_text(encoding='utf8'))
stubs=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in n.targets)))
stubs.pop('cn/kongj/blackstone/CombatTest.java')
stubs['cn/kongj/blackstone/EntranceTest.java']='''package cn.kongj.blackstone;
import java.util.*;
class PartialRunner extends RunnerService {
 ArrayDeque<Integer> interruptions=new ArrayDeque<>();int applied;
 void move(String label,int x,int y,int ms)throws Exception{
  checkpoint();moves++;durations.add(ms);int elapsed=interruptions.isEmpty()?ms:Math.min(ms,Math.max(0,interruptions.removeFirst()));applied+=elapsed;
  if(elapsed<ms||interruptions.size()>0)throw new MovementInterrupted(ms,elapsed,"cancelled after partial input");
 }
}
class EntranceFlow extends GameFlow {EntranceFlow(RunnerService r){super(r);}long combatClock(){return r.virtualTime;}}
public class EntranceTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static PartialRunner runner(int profession){PartialRunner r=new PartialRunner();RolePlan p=new RolePlan();p.profession=profession;p.enabled=true;r.plans.put(2,p);r.tapHook=()->r.vision.worldChest=true;return r;}
 static void cancelled(EntranceFlow f,int remaining)throws Exception{try{f.finishRoleCombat(1);throw new AssertionError("expected movement interruption");}catch(MovementInterrupted e){check(f.roleStage==0&&f.roleMoveRemaining==remaining,"partial route cursor retained");}}
 public static void main(String[] args)throws Exception{
  for(int profession=1;profession<=RolePlan.PRIEST;profession++){
   PartialRunner r=runner(profession);int total=r.rolePlan(2).forward();r.interruptions.add(total-300);r.interruptions.add(150);
   EntranceFlow f=new EntranceFlow(r);f.currentRole=2;cancelled(f,300);cancelled(f,150);f.finishRoleCombat(1);
   check(r.durations.equals(Arrays.asList(total,300,150))&&r.applied==total&&f.roleMoveRemaining==0&&f.roleStage>0,"all professions cap accumulated movement at configured duration");
  }
  PartialRunner r=runner(RolePlan.SAGE);r.interruptions.add(0);r.interruptions.add(0);EntranceFlow f=new EntranceFlow(r);f.currentRole=2;cancelled(f,3000);cancelled(f,3000);f.finishRoleCombat(1);check(r.applied==3000,"rejected gestures consume no movement budget");
  // Completion with a missing callback, or a failed later check, already consumed the route.
  RunnerService complete=new RunnerService(){void move(String label,int x,int y,int ms)throws Exception{moves++;durations.add(ms);throw new MovementInterrupted(ms,ms,"callback/check unavailable after full movement");}};
  complete.tapHook=()->complete.vision.worldChest=true;f=new EntranceFlow(complete);f.currentRole=2;cancelled(f,0);f.finishRoleCombat(1);check(complete.moves==1&&complete.taps>0,"continue after full movement proceeds to skills without walking again");
  f.resetRoleRoute();complete.vision.worldChest=false;check(f.roleMoveRemaining==-1&&f.roleStage==0,"new round resets movement budget only once");
  r=runner(RolePlan.SAGE);r.entry=false;f=new EntranceFlow(r);f.currentRole=2;try{f.finishRoleCombat(1);}catch(Exception expected){}check(r.moves==0&&f.roleMoveRemaining==3000,"unstable battle scene cannot consume movement before dispatch");r.entry=true;f.finishRoleCombat(1);check(r.applied==3000,"stable scene resumes original budget");
  r=runner(RolePlan.SAGE);for(int elapsed:new int[]{2773,2610,196,32,32,419,413,492})r.interruptions.add(elapsed);f=new EntranceFlow(r);f.currentRole=2;
  cancelled(f,227);cancelled(f,0);f.finishRoleCombat(1);check(r.durations.equals(Arrays.asList(3000,227))&&r.applied==3000&&r.taps>0,"reported eight-cancellation sequence cannot replay nine full entrance moves");
  r=runner(RolePlan.SAGE);r.stopped=true;f=new EntranceFlow(r);f.currentRole=2;try{f.finishRoleCombat(1);}catch(Exception expected){}check(r.moves==0,"stop prevents entrance input");
  System.out.println("PASS: actual entrance route retains partial progress, caps repeated movement, resumes at skills and resets each round");
 }
}'''

def run(stubs, actual, main):
 with tempfile.TemporaryDirectory(prefix='blackstone-entrance-') as folder:
  p=Path(folder);sources=[]
  for name,body in stubs.items():
   file=p/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(body,encoding='utf8');sources.append(str(file))
  sources += [str(SRC/name) for name in actual]
  subprocess.run([str(JAVA/'javac.exe'),'-encoding','UTF-8','-d',str(p),*sources],check=True)
  subprocess.run([str(JAVA/'java.exe'),'-cp',str(p),main],check=True)
run(stubs,('GameFlow.java','RepairFlow.java','MovementInterrupted.java','FlowPolicy.java','RolePlan.java','SkillCooldown.java','FlowControl.java','MovementModel.java','TownFlow.java','TownInventory.java'),'cn.kongj.blackstone.EntranceTest')

touch=(SRC/'TouchService.java').read_text(encoding='utf8');stroke=touch[touch.index(' public boolean stroke('):touch.index(' public boolean fillLoginPassword(')]
native={
 'android/graphics/Path.java':'package android.graphics;public class Path{public void moveTo(float x,float y){}public void lineTo(float x,float y){}}',
 'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long now;public static long uptimeMillis(){return now;}}',
 'android/os/Handler.java':'''package android.os;public class Handler{public static boolean queued;public static Runnable pending;public Handler(Object loop){}public boolean post(Runnable task){if(queued)pending=task;else task.run();return true;}public void removeCallbacks(Runnable task){if(pending==task)pending=null;}}''',
 'android/accessibilityservice/GestureDescription.java':'''package android.accessibilityservice;import android.graphics.Path;public class GestureDescription{public static class StrokeDescription{public StrokeDescription(Path p,long start,long duration){}}public static class Builder{public Builder addStroke(StrokeDescription s){return this;}public GestureDescription build(){return new GestureDescription();}}}''',
 'cn/kongj/blackstone/TouchService.java':'''package cn.kongj.blackstone;import android.accessibilityservice.*;import android.graphics.Path;import android.os.*;import java.util.concurrent.*;
class TouchService {
 int mode,dispatches;long elapsed;Object getMainLooper(){return null;}
 static class GestureResultCallback{public void onCompleted(GestureDescription d){}public void onCancelled(GestureDescription d){}}
 boolean dispatchGesture(GestureDescription g,GestureResultCallback cb,Object handler){dispatches++;android.os.SystemClock.now+=elapsed;if(mode==0){cb.onCompleted(g);return true;}if(mode==1){cb.onCancelled(g);return true;}if(mode==2)return false;if(mode==3)return true;throw new IllegalStateException("dispatch rejected");}
'''+stroke+'}',
 'cn/kongj/blackstone/StrokeTest.java':'''package cn.kongj.blackstone;import android.os.*;public class StrokeTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  TouchService t=new TouchService();t.elapsed=3000;StrokeOutcome out=t.strokeDetailed(0,0,1,1,3000);check(out.accepted&&out.completed&&out.elapsedMs==3000,"completed movement consumes entire budget");
  t.mode=1;t.elapsed=2773;out=t.strokeDetailed(0,0,1,1,3000);check(out.accepted&&!out.completed&&out.elapsedMs==2773,"cancel callback retains only elapsed accepted input");
  t.elapsed=4000;out=t.strokeDetailed(0,0,1,1,3000);check(out.elapsedMs==3000,"delayed cancellation cannot exceed requested movement");
  t.mode=2;out=t.strokeDetailed(0,0,1,1,3000);check(!out.accepted&&out.elapsedMs==0,"dispatch rejection consumes zero");
  t.mode=4;out=t.strokeDetailed(0,0,1,1,3000);check(!out.accepted&&out.elapsedMs==0,"dispatch exception consumes zero");
  t.mode=3;t.elapsed=10;out=t.strokeDetailed(0,0,1,1,1);check(out.accepted&&out.uncertain&&out.elapsedMs==1,"lost callback counts already accepted input conservatively");
  Handler.queued=true;int count=t.dispatches;out=t.strokeDetailed(0,0,1,1,1);check(!out.accepted&&out.elapsedMs==0&&Handler.pending==null&&t.dispatches==count,"expired queued gesture is removed and cannot move later");
  System.out.println("PASS: actual native stroke method separates completion, partial cancellation, rejection and callback timeout");
 }
}'''
}
run(native,('StrokeOutcome.java',),'cn.kongj.blackstone.StrokeTest')

runner=(SRC/'RunnerService.java').read_text(encoding='utf8');move=runner[runner.index(' void move('):runner.index(' void launchGame(')]
delivery={
 'cn/kongj/blackstone/TouchService.java':'''package cn.kongj.blackstone;class TouchService{static TouchService instance;StrokeOutcome outcome;int releases,dispatches;boolean releaseOk=true;String active="game";String activePackage(){return active;}StrokeOutcome strokeDetailed(float x,float y,float ex,float ey,long ms){dispatches++;return outcome;}boolean stroke(float x,float y,float ex,float ey,long ms){releases++;return releaseOk;}}''',
 'cn/kongj/blackstone/RunnerService.java':'''package cn.kongj.blackstone;class RunnerService{
 int step,width=2560,height=1440;String actionLabel="",gamePackage="game";boolean destroyed,stopped,failWait;long waited;StringBuilder logs=new StringBuilder();
 void checkpoint()throws Exception{if(stopped)throw new Exception("stop");}void log(String message){logs.append(message).append("\\n");}void waitMs(long ms)throws Exception{waited+=ms;if(failWait)throw new Exception("later check interrupted");}int param(String name,int fallback){return fallback;}
 Prefs getSharedPreferences(String name,int mode){return new Prefs();}static class Prefs{Prefs edit(){return this;}Prefs putInt(String k,int v){return this;}Prefs putString(String k,String v){return this;}void apply(){}}
'''+move+'}',
 'cn/kongj/blackstone/RunnerMoveTest.java':'''package cn.kongj.blackstone;public class RunnerMoveTest {
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static void failure(RunnerService r,int consumed)throws Exception{try{r.move("move",300,928,3000);throw new AssertionError("expected failure");}catch(MovementInterrupted e){check(e.movedMs==consumed,"physical progress retained in exception");}}
 public static void main(String[] args)throws Exception{
  TouchService t=new TouchService();TouchService.instance=t;RunnerService r=new RunnerService();t.outcome=new StrokeOutcome(true,true,false,3000,3000);r.move("move",300,928,3000);check(r.waited==1800&&t.releases==0,"normal movement and gap unchanged");
  t.outcome=new StrokeOutcome(true,false,false,3000,2773);failure(r,2773);check(t.releases==1,"cancelled accepted movement neutralizes joystick before retry");
  t.outcome=new StrokeOutcome(false,false,false,3000,0);failure(r,0);check(t.releases==1,"rejected dispatch does not send an unnecessary release");
  t.outcome=new StrokeOutcome(true,false,true,3000,8000);failure(r,3000);check(t.releases==2,"callback timeout preserves full consumed budget and releases input");
  t.outcome=new StrokeOutcome(true,true,false,3000,3000);r.failWait=true;failure(r,3000);r.failWait=false;check(t.releases==2,"failure after completion cannot replay physical movement");
  t.outcome=new StrokeOutcome(true,false,false,3000,100);t.active="other";failure(r,100);check(t.releases==2,"no neutral touch in another app");
  t.active="game";t.releaseOk=false;failure(r,100);check(r.logs.toString().contains("方向键释放未确认"),"failed release is reported truthfully");
  r.stopped=true;int before=t.dispatches;try{r.move("move",300,928,3000);}catch(Exception expected){}check(t.dispatches==before,"stop blocks dispatch");
  System.out.println("PASS: actual runner movement releases cancelled input and retains progress across callback and later-check failures");
 }
}'''
}
run(delivery,('StrokeOutcome.java','MovementInterrupted.java'),'cn.kongj.blackstone.RunnerMoveTest')
