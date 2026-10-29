package com.eidolon.sc.mobile;

import android.app.*;
import android.content.*;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** One inexact wakeup plus unique durable work. No exact-alarm permission. */
final class ReachScheduler {
    static final String PLAN="eidolon-reach-plan",GENERATE="eidolon-reach-generate";
    static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,1283,new Intent(c,ReachAlarmReceiver.class).setAction("com.eidolon.sc.mobile.REACH_WAKE"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void refresh(Context c){refresh(c,false);}
    static void refresh(Context c,boolean changed){
        try{((android.app.job.JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(283);if(!c.getSharedPreferences("settings",0).getBoolean("reachEnabled",false)){cancel(c);return;}
            // A settings mutation must not get lost if an older planner is just
            // finishing. Routine checks coalesce; mutations append one fresh check.
            WorkManager.getInstance(c).enqueueUniqueWork(PLAN,changed?ExistingWorkPolicy.APPEND_OR_REPLACE:ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(ReachPlanWorker.class).build());
        }catch(RuntimeException e){ReachOut.status(c.getSharedPreferences("settings",0),"计划任务入队失败，请重开应用或重新保存搭话设置");android.util.Log.e("EidolonReach","Plan enqueue failed",e);}
    }
    static void generate(Context c){WorkManager.getInstance(c).enqueueUniqueWork(GENERATE,ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(ReachWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build());}
    static void schedule(Context c,ChatStore s){schedule(c,s,false);}
    static void schedule(Context c,ChatStore s,boolean drain){
        if(!c.getSharedPreferences("settings",0).getBoolean("reachEnabled",false)){cancel(c);return;}
        long now=System.currentTimeMillis(),at=ReachPlanCheckRules.next(ReachConfig.read(c.getSharedPreferences("settings",0)),now,ReachPlanStore.next(s));if(drain&&!ReachPlanStore.due(s,now).isEmpty())at=Math.min(at,now+30000);
        try{((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,alarm(c));c.getSharedPreferences("settings",0).edit().putLong("reachNextWake",at).putString("reachWakeError","").apply();}
        catch(RuntimeException e){c.getSharedPreferences("settings",0).edit().remove("reachNextWake").putString("reachWakeError","系统未接受下一次唤醒，返回应用或刷新计划可重新检查").apply();ReachOut.status(c.getSharedPreferences("settings",0),"系统未接受搭话唤醒；已保留计划，可重新保存设置恢复");android.util.Log.e("EidolonReach","Inexact alarm failed",e);}
    }
    static void cancel(Context c){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(alarm(c));WorkManager.getInstance(c).cancelUniqueWork(PLAN);WorkManager.getInstance(c).cancelUniqueWork(GENERATE);c.getSharedPreferences("settings",0).edit().remove("reachNextWake").remove("reachWakeError").apply();ReachOut.cancel();}
    private ReachScheduler(){}
}
