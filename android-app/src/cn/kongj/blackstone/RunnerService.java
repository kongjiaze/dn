package cn.kongj.blackstone;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.media.*;import android.media.projection.*;import android.hardware.display.*;import android.util.DisplayMetrics;import android.view.*;import android.widget.*;import java.nio.*;import java.io.*;
public class RunnerService extends Service {
 Handler main=new Handler();HandlerThread captureThread;MediaProjection projection;ImageReader reader;VirtualDisplay display;WindowManager wm;LinearLayout overlay,expandedPanel;TextView status,compactHandle;Button pause,primary;WindowManager.LayoutParams overlayParams;boolean overlayCollapsed;
 static volatile RunnerService instance;final FlowControl control=new FlowControl();ScriptConfig config;
 SharedPreferences.OnSharedPreferenceChangeListener configListener;
 final Object lock=new Object();Bitmap latest;long frameAt;volatile boolean stopped=true,paused=false,destroyed=false;Thread worker;int width,height;String gamePackage="";Vision vision;long runStarted;File logFile;long lastLogged;int step;
 final java.util.concurrent.atomic.AtomicBoolean retryRequested=new java.util.concurrent.atomic.AtomicBoolean();volatile boolean waitingRetry;
 volatile String actionLabel="准备";int automaticRetries;volatile boolean loginRun;
 final CombatCompletion combatCompletion=new CombatCompletion();
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();MainActivity.upgradeCombatPreferences(getSharedPreferences("route",0));config=new ScriptConfig(this);instance=this;getSharedPreferences("progress",0).edit().clear().apply();configListener=(prefs,key)->resetToStart("配置已修改，下次从选角页重新开始");getSharedPreferences("route",0).registerOnSharedPreferenceChangeListener(configListener);config.prefs.registerOnSharedPreferenceChangeListener(configListener);NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("runner","黑石助手运行状态",NotificationManager.IMPORTANCE_LOW));Notification n=new Notification.Builder(this,"runner").setContentTitle("黑石日常助手").setContentText("点击小浮块展开，可随时暂停或停止").setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).build();startForeground(1,n);wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
 public int onStartCommand(Intent i,int flags,int id){if(projection!=null){if(i!=null&&i.getBooleanExtra("autoLogin",false))begin(true);else log("截图已开启，请使用现有悬浮控制");return START_NOT_STICKY;}try{vision=new Vision(this);DisplayMetrics dm=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);width=dm.widthPixels;height=dm.heightPixels;if(Math.abs((double)width/height-16.0/9)>.01)throw new Exception("请将模拟器设置为横屏16:9，例如2560×1440，并重新开启助手");
  projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(i.getIntExtra("code",0),(Intent)i.getParcelableExtra("capture"));captureThread=new HandlerThread("screens");captureThread.start();Handler h=new Handler(captureThread.getLooper());projection.registerCallback(new MediaProjection.Callback(){public void onStop(){if(!destroyed){log("截图授权已关闭");stopSelf();}}},main);
  reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);reader.setOnImageAvailableListener(r->{Image im=null;Bitmap raw=null,crop=null;try{im=r.acquireLatestImage();if(im==null||System.currentTimeMillis()-lastLogged<150)return;lastLogged=System.currentTimeMillis();Image.Plane p=im.getPlanes()[0];ByteBuffer buf=p.getBuffer();int padded=p.getRowStride()/p.getPixelStride();raw=Bitmap.createBitmap(padded,height,Bitmap.Config.ARGB_8888);raw.copyPixelsFromBuffer(buf);crop=Bitmap.createBitmap(raw,0,0,width,height);Bitmap normalized=Bitmap.createScaledBitmap(crop,2560,1440,true);synchronized(lock){if(latest!=null)latest.recycle();latest=normalized.copy(Bitmap.Config.ARGB_8888,false);frameAt=System.currentTimeMillis();observeBattleClear();}if(normalized!=crop&&normalized!=raw)normalized.recycle();}catch(Exception ex){}finally{if(crop!=null&&crop!=raw)crop.recycle();if(raw!=null)raw.recycle();if(im!=null)im.close();}},h);
  display=projection.createVirtualDisplay("blackstone",width,height,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,h);createOverlay();log("已就绪：请返回游戏角色选择页");if(i.getBooleanExtra("autoLogin",false))begin(true);
 }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();stopSelf();}return START_NOT_STICKY;}
 void createOverlay(){overlay=new LinearLayout(this);overlay.setOrientation(1);overlay.setBackgroundColor(0xdd202630);expandedPanel=new LinearLayout(this);expandedPanel.setOrientation(1);expandedPanel.setPadding(6,6,6,6);overlay.addView(expandedPanel,new LinearLayout.LayoutParams(-1,-2));status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(11);expandedPanel.addView(status);LinearLayout row=new LinearLayout(this);expandedPanel.addView(row);primary=new Button(this);primary.setText("开始");row.addView(primary);pause=new Button(this);pause.setText("暂停");row.addView(pause);Button stop=new Button(this);stop.setText("停止");row.addView(stop);Button collapse=new Button(this);collapse.setText("收起");collapse.setMinWidth(0);collapse.setMinimumWidth(0);collapse.setPadding(0,0,0,0);row.addView(collapse,new LinearLayout.LayoutParams(dp(48),-2));collapse.setOnClickListener(v->setOverlayCollapsed(true));
 compactHandle=new TextView(this);compactHandle.setTextColor(Color.WHITE);compactHandle.setTextSize(10);compactHandle.setGravity(Gravity.CENTER);compactHandle.setVisibility(View.GONE);compactHandle.setOnClickListener(v->setOverlayCollapsed(false));overlay.addView(compactHandle,new LinearLayout.LayoutParams(-1,dp(48)));
 primary.setOnClickListener(v->{if(control.state==FlowControl.READY){begin(false);return;}if(control.resume()){boolean retry=waitingRetry;paused=false;if(retry)retryRequested.set(true);setOverlayCollapsed(true);renderControl();log(retry?"继续：重试失败的当前步骤":"继续运行");}});
 pause.setOnClickListener(v->{if(control.state!=FlowControl.RUNNING)return;control.pause();paused=true;renderControl();log("手动暂停，当前手势结束后暂停；点继续恢复");});
 stop.setOnClickListener(v->resetToStart("已停止并清除进度，请回选角页重新开始"));
 WindowManager.LayoutParams p=new WindowManager.LayoutParams(750,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);overlayParams=p;p.gravity=Gravity.TOP|Gravity.LEFT;SharedPreferences position=getSharedPreferences("overlay",0);p.x=position.getInt("x",720);p.y=position.getInt("y",18);wm.addView(overlay,p);status.setContentDescription("运行状态，按住这里拖动悬浮窗");enableOverlayDrag(status,p);enableOverlayDrag(compactHandle,p);
 overlay.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{if(!destroyed&&clampOverlay(p)){wm.updateViewLayout(overlay,p);saveOverlayPosition(p);}});renderControl();}
 int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 void setOverlayCollapsed(boolean collapsed){
  if(Looper.myLooper()!=main.getLooper()){main.post(()->setOverlayCollapsed(collapsed));return;}
  if(destroyed||overlay==null||overlayParams==null)return;
  overlayCollapsed=collapsed;expandedPanel.setVisibility(collapsed?View.GONE:View.VISIBLE);compactHandle.setVisibility(collapsed?View.VISIBLE:View.GONE);overlayParams.width=collapsed?dp(48):750;if(clampOverlay(overlayParams))saveOverlayPosition(overlayParams);wm.updateViewLayout(overlay,overlayParams);
 }
 void enableOverlayDrag(View handle,WindowManager.LayoutParams p){
  int slop=ViewConfiguration.get(this).getScaledTouchSlop();
  handle.setOnTouchListener(new View.OnTouchListener(){
   float downX,downY;int startX,startY;boolean dragging;
   public boolean onTouch(View view,MotionEvent event){
    switch(event.getActionMasked()){
     case MotionEvent.ACTION_DOWN:downX=event.getRawX();downY=event.getRawY();startX=p.x;startY=p.y;dragging=false;return true;
     case MotionEvent.ACTION_MOVE:
      float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
      if(!dragging&&dx*dx+dy*dy>slop*slop)dragging=true;
      if(dragging&&!destroyed){p.x=startX+Math.round(dx);p.y=startY+Math.round(dy);clampOverlay(p);wm.updateViewLayout(overlay,p);}return true;
     case MotionEvent.ACTION_UP:
      if(dragging)saveOverlayPosition(p);else view.performClick();dragging=false;return true;
     case MotionEvent.ACTION_CANCEL:
      if(dragging)saveOverlayPosition(p);dragging=false;return true;
     default:return true;
    }
   }
  });
 }
 boolean clampOverlay(WindowManager.LayoutParams p){
  DisplayMetrics metrics=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(metrics);
  int windowWidth=p.width>0?p.width:overlay.getWidth();
  int windowHeight=overlayCollapsed?dp(48):overlay.getHeight();
  int x=Math.max(0,Math.min(p.x,Math.max(0,metrics.widthPixels-windowWidth)));
  int y=Math.max(0,Math.min(p.y,Math.max(0,metrics.heightPixels-windowHeight)));
  boolean changed=x!=p.x||y!=p.y;p.x=x;p.y=y;return changed;
 }
 void saveOverlayPosition(WindowManager.LayoutParams p){getSharedPreferences("overlay",0).edit().putInt("x",p.x).putInt("y",p.y).apply();}
 void renderControl(){main.post(()->{if(primary!=null){primary.setText(control.state==FlowControl.READY?"开始":"继续");primary.setEnabled(control.state==FlowControl.READY||control.state==FlowControl.PAUSED||(control.state==FlowControl.ERROR&&waitingRetry));}if(pause!=null)pause.setEnabled(control.state==FlowControl.RUNNING);if(compactHandle!=null){String state=control.state==FlowControl.RUNNING?"运行":control.state==FlowControl.PAUSED?"暂停":control.state==FlowControl.ERROR?"异常":"就绪";compactHandle.setText("助手\n"+state);compactHandle.setBackgroundColor(control.state==FlowControl.RUNNING?0xdd246044:control.state==FlowControl.ERROR?0xdd8c382c:control.state==FlowControl.PAUSED?0xdd806025:0xdd202630);compactHandle.setContentDescription("助手"+state+"，点击展开，按住拖动");}});}
 void resetToStart(String message){loginRun=false;stopped=true;resetBattleClear();paused=false;waitingRetry=false;retryRequested.set(false);control.reset();getSharedPreferences("progress",0).edit().clear().apply();step=0;actionLabel="准备";setOverlayCollapsed(false);renderControl();log(message);}
 void begin(boolean autoLogin){if(worker!=null&&worker.isAlive()){log("上一轮正在结束，请稍后点开始");return;}resetBattleClear();stopped=false;paused=false;waitingRetry=false;retryRequested.set(false);gamePackage="";loginRun=autoLogin;step=0;actionLabel="准备";control.start();setOverlayCollapsed(true);renderControl();getSharedPreferences("progress",0).edit().clear().apply();automaticRetries=0;runStarted=System.currentTimeMillis();File dir=new File(getExternalFilesDir(null),"runs/"+runStarted);dir.mkdirs();logFile=new File(dir,"steps.log");worker=new Thread(()->{try{runLoop();}catch(Exception e){if(!stopped)failure(e.getMessage());}finally{loginRun=false;stopped=true;paused=false;waitingRetry=false;control.reset();getSharedPreferences("progress",0).edit().clear().apply();setOverlayCollapsed(false);renderControl();}},"flow");worker.start();}
 void retrySucceeded(){control.success();automaticRetries=0;}
 void autoRetry(String message)throws Exception {
  autoRetry(message,param("retryGap",2000));
 }
 void autoRetry(String message,long gap)throws Exception {
  checkControl();int limit=param("maxRetries",20);
  if(!control.failed(message,limit)){renderControl();throw new Exception("自动重试"+limit+"次仍未成功："+message);}
  automaticRetries=control.failures();log("未到目标，自动重试 "+automaticRetries+"/"+limit+"："+message);
  if(automaticRetries==1)snapshot("auto-retry-"+step);
  long remaining=gap;while(remaining>0){checkControl();long t=System.currentTimeMillis();Thread.sleep(Math.min(100,remaining));remaining-=System.currentTimeMillis()-t;}
 }
 void awaitRetry(String message)throws Exception {
  if(stopped||destroyed)throw new Exception("用户停止");
  waitingRetry=true;paused=true;control.error();setOverlayCollapsed(false);renderControl();log("异常暂停："+message+"；点继续重试当前步骤");saveFailureFrame();
  try{while(!stopped&&!destroyed){if(retryRequested.getAndSet(false)){paused=false;control.resume();retrySucceeded();renderControl();return;}Thread.sleep(100);}throw new Exception("用户停止");}finally{waitingRetry=false;}
 }
 RolePlan rolePlan(int slot){return config.role(slot);}
 int nextRole(int after){return config.next(after);}
 boolean manualLogin(){return !LoginCredentials.configured(this);}
 int param(String k,int d){if(k.equals("startRole"))return config.first();if(k.equals("endRole"))return config.last();return getSharedPreferences("route",0).getInt(k,d);}
 Bitmap frame()throws Exception{synchronized(lock){if(latest==null||System.currentTimeMillis()-frameAt>2500)throw new Exception("截图尚未就绪或已停止刷新");return latest.copy(Bitmap.Config.ARGB_8888,false);}}
 boolean match(String key)throws Exception{Bitmap b=frame();try{return vision.matches(b,key);}finally{b.recycle();}}
 boolean battleReady()throws Exception {
  int stable=0;for(int i=0;i<12;i++){checkpoint();Bitmap b=frame();boolean found;try{found=vision.inBattle(b);}finally{b.recycle();}
   stable=found?stable+1:0;if(stable>=2)return true;waitMs(250);}return false;
 }
 void checkControl()throws Exception{while(paused&&!stopped&&!destroyed)Thread.sleep(100);if(stopped||destroyed)throw new Exception("用户停止");}
 void checkpoint()throws Exception{checkControl();if(!gamePackage.isEmpty()){TouchService t=TouchService.instance;if(t==null||!gamePackage.equals(t.activePackage()))throw new Exception("游戏已离开前台，请返回游戏后点继续");}}
 void waitMs(long duration)throws Exception{long remaining=duration;while(remaining>0){checkpoint();long t=System.currentTimeMillis();Thread.sleep(Math.min(100,remaining));remaining-=System.currentTimeMillis()-t;}}
 boolean waitMatch(String key,long timeout)throws Exception{long elapsed=0;while(elapsed<timeout){checkpoint();if(match(key))return true;waitMs(150);elapsed+=150;}return false;}
 void gesture(String label,int x,int y,int ex,int ey,long duration)throws Exception{gestureWithGap(label,x,y,ex,ey,duration,param("stepGap",1500));}
 void gestureWithGap(String label,int x,int y,int ex,int ey,long duration,long gap)throws Exception {
  checkpoint();if(!label.equals(actionLabel)){step++;actionLabel=label;}
  getSharedPreferences("progress",0).edit().putInt("actionStep",step).putString("actionLabel",actionLabel).apply();
  log("执行操作");TouchService t=TouchService.instance;
  if(t==null||!t.stroke(x*width/2560f,y*height/1440f,ex*width/2560f,ey*height/1440f,duration))throw new Exception("手势取消或无障碍服务不可用");
  log("等待 "+gap+" 毫秒后检查画面");waitMs(gap);log("间隔结束，检查操作结果");
 }
 void tap(String label,int x,int y)throws Exception{gesture(label,x,y,x,y,70);}
 void fastTap(String label,int x,int y)throws Exception{gestureWithGap(label,x,y,x,y,70,200);}
 void combatTap(String label,int x,int y)throws Exception{gestureWithGap(label,x,y,x,y,70,param("combatGap",600));}
 void combatTap(String label,int x,int y,int gap)throws Exception{gestureWithGap(label,x,y,x,y,180,gap);}
 void batchTap(String label,int x,int y)throws Exception{gestureWithGap(label,x,y,x,y,70,Math.max(600,param("craftGap",600)));}
 void doubleTap(String label,int x,int y)throws Exception{gestureWithGap(label,x,y,x,y,70,80);gestureWithGap(label,x,y,x,y,70,param("warehouseGap",700));}
 boolean threeMaterials()throws Exception{int[] ui=synthesisUi();return ui[0]==0&&ui[1]==3;}
 void resetBattleClear(){synchronized(lock){combatCompletion.reset();}}
 void observeBattleClear(){
  if(!stopped&&combatCompletion.armed&&!combatCompletion.seen)
   combatCompletion.observe(vision.matches(latest,"battleCleared"),frameAt);
 }
 void watchBattleClear()throws Exception{checkpoint();synchronized(lock){
  if(latest==null||System.currentTimeMillis()-frameAt>2500)throw new Exception("清场检查：截图尚未就绪或已停止刷新");
  combatCompletion.arm();observeBattleClear();
 }}
 boolean battleClearSeen()throws Exception{checkpoint();synchronized(lock){return combatCompletion.seen;}}
 int[] synthesisUi()throws Exception{checkpoint();synchronized(lock){
  if(latest==null||System.currentTimeMillis()-frameAt>2500)throw new Exception("合成检查：截图尚未就绪或已停止刷新");
  if(vision.matches(latest,"synthesisResult"))return new int[]{1,-1,0,0};
  int[] close=vision.synthesisErrorClose(latest);if(close!=null)return new int[]{2,-1,close[0],close[1]};
  if(vision.matches(latest,"emblemPage")&&vision.matches(latest,"gradeB")&&TownInventory.checked(latest::getPixel,620,1340)&&!TownInventory.checked(latest::getPixel,2053,1218))
   return new int[]{0,TownInventory.materials(latest::getPixel),0,0};
  return new int[]{-1,-1,0,0};
 }}
 boolean skillCooling(int x,int y)throws Exception{checkpoint();synchronized(lock){if(latest==null||System.currentTimeMillis()-frameAt>2500)throw new Exception("技能检查：截图尚未就绪或已停止刷新");return vision.skillCooling(latest,x,y);}}
 int ultimateState()throws Exception{checkpoint();synchronized(lock){
  if(latest==null||System.currentTimeMillis()-frameAt>2500)throw new Exception("大招检查：截图尚未就绪或已停止刷新");
  if(!vision.inBattle(latest)&&!vision.matches(latest,"retry"))return SkillCooldown.UNKNOWN;
  if(vision.matches(latest,"agree")||vision.matches(latest,"menu")||vision.matches(latest,"settingsReturn"))return SkillCooldown.UNKNOWN;
  return SkillCooldown.ultimateState(latest::getPixel,1720,1290);
 }}
 boolean combatFrameReady()throws Exception{checkpoint();synchronized(lock){if(latest==null||System.currentTimeMillis()-frameAt>2500)throw new Exception("技能检查：截图尚未就绪或已停止刷新");return vision.inBattle(latest);}}
 void move(String label,int ex,int ey,int ms)throws Exception{
  checkpoint();if(!label.equals(actionLabel)){step++;actionLabel=label;}
  getSharedPreferences("progress",0).edit().putInt("actionStep",step).putString("actionLabel",actionLabel).apply();
  log("执行移动，剩余 "+ms+" 毫秒");TouchService touch=TouchService.instance;
  if(touch==null)throw new Exception("无障碍服务不可用，尚未发送移动");
  StrokeOutcome outcome=touch.strokeDetailed(300*width/2560f,1148*height/1440f,ex*width/2560f,ey*height/1440f,ms);
  if(!outcome.completed){
   if(outcome.accepted)releaseJoystick(touch);
   throw new MovementInterrupted(ms,outcome.elapsedMs,(outcome.uncertain?"移动回调未确认":"移动手势被取消")+"，已记录 "+outcome.elapsedMs+" 毫秒；重试只补剩余移动");
  }
  try{int gap=param("stepGap",1500);log("移动已完成，等待 "+gap+" 毫秒后检查画面");waitMs(gap);waitMs(300);log("间隔结束，检查操作结果");}
  catch(Exception e){throw new MovementInterrupted(ms,ms,"移动已完成，后续检查中断："+e.getMessage());}
 }
 void releaseJoystick(TouchService touch){
  try{if(!destroyed&&!gamePackage.isEmpty()&&gamePackage.equals(touch.activePackage())){
   if(touch.stroke(300*width/2560f,1148*height/1440f,300*width/2560f,1148*height/1440f,70))log("已释放方向键，保留移动进度");
   else log("方向键释放未确认，保留移动进度");
  }}catch(Exception e){log("方向键释放未确认，重试前会重查战斗画面");}
 }
 void launchGame()throws Exception {
  checkpoint();java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);boolean[] ok={false};
  step++;actionLabel="打开龙之谷世界";log("启动游戏，按勾选的角色执行");
  main.post(()->{try{Intent intent=getPackageManager().getLaunchIntentForPackage("com.dn.lzgsj3");if(intent!=null){intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);startActivity(intent);ok[0]=true;}}finally{done.countDown();}});
  if(!done.await(5000,java.util.concurrent.TimeUnit.MILLISECONDS)||!ok[0])throw new Exception("未能打开龙之谷世界，请确认游戏已安装");
  waitMs(1500);
 }
 void waitGameForeground()throws Exception {
  for(int i=0;i<120;i++){checkControl();TouchService t=TouchService.instance;if(t!=null&&"com.dn.lzgsj3".equals(t.activePackage())){gamePackage="com.dn.lzgsj3";return;}Thread.sleep(250);}
  throw new Exception("游戏尚未处于前台，请返回游戏后继续");
 }
 void fillConfiguredPassword()throws Exception {
  checkpoint();String password=LoginCredentials.load(this);
  if(!"填写本机登录密码".equals(actionLabel)){step++;actionLabel="填写本机登录密码";}log("自动填写密码");
  TouchService t=TouchService.instance;
  if(t==null)throw new Exception("无障碍服务未开启");
  boolean ok=t.fillLoginPassword(password);
  if(!ok){tap("聚焦登录密码框",1230,635);waitMs(300);ok=t.fillLoginPassword(password)||LoginKeyboard.fill(password);}
  password=null;
  if(!ok)throw new LoginFlow.LoginRejected("密码框无法自动填写，请手动登录后点继续");
  waitMs(800);
 }
 void fillConfiguredLogin()throws Exception {
  checkpoint();String phone=LoginCredentials.phone(this);TouchService touch=TouchService.instance;
  if(touch==null)throw new Exception("无障碍服务未开启");
  if(phone.isEmpty())throw new LoginFlow.LoginRejected("未配置手机号，请在设置中补充账号，或手动登录后点继续");
  log("填写已配置的登录账号");boolean ok=touch.fillLoginPhone(phone);
  if(!ok){tap("聚焦手机号输入框",1300,540);waitMs(300);ok=touch.fillLoginPhone(phone)||LoginKeyboard.fillPhone(phone);}
  phone=null;if(!ok)throw new LoginFlow.LoginRejected("手机号无法自动填写，请手动登录后点继续");
  waitMs(300);fillConfiguredPassword();
 }
 void runLoop()throws Exception{if(loginRun)new LoginFlow(this).run();new GameFlow(this).run();}
 void snapshot(String stage){try{Bitmap b=frame();Bitmap small=Bitmap.createScaledBitmap(b,1280,720,true);try(FileOutputStream f=new FileOutputStream(new File(logFile.getParentFile(),stage+"-"+System.currentTimeMillis()+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,80,f);}finally{if(small!=b)small.recycle();b.recycle();}}catch(Exception e){log("阶段截图未保存："+stage);}}
 void log(String s){String display="第"+step+"步："+actionLabel+"\n"+s;String line=new java.text.SimpleDateFormat("HH:mm:ss.SSS",java.util.Locale.ROOT).format(new java.util.Date())+" "+display.replace('\n',' ');main.post(()->{if(status!=null)status.setText(display);});if(logFile!=null)try(FileOutputStream f=new FileOutputStream(logFile,true)){f.write((line+"\n").getBytes("UTF-8"));}catch(Exception e){} }
 void failure(String message){log("已停止："+message);saveFailureFrame();}
 void saveFailureFrame(){try{Bitmap b=frame();File out=new File(logFile.getParentFile(),"stop.png");try(FileOutputStream f=new FileOutputStream(out)){b.compress(Bitmap.CompressFormat.PNG,100,f);}finally{b.recycle();}}catch(Exception e){} }
 public void onDestroy(){instance=null;getSharedPreferences("route",0).unregisterOnSharedPreferenceChangeListener(configListener);if(config!=null)config.prefs.unregisterOnSharedPreferenceChangeListener(configListener);getSharedPreferences("progress",0).edit().clear().apply();control.reset();destroyed=true;stopped=true;paused=false;if(worker!=null)worker.interrupt();if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(captureThread!=null)captureThread.quitSafely();if(overlay!=null)try{wm.removeView(overlay);}catch(Exception e){}synchronized(lock){if(latest!=null){latest.recycle();latest=null;}}super.onDestroy();}
}
