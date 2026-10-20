package cn.kongj.blackstone;

/** Small fixed UI regions, independent of the moving 3D scene. Coordinates are 2560x1440. */
final class TownInventory {
    interface Pixels {int at(int x,int y);}
    static boolean gold(int c) {int r=c>>16&255,g=c>>8&255,b=c&255;return r>145&&g>110&&r>g*.95&&g>b*1.45;}
    static boolean cyan(int c) {int r=c>>16&255,g=c>>8&255,b=c&255;return g>135&&b>130&&r<g*.65&&r<b*.65;}
    static boolean purple(int c) {int r=c>>16&255,g=c>>8&255,b=c&255;return b>95&&b>r*1.12&&b>g*1.2;}
    static boolean checked(Pixels p,int x,int y) {
        int green=0;
        for(int dy=-22;dy<=22;dy+=2)for(int dx=-22;dx<=22;dx+=2) {
            int c=p.at(x+dx,y+dy),r=c>>16&255,g=c>>8&255,b=c&255;
            if(g>110&&g>r*1.12&&g>b*1.5)green++;
        }
        return green>25;
    }
    static int materials(Pixels p) {
        int count=0;
        for(int[] pos:new int[][]{{1512,223},{2122,223},{1512,833},{2122,833}}) {
            int hits=0;
            for(int y=pos[1];y<pos[1]+156;y+=2)for(int x=pos[0];x<pos[0]+156;x+=2) {
                // The glowing square border is present only on occupied slots.
                if(x>pos[0]+12&&x<pos[0]+143&&y>pos[1]+12&&y<pos[1]+143)continue;
                if(gold(p.at(x,y)))hits++;
            }
            if(hits>180)count++;
        }
        return count;
    }
    static int visibleB(Pixels p) {
        int count=0;
        for(int row=0;row<6;row++)for(int col=0;col<4;col++) {
            int x=588+col*160,y=301+row*160,hits=0;
            for(int d=10;d<120;d+=2) {
                if(purple(p.at(x+d,y+4)))hits++;
                if(purple(p.at(x+4,y+d)))hits++;
            }
            if(hits>40)count++;
        }
        return count;
    }
    static boolean aEmblem(Pixels p,int left,int top) {
        int gold=0,silver=0;
        for(int y=0;y<129;y+=2)for(int x=0;x<130;x+=2) {
            if((x<12||x>119||y<12||y>118)&&gold(p.at(left+x,top+y)))gold++;
            if(x>22&&x<112&&y>17&&y<110) {
                int c=p.at(left+x,top+y),r=c>>16&255,g=c>>8&255,b=c&255;
                int low=Math.min(r,Math.min(g,b)),high=Math.max(r,Math.max(g,b));
                if(low>90&&high<low*1.5)silver++;
            }
        }
        // The silver badge outline is shared by different emblem centers, including gray icons.
        // Grade alone would also accept gold-bordered apples, coins and other ordinary items.
        return gold>100&&silver>400;
    }
    static int goldLine(Pixels p,int left,int y) {
        int hits=0;for(int x=12;x<118;x+=2)if(gold(p.at(left+x,y)))hits++;return hits;
    }
    static int[][] aEmblems(Pixels p) {
        java.util.ArrayList<int[]> found=new java.util.ArrayList<>();
        // Columns stay fixed; find actual row borders so scrolling need not land on a full row.
        for(int col=0;col<5;col++) {
            int left=1728+(int)Math.round(col*157.5);
            for(int top=285;top<=1135;top++) {
                if(goldLine(p,left,top)<=35)continue;
                boolean bottom=false;
                for(int dy=127;dy<=133;dy++)if(goldLine(p,left,top+dy)>35){bottom=true;break;}
                if(!bottom||!aEmblem(p,left,top))continue;
                found.add(new int[]{left+62,top+64});top+=133;
            }
        }
        found.sort((a,b)->a[1]==b[1]?Integer.compare(a[0],b[0]):Integer.compare(a[1],b[1]));
        return found.toArray(new int[found.size()][]);
    }
    static boolean bagBottomEmpty(Pixels p) {
        // Empty cells have only a dark background; include partial rows near the viewport edge.
        for(int col=0;col<5;col++) {
            int left=1728+(int)Math.round(col*157.5);
            for(int y=1140;y<1258;y+=4)for(int x=22;x<112;x+=4) {
                int c=p.at(left+x,y);
                if((c>>16&255)>70||(c>>8&255)>70||(c&255)>70)return false;
            }
        }
        return true;
    }
}
