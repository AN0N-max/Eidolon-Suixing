package com.eidolon.sc.mobile;
import android.content.*;
/** Receipt only: do not spend the alarm's short exemption scanning every role. */
public final class ReachAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        Context c=context.getApplicationContext();if(!c.getSharedPreferences("settings",0).getBoolean("reachEnabled",false))return;
        ReachWake.received(c,"alarm",c.getSharedPreferences("settings",0).getLong("reachNextWake",0));ReachScheduler.refresh(c,false,"alarm");
    }
}
