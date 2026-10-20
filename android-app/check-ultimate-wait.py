"""Exercise actual GameFlow readiness waits, challenge gating and Continue.

Host-only: no game login, movement or live combat.
"""
from pathlib import Path
import ast, subprocess, tempfile
ROOT=Path(__file__).resolve().parent;JAVA=Path('D:/JDK/openjdk-17/bin')
tree=ast.parse((ROOT/'check-combat-policy.py').read_text(encoding='utf-8'))
stubs=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in n.targets)))
stubs.pop('cn/kongj/blackstone/CombatTest.java')
stubs['cn/kongj/blackstone/UltimateWaitTest.java']='''package cn.kongj.blackstone;
import java.util.*;
class WaitFlow extends GameFlow {WaitFlow(RunnerService r){super(r);}long combatClock(){return r.virtualTime;}}
class WaitRunner extends RunnerService {
 long readyAt;boolean unknown;int stateCalls;int[] sequence;
 int ultimateState()throws Exception{checkpoint();int index=stateCalls++;if(unknown)return SkillCooldown.UNKNOWN;if(sequence!=null)return sequence[Math.min(index,sequence.length-1)];return virtualTime>=readyAt?SkillCooldown.READY:SkillCooldown.COOLING;}
}
class PendingCastRunner extends RunnerService {
 boolean interrupted;
 void combatTap(String label,int x,int y,int gap)throws Exception{
  super.combatTap(label,x,y,gap);
  if(label.endsWith("释放大招一次")&&!interrupted){interrupted=true;throw new Exception("cast callback uncertain");}
 }
}
public class UltimateWaitTest {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  for(int slot:new int[]{2,3,4,5})for(int duration:new int[]{0,1000,25000,155000}){
   WaitRunner r=new WaitRunner();r.readyAt=duration*1000000L;r.params.put("ultimateCooldown",1);r.params.put("maxRetries",1);
   WaitFlow f=new WaitFlow(r);f.currentRole=slot;
   check(f.waitUltimate(false)&&r.virtualTime>=r.readyAt+700000000L&&r.virtualTime<r.readyAt+1050000000L,"all professions wait actual duration plus stable samples");
   check(r.taps==0&&r.moves==0&&r.automaticRetries==0,"ordinary cooldown never consumes retries or sends input");
  }
  WaitRunner flashing=new WaitRunner();flashing.sequence=new int[]{0,1,0,0,0};WaitFlow f=new WaitFlow(flashing);
  check(f.waitUltimate(false)&&flashing.waited==1400,"one ready frame followed by cooldown resets the stable check");
  for(int slot:new int[]{2,3,4,5}){
   WaitRunner r=new WaitRunner();r.readyAt=7000000000L;r.retry=true;r.entry=false;WaitFlow flow=new WaitFlow(r);flow.currentRole=slot;flow.battlePhase=4;
   r.tapLabelHook=label->{check(label.equals("再次挑战")&&r.virtualTime>=r.readyAt+700000000L,"challenge must follow observed readiness");r.stopped=true;};
   try{flow.brushUntilEmpty(true);throw new AssertionError("stop after challenge");}catch(Exception e){check(e.getMessage().equals("用户停止")&&r.taps==1&&r.automaticRetries==0,"settlement waits without combat inputs or retry exhaustion");}
  }
  for(int slot:new int[]{2,3,4,5}){
   WaitRunner r=new WaitRunner();r.readyAt=2100000000L;WaitFlow flow=new WaitFlow(r);flow.currentRole=slot;
   r.tapLabelHook=label->{check(r.virtualTime>=r.readyAt+700000000L,"no skills during initial cooldown");if(label.endsWith("释放大招一次"))r.vision.worldChest=true;};
   check(flow.finishRoleCombat(1)&&r.moves==1&&r.taps==(slot==2?3:slot==3?2:1),"first entry with real cooldown waits, then follows profession opening sequence");
  }
  WaitRunner unknown=new WaitRunner();unknown.unknown=true;f=new WaitFlow(unknown);f.currentRole=3;
  try{f.finishRoleCombat(1);throw new AssertionError("unknown button");}catch(Exception e){check(e.getMessage().contains("无法可靠识别")&&unknown.taps==0&&unknown.moves==1&&f.roleStage==1,"unreadable button is not ready; retain completed movement");}
  unknown.unknown=false;unknown.tapLabelHook=label->{if(label.endsWith("释放大招一次"))unknown.vision.worldChest=true;};
  check(f.finishRoleCombat(1)&&unknown.moves==1&&unknown.taps==2,"Continue checks readiness again without replaying movement");
  WaitRunner interruptedWait=new WaitRunner();interruptedWait.readyAt=200000000000L;f=new WaitFlow(interruptedWait);interruptedWait.waitHook=()->{if(interruptedWait.waited>=700)interruptedWait.stopped=true;};
  try{f.waitUltimate(false);throw new AssertionError("stop cooldown wait");}catch(Exception e){check(e.getMessage().equals("用户停止")&&interruptedWait.taps==0&&interruptedWait.automaticRetries==0,"Stop interrupts long cooldown wait");}
  WaitRunner changed=new WaitRunner();changed.retry=true;changed.readyAt=200000000000L;f=new WaitFlow(changed);changed.waitHook=()->{if(changed.waited>=700)changed.retry=false;};
  try{f.waitUltimate(true);throw new AssertionError("changed settlement");}catch(Exception e){check(e.getMessage().contains("结算画面已变化")&&changed.taps==0,"changed screen cannot fall through to challenge");}
  RunnerService empty=new RunnerService(){int ultimateState(){throw new AssertionError("no readiness wait with exhausted fatigue");}};empty.retry=true;empty.vision.fatigueValue=0;f=new WaitFlow(empty);f.currentRole=3;f.battlePhase=3;f.brushUntilEmpty(true);check(empty.taps==0,"exhausted fatigue returns without a cooldown wait");
  RunnerService dialog=new RunnerService(){int ultimateState(){throw new AssertionError("do not delay timed confirmation");}};dialog.retry=true;dialog.screens.put("agree",true);f=new WaitFlow(dialog);f.currentRole=3;f.battlePhase=4;dialog.tapLabelHook=label->{check(label.equals("倒计时弹窗：同意"),"already open confirmation continues immediately");dialog.stopped=true;};
  try{f.brushUntilEmpty(true);throw new AssertionError("stop after agree");}catch(Exception e){check(e.getMessage().equals("用户停止")&&dialog.taps==1,"existing countdown bypasses readiness wait");}
  PendingCastRunner pending=new PendingCastRunner();f=new WaitFlow(pending);f.currentRole=3;
  try{f.finishRoleCombat(1);throw new AssertionError("uncertain callback");}catch(Exception e){check(e.getMessage().equals("cast callback uncertain")&&f.pendingUltimateTapAt>=0&&f.roleStage==1&&pending.taps==2,"retain actual accepted cast for confirmation");}
  long before=pending.virtualTime;pending.waitHook=()->pending.vision.worldChest=true;
  check(f.finishRoleCombat(1)&&pending.moves==1&&pending.taps==2&&pending.virtualTime-before<2000000000L,"Continue confirms pending cooldown without waiting out or recasting accepted ultimate");
  System.out.println("PASS: four professions, variable actual cooldown, stable samples, no retry exhaustion, unreadable icon, Continue, Stop, settlement/fatigue/countdown and pending cast");
 }
}'''
with tempfile.TemporaryDirectory(prefix='blackstone-ultimate-wait-') as folder:
 p=Path(folder);sources=[]
 for name,body in stubs.items():
  file=p/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(body,encoding='utf-8');sources.append(str(file))
 sources += [str(ROOT/'src/cn/kongj/blackstone'/name) for name in ('GameFlow.java','RepairFlow.java','MovementInterrupted.java','FlowPolicy.java','RolePlan.java','SkillCooldown.java','FlowControl.java','MovementModel.java','TownFlow.java','TownInventory.java')]
 subprocess.run([str(JAVA/'javac.exe'),'-encoding','UTF-8','-d',str(p),*sources],check=True)
 subprocess.run([str(JAVA/'java.exe'),'-cp',str(p),'cn.kongj.blackstone.UltimateWaitTest'],check=True)
