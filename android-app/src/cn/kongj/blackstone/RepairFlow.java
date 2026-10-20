package cn.kongj.blackstone;

/** Repair once before farming, preserving each completed click across Continue. */
class RepairFlow {
    final GameFlow flow;final RunnerService r;int stage;
    RepairFlow(GameFlow flow){
        this.flow=flow;r=flow.r;
        android.content.SharedPreferences p=r.getSharedPreferences("progress",0);
        if(p.getInt("phase",-1)==GameFlow.REPAIR&&p.getInt("repairRole",-1)==flow.currentRole)
            stage=Math.max(0,Math.min(6,p.getInt("repairStage",0)));
    }
    void run()throws Exception {
        while(stage<6){
            r.checkpoint();
            switch(stage){
                case 0:
                    if(r.match("repairWindow")||r.match("repairConfirm"))break;
                    flow.transition("hud","repairBag","修理装备：打开背包",2300,455,6000,"背包页面未出现");break;
                case 1:
                    if(r.match("repairConfirm"))break;
                    flow.transition("repairBag","repairWindow","修理装备：打开修理",2405,1331,6000,"修理装备窗口未出现");break;
                case 2:
                    if(r.match("repairEmpty")||r.match("repairConfirm"))break;
                    flow.transition("repairWindow","repairConfirm","修理装备：全部修理",1640,1160,6000,"全部修理的确认弹窗未出现");break;
                case 3:
                    if(r.match("repairEmpty")){r.log("已没有装备需要修理，继续关闭窗口");break;}
                    flow.transition("repairConfirm","repairEmpty","修理装备：确认手续费",1550,988,12000,"尚未确认修理完成，保留确认步骤重查");break;
                case 4:
                    flow.transition("repairWindow","repairBag","修理装备：关闭修理窗口",2040,228,6000,"修理窗口尚未关闭");break;
                case 5:
                    flow.transition("repairBag","hud","修理装备：关闭背包",2460,55,6000,"关闭背包后主界面未出现");break;
                default:throw new Exception("修理装备步骤无效");
            }
            stage++;r.retrySucceeded();
            r.getSharedPreferences("progress",0).edit().putInt("repairRole",flow.currentRole).putInt("repairStage",stage).apply();
            flow.saveProgress();
        }
    }
}
