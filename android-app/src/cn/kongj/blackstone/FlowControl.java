package cn.kongj.blackstone;

/** Lifecycle and consecutive failure budget shared by the overlay and tests. */
final class FlowControl {
    static final int READY=0,RUNNING=1,PAUSED=2,ERROR=3;
    volatile int state=READY;
    private int failures;
    private String failureKey="";
    synchronized void start(){state=RUNNING;failures=0;}
    synchronized void reset(){state=READY;failures=0;}
    synchronized void pause(){if(state==RUNNING)state=PAUSED;}
    synchronized boolean resume(){if(state!=PAUSED&&state!=ERROR)return false;state=RUNNING;failures=0;return true;}
    synchronized boolean failed(String key,int retries){failureKey=key;failures++;if(failures>retries){state=ERROR;return false;}return true;}
    synchronized void success(){failures=0;failureKey="";}
    synchronized int failures(){return failures;}
    synchronized void error(){state=ERROR;}
}
