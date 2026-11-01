package com.eidolon.sc.mobile;
import android.content.*;
public final class CallIncomingReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){String id=i.getStringExtra("callId");if(id==null)return;try{CallIncoming.reject(c,id);}catch(Exception ignored){}}
}
