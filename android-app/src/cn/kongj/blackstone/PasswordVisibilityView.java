package cn.kongj.blackstone;

import android.content.Context;
import android.graphics.*;
import android.text.method.TransformationMethod;
import android.view.View;
import android.widget.EditText;

/** Eye toggle keeps the editable value intact while masking every displayed character. */
final class PasswordVisibilityView extends View {
 final EditText field;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);boolean revealed;
 static final TransformationMethod MASK=new TransformationMethod(){
  public CharSequence getTransformation(final CharSequence source,View view){return new CharSequence(){
   public int length(){return source.length();}public char charAt(int index){return '*';}
   public CharSequence subSequence(int start,int end){StringBuilder b=new StringBuilder();for(int i=start;i<end;i++)b.append('*');return b.toString();}
   public String toString(){return subSequence(0,length()).toString();}
  };}
  public void onFocusChanged(View view,CharSequence text,boolean focused,int direction,Rect previous){}
 };
 PasswordVisibilityView(Context context,EditText field){
  super(context);this.field=field;setClickable(true);setFocusable(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
  setOnClickListener(v->setRevealed(!revealed));setRevealed(false);
 }
 void hidePassword(){setRevealed(false);}
 void setRevealed(boolean value){
  int start=field.getSelectionStart(),end=field.getSelectionEnd();revealed=value;field.setTransformationMethod(value?null:MASK);
  if(start>=0&&end>=0)field.setSelection(Math.min(start,field.length()),Math.min(end,field.length()));
  setContentDescription(value?"隐藏密码":"显示密码");invalidate();
 }
 protected void onDraw(Canvas canvas){
  super.onDraw(canvas);canvas.save();canvas.translate((getWidth()-getHeight())/2f,0);canvas.scale(getHeight()/24f,getHeight()/24f);
  paint.setColor(0xff657785);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.3f);
  Path eye=new Path();eye.moveTo(5,12);eye.cubicTo(8,6,16,6,19,12);eye.cubicTo(16,18,8,18,5,12);canvas.drawPath(eye,paint);canvas.drawCircle(12,12,2.4f,paint);
  if(revealed)canvas.drawLine(5,5,19,19,paint);canvas.restore();
 }
}
