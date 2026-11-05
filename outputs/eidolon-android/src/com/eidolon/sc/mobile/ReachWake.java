package com.eidolon.sc.mobile;
import android.content.*;import android.os.*;import org.json.*;

/** Metadata only. The alarm receipt is distinct from planner/transport execution. */
final class ReachWake {
 static long preserveEarlier(long now,long proposed,long pending){return pending>now&&pending<proposed?pending:proposed;}
 static void received(Context c,String source,long expected){long now=System.currentTimeMillis();c.getSharedPreferences("settings",0).edit().putLong("reachWakeReceivedAt",now).putLong("reachWakeExpectedAt",expected).putString("reachWakeSource",source).apply();}
 static JSONObject snapshot(Context c)throws Exception{
  SharedPreferences p=c.getSharedPreferences("settings",0);PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);android.app.ActivityManager am=(android.app.ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
  return new JSONObject().put("receivedAt",p.getLong("reachWakeReceivedAt",0)).put("expectedAt",p.getLong("reachWakeExpectedAt",0)).put("source",p.getString("reachWakeSource",""))
   .put("plannerAt",p.getLong("reachPlannerStartedAt",0)).put("queuedAt",p.getLong("reachGenerateQueuedAt",0)).put("startedAt",p.getLong("reachGenerateStartedAt",0)).put("stoppedAt",p.getLong("reachGenerateStoppedAt",0)).put("foregroundAt",p.getLong("reachForegroundAt",0))
   .put("idle",power!=null&&power.isDeviceIdleMode()).put("powerSave",power!=null&&power.isPowerSaveMode()).put("batteryExempt",power!=null&&power.isIgnoringBatteryOptimizations(c.getPackageName())).put("backgroundRestricted",Build.VERSION.SDK_INT>=28&&am!=null&&am.isBackgroundRestricted());
 }
 private ReachWake(){}
}
