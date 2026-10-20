package cn.kongj.blackstone;
import android.accessibilityservice.*;import android.graphics.Path;import android.os.*;import android.view.accessibility.*;import java.util.concurrent.*;
public class TouchService extends AccessibilityService {
 public static volatile TouchService instance; public volatile String foreground="";
 protected void onServiceConnected(){instance=this;}
 public void onAccessibilityEvent(AccessibilityEvent e){if(e.getPackageName()!=null)foreground=e.getPackageName().toString();}
 public void onInterrupt(){} public void onDestroy(){instance=null;super.onDestroy();}
 public String activePackage(){AccessibilityNodeInfo n=getRootInActiveWindow();if(n==null)return foreground;String p=n.getPackageName()==null?foreground:n.getPackageName().toString();n.recycle();return p;}
 public boolean stroke(float x,float y,float ex,float ey,long ms)throws Exception{return strokeDetailed(x,y,ex,ey,ms).completed;}
 StrokeOutcome strokeDetailed(float x,float y,float ex,float ey,long ms)throws Exception {
  CountDownLatch latch=new CountDownLatch(1);Handler handler=new Handler(getMainLooper());
  java.util.concurrent.atomic.AtomicLong started=new java.util.concurrent.atomic.AtomicLong(-1);
  java.util.concurrent.atomic.AtomicBoolean accepted=new java.util.concurrent.atomic.AtomicBoolean(),expired=new java.util.concurrent.atomic.AtomicBoolean();
  java.util.concurrent.atomic.AtomicReference<StrokeOutcome> result=new java.util.concurrent.atomic.AtomicReference<>();Object dispatchLock=new Object();
  Runnable task=()->{
   synchronized(dispatchLock){
   if(expired.get())return;
   try{
    Path path=new Path();path.moveTo(x,y);if(x!=ex||y!=ey)path.lineTo(ex,ey);
    GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,ms)).build();started.set(SystemClock.uptimeMillis());
    boolean dispatched=dispatchGesture(g,new GestureResultCallback(){
     public void onCompleted(GestureDescription d){result.set(new StrokeOutcome(true,true,false,ms,ms));latch.countDown();}
     public void onCancelled(GestureDescription d){result.set(new StrokeOutcome(true,false,false,ms,SystemClock.uptimeMillis()-started.get()));latch.countDown();}
    },null);
    accepted.set(dispatched);
    if(!dispatched){result.compareAndSet(null,new StrokeOutcome(false,false,false,ms,0));latch.countDown();}
   }catch(RuntimeException e){result.compareAndSet(null,new StrokeOutcome(false,false,false,ms,0));latch.countDown();}
   }
  };
  handler.post(task);
  try{latch.await(ms+2500,TimeUnit.MILLISECONDS);}catch(InterruptedException e){expired.set(true);handler.removeCallbacks(task);throw e;}
  StrokeOutcome outcome=result.get();if(outcome!=null)return outcome;
  synchronized(dispatchLock){expired.set(true);handler.removeCallbacks(task);outcome=result.get();if(outcome!=null)return outcome;
  // An accepted stroke with no callback may already be complete. Count elapsed
  // input conservatively so a callback timeout cannot replay the whole route.
  return new StrokeOutcome(accepted.get(),false,true,ms,started.get()<0?0:SystemClock.uptimeMillis()-started.get());}
 }
 public boolean fillLoginPassword(String password)throws Exception {
  CountDownLatch done=new CountDownLatch(1);boolean[] result={false};
  new Handler(getMainLooper()).post(()->{try {
   for(AccessibilityWindowInfo window:getWindows()) {
    AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
    try{if(root.getPackageName()!=null&&"com.dn.lzgsj3".contentEquals(root.getPackageName())&&setPassword(root,password)){result[0]=true;break;}}finally{root.recycle();}
   }
   if(!result[0]){AccessibilityNodeInfo root=getRootInActiveWindow();if(root!=null)try{if(root.getPackageName()!=null&&"com.dn.lzgsj3".contentEquals(root.getPackageName()))result[0]=setPassword(root,password);}finally{root.recycle();}}
   if(result[0])for(AccessibilityWindowInfo window:getWindows())if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD){performGlobalAction(GLOBAL_ACTION_BACK);break;}
  }finally{done.countDown();}});
  return done.await(3000,TimeUnit.MILLISECONDS)&&result[0];
 }
 private boolean setPassword(AccessibilityNodeInfo node,String password) {
  if(node.isEditable()&&node.isPassword()&&node.isEnabled()) {
   android.os.Bundle args=new android.os.Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,password);
   if(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))return true;
  }
  for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)try{if(setPassword(child,password))return true;}finally{child.recycle();}}
  return false;
 }
 public boolean fillLoginPhone(String phone)throws Exception {
  CountDownLatch done=new CountDownLatch(1);boolean[] result={false};
  new Handler(getMainLooper()).post(()->{try{for(AccessibilityWindowInfo window:getWindows()){
   AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
   try{if(root.getPackageName()!=null&&"com.dn.lzgsj3".contentEquals(root.getPackageName())&&setPhone(root,phone)){result[0]=true;break;}}finally{root.recycle();}
  }if(!result[0]){AccessibilityNodeInfo root=getRootInActiveWindow();if(root!=null)try{if(root.getPackageName()!=null&&"com.dn.lzgsj3".contentEquals(root.getPackageName()))result[0]=setPhone(root,phone);}finally{root.recycle();}}if(result[0])for(AccessibilityWindowInfo window:getWindows())if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD){performGlobalAction(GLOBAL_ACTION_BACK);break;}}finally{done.countDown();}});
  return done.await(3000,TimeUnit.MILLISECONDS)&&result[0];
 }
 private boolean setPhone(AccessibilityNodeInfo node,String phone){
  android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);
  int screenHeight=getResources().getDisplayMetrics().heightPixels;
  if(node.isEditable()&&!node.isPassword()&&node.isEnabled()&&bounds.centerY()>screenHeight*.30&&bounds.centerY()<screenHeight*.43){
   Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,phone);
   if(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))return true;
  }
  for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)try{if(setPhone(child,phone))return true;}finally{child.recycle();}}return false;
 }
}
