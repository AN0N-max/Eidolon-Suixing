package com.eidolon.sc.mobile;
import android.content.Context;
import androidx.work.*;
public final class ReachPlanWorker extends Worker {
    public ReachPlanWorker(Context c,WorkerParameters p){super(c,p);}
    @Override public Result doWork(){Context c=getApplicationContext();try(ChatStore s=new ChatStore(c)){
        String source=getInputData().getString("reachSource");if("timer".equals(source)||"watchdog".equals(source))ReachWake.received(c,source,getInputData().getLong("reachExpected",0));c.getSharedPreferences("settings",0).edit().putLong("reachPlannerStartedAt",System.currentTimeMillis()).apply();
        if(!c.getSharedPreferences("settings",0).getBoolean("reachEnabled",false))return Result.success();ReachPlanCheck.check(c,false);
        if(!ReachPlanStore.due(s,System.currentTimeMillis()).isEmpty())ReachScheduler.generate(c);ReachScheduler.schedule(c,s);return Result.success();
    }catch(Exception e){ReachOut.status(c.getSharedPreferences("settings",0),"本地计划检查失败，请打开搭话设置查看或重新安排");android.util.Log.e("EidolonReach","Planner failed",e);return getRunAttemptCount()<2?Result.retry():Result.failure();}}
}
