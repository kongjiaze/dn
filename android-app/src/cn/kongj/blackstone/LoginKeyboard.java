package cn.kongj.blackstone;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Optional local input method when the game's login field hides its accessibility nodes. */
public class LoginKeyboard extends InputMethodService {
    static volatile LoginKeyboard instance;
    public void onCreate(){super.onCreate();instance=this;}
    public void onDestroy(){instance=null;super.onDestroy();}
    public View onCreateInputView(){return null;}
    static boolean fill(String password)throws Exception {
        return fillField(password,true);
    }
    static boolean fillPhone(String phone)throws Exception {return fillField(phone,false);}
    static boolean fillField(String password,boolean secret)throws Exception {
        LoginKeyboard service=instance;if(service==null)return false;
        CountDownLatch done=new CountDownLatch(1);boolean[] result={false};
        new Handler(Looper.getMainLooper()).post(()->{try {
            InputConnection input=service.getCurrentInputConnection();EditorInfo info=service.getCurrentInputEditorInfo();
            if(input==null||info==null||!"com.dn.lzgsj3".equals(info.packageName))return;
            int variation=info.inputType&android.text.InputType.TYPE_MASK_VARIATION;
            boolean passwordField=variation==android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD||variation==android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
            if(secret!=passwordField)return;
            input.beginBatchEdit();
            try{result[0]=input.deleteSurroundingText(4096,4096)&&input.commitText(password,1);}finally{input.endBatchEdit();}
            service.requestHideSelf(0);
        }finally{done.countDown();}});
        return done.await(3000,TimeUnit.MILLISECONDS)&&result[0];
    }
}
