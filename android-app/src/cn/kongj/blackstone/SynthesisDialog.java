package cn.kongj.blackstone;

/** Generic game information dialog; used only while synthesizing emblems. */
final class SynthesisDialog {
    final Mask title,close;
    SynthesisDialog(TownInventory.Pixels titlePixels,int titleWidth,int titleHeight,
                    TownInventory.Pixels closePixels,int closeWidth,int closeHeight){
        title=new Mask(titlePixels,titleWidth,titleHeight);
        close=new Mask(closePixels,closeWidth,closeHeight);
    }
    int[] locateClose(TownInventory.Pixels frame){
        // Dialogs share a centered title, but may change height/width with the message.
        int[] t=find(frame,title,1216,1248,220,680,4);
        if(t==null)return null;
        int[] x=find(frame,close,1590,2112,t[1]-24,t[1]+24,6);
        if(x==null)return null;
        return new int[]{x[0]+close.width/2,x[1]+close.height/2};
    }
    static int[] find(TownInventory.Pixels frame,Mask mask,int left,int right,int top,int bottom,int step){
        double best=100;int bx=0,by=0;
        for(int y=top;y<=bottom;y+=step)for(int x=left;x<=right;x+=step){
            double error=mask.error(frame,x,y,4);
            if(error<best){best=error;bx=x;by=y;}
        }
        if(best>55)return null;
        best=100;int rx=0,ry=0;
        for(int y=by-step;y<=by+step;y++)for(int x=bx-step;x<=bx+step;x++){
            double error=mask.error(frame,x,y,2);
            if(error<best){best=error;rx=x;ry=y;}
        }
        return best<24?new int[]{rx,ry}:null;
    }
    static boolean bright(int c){
        int r=c>>16&255,g=c>>8&255,b=c&255;
        return Math.min(r,Math.min(g,b))>140&&Math.max(r,Math.max(g,b))>190;
    }
    static final class Mask {
        final int width,height;final boolean[][] pixels;
        Mask(TownInventory.Pixels source,int width,int height){
            this.width=width;this.height=height;pixels=new boolean[height][width];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[y][x]=bright(source.at(x,y));
        }
        double error(TownInventory.Pixels frame,int left,int top,int stride){
            int expected=0,observed=0,intersection=0;
            for(int y=0;y<height;y+=stride)for(int x=0;x<width;x+=stride){
                boolean a=pixels[y][x],b=bright(frame.at(left+x,top+y));
                if(a)expected++;if(b)observed++;if(a&&b)intersection++;
            }
            return expected+observed==0?100:100.0*(1-2.0*intersection/(expected+observed));
        }
    }
}
