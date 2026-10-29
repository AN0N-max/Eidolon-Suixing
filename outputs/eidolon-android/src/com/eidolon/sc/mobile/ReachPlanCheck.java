package com.eidolon.sc.mobile;

import android.content.*;
import java.util.concurrent.*;
import org.json.*;

/** Local planning has its own executor; neither the model executor nor WorkManager gates a manual check. */
final class ReachPlanCheck {
    static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor(r->new Thread(r,"eidolon-local-planner"));
    static void resume(Context context){
        Context c=context.getApplicationContext();
        if(!c.getSharedPreferences("settings",0).getBoolean("reachEnabled",false))return;
        EXECUTOR.execute(()->{try{check(c,false);}catch(Exception e){android.util.Log.w("EidolonReach","Foreground planner check failed",e);}});
    }
    static boolean databaseBusy(Exception error){for(Throwable e=error;e!=null;e=e.getCause())if(e instanceof android.database.sqlite.SQLiteDatabaseLockedException||e instanceof android.database.sqlite.SQLiteTableLockedException)return true;return false;}
    static JSONObject check(Context c,boolean replace)throws Exception{
        synchronized(ReachPlans.LOCK){
            SharedPreferences p=c.getSharedPreferences("settings",0);
            if(replace&&!p.getBoolean("reachEnabled",false))throw new Exception("请先开启并保存主动搭话");
            if(replace&&ReachOut.running.get())throw new Exception("正在生成主动搭话，请等待完成后再重新安排");
            p.edit().putLong("reachCheckStarted",System.currentTimeMillis()).apply();
            try{
                LocalDbRetry.run(()->{try(ChatStore s=new ChatStore(c)){
                    android.database.sqlite.SQLiteDatabase db=s.getWritableDatabase();db.beginTransactionNonExclusive();try{
                        long now=System.currentTimeMillis();
                        if(p.getBoolean("reachEnabled",false)){
                            if(!ReachOut.running.get())ReachPlanStore.interrupted(s,now);
                            if(replace)ReachPlanStore.replacePending(s,now);
                            ReachPlanStore.expire(s,now);ReachPlans.reconcile(c,s,now);ReachPlans.ensure(c,s,now);
                        }
                        db.setTransactionSuccessful();
                    }finally{db.endTransaction();}
                }return true;},ReachPlanCheck::databaseBusy,Thread::sleep);
            }catch(Exception e){
                String message=databaseBusy(e)?"本地数据正忙，暂未完成本次检查；原计划已保留，请稍后重试":"本地排程未完成，原计划已保留，请重试（"+e.getClass().getSimpleName()+"）";
                p.edit().putLong("reachCheckAt",System.currentTimeMillis()).putString("reachCheckError",message).apply();
                android.util.Log.e("EidolonReach","Atomic local plan check failed",e);
                try(ChatStore recovery=new ChatStore(c)){ReachScheduler.schedule(c,recovery);}catch(Exception ignored){}
                if(e instanceof InterruptedException)Thread.currentThread().interrupt();
                throw new Exception(message,e);
            }
            // A committed replan is not undone or reported as a replan failure if only its view cannot be read.
            try(ChatStore s=new ChatStore(c)){
                long finished=System.currentTimeMillis();JSONArray rows=ReachPlanStore.today(s,ReachConfig.read(p),finished);int active=0;
                for(int i=0;i<rows.length();i++)if(ReachPlanCheckRules.active(rows.getJSONObject(i).getString("status")))active++;
                p.edit().putLong("reachCheckAt",finished).putString("reachCheckError","").putString("reachCheckResult",p.getBoolean("reachEnabled",false)?"本地检查完成，当前 "+active+" 项安排":"主动搭话已关闭").apply();
                ReachScheduler.schedule(c,s);return snapshot(c,s,finished);
            }catch(Exception e){android.util.Log.w("EidolonReach","Plan committed; result view unavailable",e);throw new Exception("本地计划已检查，结果页面暂未读取成功；请点刷新计划",e);}
        }
    }
    static JSONObject snapshot(Context c)throws Exception{
        synchronized(ReachPlans.LOCK){try(ChatStore s=new ChatStore(c)){return snapshot(c,s,System.currentTimeMillis());}}
    }
    private static JSONObject snapshot(Context c,ChatStore s,long now)throws Exception{
        SharedPreferences p=c.getSharedPreferences("settings",0);ReachConfig config=ReachConfig.read(p);
        long wake=p.getLong("reachNextWake",0);
        return new JSONObject().put("enabled",p.getBoolean("reachEnabled",false)).put("date",ReachPolicy.date(config,now)).put("zone",config.text("zone")).put("now",now)
            .put("nextWake",wake).put("wakeOverdue",ReachPlanCheckRules.overdue(wake,now)).put("wakeError",p.getString("reachWakeError",""))
            .put("checkedAt",p.getLong("reachCheckAt",0)).put("checkError",p.getString("reachCheckError","")).put("checkResult",p.getString("reachCheckResult","尚未完成本地检查"))
            .put("tasks",ReachPlanStore.today(s,config,now)).put("assessments",ReachPlans.explain(c,s,now));
    }
    private ReachPlanCheck(){}
}
