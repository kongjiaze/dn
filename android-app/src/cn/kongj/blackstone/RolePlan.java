package cn.kongj.blackstone;

/** A selected slot and its profession, independent of character names. */
final class RolePlan {
    static final int NONE=0,SAGE=1,ELEMENTALIST=2,SORCERESS=3,ALCHEMIST=4,PRIEST=5;
    static final String[] PROFESSIONS={"请选择职业","贤者","元素师","魔导师","药剂师","祭祀"};
    boolean enabled,recover=true,craft=true,store=true;
    int profession,position,baseForward;
    RolePlan copy(){RolePlan p=new RolePlan();p.enabled=enabled;p.recover=recover;p.craft=craft;p.store=store;p.profession=profession;p.position=position;p.baseForward=baseForward;return p;}
    boolean supported(){return profession>=SAGE&&profession<=PRIEST;}
    boolean controlBeforeUltimate(){return profession==ELEMENTALIST;}
    boolean center(){return profession==SAGE||profession==ELEMENTALIST||profession==PRIEST;}
    int defaultForward(){return profession==ELEMENTALIST||profession==PRIEST?2800:profession==SAGE?3000:profession==SORCERESS?2100:2200;}
    int forward(){int base=baseForward>0?baseForward:defaultForward();return Math.max(250,Math.min(10000,base+position*100));}
    int chestForward(){return center()?0:500;}
    String professionName(){return supported()?PROFESSIONS[profession]:PROFESSIONS[0];}
    void movePosition(int delta){position=Math.max(-10,Math.min(10,position+delta));}
}
