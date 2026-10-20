package cn.kongj.blackstone;

import android.graphics.Bitmap;

/** One role's post-dungeon work. Keep stage and batch cursor when Continue retries. */
class TownFlow {
    final GameFlow flow;final RunnerService r;
    int stage,batch,cycle,cycleStep,travelStage;
    boolean batchStarted,travelMapChecked,resetMaterials;
    TownFlow(GameFlow flow){
        this.flow=flow;r=flow.r;
        android.content.SharedPreferences p=r.getSharedPreferences("progress",0);
        if(p.getInt("phase",-1)==GameFlow.TOWN&&p.getInt("townTravelRole",-1)==flow.currentRole)
            {travelStage=Math.max(0,Math.min(4,p.getInt("townTravelStage",0)));travelMapChecked=p.getBoolean("townTravelMapChecked",false);}
    }
    void run()throws Exception {
        while(stage<15) {
            r.checkpoint();
            if(stage==1&&!flow.plan().craft){stage=6;save();continue;}
            if(stage==6&&!flow.plan().store){stage=15;save();continue;}
            switch(stage) {
                case 0:
                    returnAndPrepareTown();
                    break;
                case 1:
                    if(r.match("crafting"))break;
                    flow.openMenu();flow.transition("craftMenu","crafting","打开制作",1835,680,10000,"制作页尚未出现");break;
                case 2:flow.transition("crafting","emblemPage","选择纹章合成",100,675,6000,"纹章合成页尚未出现");break;
                case 3:filters();break;
                case 4:synthesize();break;
                case 5:flow.transition("crafting","townHud","关闭纹章制作",2460,55,10000,"制作页尚未关闭");break;
                case 6:flow.transition("townHud","townMap","打开全屏地图",2340,175,8000,"全屏地图尚未打开");break;
                case 7:flow.transition("townMap","mapFunction","选择地图功能",2180,225,6000,"地图功能分类尚未选中");break;
                case 8:flow.transition("mapFunction","keeperGo","选择仓库看守多恩",2000,515,6000,"仓库看守前往弹窗尚未出现");break;
                case 9:
                    if(r.match("warehouseDialog")||r.match("warehouse"))break;
                    // Once the popover disappears, preserve the auto-path instead of selecting it again.
                    if(r.match("keeperGo"))r.tap("前往仓库看守",855,842);
                    else if(!r.match("townMap"))throw new Exception("前往仓库画面不符，重查当前寻路步骤");
                    break;
                case 10:
                    if(r.match("warehouse"))break;
                    r.log("等待自动寻路：地图自行关闭并出现打开仓库选项");
                    flow.require("warehouseDialog",60000,"尚未到仓库看守，继续等待当前寻路");break;
                case 11:flow.transition("warehouseDialog","warehouse","打开仓库",1850,830,10000,"仓库页尚未打开");break;
                case 12:flow.transition("warehouse","normalWarehouse","选择普通仓库",105,490,6000,"普通仓库尚未选中");break;
                case 13:store();break;
                case 14:flow.transition("warehouse","townHud","关闭普通仓库",2460,55,10000,"仓库页尚未关闭");break;
                default:throw new Exception("城镇步骤无效");
            }
            stage++;r.retrySucceeded();save();
        }
    }
    void save(){r.getSharedPreferences("progress",0).edit().putInt("townStage",stage).putInt("synthesisBatch",batch).putInt("synthesisCycle",cycle).putInt("synthesisStep",cycleStep).putBoolean("synthesisResetMaterials",resetMaterials).putInt("townTravelRole",flow.currentRole).putInt("townTravelStage",travelStage).putBoolean("townTravelMapChecked",travelMapChecked).apply();}
    void returnAndPrepareTown()throws Exception {
        // The return destination may be another village. A town HUD is sufficient
        // to finish this action; do not keep clicking a vanished settlement bar.
        if(travelStage==0&&!r.match("town")&&!r.match("townHud")&&!r.match("townMap")&&!r.match("worldMap")&&!r.match("magicTravelConfirm"))
            flow.transition("returnTown","townHud","疲劳不足：返回村庄",900,310,60000,"返回村庄尚未完成");
        if(flow.plan().craft||flow.plan().store)ensureMagicTown();
    }
    void ensureMagicTown()throws Exception {
        while(true) {
            r.checkpoint();
            if(magicTownStable()){travelStage=0;travelMapChecked=false;save();r.log("已确认到达魔法山脊，继续城镇任务");return;}
            switch(travelStage) {
                case 0:
                    if(r.match("magicTravelConfirm")){travelStage=3;break;}
                    if(r.match("worldMap")){travelStage=2;break;}
                    flow.transition("townHud","townMap","前往魔法山脊：打开全屏地图",2340,175,8000,"全屏地图尚未打开");
                    travelStage=1;break;
                case 1:
                    if(r.match("magicTravelConfirm")){travelStage=3;break;}
                    if(r.match("worldMap")){travelStage=2;break;}
                    flow.require("townMap",8000,"本地地图尚未稳定，先确认当前城镇");
                    if(magicMapStable()){
                        flow.transition("townMap","townHud","已在魔法山脊：关闭地图，跳过传送",2460,55,10000,"关闭地图后城镇主界面尚未出现");
                        travelStage=0;travelMapChecked=false;save();r.log("本地地图已确认魔法山脊，直接继续合成和存仓");return;
                    }
                    travelMapChecked=true;save();
                    flow.transition("townMap","worldMap","前往魔法山脊：打开世界地图",165,1325,8000,"世界地图尚未打开");
                    travelStage=2;break;
                case 2:
                    if(!travelMapChecked){
                        flow.transition("worldMap","townHud","复核当前城镇：关闭世界地图",2460,55,10000,"关闭世界地图后城镇主界面尚未出现");
                        travelStage=0;break;
                    }
                    // Tap the town illustration; the orange name ribbon did not
                    // open the travel dialog in the recorded world-map failure.
                    flow.transition("worldMap","magicTravelConfirm","世界地图：选择魔法山脊",800,480,8000,"前往魔法山脊的确认弹窗尚未出现");
                    travelStage=3;break;
                case 3:
                    if(r.match("worldMap")){travelStage=2;break;}
                    if(!travelMapChecked){
                        flow.transition("magicTravelConfirm","worldMap","复核当前城镇：取消传送确认",1000,968,8000,"传送确认弹窗尚未关闭");
                        travelStage=2;break;
                    }
                    // Save before dispatch: a cancelled callback can still have
                    // accepted the teleport. Continue must wait for that loading.
                    travelStage=4;save();
                    if(r.match("magicTravelConfirm"))r.tap("前往魔法山脊：是",1560,968);
                    break;
                case 4:
                    // A same-town rejection returns to the world map instead of
                    // loading. Recheck the local map rather than repeating Yes.
                    if(r.match("worldMap")){travelMapChecked=false;travelStage=2;break;}
                    if(r.match("magicTravelConfirm"))r.tap("前往魔法山脊：重试确认是",1560,968);
                    waitMagicArrival();break;
                default:throw new Exception("前往魔法山脊的步骤无效");
            }
            r.retrySucceeded();save();
        }
    }
    boolean magicTownStable()throws Exception {
        for(int n=0;n<3;n++){r.checkpoint();if(!r.match("town"))return false;if(n<2)r.waitMs(250);}
        return true;
    }
    boolean magicMapStable()throws Exception {
        for(int n=0;n<3;n++){r.checkpoint();if(!r.match("magicTownMap"))return false;if(n<2)r.waitMs(250);}
        return true;
    }
    void waitMagicArrival()throws Exception {
        r.log("等待传送读图完成，确认魔法山脊后继续合成和存仓");
        for(long waited=0;waited<60000;waited+=250){
            r.checkpoint();if(magicTownStable())return;
            if(r.match("worldMap")){travelMapChecked=false;travelStage=2;save();return;}
            if(r.match("townHud")){
                travelMapChecked=false;travelStage=0;save();return;
            }
            r.waitMs(250);
        }
        throw new Exception("尚未确认到达魔法山脊，保留传送步骤继续等待");
    }
    void filters()throws Exception {
        flow.require("emblemPage",5000,"请恢复纹章合成页");
        if(!r.match("gradeB"))r.tap("选择B级纹章",820,215);
        flow.require("gradeB",5000,"B级纹章尚未选中");
        if(!checked(620,1340))r.tap("勾选不显示绑定纹章",620,1340);
        if(!checked(620,1340))throw new Exception("隐藏绑定纹章尚未勾选");
        if(checked(2053,1218))r.tap("关闭可用非绑定金币",2053,1218);
        if(checked(2053,1218))throw new Exception("非绑定金币选项尚未关闭");
    }
    boolean checked(int x,int y)throws Exception {Bitmap b=r.frame();try{return TownInventory.checked(b::getPixel,x,y);}finally{b.recycle();}}
    int[] synthesisState()throws Exception {
        Bitmap b=r.frame();try {
            if(!r.vision.matches(b,"emblemPage")||!r.vision.matches(b,"gradeB")||!TownInventory.checked(b::getPixel,620,1340))
                throw new Exception("批次检查：B级非绑定纹章页面不符");
            if(TownInventory.checked(b::getPixel,2053,1218))throw new Exception("批次检查：非绑定金币选项被开启");
            return new int[]{TownInventory.visibleB(b::getPixel),-1,TownInventory.materials(b::getPixel)};
        }finally{b.recycle();}
    }
    void synthesize()throws Exception {
        // Local guards use the ongoing capture. Full inventory/OCR scans remain batched.
        // 0=select, 1=ready to submit, 2=submitted, 3=result seen/awaiting refresh.
        while(true) {
            r.checkpoint();
            if(recoverSynthesisError())continue;
            if(resetMaterials){clearStaleMaterials();continue;}
            if(!batchStarted) {
                dismissResult();int[] state=synthesisState();
                if(state[0]+state[2]<3){r.log("非绑定B级纹章不足3枚，结束合成");return;}
                if(batch>=60)throw new Exception("合成已达60批，请检查背包后继续");
                cycle=0;batchStarted=true;save();
                r.log("合成第"+(batch+1)+"批，最多10轮；每轮等待结果和物品刷新");
            }
            while(cycle<10) {
                r.checkpoint();String label="合成第"+(batch+1)+"批第"+(cycle+1)+"轮：";
                if(recoverSynthesisError())continue;
                if(resetMaterials){clearStaleMaterials();continue;}
                if(cycleStep==0) {
                    dismissResult();
                    if(!r.threeMaterials())r.batchTap(label+"放入3个",1830,1108);
                    if(!waitMaterials(3,4000)){
                        if(recoverSynthesisError())continue;
                        int[] state=synthesisState();
                        if(state[0]+state[2]<3){r.log("非绑定B级纹章不足3枚，结束合成");return;}
                        resetMaterials=true;save();
                        r.autoRetry("本轮选料尚未完成，等待刷新后重新放入3个",1000);continue;
                    }
                    cycleStep=1;save();
                }
                if(cycleStep==1) {
                    if(!waitMaterials(3,2000)){
                        if(recoverSynthesisError())continue;
                        cycleStep=0;resetMaterials=true;save();
                        r.autoRetry("选料已变化，刷新本轮材料后重试",1000);continue;
                    }
                    // A cancelled callback may still have submitted successfully.
                    // Save before tapping, then only inspect this pending result on Continue.
                    cycleStep=2;save();r.batchTap(label+"合成",1890,1325);
                }
                if(cycleStep==2) {
                    boolean result=false;
                    for(int waited=0;waited<12000;waited+=200){
                        int[] ui=r.synthesisUi();
                        if(ui[0]==1){result=true;break;}
                        if(ui[0]==2)break;
                        r.waitMs(200);
                    }
                    if(recoverSynthesisError())continue;
                    if(result){cycleStep=3;save();}
                    else if(waitMaterials(0,1000)){
                        // Also accepts a result already closed by the user. A stable empty
                        // base page after submission must not be charged a second time.
                        r.log("本轮结果弹窗已关闭，材料槽已清空，继续下一轮");
                        cycleStep=3;save();
                    }else{
                        cycleStep=0;resetMaterials=true;save();
                        r.autoRetry("合成尚未返回结果，清空旧选料后重试当前轮",1000);continue;
                    }
                }
                if(cycleStep==3){
                    if(r.synthesisUi()[0]==1)r.batchTap(label+"确认",1280,920);
                    if(!waitMaterials(0,8000)){
                        if(recoverSynthesisError())continue;
                        r.autoRetry("等待合成结果关闭及材料刷新，不重复合成",1000);continue;
                    }
                    cycle++;cycleStep=0;r.retrySucceeded();save();
                }
            }
            int[] state=synthesisState();
            if(state[0]+state[2]<3){r.log("非绑定B级纹章不足3枚，结束合成");return;}
            // Visible inventory is capped at 24; gold may be covered by the overlay.
            // Ten individually completed result/refresh transitions prove batch progress.
            r.log("本批10轮结果已确认，继续检查剩余B级纹章");
            batch++;batchStarted=false;cycleStep=0;r.retrySucceeded();save();
        }
    }
    void dismissResult()throws Exception {
        if(r.synthesisUi()[0]==1) {
            r.batchTap("关闭已有合成结果",1280,920);
            if(!waitMaterials(0,8000))throw new Exception("已有合成结果尚未关闭或物品尚未刷新");
        }
    }
    boolean waitMaterials(int expected,int timeout)throws Exception {
        int stable=0;
        for(int waited=0;waited<timeout;waited+=200){
            r.checkpoint();int[] ui=r.synthesisUi();
            if(ui[0]==2)return false;
            stable=ui[0]==0&&ui[1]==expected?stable+1:0;
            if(stable>=3)return true;
            r.waitMs(200);
        }
        return false;
    }
    boolean recoverSynthesisError()throws Exception {
        int[] ui=r.synthesisUi();if(ui[0]!=2)return false;
        // Close only a localized game information dialog, then discard stale item IDs.
        cycleStep=0;resetMaterials=true;save();
        r.batchTap("关闭合成物品提示：叉号",ui[2],ui[3]);
        r.autoRetry("合成收到物品提示，等待背包刷新后重选当前轮材料",1000);
        return true;
    }
    void clearStaleMaterials()throws Exception {
        r.waitMs(1000);
        for(int attempt=0;attempt<3;attempt++){
            int[] ui=r.synthesisUi();
            if(ui[0]==2){recoverSynthesisError();return;}
            if(ui[0]==1){resetMaterials=false;cycleStep=3;save();return;}
            if(waitMaterials(0,1000)){resetMaterials=false;cycleStep=0;save();return;}
            ui=r.synthesisUi();
            if(ui[0]==2){recoverSynthesisError();return;}
            if(ui[0]==1){resetMaterials=false;cycleStep=3;save();return;}
            if(ui[0]!=0)throw new Exception("等待纹章页面恢复后清空旧选料");
            if(attempt<2)r.batchTap("清空失效选料：切换放入3个",1830,1108);
        }
        throw new Exception("旧选料尚未清空，保留当前轮等待刷新");
    }
    void store()throws Exception {
        for(int stored=0,scrolls=0;stored<160;) {
            r.checkpoint();flow.require("normalWarehouse",5000,"请恢复普通仓库页后继续存入");
            Bitmap b=r.frame();int[] capacity;int[][] targets;boolean bottomEmpty;
            try {
                if(!r.vision.matches(b,"normalWarehouse"))throw new Exception("背包扫描：普通仓库页面不符");
                targets=TownInventory.aEmblems(b::getPixel);bottomEmpty=TownInventory.bagBottomEmpty(b::getPixel);
                capacity=r.vision.warehouseCapacity(b);
            }finally{b.recycle();}
            if(targets.length==0){
                // Ten overlapping swipes cover all 160 bag slots, including identical-looking pages.
                if(bottomEmpty||scrolls>=10){r.log("背包已扫描完，未发现剩余可存A级纹章，结束存仓");return;}
                r.gesture("向下翻背包查找A级纹章 · 第"+(scrolls+1)+"次",2440,1150,2440,670,500);
                scrolls++;continue;
            }
            if(capacity==null)throw new Exception("普通仓库容量暂未识别，保留存仓步骤重查");
            if(capacity[0]>=capacity[1]){r.log("普通仓库已满，结束存仓并返回选角");return;}
            int[] target=targets[0];
            r.doubleTap("双击背包A级纹章（本屏"+targets.length+"枚，仓库已有"+capacity[0]+"枚）",target[0],target[1]);
            boolean changed=false;
            for(int n=0;n<20;n++) {
                r.waitMs(150);b=r.frame();int[] after;
                try{after=r.vision.warehouseCapacity(b);}finally{b.recycle();}
                if(after!=null&&after[0]>capacity[0]&&after[1]==capacity[1]){changed=true;break;}
            }
            if(!changed)throw new Exception("纹章尚未存入，继续时重新查找当前背包；请检查仓库或物品弹窗");
            stored++;r.retrySucceeded();
        }
        throw new Exception("存仓次数达上限，请检查仓库后继续");
    }
}
