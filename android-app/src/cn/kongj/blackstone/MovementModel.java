package cn.kongj.blackstone;

/** Learns joystick-to-minimap displacement from completed navigation moves. */
final class MovementModel {
    double xx=28.6,xy=0,yx=0,yy=12;
    int observations;
    static final class Plan {
        final int x,y,ms;final double inputX,inputY,predictedX,predictedY;
        Plan(int x,int y,int ms,double ix,double iy,double px,double py){this.x=x;this.y=y;this.ms=ms;inputX=ix;inputY=iy;predictedX=px;predictedY=py;}
    }
    Plan plan(int dx,int dy,int cap) {
        double determinant=xx*yy-xy*yx;
        double tx=(yy*dx-xy*dy)/determinant,ty=(-yx*dx+xx*dy)/determinant;
        double duration=Math.hypot(tx,ty);
        if(!Double.isFinite(duration)||duration<.0001)throw new IllegalArgumentException("移动估算无效");
        int ms=Math.min(cap,Math.max(90,(int)Math.round(duration*1000)));
        int x=300+(int)Math.round(220*tx/duration),y=1148+(int)Math.round(220*ty/duration);
        double ix=(x-300)/220.0*ms/1000.0,iy=(y-1148)/220.0*ms/1000.0;
        return new Plan(x,y,ms,ix,iy,xx*ix+xy*iy,yx*ix+yy*iy);
    }
    boolean observe(int[] start,int[] end,Plan plan) {
        double ax=end[0]-start[0],ay=end[1]-start[1],actual=Math.hypot(ax,ay),predicted=Math.hypot(plan.predictedX,plan.predictedY);
        double norm=plan.inputX*plan.inputX+plan.inputY*plan.inputY;
        if(plan.ms<250||actual<6||actual>180||norm<.01||predicted<1||actual/predicted>6||ax*plan.predictedX+ay*plan.predictedY<=0)return false;
        double ex=ax-(xx*plan.inputX+xy*plan.inputY),ey=ay-(yx*plan.inputX+yy*plan.inputY);
        double rate=.85/norm;
        double a=xx+rate*ex*plan.inputX,b=xy+rate*ex*plan.inputY,c=yx+rate*ey*plan.inputX,d=yy+rate*ey*plan.inputY;
        if(a*d-b*c<.5||Math.hypot(a,c)<.5||Math.hypot(b,d)<.5||Math.hypot(a,c)>180||Math.hypot(b,d)>180)return false;
        xx=a;xy=b;yx=c;yy=d;observations++;return true;
    }
}
