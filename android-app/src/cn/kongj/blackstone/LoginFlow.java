package cn.kongj.blackstone;

import android.graphics.Bitmap;

/** Recorded login dialogs, then the existing character workflow. No password appears in logs. */
class LoginFlow {
    static final int UNKNOWN=0,ACCOUNT=1,QUICK_FAILED=2,SMS=3,PASSWORD=4,NOTICE=5,SERVER=6,ROLES=7,WORLD=8;
    final RunnerService r;final GameFlow retryFlow;
    boolean launched,submitted;
    LoginFlow(RunnerService r){this.r=r;retryFlow=new GameFlow(r);}
    static class LoginRejected extends Exception{LoginRejected(String message){super(message);}}
    int screen()throws Exception {
        Bitmap b=r.frame();try {
            if(r.vision.matches(b,"roles")&&r.vision.matches(b,"roleEnter"))return ROLES;
            if(r.vision.matches(b,"loginQuickFailed"))return QUICK_FAILED;
            if(r.vision.matches(b,"loginPassword"))return PASSWORD;
            if(r.vision.matches(b,"loginSms"))return SMS;
            if(r.vision.matches(b,"loginAccount"))return ACCOUNT;
            if(r.vision.matches(b,"loginNotice"))return NOTICE;
            if(r.vision.matches(b,"loginServer"))return SERVER;
            if(r.vision.matches(b,"hud")||r.vision.matches(b,"menuSettings")||r.vision.matches(b,"settingsReturn")||r.vision.matches(b,"returnConfirm"))return WORLD;
            return UNKNOWN;
        }finally{b.recycle();}
    }
    boolean rejected()throws Exception{return r.match("loginRejected");}
    void waitChange(int from,long timeout)throws Exception {
        for(long elapsed=0;elapsed<timeout;elapsed+=250) {
            r.checkpoint();
            if(from==PASSWORD&&rejected())throw new LoginRejected("游戏提示账号或密码错误，请修改APP密码后重新一键启动；也可手动完成登录后点继续");
            int next=screen();if(next!=from&&next!=UNKNOWN){r.retrySucceeded();return;}r.waitMs(250);
        }
        if(from==PASSWORD)throw new LoginRejected("登录结果未确认，不重复提交密码。请检查游戏提示，手动登录后继续，或修改密码重新一键启动");
        throw new Exception("登录步骤尚未进入下一画面，保留当前步骤重试");
    }
    void passwordLogin()throws Exception {
        if(!submitted) {
            r.fillConfiguredLogin();
            if(!r.waitMatch("loginPasswordReady",5000))throw new Exception("密码填写后进入游戏按钮尚未亮起，重试填写步骤");
            if(!r.match("loginAgreement"))r.tap("勾选登录协议同意",1004,993);
            if(!r.waitMatch("loginAgreement",5000))throw new Exception("登录协议尚未勾选，重试当前步骤");
            // Keep this flag if the input succeeded but the gesture callback was cancelled.
            submitted=true;
            try{r.tap("密码登录：进入游戏",1280,778);}catch(Exception e){r.checkpoint();r.log("登录点击结果待确认，先检查画面，不重复提交");}
        }
        waitChange(PASSWORD,30000);submitted=false;
    }
    void run()throws Exception {
        while(true) {
            r.checkpoint();
            try {
                if(!launched){r.launchGame();launched=true;}
                r.waitGameForeground();
                int state=screen();
                switch(state) {
                    case ROLES:r.log("登录完成，按勾选的角色执行");r.retrySucceeded();return;
                    case ACCOUNT:requireCredentials();r.tap("更换账号登录",1280,985);waitChange(ACCOUNT,20000);break;
                    case QUICK_FAILED:requireCredentials();r.tap("快速登录失效：继续",1130,812);waitChange(QUICK_FAILED,10000);break;
                    case SMS:requireCredentials();r.tap("切换到密码登录",1280,860);waitChange(SMS,10000);break;
                    case PASSWORD:requireCredentials();passwordLogin();break;
                    case NOTICE:r.tap("关闭登录公告",2300,720);waitChange(NOTICE,10000);break;
                    case SERVER:r.tap("登录所选服务器",1280,1107);waitChange(SERVER,60000);break;
                    case WORLD:retryFlow.returnToRoles();break;
                    default:
                        r.log("等待游戏启动、加载或登录页面");
                        long waited=0;while(screen()==UNKNOWN&&waited<60000){r.waitMs(500);waited+=500;}
                        if(screen()==UNKNOWN)throw new Exception("尚未识别登录画面，等待加载或处理新弹窗后继续");
                }
            }catch(LoginRejected e){r.awaitRetry(e.getMessage());submitted=false;}
            catch(Exception e){retryFlow.retry(e.getMessage());}
        }
    }
    void requireCredentials()throws LoginRejected {
        if(r.manualLogin())throw new LoginRejected("当前需要重新登录，尚未配置账号和密码；请手动登录后点继续，或到设置中补充账号");
    }
}
