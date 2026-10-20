"""Run actual LoginFlow with a simulated game UI, without launching or logging into it."""
from pathlib import Path
import ast, subprocess, tempfile
ROOT=Path(__file__).resolve().parent
JAVA=Path('D:/JDK/openjdk-17/bin')
tree=ast.parse((ROOT/'check-combat-policy.py').read_text(encoding='utf8'))
stubs=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in n.targets)))
stubs.pop('cn/kongj/blackstone/CombatTest.java')
v=stubs['cn/kongj/blackstone/Vision.java'].replace('boolean matches(Bitmap b,String k){return true;}',
 'java.util.Map<String,Boolean> screens;boolean matches(Bitmap b,String k){return screens.getOrDefault(k,false);}')
stubs['cn/kongj/blackstone/Vision.java']=v
r=stubs['cn/kongj/blackstone/RunnerService.java']
r=r.replace('Bitmap frame(){return new Bitmap();}', '''
 boolean loginRun,manualMode;int launched,filled;Runnable fillHook;
 boolean manualLogin(){return manualMode;}
 RunnerService(){vision.screens=screens;}
 void launchGame()throws Exception{checkpoint();launched++;}
 void waitGameForeground()throws Exception{checkpoint();}
 void fillConfiguredLogin()throws Exception{checkpoint();filled++;screens.put("loginPasswordReady",true);if(fillHook!=null)fillHook.run();}
 Bitmap frame(){return new Bitmap();}''')
# The real range method reads preferences; adapt only the preference container to the stub.
r=r.replace('return progress;', 'return k.equals("route")?route:progress;')
r=r.replace('android.content.SharedPreferences progress=', 'android.content.SharedPreferences route=new android.content.SharedPreferences();android.content.SharedPreferences progress=')
stubs['cn/kongj/blackstone/RunnerService.java']=r
stubs['cn/kongj/blackstone/LoginTest.java']='''package cn.kongj.blackstone;
import java.util.*;
public class LoginTest {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static void state(RunnerService r,String key){r.screens.clear();r.screens.put(key,true);if(key.equals("roles"))r.screens.put("roleEnter",true);}
 static List<String> path(RunnerService r,boolean agreed){
  List<String> labels=new ArrayList<>();r.tapLabelHook=label->{labels.add(label);
   switch(label){
    case "更换账号登录":state(r,"loginSms");break;
    case "快速登录失效：继续":state(r,"loginSms");break;
    case "切换到密码登录":state(r,"loginPassword");if(agreed)r.screens.put("loginAgreement",true);break;
    case "勾选登录协议同意":r.screens.put("loginAgreement",true);break;
    case "密码登录：进入游戏":state(r,"loginNotice");break;
    case "关闭登录公告":state(r,"loginServer");break;
    case "登录所选服务器":state(r,"roles");break;
    default:throw new AssertionError("unexpected input "+label);
   }
  };return labels;
 }
 public static void main(String[] args)throws Exception{
  RunnerService r=new RunnerService();state(r,"loginAccount");List<String> labels=path(r,false);new LoginFlow(r).run();
  check(r.launched==1&&r.filled==1&&labels.equals(Arrays.asList("更换账号登录","切换到密码登录","勾选登录协议同意","密码登录：进入游戏","关闭登录公告","登录所选服务器")),"configured account re-login, agreement and server path");
  r=new RunnerService();state(r,"loginAccount");labels=path(r,true);new LoginFlow(r).run();check(!labels.contains("勾选登录协议同意")&&r.filled==1,"already agreed must not uncheck it");
  r=new RunnerService();state(r,"loginNotice");labels=path(r,false);new LoginFlow(r).run();check(r.filled==0&&r.taps==2,"valid saved login skips password and reaches roles");
  r=new RunnerService();state(r,"roles");new LoginFlow(r).run();check(r.taps==0&&r.filled==0,"already on roles performs no login input");
  r=new RunnerService();state(r,"loginPassword");r.screens.put("loginAgreement",true);path(r,true);r.cancels=1;new LoginFlow(r).run();check(r.taps==3&&r.filled==1,"successful login input with cancelled gesture callback must not submit twice");
  r=new RunnerService();state(r,"loginPassword");r.screens.put("loginAgreement",true);final RunnerService rejected=r;
  r.tapLabelHook=label->{check(label.equals("密码登录：进入游戏"),"only login submission");rejected.screens.put("loginRejected",true);};
  r.retryHook=()->{check(rejected.taps==1&&rejected.filled==1&&rejected.automaticRetries==0,"incorrect credentials do not auto-submit repeatedly");state(rejected,"roles");};
  new LoginFlow(r).run();check(r.manualRetries==1&&r.taps==1,"continue after manual login follows current screen");
  r=new RunnerService();state(r,"loginPassword");r.screens.put("loginAgreement",true);final RunnerService pending=r;
  r.retryHook=()->{check(pending.taps==1&&pending.waited>=30000,"unknown login result waits before pausing");state(pending,"roles");};
  new LoginFlow(r).run();check(r.manualRetries==1&&r.taps==1,"no repeated submit on unchanged password screen");
  r=new RunnerService();state(r,"settingsReturn");final RunnerService world=r;r.tapLabelHook=label->{check(label.equals("返回角色选择"),"return from current world to roles");state(world,"roles");};
  new LoginFlow(r).run();check(r.taps==1&&r.filled==0,"existing world can return to roles without auth");
  r=new RunnerService();state(r,"loginPassword");r.stopped=true;try{new LoginFlow(r).run();throw new AssertionError("stop");}catch(Exception expected){check(r.launched==0&&r.taps==0&&r.filled==0,"stop prevents launch/password/submit");}
  r=new RunnerService();r.manualMode=true;state(r,"loginPassword");final RunnerService manual=r;r.retryHook=()->{check(manual.taps==0&&manual.filled==0,"manual mode never fills or submits");state(manual,"roles");};new LoginFlow(r).run();check(r.manualRetries==1,"manual login pauses with retained flow and Continue resumes");
  r=new RunnerService();state(r,"loginQuickFailed");labels=path(r,false);new LoginFlow(r).run();check(labels.get(0).equals("快速登录失效：继续")&&r.filled==1,"quick failure branch retained");
  System.out.println("PASS: configured-account login paths, manual-login pause, duplicate-submit prevention and stop");
 }
}'''
with tempfile.TemporaryDirectory(prefix='blackstone-login-test-') as folder:
 p=Path(folder);sources=[]
 for name,body in stubs.items():
  file=p/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(body,encoding='utf8');sources.append(str(file))
 sources += [str(ROOT/'src/cn/kongj/blackstone'/name) for name in ('LoginFlow.java','GameFlow.java','RepairFlow.java','MovementInterrupted.java','FlowPolicy.java','RolePlan.java','SkillCooldown.java','FlowControl.java','MovementModel.java','TownFlow.java','TownInventory.java')]
 subprocess.run([str(JAVA/'javac.exe'),'-encoding','UTF-8','-d',str(p),*sources],check=True)
 subprocess.run([str(JAVA/'java.exe'),'-cp',str(p),'cn.kongj.blackstone.LoginTest'],check=True)
