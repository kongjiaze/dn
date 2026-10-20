package cn.kongj.blackstone;

/** Capture-thread observation retained until the next dungeon round or explicit stop. */
final class CombatCompletion {
    boolean armed,seen;
    int hits;
    long lastFrame=-1;
    void arm(){armed=true;}
    void reset(){armed=false;seen=false;hits=0;lastFrame=-1;}
    void observe(boolean message,long frameAt){
        if(!armed||seen||frameAt<=lastFrame)return;
        if(lastFrame>=0&&frameAt-lastFrame>750)hits=0;
        lastFrame=frameAt;
        hits=message?hits+1:0;
        if(hits>=2)seen=true;
    }
}
