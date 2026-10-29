package com.eidolon.sc.mobile;
import android.content.*;
/** The wake performs a bounded local check off the receiver thread; network work remains in WorkManager. */
public final class ReachAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        Context c=context.getApplicationContext();PendingResult pending=goAsync();
        new Thread(()->{try{
            if(!c.getSharedPreferences("settings",0).getBoolean("reachEnabled",false))return;
            ReachPlanCheck.check(c,false);
            try(ChatStore s=new ChatStore(c)){if(!ReachPlanStore.due(s,System.currentTimeMillis()).isEmpty())ReachScheduler.generate(c);}
        }catch(Exception e){android.util.Log.e("EidolonReach","Wake check failed",e);}finally{pending.finish();}},"eidolon-reach-wake").start();
    }
}
