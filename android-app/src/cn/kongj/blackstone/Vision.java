package cn.kongj.blackstone;

import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.util.*;
import java.io.*;

public class Vision {
    final Map<String,Bitmap> templates=new HashMap<>();
    final Map<String,int[]> boxes=new HashMap<>();
    final Map<String,Double> thresholds=new HashMap<>();
    final JSONObject digits;
    final JSONObject townDigits;
    final JSONObject actions;
    final ArrayList<int[][]> chestSamples=new ArrayList<>();
    final SynthesisDialog synthesisDialog;
    public Vision(Context context)throws Exception {
        JSONObject metadata=new JSONObject(read(context,"templates.json"));
        Iterator<String> keys=metadata.keys();
        while(keys.hasNext()) {
            String key=keys.next();JSONObject row=metadata.getJSONObject(key);JSONArray b=row.getJSONArray("box");
            boxes.put(key,new int[]{b.getInt(0),b.getInt(1),b.getInt(2),b.getInt(3)});
            thresholds.put(key,row.getDouble("threshold"));
            try(InputStream in=context.getAssets().open(key+".png")){templates.put(key,BitmapFactory.decodeStream(in));}
        }
        Bitmap infoTitle=templates.get("synthesisInfoTitle"),infoClose=templates.get("synthesisInfoClose");
        synthesisDialog=new SynthesisDialog(infoTitle::getPixel,infoTitle.getWidth(),infoTitle.getHeight(),infoClose::getPixel,infoClose.getWidth(),infoClose.getHeight());
        digits=new JSONObject(read(context,"digits.json"));
        townDigits=new JSONObject(read(context,"town-digits.json"));
        actions=new JSONObject(read(context,"actions.json"));
        // Store only masked reference points; unused account/character pixels are not packaged.
        try(DataInputStream in=new DataInputStream(context.getAssets().open("world-chest-samples.dat"))) {
            if(in.readInt()!=0x42534331||in.readInt()!=9)throw new IOException("Invalid chest references");
            for(int i=0;i<9;i++) {
                int count=in.readInt();if(count<=0||count>1000)throw new IOException("Invalid chest reference size");
                int[][] samples=new int[count][5];
                for(int[] point:samples)for(int j=0;j<5;j++)point[j]=in.readInt();
                chestSamples.add(samples);
            }
            if(in.read()!=-1)throw new IOException("Trailing chest reference data");
        }
    }
    void addChestSamples(Bitmap original,boolean nameOccluded) {
        for(double scale:new double[]{.7,1,1.3}) {
            Bitmap t=Bitmap.createScaledBitmap(original,(int)Math.round(original.getWidth()*scale),(int)Math.round(original.getHeight()*scale),true);
            ArrayList<int[]> samples=new ArrayList<>();
            for(int y=8;y<t.getHeight()-8;y+=6)for(int x=8;x<t.getWidth()-8;x+=6) {
                double tx=x/scale*110/original.getWidth(),ty=y/scale*115/original.getHeight();
                if(tx<=15||tx>=98||ty<=12||ty>=105||tx+ty<=45||tx-ty>=57)continue;
                // The front-view sample excludes clan/character text and the guild crest.
                if(nameOccluded&&(ty<20||(ty>40&&ty<83)||(tx<38&&ty>35&&ty<100)))continue;
                int c=t.getPixel(x,y);samples.add(new int[]{x,y,Color.red(c),Color.green(c),Color.blue(c)});
            }
            chestSamples.add(samples.toArray(new int[0][]));if(t!=original)t.recycle();
        }
    }
    String read(Context context,String name)throws Exception {
        try(InputStream in=context.getAssets().open(name)) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))>0)out.write(buf,0,n);return out.toString("UTF-8");
        }
    }
    public double error(Bitmap frame,String name) {
        int[] box=boxes.get(name);Bitmap template=templates.get(name);long err=0,count=0;
        if(box==null||template==null)return Double.POSITIVE_INFINITY;
        if(name.equals("battleCleared")){
            // This is the complete success sentence, not a score or an empty arena.
            // Require both halves so a different yellow objective banner cannot pass.
            double best=100;
            for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
                int[] expected={0,0},observed={0,0},intersection={0,0};
                for(int y=0;y<template.getHeight();y+=3)for(int x=0;x<template.getWidth();x+=3){
                    int half=x<template.getWidth()/2?0:1;
                    boolean a=noticeText(template.getPixel(x,y)),b=noticeText(frame.getPixel(box[0]+dx+x,box[1]+dy+y));
                    if(a)expected[half]++;if(b)observed[half]++;if(a&&b)intersection[half]++;
                }
                double worst=0;
                for(int half=0;half<2;half++)worst=Math.max(worst,expected[half]+observed[half]==0?100:100.0*(1-2.0*intersection[half]/(expected[half]+observed[half])));
                best=Math.min(best,worst);
            }
            return best;
        }
        if(name.equals("emblemPage")){
            // Selected lettering is yellow and its surrounding glow varies. Match
            // the bright glyphs, allowing the normalized screenshot's small offset.
            double best=100;
            for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
                int expected=0,observed=0,intersection=0;
                for(int y=0;y<template.getHeight();y+=2)for(int x=0;x<template.getWidth();x+=2){
                    boolean a=noticeText(template.getPixel(x,y)),b=noticeText(frame.getPixel(box[0]+dx+x,box[1]+dy+y));
                    if(a)expected++;if(b)observed++;if(a&&b)intersection++;
                }
                double e=expected+observed==0?100:100.0*(1-2.0*intersection/(expected+observed));
                best=Math.min(best,e);
            }
            return best;
        }
        if(name.equals("loginAccount")||name.equals("loginQuickFailed")||name.equals("loginSms")||name.equals("loginPassword")||name.equals("loginAgreement")||name.equals("loginRejected")) {
            int expected=0,observed=0,intersection=0;
            for(int y=0;y<template.getHeight();y+=2)for(int x=0;x<template.getWidth();x+=2) {
                boolean a=dark(template.getPixel(x,y)),b=dark(frame.getPixel(box[0]+x,box[1]+y));
                if(a)expected++;if(b)observed++;if(a&&b)intersection++;
            }
            return expected+observed==0?100:100.0*(1-2.0*intersection/(expected+observed));
        }
        if(name.equals("townHud")) {
            int expected=0,observed=0,intersection=0;
            for(int y=0;y<template.getHeight();y++)for(int x=0;x<template.getWidth();x++) {
                boolean a=townGreen(template.getPixel(x,y)),b=townGreen(frame.getPixel(box[0]+x,box[1]+y));
                if(a)expected++;if(b)observed++;if(a&&b)intersection++;
            }
            return expected+observed==0?100:100.0*(1-2.0*intersection/(expected+observed));
        }
        // Compare fixed UI lettering/icons independently of the moving world background.
        if(name.equals("loginNotice")||name.equals("recoveryConfirm")||name.equals("hud")||name.equals("entry")||name.equals("menu")||name.equals("menuSettings")||name.equals("town")||name.equals("craftMenu")||name.equals("warehouseDialog")||name.equals("returnTown")||name.equals("repairBag")||name.equals("repairWindow")||name.equals("repairConfirm")||name.equals("magicTravelConfirm")) {
            int expected=0,observed=0,intersection=0;
            for(int y=0;y<template.getHeight();y+=2)for(int x=0;x<template.getWidth();x+=2) {
                boolean a=name.equals("loginNotice")||name.equals("repairWindow")?noticeText(template.getPixel(x,y)):white(template.getPixel(x,y));
                boolean b=name.equals("loginNotice")||name.equals("repairWindow")?noticeText(frame.getPixel(box[0]+x,box[1]+y)):white(frame.getPixel(box[0]+x,box[1]+y));
                if(a)expected++;if(b)observed++;if(a&&b)intersection++;
            }
            return expected+observed==0?100:100.0*(1-2.0*intersection/(expected+observed));
        }
        for(int y=0;y<template.getHeight();y+=2)for(int x=0;x<template.getWidth();x+=2) {
            int a=frame.getPixel(box[0]+x,box[1]+y),b=template.getPixel(x,y);
            err+=Math.abs(Color.red(a)-Color.red(b))+Math.abs(Color.green(a)-Color.green(b))+Math.abs(Color.blue(a)-Color.blue(b));count+=3;
        }
        return (double)err/count;
    }
    public boolean matches(Bitmap frame,String name){
        if(name.equals("magicTownMap"))return matches(frame,"townMap")
            &&(matches(frame,"magicTownMapTitle")||(matches(frame,"magicTownMapCentre")&&matches(frame,"magicTownMapTree")));
        if(name.equals("townHud")&&(!matches(frame,"hud")||matches(frame,"recovery")||matches(frame,"menu")||matches(frame,"roles")))return false;
        if(name.equals("worldMap")&&matches(frame,"magicTravelConfirm"))return false;
        if(name.equals("townMap")&&(matches(frame,"worldMap")||matches(frame,"magicTravelConfirm")))return false;
        if(name.equals("repairBag")&&(matches(frame,"repairWindow")||matches(frame,"repairConfirm")))return false;
        if(name.equals("repairEmpty")&&(!matches(frame,"repairWindow")||matches(frame,"repairConfirm")||!matches(frame,"repairEmptyRight")))return false;
        if(thresholds.containsKey(name)&&error(frame,name)<thresholds.get(name))return true;
        // A dragged overlay may cover one anchor; the opposite map anchor remains usable.
        return name.equals("townMap")&&thresholds.containsKey("townMapHeader")
            &&error(frame,"townMapHeader")<thresholds.get("townMapHeader");
    }
    public boolean inBattle(Bitmap frame) {
        return matches(frame,"entry")&&matches(frame,"hud")&&!matches(frame,"retry")&&!matches(frame,"agree")
            &&!matches(frame,"menu")&&!matches(frame,"recovery")&&!matches(frame,"settingsReturn")
            &&!matches(frame,"returnConfirm")&&!matches(frame,"dungeons")&&!matches(frame,"roles");
    }
    public boolean has(String name){return boxes.containsKey(name);}
    public boolean skillCooling(Bitmap frame,int x,int y){return SkillCooldown.visible(frame::getPixel,x,y);}
    double chestObjectError(Bitmap frame,int[][] samples,int x,int y,double cutoff) {
        long error=0;int limit=(int)(cutoff*samples.length*3);
        for(int[] p:samples) {
            int c=frame.getPixel(x+p[0],y+p[1]);
            error+=Math.abs(Color.red(c)-p[2])+Math.abs(Color.green(c)-p[3])+Math.abs(Color.blue(c)-p[4]);
            if(error>limit)return 100;
        }
        return error/(samples.length*3.0);
    }
    public boolean worldChestVisible(Bitmap frame) {
        for(int[][] samples:chestSamples) {
            double best=25;int bx=-1,by=-1;
            for(int y=350;y<850;y+=10)for(int x=600;x<1650;x+=10) {
                double e=chestObjectError(frame,samples,x,y,best);
                if(e<best){best=e;bx=x;by=y;}
            }
            if(bx<0)continue;
            for(int y=by-10;y<=by+10;y+=2)for(int x=bx-10;x<=bx+10;x+=2)
                if(chestObjectError(frame,samples,x,y,12)<12)return true;
        }
        return false;
    }
    /** Approximate arena bottom midpoint from the dark room outline, excluding the arrow. */
    public int[] arenaSample(Bitmap frame) {
        int[] pixels=new int[TemporalMinimap.WIDTH*TemporalMinimap.HEIGHT];
        frame.getPixels(pixels,0,TemporalMinimap.WIDTH,2120,0,TemporalMinimap.WIDTH,TemporalMinimap.HEIGHT);
        return pixels;
    }
    public int[] temporalArenaPosition(ArrayList<int[]> samples,int[] hint) {
        return TemporalMinimap.locate(samples.toArray(new int[samples.size()][]),hint);
    }
    public int[] arenaPosition(Bitmap frame) {
        return arenaPosition(frame,-1,-1);
    }
    public int[] arenaPosition(Bitmap frame,int expectedX,int expectedY) {
        boolean tracking=expectedX>=0&&expectedY>=0;
        double best=-100;int bx=0,by=0;
        ArrayList<double[]> candidates=new ArrayList<>();
        for(int y=130;y<280;y+=2)for(int x=140;x<290;x+=2) {
            if(tracking&&(Math.abs(x-expectedX)>24||Math.abs(y-expectedY)>24))continue;
            double total=0;int count=0,aligned=0;
            for(int angle=0;angle<=180;angle+=15) {
                double a=angle*Math.PI/180;
                int ix=(int)Math.round(x+58*Math.cos(a)),iy=(int)Math.round(y-65+58*Math.sin(a));
                int ox=(int)Math.round(x+73*Math.cos(a)),oy=(int)Math.round(y-65+73*Math.sin(a));
                if((Math.abs(ix-214)<22&&Math.abs(iy-186)<30)||(Math.abs(ox-214)<22&&Math.abs(oy-186)<30))continue;
                double delta=brightness(frame.getPixel(2120+ox,oy))-brightness(frame.getPixel(2120+ix,iy));
                if(delta>8)aligned++;
                total+=Math.max(-40,Math.min(70,delta));count++;
            }
            if(count<7||aligned<count*.7)continue;
            double score=count==0?-100:total/count;
            candidates.add(new double[]{score,x,y});
            if(score>best){best=score;bx=x;by=y;}
        }
        double rival=-100;
        for(double[] c:candidates)if(Math.abs(c[1]-bx)+Math.abs(c[2]-by)>30)rival=Math.max(rival,c[0]);
        return best>=(tracking?16:22)&&best-rival>=(tracking?1.5:3)?new int[]{bx,by}:null;
    }
    double brightness(int color){return (Color.red(color)+Color.green(color)+Color.blue(color))/3.0;}
    public JSONObject action(String name)throws Exception{return actions.getJSONObject(name);}
    double letteringError(Bitmap frame,Bitmap template,int left,int top,int stride) {
        if(left<0||top<0||left+template.getWidth()>frame.getWidth()||top+template.getHeight()>frame.getHeight())return 100;
        int expected=0,observed=0,intersection=0;
        for(int y=0;y<template.getHeight();y+=stride)for(int x=0;x<template.getWidth();x+=stride) {
            boolean a=white(template.getPixel(x,y)),b=white(frame.getPixel(left+x,top+y));
            if(a)expected++;if(b)observed++;if(a&&b)intersection++;
        }
        return expected+observed==0?100:100.0*(1-2.0*intersection/(expected+observed));
    }
    /** Find the named card anywhere vertically in the left column, then bind its button. */
    public int[] locateBlackstone(Bitmap frame) {
        Bitmap title=templates.get("blackstoneCard"),button=templates.get("blackstoneGo");
        int[] original=boxes.get("blackstoneCard"),go=boxes.get("blackstoneGo");
        double best=100;int bestX=-1,bestY=-1;
        for(int y=200;y<=1030;y+=2)for(int x=original[0]-6;x<=original[0]+6;x+=2) {
            double e=letteringError(frame,title,x,y,6);
            if(e<best){best=e;bestX=x;bestY=y;}
        }
        if(best>40)return null;
        double refined=100;int foundX=-1,foundY=-1;
        for(int y=bestY-2;y<=bestY+2;y++)for(int x=bestX-2;x<=bestX+2;x++) {
            double e=letteringError(frame,title,x,y,2);
            if(e<refined){refined=e;foundX=x;foundY=y;}
        }
        if(refined>=18)return null;
        int dx=foundX-original[0],dy=foundY-original[1];
        if(letteringError(frame,button,go[0]+dx,go[1]+dy,2)>=18)return null;
        int tapX=1230+dx,tapY=930+dy;
        if(tapY<200||tapY>1390)return null;
        return new int[]{tapX,tapY};
    }
    boolean white(int v){int r=Color.red(v),g=Color.green(v),b=Color.blue(v);return Math.min(r,Math.min(g,b))>140&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<55;}
    boolean noticeText(int v){return Math.min(Color.red(v),Math.min(Color.green(v),Color.blue(v)))>140&&Math.max(Color.red(v),Math.max(Color.green(v),Color.blue(v)))>190;}
    boolean dark(int v){return Math.max(Color.red(v),Math.max(Color.green(v),Color.blue(v)))<100;}
    boolean townGreen(int v){return Color.green(v)>115&&Color.green(v)>Color.red(v)*1.35&&Color.green(v)>Color.blue(v)*1.2;}
    ArrayList<String> readGlyphs(Bitmap frame,int left,int top,int width,int height,int slashFromEnd)throws Exception {
        return readGlyphs(frame,left,top,width,height,slashFromEnd,digits);
    }
    ArrayList<String> readGlyphs(Bitmap frame,int left,int top,int width,int height,int slashFromEnd,JSONObject alphabet)throws Exception {
        boolean[][] bits=new boolean[width][height];
        for(int x=0;x<width;x++)for(int y=0;y<height;y++)bits[x][y]=white(frame.getPixel(left+x,top+y));
        ArrayList<int[]> runs=new ArrayList<>();int start=-1;
        for(int x=0;x<=width;x++) {
            boolean on=false;if(x<width)for(int y=0;y<height;y++)on|=bits[x][y];
            if(on&&start<0)start=x;
            if(!on&&start>=0){if(x-start>=2)runs.add(new int[]{start,x});start=-1;}
        }
        ArrayList<String> result=new ArrayList<>();
        if(runs.size()>8)return result;
        for(int i=0;i<runs.size();i++) {
            if(slashFromEnd>0&&i==runs.size()-slashFromEnd){result.add("/");continue;}
            int[] r=runs.get(i);int minY=height,maxY=0;
            for(int x=r[0];x<r[1];x++)for(int y=0;y<height;y++)if(bits[x][y]){minY=Math.min(minY,y);maxY=Math.max(maxY,y+1);}
            if(minY>=maxY)return new ArrayList<>();
            StringBuilder glyph=new StringBuilder();
            for(int y=0;y<24;y++)for(int x=0;x<16;x++) {
                int sx=r[0]+Math.min(r[1]-r[0]-1,(int)((x+.5)*(r[1]-r[0])/16));
                int sy=minY+Math.min(maxY-minY-1,(int)((y+.5)*(maxY-minY)/24));
                glyph.append(bits[sx][sy]?'1':'0');
            }
            double best=1;String digit="";Iterator<String> it=alphabet.keys();
            while(it.hasNext()) {
                String k=it.next();JSONArray array=alphabet.getJSONArray(k);
                for(int j=0;j<array.length();j++) {
                    String t=array.getString(j);int diff=0;
                    for(int z=0;z<384;z++)if(t.charAt(z)!=glyph.charAt(z))diff++;
                    double score=diff/384.0;if(score<best){best=score;digit=k;}
                }
            }
            if(best>.22)return new ArrayList<>();result.add(digit);
        }
        return result;
    }
    int[] synthesisErrorClose(Bitmap frame){return synthesisDialog.locateClose(frame::getPixel);}
    public int townGold(Bitmap b)throws Exception {
        ArrayList<String> chars=readGlyphs(b,1780,25,190,60,0,townDigits);
        if(chars.isEmpty())return -1;
        StringBuilder value=new StringBuilder();for(String c:chars)value.append(c);
        return Integer.parseInt(value.toString());
    }
    public int[] warehouseCapacity(Bitmap b)throws Exception {
        ArrayList<String> chars=readGlyphs(b,470,1308,140,52,4,townDigits);
        if(chars.size()<5)return null;
        StringBuilder value=new StringBuilder();for(String c:chars)value.append(c);
        String text=value.toString();if(!text.endsWith("/160"))return null;
        int used=Integer.parseInt(text.substring(0,text.length()-4));
        return used<=160?new int[]{used,160}:null;
    }
    int fatigueAt(Bitmap frame,int left,int top,int width,int height)throws Exception {
        ArrayList<String> glyphs=readGlyphs(frame,left,top,width,height,4);
        if(glyphs.size()<5||glyphs.size()>8)return -1;
        StringBuilder text=new StringBuilder();for(String g:glyphs)text.append(g);
        String value=text.toString();if(!value.endsWith("/500"))return -1;
        int parsed=Integer.parseInt(value.substring(0,value.length()-4));return parsed<=1000?parsed:-1;
    }
    public int fatigue(Bitmap frame)throws Exception{return fatigueAt(frame,392,158,114,21);}
    public int recoveryFatigue(Bitmap frame)throws Exception{return fatigueAt(frame,1825,1009,140,33);}
    public int exchangeRemaining(Bitmap frame)throws Exception {
        double best=Double.POSITIVE_INFINITY,second=best;int value=-1;
        for(int n=0;n<=3;n++) {
            double e=error(frame,"exchanges"+n);
            if(e<best){second=best;best=e;value=n;}else if(e<second)second=e;
        }
        if(best<8&&second-best>2)return value;
        // This short number region excludes the surrounding Chinese lettering,
        // whose one-pixel placement changes otherwise defeat the raw templates.
        int found=-1;
        for(int[] region:new int[][]{{1716,928,54,34},{1718,928,48,34}}){
            ArrayList<String> chars=readGlyphs(frame,region[0],region[1],region[2],region[3],2);
            if(chars.size()!=3||!chars.get(1).equals("/")||!chars.get(2).equals("3"))continue;
            int parsed=Integer.parseInt(chars.get(0));if(parsed<0||parsed>3)continue;
            if(found>=0&&found!=parsed)return -1;found=parsed;
        }
        return found;
    }
    public boolean selectedRole(Bitmap frame,int role) {
        int cy=FlowPolicy.roleY(role);int hits=0;
        for(int y=cy-80;y<=cy+80;y+=2)for(int x=65;x<=615;x+=2) {
            if(x>82&&x<596&&y>cy-62&&y<cy+62)continue;
            int c=frame.getPixel(x,y);int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
            if(r>190&&g>165&&b<145&&r>=g)hits++;
        }
        return hits>100;
    }
}
