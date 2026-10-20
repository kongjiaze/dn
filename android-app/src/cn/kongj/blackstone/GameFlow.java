package cn.kongj.blackstone;

import android.graphics.Bitmap;
import org.json.JSONObject;

/** Ordered game actions. A transition must be observed before the next action. */
class GameFlow {
    final RunnerService r;
    TownFlow townFlow;
    RepairFlow repairFlow;
    int currentRole;
    long combatDeadline;
    int phase,entranceFatigue,battlePhase,battleRound=1;
    boolean resumeBattle;
    int routePass,routePillar=1,routeStage;
    int[] navigationHint;
    int roleStage,stationarySkill,chestNudges,openingSkill,roleMoveRemaining=-1,roleChestMoveRemaining=-1,chestNudgeRemaining=-1;
    static final int SAGE_SKILL_GAP=200;
    long spawnWaitRemaining,pendingUltimateTapAt=-1;
    boolean roleChestMoved,preUltimateControlCast,roleChestConfirmed,battleCleared;
    void resetRoleRoute(){roleStage=0;roleMoveRemaining=-1;roleChestMoveRemaining=-1;chestNudgeRemaining=-1;stationarySkill=0;chestNudges=0;roleChestMoved=false;spawnWaitRemaining=0;openingSkill=0;preUltimateControlCast=false;roleChestConfirmed=false;battleCleared=false;pendingUltimateTapAt=-1;r.resetBattleClear();}
    RolePlan plan(){return r.rolePlan(currentRole);}
    int profession(){return plan().profession;}
    boolean personalRoute(){return plan().supported();}
    String roleName(){return "角色"+currentRole+" · "+plan().professionName();}
    int roleForward(){return plan().forward();}
    int roleChestForward(){return plan().chestForward();}
    boolean waitUltimate(boolean settlement)throws Exception {
        long waited=0,nextLog=0;int stable=0;
        while(true){
            r.checkpoint();
            if(settlement){if(!r.match("retry")||r.match("agree"))throw new Exception("等待大招时结算画面已变化，保留再次挑战步骤重查");}
            else if(rewardReady())return false;
            int state=r.ultimateState();
            if(state==SkillCooldown.UNKNOWN)throw new Exception("大招按钮无法可靠识别，保留当前步骤重新检查");
            stable=state==SkillCooldown.READY?stable+1:0;
            if(stable>=3){r.log(roleName()+"：已连续确认大招无冷却");r.retrySucceeded();return true;}
            if(waited>=nextLog){r.log(roleName()+"："+(state==SkillCooldown.COOLING?"大招仍在冷却，等待数字消失":"检查大招无冷却是否稳定"));nextLog=waited+10000;}
            // Cooldown is expected waiting, not a failed action/retry attempt.
            r.waitMs(350);waited+=350;
        }
    }
    boolean finishRoleCombat(int round)throws Exception {
        combatDeadline=0;r.watchBattleClear();
        if(rewardReady())return true;
        if(roleStage==0){
            r.log(roleName()+" · 战斗步骤1：直走到"+(plan().center()?"三柱中心":"施法位置"));
            if(roleMoveRemaining<0)roleMoveRemaining=roleForward();
            if(roleMoveRemaining>0){
                try{combatMove(roleName()+"：直走到施法点",300,928,roleMoveRemaining);roleMoveRemaining=0;}
                catch(MovementInterrupted e){
                    roleMoveRemaining=Math.max(0,roleMoveRemaining-e.movedMs);
                    r.log("进场移动中断，已记录 "+e.movedMs+" 毫秒；剩余 "+roleMoveRemaining+" 毫秒，继续时不重复整段路线");throw e;
                }
            }
            roleStage=1;r.retrySucceeded();r.snapshot("role-"+currentRole+"-round-"+round+"-cast-pose");
        }
        if(rewardReady())return true;
        if(roleStage==1){
            r.log(roleName()+" · 战斗步骤2：检查大招无冷却");
            // A cancelled gesture may have cast successfully. Confirm a pending
            // attempt before waiting, or Continue would wait out the new cooldown.
            if(pendingUltimateTapAt<0&&!waitUltimate(false))return true;
            if(rewardReady())return true;
            if(!r.battleReady())throw new Exception("等待战斗界面稳定后释放大招");
            if(plan().controlBeforeUltimate()&&!preUltimateControlCast){
                combatTap(roleName()+"：大招前先释放三道冰锥控制",2190,805);
                // Resume at the ultimate if its gesture fails after control succeeds.
                preUltimateControlCast=true;r.retrySucceeded();
                if(rewardReady())return true;
                if(!r.battleReady())throw new Exception("控制已释放，等待战斗界面稳定后继续大招");
            }
            if(confirmSkillCast(roleName()+(profession()==RolePlan.PRIEST?" · 天堂审判":"")+"：释放大招一次",1720,1290,true)<0)return true;
            pendingUltimateTapAt=-1;roleStage=2;preUltimateControlCast=false;
            spawnWaitRemaining=r.param("monsterSpawnWait",5000);r.retrySucceeded();
        }
        if(roleStage==2){
            if(profession()==RolePlan.SAGE){
                protectOpening();spawnWaitRemaining=0;
                if(rewardUiReady())return true;
                r.log(roleName()+" · 战斗步骤3：已尝试增益，继续三技能循环");
            }else{
                r.log(roleName()+" · 战斗步骤3：原地等待怪物出现");
                while(spawnWaitRemaining>0){
                    if(rewardReady())return true;
                    long chunk=Math.min(1000,spawnWaitRemaining);r.waitMs(chunk);spawnWaitRemaining-=chunk;
                }
            }
            roleStage=3;r.retrySucceeded();
        }
        // Continue an interrupted post-cast approach immediately; do not wait
        // through another clearing batch or replay the complete movement.
        if(roleChestForward()>0&&roleChestMoveRemaining>=0){
            if(probeRoleChestApproach())return true;
            if(r.ultimateState()==SkillCooldown.READY)roleStage=1;
            throw new Exception(roleName()+"开箱按钮未出现，保留靠近进度，下次直接补走，不再重复清场等待");
        }
        r.log(roleName()+" · 战斗步骤4："+(profession()==RolePlan.SAGE?"轮流尝试控制、减益、增益，等待宝箱":"原地等待大招清场和宝箱"));
        long waited=0,limit=r.param("roleClearWait",30000),clearStarted=combatClock();
        int[][] skills={{2010,890},{1900,1290},{1945,1090}};
        while(roleChestForward()>0?combatClock()-clearStarted<limit*1000000L:waited<limit){
            r.checkpoint();
            // Scan the chest object once per three-skill cycle; UI rewards and
            // the current battle frame are still checked before every input.
            if(profession()==RolePlan.SAGE&&stationarySkill!=0?rewardUiReady():rewardReady())return true;
            if(profession()==RolePlan.SAGE){
                if(!r.combatFrameReady())throw new Exception("清场画面不符，保留当前技能重查");
                int index=stationarySkill%3;int[] point=skills[index];
                combatTap(roleName()+"：原地"+new String[]{"控制","减益","增益"}[index],point[0],point[1],SAGE_SKILL_GAP);
                stationarySkill=(stationarySkill+1)%3;waited+=180+SAGE_SKILL_GAP;
            }else{
                if(!(roleChestForward()>0?r.combatFrameReady():r.battleReady()))throw new Exception("清场画面尚未稳定，保留当前阶段重查");
                r.waitMs(1000);waited+=1000;
            }
        }
        if(rewardReady())return true;
        if(roleChestForward()>0&&!roleChestMoved){
            // The chest body can be missed at the casting point. Once the
            // clearing batch has finished, approach once and check interaction
            // instead of waiting in place for another chest-body detection.
            r.log(roleName()+"：清场等待已结束，向前一小段再检查开箱按钮");
            if(probeRoleChestApproach())return true;
        }
        if(profession()!=RolePlan.SAGE&&r.ultimateState()==SkillCooldown.READY)roleStage=1;
        throw new Exception(roleName()+"尚未出现宝箱，保留施法位置重试清场，不重复进场移动");
    }
    void protectOpening()throws Exception {
        // Small skills are attempts: an absent target or an animation must not
        // hold this sequence waiting for cooldown. The ultimate still confirms.
        // Keep this cursor across input failures so Continue preserves completed actions.
        if(openingSkill==0){
            if(rewardUiReady())return;
            if(!r.combatFrameReady())throw new Exception("尝试减益：战斗画面不符，保留当前技能等待重查");
            combatTap(roleName()+"：防顶飞先释放减益",1900,1290,SAGE_SKILL_GAP);
            openingSkill=1;r.retrySucceeded();
        }
        if(openingSkill==1){
            if(rewardUiReady())return;
            if(!r.combatFrameReady())throw new Exception("尝试增益：战斗画面不符，保留当前技能等待重查");
            combatTap(roleName()+"：防顶飞再释放增益",1945,1090,SAGE_SKILL_GAP);
            r.log("减益和增益已依次尝试，不等待小技能冷却，继续快速三技能循环");
            openingSkill=2;r.retrySucceeded();
        }
    }
    long confirmSkillCast(String label,int x,int y,boolean ultimate)throws Exception {
        long lastTapAt=-1;
        while(true){
            r.checkpoint();
            if(rewardUiReady())return -1;
            if(!r.combatFrameReady())throw new Exception(label+"：战斗画面不符，保留当前技能等待重查");
            if(r.skillCooling(x,y)){
                // An old round's cooldown must not count as a new cast. Keep a pending
                // attempt across Continue, including a successful cast with a cancelled callback.
                if(ultimate&&pendingUltimateTapAt<0){if(!waitUltimate(false))return -1;continue;}
                r.log(label+"：已确认进入冷却");
                return ultimate?pendingUltimateTapAt:lastTapAt>=0?lastTapAt:combatClock();
            }
            lastTapAt=combatClock();if(ultimate)pendingUltimateTapAt=lastTapAt;
            combatTap(label,x,y,0);
            int stable=0;
            for(int n=0;n<16;n++){
                // Capture refreshes every 150ms. Observe the result while allowing the
                // preceding animation to finish; dispatch completion is not a cast.
                // Do not re-tap every second: a delayed cast may still be winding up.
                r.waitMs(150);
                if(rewardUiReady())return -1;
                stable=r.skillCooling(x,y)?stable+1:0;
                if(stable>=2){r.log(label+"：已确认进入冷却");return lastTapAt;}
            }
            r.snapshot(ultimate?"ultimate-no-cooldown":"protection-no-cooldown-"+(x==1900?"debuff":"buff"));
            r.autoRetry(label+"未见冷却，等待施法动作后重试当前技能",400);
        }
    }
    void moveRoleChestForward()throws Exception {
        if(roleChestMoved)return;
        if(roleChestMoveRemaining<0)roleChestMoveRemaining=roleChestForward();
        if(roleChestMoveRemaining>0){
            try{chestMove(roleName()+" · 开箱步骤1：向前靠近宝箱",300,928,roleChestMoveRemaining);roleChestMoveRemaining=0;}
            catch(MovementInterrupted e){
                roleChestMoveRemaining=Math.max(0,roleChestMoveRemaining-e.movedMs);
                r.log("开箱前移动中断，已记录 "+e.movedMs+" 毫秒；剩余 "+roleChestMoveRemaining+" 毫秒，继续只补剩余距离");throw e;
            }
        }
        roleChestMoved=true;
    }
    boolean chestButtonReady()throws Exception {
        if(r.match("retry"))return true;
        if(r.waitMatch("chest",1000)){roleChestConfirmed=true;return true;}
        return rewardReady();
    }
    void nudgeRoleChest()throws Exception {
        if(chestNudgeRemaining<0)chestNudgeRemaining=r.param("chestNudge",200);
        if(chestNudgeRemaining>0){
            try{chestMove(roleName()+"：再向前靠近宝箱 "+(chestNudges+1),300,928,chestNudgeRemaining);chestNudgeRemaining=0;}
            catch(MovementInterrupted e){
                chestNudgeRemaining=Math.max(0,chestNudgeRemaining-e.movedMs);
                r.log("开箱补走中断，剩余 "+chestNudgeRemaining+" 毫秒，继续仅补本次剩余距离");throw e;
            }
        }
        chestNudges++;chestNudgeRemaining=-1;
    }
    boolean probeRoleChestApproach()throws Exception {
        moveRoleChestForward();if(chestButtonReady())return true;
        if(chestNudges<r.param("chestNudgeLimit",20)){
            nudgeRoleChest();if(chestButtonReady())return true;
        }
        return false;
    }
    void collectRoleChest()throws Exception {
        combatDeadline=0;
        while(true){
            r.checkpoint();if(r.match("retry"))return;
            if(!rewardReady())throw new Exception("尚未确认宝箱，保留开箱步骤重查");
            if(!r.match("chest")&&!roleChestMoved){
                moveRoleChestForward();
            }
            if(r.match("retry"))return;
            if(!r.waitMatch("chest",2000)){
                if(!plan().center()&&chestNudges<r.param("chestNudgeLimit",20)){
                    nudgeRoleChest();
                    if(r.waitMatch("chest",2000))continue;
                }
                retry(roleName()+"黄色开箱按钮未出现，等待重查当前开箱步骤");continue;
            }
            battlePhase=2;saveProgress();r.tap("开启中央宝箱",1540,1085);
            if(waitForPickup())return;
            battlePhase=1;saveProgress();retry("开箱按钮仍存在，原地重试开箱");
        }
    }
    MovementModel movement=new MovementModel();int movementRole=-1;
    MovementModel.Plan pendingMove;int[] pendingStart;
    void resetMovementObservation(){pendingMove=null;pendingStart=null;}
    void ensureMovementRole(){if(movementRole!=currentRole){movement=new MovementModel();movementRole=currentRole;resetMovementObservation();}}
    void resetCombatRoute(){resetRoleRoute();routePass=0;routePillar=1;routeStage=0;navigationHint=null;resetMovementObservation();}
    static final int SELECT=0,RECOVER=1,ENTER=2,BATTLE=3,RETURN=4,TOWN=5,REPAIR=6;
    GameFlow(RunnerService runner){r=runner;}

    void bindGame()throws Exception {
        TouchService touch=TouchService.instance;
        if(touch==null)throw new Exception("无障碍服务未开启");
        r.gamePackage=touch.activePackage();
        if(!"com.dn.lzgsj3".equals(r.gamePackage))throw new Exception("龙之谷世界未处于前台");
    }
    void runFromDungeon()throws Exception {
        runResume();
    }
    void saveProgress() {
        r.getSharedPreferences("progress",0).edit().putInt("activeRole",currentRole)
            .putInt("resumeValid",1).putInt("phase",phase).putInt("entranceFatigue",entranceFatigue)
            .putInt("rangeStart",r.param("startRole",2)).putInt("rangeEnd",r.param("endRole",5))
            .putInt("battlePhase",battlePhase).putInt("battleRound",battleRound).putBoolean("battleCleared",battleCleared).apply();
    }
    void runResume()throws Exception {
        while(true)try {
            bindGame();
            android.content.SharedPreferences p=r.getSharedPreferences("progress",0);
            int start=r.param("startRole",2),end=r.param("endRole",5);FlowPolicy.validateRange(start,end);
            currentRole=p.getInt("activeRole",start);
            boolean compatible=currentRole>=start&&currentRole<=end&&r.rolePlan(currentRole).enabled
                &&p.getInt("rangeStart",start)==start&&p.getInt("rangeEnd",end)==end;
            if(!compatible) {
                if(!r.match("roles"))throw new Exception("角色范围已改为第"+start+"至第"+end+"个，请回到角色选择页再点继续，防止操作旧角色");
                currentRole=start;phase=SELECT;battlePhase=0;battleRound=1;entranceFatigue=0;
                r.log("角色范围已改变，忽略旧进度，从第"+start+"个角色开始");
            }else if(p.getInt("resumeValid",0)==1) {
                phase=p.getInt("phase",SELECT);entranceFatigue=p.getInt("entranceFatigue",0);
                battlePhase=p.getInt("battlePhase",0);battleRound=p.getInt("battleRound",1);
                if(phase==BATTLE&&personalRoute()&&p.getBoolean("battleCleared",false)){battleCleared=true;roleChestConfirmed=true;}
            }else if(r.match("roles"))phase=SELECT;
            else if(r.match("entry")){phase=BATTLE;battlePhase=r.match("retry")?3:0;}
            else if(r.match("agree")){phase=BATTLE;battlePhase=5;}
            else if(r.match("retry")){phase=BATTLE;battlePhase=3;}
            else if(r.match("settingsReturn")||r.match("returnConfirm"))phase=RETURN;
            else if(r.match("hud")||r.match("recovery"))phase=RECOVER;
            else if(r.match("menu")||r.match("dungeons")){phase=ENTER;entranceFatigue=readFatigue();}
            else throw new Exception("无法定位继续步骤，请回到已记录的游戏画面后点继续");
            resumeBattle=true;saveProgress();break;
        }catch(Exception e){r.awaitRetry(e.getMessage());}
        runPhases();
    }
    void run()throws Exception {
        while(true)try {
            bindGame();if(!r.match("roles"))throw new Exception("开始需要角色选择页，请返回选角页后点继续");
            r.retrySucceeded();break;
        }catch(Exception e){retry(e.getMessage());}
        currentRole=r.param("startRole",2);FlowPolicy.validateRange(currentRole,r.param("endRole",5));
        phase=SELECT;battlePhase=0;battleRound=1;entranceFatigue=0;resumeBattle=false;
        r.log("按已保存范围开始：第"+currentRole+"至第"+r.param("endRole",5)+"个角色");
        saveProgress();runPhases();
    }
    void runPhases()throws Exception {
        while(currentRole<=r.param("endRole",5)) {
            try {
                bindGame();FlowPolicy.validateRange(currentRole,r.param("endRole",5));saveProgress();
                r.log("角色 "+currentRole+" · "+new String[]{"选角进入","领取体力","进入黑石","战斗及结算","返回角色","回村庄、合成及存仓","修理装备"}[phase]);
                switch(phase) {
                    case SELECT:repairFlow=null;r.getSharedPreferences("progress",0).edit().putInt("repairRole",currentRole).putInt("repairStage",0).putInt("townTravelRole",currentRole).putInt("townTravelStage",0).putBoolean("townTravelMapChecked",false).apply();selectAndEnter();phase=RECOVER;break;
                    case RECOVER:
                        if(plan().recover)recoverFatigue();
                        else if(r.match("recovery"))closeRecovery(-1);
                        entranceFatigue=readFatigue();
                        if(!FlowPolicy.canEnter(entranceFatigue))r.log("角色"+currentRole+"体力不足100，跳过副本、合成及存仓");
                        phase=FlowPolicy.canEnter(entranceFatigue)?REPAIR:RETURN;break;
                    case REPAIR:repairEquipment();entranceFatigue=readFatigue();phase=FlowPolicy.canEnter(entranceFatigue)?ENTER:RETURN;break;
                    case ENTER:enterBlackstone(entranceFatigue);battlePhase=0;battleRound=1;resumeBattle=false;resetCombatRoute();phase=BATTLE;break;
                    case BATTLE:brushUntilEmpty(resumeBattle);phase=TOWN;break;
                    case TOWN:if(townFlow==null)townFlow=new TownFlow(this);townFlow.run();phase=RETURN;break;
                    case RETURN:returnToRoles();r.getSharedPreferences("progress",0).edit().putInt("lastCompletedRole",currentRole).apply();currentRole=r.nextRole(currentRole);phase=SELECT;battlePhase=0;battleRound=1;townFlow=null;repairFlow=null;break;
                    default:throw new Exception("继续步骤记录无效，请在选角页重新开始");
                }
                r.retrySucceeded();saveProgress();
            }catch(Exception e) {
                if(phase==BATTLE&&battlePhase==0)resumeBattle=true;
                retry(e.getMessage());
            }
        }
        r.getSharedPreferences("progress",0).edit().putInt("resumeValid",0).apply();
        r.log("所选角色全部完成");
    }
    void require(String state,long timeout,String message)throws Exception {
        if(!r.waitMatch(state,timeout))throw new Exception(message);
    }
    void repairEquipment()throws Exception {if(repairFlow==null)repairFlow=new RepairFlow(this);repairFlow.run();}
    void retry(String message)throws Exception {
        try{r.autoRetry(message);}catch(Exception e){r.awaitRetry(e.getMessage());}
    }
    void selectAndEnter()throws Exception {
        if(r.match("hud"))return;
        if(!r.match("roles")){require("hud",60000,"等待进入主城，超时后可点继续重查");return;}
        r.getSharedPreferences("progress",0).edit().putInt("activeRole",currentRole).apply();
        require("roles",10000,"未回到角色选择页");
        while(true) {
            r.tap("选择第"+currentRole+"个角色",340,FlowPolicy.roleY(currentRole));
            long elapsed=0;boolean selected=false;
            while(elapsed<10000) {
                Bitmap frame=r.frame();try{selected=r.vision.selectedRole(frame,currentRole);}finally{frame.recycle();}
                if(selected&&r.match("roleEnter"))break;r.waitMs(300);elapsed+=300;
            }
            if(selected&&r.match("roleEnter"))break;
            retry("角色高亮尚未出现，等待后重新选择第"+currentRole+"个角色");
            if(r.match("hud"))return;
            if(!r.match("roles"))throw new Exception("选角画面发生变化，等待主城或恢复选角画面");
        }
        r.tap("进入游戏",1280,1320);
        require("hud",60000,"进入游戏超时或出现未记录弹窗");r.waitMs(1500);
    }
    int readFatigue()throws Exception {
        int last=-1,agree=0;
        for(int i=0;i<15;i++) {
            Bitmap frame=r.frame();int value;try{value=r.vision.fatigue(frame);}finally{frame.recycle();}
            if(value>=0&&value==last)agree++;else agree=0;
            last=value;if(value>=0&&agree>=2)return value;r.waitMs(200);
        }
        throw new Exception("疲劳值无法可靠识别");
    }
    int[] readRecoveryState()throws Exception {
        int lastRemaining=-1,lastFatigue=-2,agree=0;
        for(int i=0;i<15;i++){
            r.checkpoint();if(!r.match("recovery"))throw new Exception("体力恢复窗口已变化，等待重查");
            Bitmap frame=r.frame();int remaining,fatigue;
            try{remaining=r.vision.exchangeRemaining(frame);fatigue=r.vision.recoveryFatigue(frame);}finally{frame.recycle();}
            boolean valid=remaining>=0&&(fatigue>=0||remaining==0);
            agree=valid&&remaining==lastRemaining&&fatigue==lastFatigue?agree+1:0;
            lastRemaining=remaining;lastFatigue=fatigue;
            if(valid&&agree>=2)return new int[]{remaining,fatigue};r.waitMs(200);
        }
        throw new Exception("恢复窗口兑换次数或疲劳值尚未稳定");
    }
    void recoverFatigue()throws Exception {
        if(!r.match("recovery"))transition("hud","recovery","打开体力恢复",550,166,5000,"体力恢复窗口未出现");
        r.tap("选择货币恢复",1650,650);r.waitMs(400);
        int[] initial=readRecoveryState();int before=initial[1];
        for(int i=0;i<3;i++) {
            int[] observed=readRecoveryState();int remaining=observed[0],modal=observed[1];
            if(remaining==0){r.log("今日体力已兑换完毕");break;}
            if(before<0||modal<0||modal!=before)throw new Exception("恢复窗口疲劳值校验失败");
            require("recoveryConfirm",4000,"恢复确认按钮未出现或尚未稳定");
            // Recheck after waiting, so a stale frame cannot spend an extra exchange.
            Bitmap frame=r.frame();int currentRemaining,currentFatigue;
            try{currentRemaining=r.vision.exchangeRemaining(frame);currentFatigue=r.vision.recoveryFatigue(frame);}finally{frame.recycle();}
            if(!r.match("recovery")||currentRemaining!=remaining||currentFatigue!=before)
                throw new Exception("等待确认期间恢复窗口发生变化，停止");
            r.tap("确认恢复体力（剩余"+remaining+"次）",1280,1210);
            boolean succeeded=false;
            for(int n=0;n<25;n++) {
                r.waitMs(200);frame=r.frame();int after,left;
                try{after=r.vision.recoveryFatigue(frame);left=r.vision.exchangeRemaining(frame);}finally{frame.recycle();}
                if(after==before+100&&left==remaining-1){before=after;succeeded=true;break;}
            }
            if(!succeeded)throw new Exception("体力兑换未成功，可能金币不足或出现新提示");
        }
        closeRecovery(before);
    }
    void closeRecovery(int expected)throws Exception {
        while(true)try{
            // The HUD can be visible behind the dialog, so require the modal to disappear.
            if(r.match("recovery"))r.tap("关闭体力恢复窗口",2040,227);
            for(int i=0;i<25&&r.match("recovery");i++)r.waitMs(200);
            if(r.match("recovery"))throw new Exception("体力恢复窗口未关闭");
            require("hud",5000,"关闭体力窗口后主界面尚未出现");
            int actual=readFatigue();
            if(expected>=0&&actual!=expected)throw new Exception("关闭恢复窗口后的体力不符");
            return;
        }catch(Exception e){retry(e.getMessage());}
    }
    void openMenu()throws Exception {
        if(r.match("menu")||r.match("menuSettings"))return;
        transition("hud","menu","打开菜单",2470,460,5000,"游戏菜单未出现");
    }
    void transition(String from,String target,String label,int x,int y,long timeout,String message)throws Exception {
        while(true)try {
            if(r.match(target))return;
            if(!r.match(from))throw new Exception(label+"：当前画面不符，请修正后点继续");
            r.tap(label,x,y);require(target,timeout,message);return;
        }catch(Exception e){retry(e.getMessage());}
    }
    void enterBlackstone(int before)throws Exception {
        if(r.match("entry")){FlowPolicy.verifyConsumption(before,readFatigue());return;}
        if(!r.match("dungeons")) {
            openMenu();transition("menu","dungeons","打开副本列表",1835,1075,5000,"副本列表未出现");
        }
        int[] target=findBlackstone();
        for(int scroll=0;target==null&&scroll<3;scroll++) {
            r.gesture("日常列表向下滚动",1380,1200,1380,550,600);r.waitMs(700);
            for(int check=0;check<4&&target==null;check++){target=findBlackstone();if(target==null)r.waitMs(200);}
        }
        if(target==null)throw new Exception("未定位到黑石秘境（高级）及其前往按钮");
        // Confirm the list has stopped moving before committing the tap.
        r.waitMs(250);int[] stable=findBlackstone();
        if(stable==null||Math.abs(stable[0]-target[0])>3||Math.abs(stable[1]-target[1])>3)
            throw new Exception("黑石列表仍在移动，停止点击");
        r.log("已定位黑石秘境前往按钮："+stable[0]+","+stable[1]);
        r.tap("进入黑石秘境（高级）",stable[0],stable[1]);waitEntrance();
        FlowPolicy.verifyConsumption(before,readFatigue());
    }
    int[] findBlackstone()throws Exception {
        r.checkpoint();Bitmap frame=r.frame();
        try {return r.vision.matches(frame,"dungeons")?r.vision.locateBlackstone(frame):null;}
        finally{frame.recycle();}
    }
    void waitEntrance()throws Exception {
        require("entry",45000,"黑石秘境进入超时");require("zero",10000,"入口积分未归零");
        r.waitMs(1800);if(r.match("retry")||r.match("agree"))throw new Exception("仍停留在结算或确认画面");
    }
    void combatTap(String label,int x,int y)throws Exception {
        combatTap(label,x,y,-1);
    }
    void combatTap(String label,int x,int y,int gap)throws Exception {
        for(int attempt=0;attempt<3;attempt++) {
            if(rewardUiReady())return;
            try{if(gap<0)r.combatTap(label,x,y);else r.combatTap(label,x,y,gap);return;}
            catch(Exception error) {
                r.checkpoint();
                if(attempt==2||!"手势取消或无障碍服务不可用".equals(error.getMessage())||TouchService.instance==null)
                    throw error;
                if(!r.battleReady())throw error;
                r.log("战斗点击取消，等待后重试 "+(attempt+1));r.waitMs(350);
            }
        }
    }
    void fight(int clicks)throws Exception {
        int ticks=Math.max(FlowPolicy.SKILLS.length,clicks);
        for(int j=0;j<ticks;j++) {
            checkCombatBudget();
            if(rewardReady())return;
            if(!r.battleReady())throw new Exception("未稳定识别黑石战斗界面，等待重查后继续技能");
            int[] skill=FlowPolicy.SKILLS[j%FlowPolicy.SKILLS.length];
            combatTap("使用全部技能 "+(j%FlowPolicy.SKILLS.length+1),skill[0],skill[1]);r.waitMs(550);
            if(j%2==1){combatTap("普攻清理怪物",2210,1100);r.waitMs(300);}
        }
    }
    void checkCombatBudget()throws Exception {
        r.checkpoint();
        if(combatDeadline!=0&&System.nanoTime()>=combatDeadline)
            throw new Exception("本次巡回达到时间上限，等待后继续下一次巡回");
    }
    void combatMove(String label,int x,int y,int ms)throws Exception {
        if(rewardReady())return;
        checkCombatBudget();
        if(!r.battleReady())throw new Exception("未稳定识别黑石战斗界面，等待重查后继续移动");
        r.move(label,x,y,ms);checkCombatBudget();
    }
    boolean sweepArena(int pass,int round)throws Exception {
        int distance=r.param("sweepStep",1200),clicks=r.param("sweepClicks",10);
        // Approximate coverage around the calibrated centre; not measured pillar coordinates.
        int side=pass%2==0?-1:1;
        // Vary coverage between passes instead of replaying the same misses.
        distance=distance*(100+(pass%3)*25)/100;
        int[][] legs={{side,0,1},{0,-1,1},{-side,0,2},{0,1,2},{side,0,2},{0,-1,1},{-side,0,1}};
        for(int i=0;i<legs.length;i++) {
            if(rewardReady())return true;
            combatMove("扫场 "+(pass+1)+"：移动到第"+(i+1)+"个区域",300+legs[i][0]*220,1148+legs[i][1]*220,distance*legs[i][2]);
            fight(clicks);
        }
        r.snapshot("round-"+round+"-sweep-"+(pass+1));
        return rewardReady();
    }
    boolean rewardUiReady()throws Exception {
        r.checkpoint();
        if(!battleCleared&&r.battleClearSeen()){
            battleCleared=true;roleChestConfirmed=true;saveProgress();
            r.log("已识别清场成功文字：停止释放技能，转入开箱；本轮不再恢复战斗");
        }
        if(battleCleared)return true;
        if(r.match("chest")||r.match("retry")){if(personalRoute())roleChestConfirmed=true;return true;}
        // A spawned chest persists; approaching it may hide its body behind the character.
        if(personalRoute()&&roleChestConfirmed)return true;
        return false;
    }
    boolean rewardReady()throws Exception {
        if(rewardUiReady())return true;
        Bitmap frame=r.frame();boolean visible;try{visible=r.vision.worldChestVisible(frame);}finally{frame.recycle();}
        if(!visible)return false;
        r.waitMs(200);frame=r.frame();try{visible=r.vision.worldChestVisible(frame);}finally{frame.recycle();}
        if(visible&&personalRoute())roleChestConfirmed=true;return visible;
    }
    void chestMove(String label,int x,int y,int ms)throws Exception {
        r.checkpoint();if(r.match("retry")||r.match("chest"))return;
        if(!r.battleReady())throw new Exception("等待开箱画面稳定后继续靠近箱子");
        r.move(label,x,y,ms);
    }
    void approachChest(int attempt)throws Exception {
        int[][] offsets={{0,0},{6,0},{-6,0},{0,-6},{0,6},{6,-6},{-6,-6},{6,6},{-6,6}};
        int[] offset=offsets[attempt%offsets.length];
        navigateToReference("箱子",210+offset[0],236+offset[1],true);
    }
    int[] stableArenaPosition()throws Exception {
        java.util.ArrayList<int[]> samples=new java.util.ArrayList<>();
        for(int i=0;i<32;i++) {
            r.checkpoint();
            if(r.match("chest")||r.match("retry"))return null;
            Bitmap frame=r.frame();try{samples.add(r.vision.arenaSample(frame));}finally{frame.recycle();}
            if((i+1)%4==0)r.log("原地采集小地图 "+(i+1)+" 帧，检查动态背景与地图边界");
            if(i==15||i==31) {
                int[] position=r.vision.temporalArenaPosition(samples,navigationHint);
                if(position!=null){navigationHint=position.clone();return position;}
                if(i==15)r.log("16帧定位尚不稳定，保持原地扩展到32帧");
            }
            if(i<31)r.waitMs(500);
        }
        return null; // Preserve last accepted pose/pulse hint across stationary retries.
    }
    void navigateToReference(String label,int targetX,int targetY,boolean chest)throws Exception {
        ensureMovementRole();
        int tolerance=r.param("positionTolerance",8),cap=r.param("navPulse",2500);
        int previousDistance=Integer.MAX_VALUE,stalled=0;
        for(int attempt=0;attempt<40;attempt++) {
            r.checkpoint();
            if(chest?(r.match("chest")||r.match("retry")):rewardReady()){resetMovementObservation();return;}
            if(!chest)checkCombatBudget();
            int[] position=stableArenaPosition();
            if(chest?(r.match("chest")||r.match("retry")):rewardReady()){resetMovementObservation();return;}
            if(position==null)throw new Exception(label+"导航：多帧小地图定位不可靠，原地重新采集");
            if(pendingMove!=null) {
                int ax=position[0]-pendingStart[0],ay=position[1]-pendingStart[1];
                boolean learned=movement.observe(pendingStart,position,pendingMove);
                r.log("实际移动 "+ax+","+ay+" / "+pendingMove.ms+"毫秒；"+(learned?"已更新本角色移动标定，第"+movement.observations+"次":"位移证据不足，保留已有标定"));
                resetMovementObservation();
            }
            int dx=targetX-position[0],dy=targetY-position[1];
            int distance=Math.max(Math.abs(dx),Math.abs(dy));
            r.log(label+"靠近第"+(attempt+1)+"次：位置 "+position[0]+","+position[1]+"，偏差 "+dx+","+dy+"，阈值 "+tolerance);
            if(distance<=tolerance){r.log(label+"已进入位置阈值，结束移动");return;}
            if(attempt>0){stalled=distance>=previousDistance-1?stalled+1:0;if(stalled>=4)throw new Exception(label+"导航：连续移动未靠近目标，等待重试当前目标");}
            previousDistance=distance;
            MovementModel.Plan plan=movement.plan(Math.abs(dx)<=tolerance?0:dx,Math.abs(dy)<=tolerance?0:dy,cap);
            r.log("按完整偏差计算方向和时间："+plan.ms+"毫秒，走完再确认；已学习"+movement.observations+"段实际移动");
            r.snapshot("navigate-"+label+"-"+attempt);
            resetMovementObservation();
            if(chest)chestMove("靠近"+label+"：按计算方向移动",plan.x,plan.y,plan.ms);
            else combatMove("靠近"+label+"：按计算方向移动",plan.x,plan.y,plan.ms);
            pendingStart=position.clone();pendingMove=plan;
            navigationHint=new int[]{position[0]+(int)Math.round(plan.predictedX),position[1]+(int)Math.round(plan.predictedY)};
        }
        throw new Exception(label+"导航：多次修正仍未到位，等待重试当前目标");
    }
    void collectChest()throws Exception {
        if(personalRoute()){collectRoleChest();return;}
        int attempt=0;combatDeadline=0;
        while(true) {
            r.checkpoint();if(r.match("retry"))return;
            approachChest(attempt++);
            if(r.match("retry"))return;
            if(!r.waitMatch("chest",1500)) {
                retry("尚未到开箱范围，重新计算位置");continue;
            }
            battlePhase=2;saveProgress();
            r.tap("开启中央宝箱",1540,1085);
            if(waitForPickup())return;
            battlePhase=1;saveProgress();
            retry("开箱按钮持续存在，重新检查并重试开箱");
        }
    }
    boolean waitForPickup()throws Exception {
        long elapsed=0;int buttonStable=0;
        while(true) {
            r.checkpoint();if(r.match("retry"))return true;
            // A disappeared button may mean the chest opened and loot is still being collected.
            // Stay in place, even beyond the old 20-second timeout.
            if(elapsed>=5000)buttonStable=r.match("chest")?buttonStable+1:0;
            if(buttonStable>=3)return false;
            if(elapsed%10000==0)r.log("开箱后原地等待拾取完成，顶部结算栏尚未出现");
            r.waitMs(1000);elapsed+=1000;
        }
    }
    long combatClock(){return System.nanoTime();}
    void timedCombat(boolean skills,int pillar)throws Exception {
        resetMovementObservation();navigationHint=null; // Skills and monster knockback invalidate movement-only prediction.
        long end=combatClock()+10000000000L;int tick=0;
        while(combatClock()<end) {
            checkCombatBudget();if(rewardReady())return;
            if(!r.battleReady())throw new Exception("等待战斗画面稳定后继续当前巡回");
            if(skills) {
                int[] skill=FlowPolicy.SKILLS[tick++%FlowPolicy.SKILLS.length];
                combatTap("柱子"+pillar+"：释放技能10秒 · 技能"+tick,skill[0],skill[1]);
            }else combatTap("柱子"+pillar+"：普攻10秒，等待技能冷却",2210,1100);
        }
    }
    int[] readArenaPosition()throws Exception {
        r.checkpoint();Bitmap frame=r.frame();try{return navigationHint==null?r.vision.arenaPosition(frame):r.vision.arenaPosition(frame,navigationHint[0],navigationHint[1]);}finally{frame.recycle();}
    }
    void approachPillar(int pillar,int pass)throws Exception {
        int[][] targets={{194,240},{208,226},{220,240}};
        navigateToReference("柱子"+pillar,targets[pillar-1][0],targets[pillar-1][1],false);
    }
    boolean searchNearCentre(int pass)throws Exception {
        // Precomputed local route; check after each move, without precise steering.
        int distance=350+(pass%3)*200;
        int side=pass%2==0?1:-1;
        int[][] legs={{side,0,1},{0,-1,1},{-side,0,2},{0,1,2},{side,0,2},{0,-1,1},{-side,0,1}};
        if(r.waitMatch("chest",1000)||rewardReady())return true;
        for(int i=0;i<legs.length;i++) {
            if(rewardReady())return true;
            combatMove("中央寻找箱子：第"+(i+1)+"个位置",300+legs[i][0]*220,1148+legs[i][1]*220,distance*legs[i][2]);
            if(r.waitMatch("chest",1000)||rewardReady())return true;
        }
        return false;
    }
    void returnToCentre()throws Exception {
        r.log("校准下方和左侧边缘，再走回中央箱子附近");
        combatMove("向下靠边第一段",300,1350,r.param("down",8000));
        combatMove("向下靠边第二段",300,1350,r.param("down2",5000));
        combatMove("向左靠边",80,1148,r.param("left",8000));
        combatMove("向右第一段",520,1148,r.param("right1",3000));
        combatMove("向上第一段",300,928,r.param("up1",2000));
        combatMove("向右第二段",520,1148,r.param("right2",1000));
        combatMove("向上靠近箱子",300,928,r.param("up2",500));
    }
    boolean finishCombatAndApproachChest(boolean rescue,int round)throws Exception {
        combatDeadline=System.nanoTime()+r.param("sweepSeconds",1800)*1000000000L;
        if(rewardReady())return true;
        r.snapshot("round-"+round+(rescue?"-resume-position":"-fight-start"));
        if(rewardReady())return true;
        int passes=r.param("sweepPasses",20);
        while(routePass<passes) {
            r.log("柱子巡回第"+(routePass+1)+"轮 / "+passes+"，当前柱子"+routePillar+"，阶段"+routeStage);
            while(routePillar<=3) {
                if(rewardReady())return true;
                if(routeStage==0){approachPillar(routePillar,routePass);routeStage=1;r.retrySucceeded();}
                if(routeStage==1){timedCombat(true,routePillar);routeStage=2;r.retrySucceeded();}
                if(routeStage==2){timedCombat(false,routePillar);routePillar++;routeStage=0;r.retrySucceeded();}
            }
            if(rewardReady())return true;
            r.snapshot("round-"+round+"-pillars-"+routePass);r.waitMs(1500);
            if(rewardReady())return true;
            r.log("未出现箱子按钮，继续下一轮三个柱子巡回");
            routePass++;routePillar=1;routeStage=0;
        }
        resetCombatRoute();
        return false;
    }
    void brushUntilEmpty(boolean resumeFirstRound)throws Exception {
        int limit=r.param("limit",12);
        while(true) {
            saveProgress();r.log("角色 "+currentRole+" 第 "+battleRound+" 轮，步骤 "+battlePhase);
            int previousBattlePhase=battlePhase;
            switch(battlePhase) {
                case 0:
                    if(r.match("retry")){battlePhase=3;break;}
                    if(personalRoute()){
                        // Completion effects can briefly obscure the ordinary HUD guard.
                        // Arm the independent text watcher before waiting for that guard.
                        r.watchBattleClear();if(rewardUiReady()){battlePhase=1;break;}
                    }
                    if(!r.battleReady()||(!resumeFirstRound&&!r.match("zero")))throw new Exception("黑石战斗界面或首次入场积分尚未稳定，自动重查");
                    if(!(personalRoute()?finishRoleCombat(battleRound):finishCombatAndApproachChest(resumeFirstRound,battleRound)))throw new Exception("本次巡回结束仍无箱子按钮，等待后自动继续巡回；也可点继续立即重试");
                    battlePhase=1;break;
                case 1:
                    collectChest();battlePhase=3;break;
                case 2:
                    battlePhase=waitForPickup()?3:1;
                    break;
                case 3:
                    require("retry",5000,"未见再次挑战按钮");r.waitMs(2000);
                    entranceFatigue=readFatigue();r.log("本轮结束，剩余疲劳 "+entranceFatigue);
                    if(!FlowPolicy.canEnter(entranceFatigue)){r.log("疲劳不足100，返回村庄合成纹章并存仓");return;}
                    if(battleRound>=r.param("limit",limit))throw new Exception("达到单角色轮数上限但仍有疲劳，可调整参数后继续");
                    battlePhase=4;break;
                case 4:
                    if(personalRoute()&&!r.match("agree")){r.log(roleName()+"：再次挑战前检查大招无冷却");waitUltimate(true);}
                    if(r.match("agree")){battlePhase=5;break;}
                    if(!r.match("retry"))throw new Exception("再次挑战按钮已消失，请恢复结算画面后继续");
                    battlePhase=5;saveProgress();r.fastTap("再次挑战",1650,310);require("agree",2500,"再次挑战确认弹窗未出现");break;
                case 5:
                    if(r.match("entry")&&!r.match("retry")&&!r.match("agree")){battlePhase=6;break;}
                    if(!r.match("agree")){battlePhase=4;break;}
                    battlePhase=6;saveProgress();r.fastTap("倒计时弹窗：同意",1560,1005);r.waitMs(1200);break;
                case 6:
                    if(r.match("agree")){battlePhase=5;break;}
                    if(r.match("retry")){battlePhase=4;break;}
                    waitEntrance();FlowPolicy.verifyConsumption(entranceFatigue,readFatigue());battleRound++;battlePhase=0;resumeFirstRound=false;resetCombatRoute();break;
                default:throw new Exception("副本继续步骤无效");
            }
            if(battlePhase!=previousBattlePhase)r.retrySucceeded();
        }
    }
    void returnToRoles()throws Exception {
        if(r.match("roles")&&r.match("roleEnter"))return;
        if(!r.match("settingsReturn")&&!r.match("returnConfirm")) {
            openMenu();require("menuSettings",3000,"设置入口未出现");
            transition("menuSettings","settingsReturn","打开设置",325,1150,5000,"设置中的返回角色按钮未出现");
        }
        JSONObject action=r.vision.action("returnRole");
        if(!r.match("returnConfirm"))r.tap("返回角色选择",action.getInt("x"),action.getInt("y"));
        for(int i=0;i<75;i++) {
            r.waitMs(200);
            if(r.match("roles")&&r.match("roleEnter"))return;
            if(r.vision.has("returnConfirm")&&r.match("returnConfirm")) {
                JSONObject confirm=r.vision.action("returnConfirm");
                r.tap("确认返回角色",confirm.getInt("x"),confirm.getInt("y"));
                require("roles",20000,"返回角色超时");require("roleEnter",5000,"角色进入按钮未出现");return;
            }
        }
        throw new Exception("返回角色后未到选择页");
    }
}
