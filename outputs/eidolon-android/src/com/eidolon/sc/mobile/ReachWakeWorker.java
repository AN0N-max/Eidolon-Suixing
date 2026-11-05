package com.eidolon.sc.mobile;
import android.content.Context;import androidx.work.*;
/** Short durable dispatch only; model requests remain in ReachWorker behind its existing claim. */
public final class ReachWakeWorker extends Worker {
 public ReachWakeWorker(Context c,WorkerParameters p){super(c,p);}
 @Override public Result doWork(){Context c=getApplicationContext();if(!c.getSharedPreferences("settings",0).getBoolean("reachEnabled",false))return Result.success();String source=getInputData().getString("reachSource");ReachWake.received(c,"watchdog".equals(source)?"watchdog":"timer",getInputData().getLong("reachExpected",0));ReachScheduler.refresh(c,false,"durable");return Result.success();}
}
