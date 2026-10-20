package cn.kongj.blackstone;

/** Detect white cooldown digits without confusing the ready shield icon with a number. */
final class SkillCooldown {
    interface Pixels {int at(int x,int y);}
    static final int UNKNOWN=-1,READY=0,COOLING=1;
    static int ultimateState(Pixels p,int cx,int cy) {
        if(visible(p,cx,cy))return COOLING;
        // An absent/covered button is not a ready skill. Require the purple
        // ultimate styling at this slot when no cooldown digits are visible.
        int purple=0;
        for(int y=-44;y<=44;y+=2)for(int x=-44;x<=44;x+=2){
            if(x*x+y*y>44*44)continue;
            int c=p.at(cx+x,cy+y),r=c>>16&255,g=c>>8&255,b=c&255;
            if(r>65&&b>70&&r>g*1.2&&b>g*1.2)purple++;
        }
        return purple>=100?READY:UNKNOWN;
    }
    static boolean visible(Pixels p,int cx,int cy) {
        final int w=97,h=85;boolean[] ink=new boolean[w*h],seen=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int c=p.at(cx-48+x,cy-42+y),r=c>>16&255,g=c>>8&255,b=c&255;
            int low=Math.min(r,Math.min(g,b)),high=Math.max(r,Math.max(g,b));
            ink[y*w+x]=low>225&&high-low<30;
        }
        int[] queue=new int[w*h];
        for(int start=0;start<ink.length;start++) {
            if(!ink[start]||seen[start])continue;
            int head=0,tail=1,minX=w,minY=h,maxX=0,maxY=0;queue[0]=start;seen[start]=true;
            while(head<tail) {
                int i=queue[head++],x=i%w,y=i/w;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                if(x>0&&ink[i-1]&&!seen[i-1]){seen[i-1]=true;queue[tail++]=i-1;}
                if(x<w-1&&ink[i+1]&&!seen[i+1]){seen[i+1]=true;queue[tail++]=i+1;}
                if(y>0&&ink[i-w]&&!seen[i-w]){seen[i-w]=true;queue[tail++]=i-w;}
                if(y<h-1&&ink[i+w]&&!seen[i+w]){seen[i+w]=true;queue[tail++]=i+w;}
            }
            int width=maxX-minX+1,height=maxY-minY+1;
            if(tail>=70&&width>=6&&width<=42&&height>=32&&height<=68&&height>=width*.9
                &&minX>1&&maxX<w-2&&minY>1&&maxY<h-2)return true;
        }
        return false;
    }
}
