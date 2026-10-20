package cn.kongj.blackstone;

/** Separates map attenuation from animated scenery; returns null on unstable evidence. */
final class TemporalMinimap {
    static final int WIDTH=440,HEIGHT=300;
    static final class Fit {
        final int x,y;final double score,support,margin;
        Fit(int x,int y,double score,double support,double margin){this.x=x;this.y=y;this.score=score;this.support=support;this.margin=margin;}
        boolean reliable(){return score>=.5&&support>=.8&&margin>=.15;}
    }
    static int[] locate(int[][] frames,int[] hint) {
        int[] temporal=locate(frames);
        if(temporal!=null)return temporal; // Full-distance moves may exceed the initial speed prediction.
        if(hint==null||frames.length<16)return null;
        Fit[] fits={spatialFit(frames,0,frames.length,1,hint),spatialFit(frames,0,frames.length/2,1,hint),
            spatialFit(frames,frames.length/2,frames.length,1,hint),spatialFit(frames,0,frames.length,2,hint),spatialFit(frames,1,frames.length,2,hint)};
        int minX=999,minY=999,maxX=-1,maxY=-1;
        for(Fit f:fits){if(f==null||f.score<16||f.support<.7||f.margin<1.5)return null;minX=Math.min(minX,f.x);maxX=Math.max(maxX,f.x);minY=Math.min(minY,f.y);maxY=Math.max(maxY,f.y);}
        if(maxX-minX>8||maxY-minY>8)return null;
        return new int[]{fits[0].x,fits[0].y};
    }
    static Fit spatialFit(int[][] frames,int from,int to,int stride,int[] hint) {
        int n=(to-from+stride-1)/stride;
        float[] brightness=new float[WIDTH*HEIGHT];int[] values=new int[n];
        for(int p=0;p<brightness.length;p++)for(int shift=0;shift<=16;shift+=8) {
            int j=0;for(int k=from;k<to;k+=stride)values[j++]=(frames[k][p]>>shift)&255;
            java.util.Arrays.sort(values);brightness[p]+=(values[n/2]+values[(n-1)/2])/6f;
        }
        double best=-100;int bx=0,by=0;double bs=0;
        double[] scores=new double[75*75];java.util.Arrays.fill(scores,-100);int index=0;
        for(int y=130;y<280;y+=2)for(int x=140;x<290;x+=2) {
            if(Math.abs(x-hint[0])>24||Math.abs(y-hint[1])>24){index++;continue;}
            double total=0;int count=0,aligned=0;
            for(int angle=0;angle<=180;angle+=15) {
                double a=angle*Math.PI/180;
                int ix=(int)Math.round(x+58*Math.cos(a)),iy=(int)Math.round(y-65+58*Math.sin(a));
                int ox=(int)Math.round(x+73*Math.cos(a)),oy=(int)Math.round(y-65+73*Math.sin(a));
                if((Math.abs(ix-214)<22&&Math.abs(iy-186)<30)||(Math.abs(ox-214)<22&&Math.abs(oy-186)<30))continue;
                double value=brightness[oy*WIDTH+ox]-brightness[iy*WIDTH+ix];
                if(value>8)aligned++;total+=Math.max(-40,Math.min(70,value));count++;
            }
            if(count>=7&&aligned>=count*.7){double score=total/count;scores[index]=score;if(score>best){best=score;bx=x;by=y;bs=(double)aligned/count;}}
            index++;
        }
        if(best==-100)return null;double rival=-100;index=0;
        for(int y=130;y<280;y+=2)for(int x=140;x<290;x+=2){if(Math.abs(x-bx)+Math.abs(y-by)>30)rival=Math.max(rival,scores[index]);index++;}
        return new Fit(bx,by,best,bs,best-rival);
    }
    static int[] locate(int[][] frames) {
        if(frames.length<16)return null;
        Fit[] fits=new Fit[5];
        fits[0]=fit(frames,0,frames.length,1);
        fits[1]=fit(frames,0,frames.length/2,1);
        fits[2]=fit(frames,frames.length/2,frames.length,1);
        fits[3]=fit(frames,0,frames.length,2);
        fits[4]=fit(frames,1,frames.length,2);
        int minX=999,minY=999,maxX=-1,maxY=-1;
        for(Fit f:fits){if(f==null||!f.reliable())return null;minX=Math.min(minX,f.x);maxX=Math.max(maxX,f.x);minY=Math.min(minY,f.y);maxY=Math.max(maxY,f.y);}
        if(maxX-minX>8||maxY-minY>8)return null;
        return new int[]{fits[0].x,fits[0].y};
    }
    static Fit fit(int[][] frames,int from,int to,int stride) {
        int n=(to-from+stride-1)/stride;if(n<2)return null;
        float[] variation=new float[WIDTH*HEIGHT],smooth=new float[WIDTH*HEIGHT];
        for(int p=0;p<variation.length;p++) {
            double sr=0,sg=0,sb=0,rr=0,gg=0,bb=0;
            for(int k=from;k<to;k+=stride){int c=frames[k][p];int r=(c>>16)&255,g=(c>>8)&255,b=c&255;sr+=r;sg+=g;sb+=b;rr+=r*r;gg+=g*g;bb+=b*b;}
            variation[p]=(float)((Math.sqrt(Math.max(0,rr/n-sr*sr/(n*n)))+Math.sqrt(Math.max(0,gg/n-sg*sg/(n*n)))+Math.sqrt(Math.max(0,bb/n-sb*sb/(n*n))))/3);
        }
        for(int y=2;y<HEIGHT-2;y++)for(int x=2;x<WIDTH-2;x++) {
            float sum=0;for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++)sum+=variation[(y+dy)*WIDTH+x+dx];smooth[y*WIDTH+x]=sum/25;
        }
        double best=-100;int bx=0,by=0;double bs=0;
        double[] scores=new double[75*75];java.util.Arrays.fill(scores,-100);
        int index=0;
        for(int y=130;y<280;y+=2)for(int x=140;x<290;x+=2) {
            double total=0;int count=0,aligned=0;
            for(int angle=0;angle<=180;angle+=15) {
                double a=angle*Math.PI/180;
                int ix=(int)Math.round(x+58*Math.cos(a)),iy=(int)Math.round(y-65+58*Math.sin(a));
                int ox=(int)Math.round(x+73*Math.cos(a)),oy=(int)Math.round(y-65+73*Math.sin(a));
                if((Math.abs(ix-214)<22&&Math.abs(iy-186)<30)||(Math.abs(ox-214)<22&&Math.abs(oy-186)<30))continue;
                double value=Math.max(-1,Math.min(1.5,Math.log((smooth[oy*WIDTH+ox]+1)/(smooth[iy*WIDTH+ix]+1))));
                if(value>.15)aligned++;total+=value;count++;
            }
            if(count>=7&&aligned>=count*.7){double score=total/count;scores[index]=score;if(score>best){best=score;bx=x;by=y;bs=(double)aligned/count;}}
            index++;
        }
        if(best==-100)return null;
        double rival=-1;index=0;
        for(int y=130;y<280;y+=2)for(int x=140;x<290;x+=2){if(Math.abs(x-bx)+Math.abs(y-by)>30)rival=Math.max(rival,scores[index]);index++;}
        return new Fit(bx,by,best,bs,best-rival);
    }
}
