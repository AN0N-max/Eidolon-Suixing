package com.eidolon.sc.mobile;

import android.app.*;
import android.content.*;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Inexact alarm + persistent timer/watchdog. All dispatches share the original durable claim. */
final class ReachScheduler {
    static final String PLAN="eidolon-reach-plan",GENERATE="eidolon-reach-generate";
    static final String TIMER="eidolon-reach-timer",WATCHDOG="eidolon-reach-watchdog";
    static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,1283,new Intent(c,ReachAlarmReceiver.class).setAction("com.eidolon.sc.mobile.REACH_WAKE"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void refresh(Context c){refresh(c,false);}
    static void refresh(Context c,boolean changed){
        refresh(c,changed,"refresh");
    }
    static void refresh(Context c,boolean changed,String source){
        try{((android.app.job.JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(283);if(!c.getSharedPreferences("settings",0).getBoolean("reachEnabled",false)){cancel(c);return;}
            // A settings mutation must not get lost if an older planner is just
            // finishing. Routine checks coalesce; mutations append one fresh check.
            OneTimeWorkRequest.Builder request=new OneTimeWorkRequest.Builder(ReachPlanWorker.class).setInputData(new Data.Builder().putString("reachSource",source).build());
            if(android.os.Build.VERSION.SDK_INT>=31)request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
            WorkManager manager=WorkManager.getInstance(c);manager.enqueueUniqueWork(PLAN,changed?ExistingWorkPolicy.APPEND_OR_REPLACE:ExistingWorkPolicy.KEEP,request.build());
            manager.enqueueUniquePeriodicWork(WATCHDOG,ExistingPeriodicWorkPolicy.UPDATE,new PeriodicWorkRequest.Builder(ReachWakeWorker.class,ReachConfig.read(c.getSharedPreferences("settings",0)).integer("backgroundMinutes"),TimeUnit.MINUTES).setInputData(new Data.Builder().putString("reachSource","watchdog").build()).build());
        }catch(RuntimeException e){ReachOut.status(c.getSharedPreferences("settings",0),"计划任务入队失败，请重开应用或重新保存搭话设置");android.util.Log.e("EidolonReach","Plan enqueue failed",e);}
    }
    static void generate(Context c){OneTimeWorkRequest.Builder request=new OneTimeWorkRequest.Builder(ReachWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS);
        if(android.os.Build.VERSION.SDK_INT>=31)request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        WorkManager.getInstance(c).enqueueUniqueWork(GENERATE,ExistingWorkPolicy.KEEP,request.build());c.getSharedPreferences("settings",0).edit().putLong("reachGenerateQueuedAt",System.currentTimeMillis()).apply();}
    static void schedule(Context c,ChatStore s){schedule(c,s,false);}
    static void schedule(Context c,ChatStore s,boolean drain){
        if(!c.getSharedPreferences("settings",0).getBoolean("reachEnabled",false)){cancel(c);return;}
        long now=System.currentTimeMillis(),at=ReachPlanCheckRules.next(ReachConfig.read(c.getSharedPreferences("settings",0)),now,ReachPlanStore.next(s));if(drain&&!ReachPlanStore.due(s,now).isEmpty())at=Math.min(at,now+30000);
        at=ReachWake.preserveEarlier(now,at,c.getSharedPreferences("settings",0).getLong("reachNextWake",0));
        // Register durable work independently: an alarm failure must not remove this fallback.
        try{WorkManager.getInstance(c).enqueueUniqueWork(TIMER,c.getSharedPreferences("settings",0).getLong("reachTimerAt",0)==at?ExistingWorkPolicy.KEEP:ExistingWorkPolicy.REPLACE,new OneTimeWorkRequest.Builder(ReachWakeWorker.class).setInitialDelay(Math.max(0,at-now),TimeUnit.MILLISECONDS).setInputData(new Data.Builder().putString("reachSource","timer").putLong("reachExpected",at).build()).build());c.getSharedPreferences("settings",0).edit().putLong("reachTimerAt",at).apply();}
        catch(RuntimeException e){ReachOut.status(c.getSharedPreferences("settings",0),"持久检查任务未入队，保留系统唤醒；可打开后台设置检查");android.util.Log.w("EidolonReach","Durable timer enqueue failed",e);}
        try{((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,alarm(c));c.getSharedPreferences("settings",0).edit().putLong("reachNextWake",at).putString("reachWakeError","").apply();}
        catch(RuntimeException e){c.getSharedPreferences("settings",0).edit().remove("reachNextWake").putString("reachWakeError","系统未接受下一次唤醒，返回应用或刷新计划可重新检查").apply();ReachOut.status(c.getSharedPreferences("settings",0),"系统未接受搭话唤醒；已保留计划，可重新保存设置恢复");android.util.Log.e("EidolonReach","Inexact alarm failed",e);}
    }
    static void cancel(Context c){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(alarm(c));WorkManager.getInstance(c).cancelUniqueWork(PLAN);WorkManager.getInstance(c).cancelUniqueWork(GENERATE);WorkManager.getInstance(c).cancelUniqueWork(TIMER);WorkManager.getInstance(c).cancelUniqueWork(WATCHDOG);c.getSharedPreferences("settings",0).edit().remove("reachNextWake").remove("reachTimerAt").remove("reachWakeError").apply();ReachOut.cancel();}
    private ReachScheduler(){}
}
