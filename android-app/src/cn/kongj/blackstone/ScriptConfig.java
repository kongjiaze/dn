package cn.kongj.blackstone;

import android.content.Context;
import android.content.SharedPreferences;

final class ScriptConfig {
    final SharedPreferences prefs;
    ScriptConfig(Context context){prefs=context.getSharedPreferences("profile",0);migrate(context.getSharedPreferences("route",0));calibrateElementalist();calibrateSorceress();}
    void calibrateSorceress(){
        if(prefs.getInt("sorceressPositionRevision",0)>=1)return;
        SharedPreferences.Editor edit=prefs.edit().putInt("sorceressPositionRevision",1);
        for(int slot=1;slot<=6;slot++){
            String key="role"+slot+".";int base=prefs.getInt(key+"baseForward",0);
            if(prefs.getInt(key+"profession",0)==RolePlan.SORCERESS&&base>0)
                edit.putInt(key+"baseForward",Math.max(250,base-100));
        }
        edit.apply();
    }
    void calibrateElementalist(){
        if(prefs.getInt("elementalistPositionRevision",0)>=1)return;
        SharedPreferences.Editor edit=prefs.edit().putInt("elementalistPositionRevision",1);
        for(int slot=1;slot<=6;slot++){
            String key="role"+slot+".";int base=prefs.getInt(key+"baseForward",0);
            if(prefs.getInt(key+"profession",0)==RolePlan.ELEMENTALIST&&base>0)
                edit.putInt(key+"baseForward",Math.max(250,base-200));
        }
        edit.apply();
    }
    void migrate(SharedPreferences legacy){
        if(prefs.getInt("revision",0)>=1)return;
        SharedPreferences.Editor edit=prefs.edit().putInt("revision",1);
        // Upgrade only a device that already has the previous role configuration.
        boolean existing=legacy.contains("startRole");
        for(int slot=1;slot<=6;slot++){
            String prefix="role"+slot+".";
            boolean enabled=existing&&slot>=legacy.getInt("startRole",2)&&slot<=legacy.getInt("endRole",5);
            int profession=existing&&slot>=2&&slot<=5?slot-1:0;
            edit.putBoolean(prefix+"enabled",enabled).putInt(prefix+"profession",profession)
                .putBoolean(prefix+"recover",true).putBoolean(prefix+"craft",true).putBoolean(prefix+"store",true)
                .putInt(prefix+"position",0).putInt(prefix+"baseForward",existing&&profession>0?legacy.getInt("role"+slot+"Forward",slot<=3?3000:2200):0);
        }
        edit.apply();
    }
    boolean configured(){return prefs.getBoolean("configured",false);}
    RolePlan role(int slot){
        RolePlan p=new RolePlan();String key="role"+slot+".";
        p.enabled=prefs.getBoolean(key+"enabled",false);p.profession=prefs.getInt(key+"profession",0);
        p.recover=prefs.getBoolean(key+"recover",true);p.craft=prefs.getBoolean(key+"craft",true);p.store=prefs.getBoolean(key+"store",true);
        p.position=Math.max(-10,Math.min(10,prefs.getInt(key+"position",0)));p.baseForward=prefs.getInt(key+"baseForward",0);return p;
    }
    RolePlan[] roles(){RolePlan[] roles=new RolePlan[6];for(int i=0;i<6;i++)roles[i]=role(i+1);return roles;}
    void saveRoles(RolePlan[] roles){
        validate(roles);SharedPreferences.Editor edit=prefs.edit();
        for(int i=0;i<6;i++){RolePlan p=roles[i];String key="role"+(i+1)+".";
            edit.putBoolean(key+"enabled",p.enabled).putInt(key+"profession",p.profession).putBoolean(key+"recover",p.recover)
                .putBoolean(key+"craft",p.craft).putBoolean(key+"store",p.store).putInt(key+"position",p.position).putInt(key+"baseForward",p.baseForward);
        }edit.apply();
    }
    static void validate(RolePlan[] roles){
        int count=0;for(int i=0;i<roles.length;i++)if(roles[i].enabled){count++;if(!roles[i].supported())throw new IllegalArgumentException("请为角色"+(i+1)+"选择职业");}
        if(count==0)throw new IllegalArgumentException("请至少勾选一个角色");
    }
    int next(int after){for(int slot=after+1;slot<=6;slot++)if(role(slot).enabled)return slot;return 7;}
    int first(){return next(0);}
    int last(){for(int slot=6;slot>=1;slot--)if(role(slot).enabled)return slot;return 0;}
    String summary(){StringBuilder s=new StringBuilder();for(int slot=1;slot<=6;slot++){RolePlan p=role(slot);if(!p.enabled)continue;if(s.length()>0)s.append('\n');s.append("角色").append(slot).append(" · ").append(p.professionName()).append("\n");s.append(p.recover?"领取体力":"不领取体力").append(" · ").append(p.craft?"合成纹章":"不合成").append(" · ").append(p.store?"存仓":"不存仓");}return s.toString();}
}
