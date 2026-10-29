package com.eidolon.sc.mobile;
import android.app.job.*;
import android.content.*;
import java.time.*;

/** Offline 03:00 check plus a separate network job; plans reuse existing durable data. */
public final class WritingMorningJob extends JobService {
    static final int CHECK=288,DIARY=289;
    private volatile Thread task;
    private volatile java.util.concurrent.atomic.AtomicBoolean stopped;
    static boolean enabled(Context c){android.content.SharedPreferences p=c.getSharedPreferences("settings",0);return p.getBoolean("hibiLetters",false)||p.getBoolean("hibiDiary",false);}
    static void schedule(Context c){
        try{JobScheduler jobs=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);if(!enabled(c)){jobs.cancel(CHECK);jobs.cancel(DIARY);c.getSharedPreferences("settings",0).edit().remove("writingMorningNext").apply();return;}
            try(ChatStore s=new ChatStore(c)){android.content.SharedPreferences p=c.getSharedPreferences("settings",0);ZonedDateTime now=ZonedDateTime.now(ScheduleData.zone(s.personal()));long at=Math.max(WritingMorningRules.nextCheck(now,p.getString("writingMorningDay",""),p.getString("writingMorningZone","")),p.getLong("writingMorningRetry",0));long delay=Math.max(1000,at-System.currentTimeMillis());
                if(jobs.schedule(new JobInfo.Builder(CHECK,new ComponentName(c,WritingMorningJob.class)).setPersisted(true).setMinimumLatency(delay).setOverrideDeadline(delay+120000).build())!=JobScheduler.RESULT_SUCCESS)throw new Exception("系统暂未接受凌晨三点检查任务");
                p.edit().putLong("writingMorningNext",at).apply();if(!p.getBoolean("hibiDiary",false))jobs.cancel(DIARY);
            }
        }catch(Exception e){error(c,"凌晨三点检查未成功安排；打开应用后会再核对");android.util.Log.w("EidolonWriting","Morning schedule failed",e);}
    }
    static void scheduleDiary(Context c){
        try(ChatStore s=new ChatStore(c)){JobScheduler jobs=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);android.content.SharedPreferences p=c.getSharedPreferences("settings",0);if(!p.getBoolean("hibiDiary",false)){jobs.cancel(DIARY);return;}
            if(Vault.read(c).isEmpty()&&Vault.read(c,"backgroundKey").isEmpty()){jobs.cancel(DIARY);return;}
            long next=Long.MAX_VALUE;org.json.JSONArray rows=WritingPlanView.get(c,s).getJSONArray("rows");for(int i=0;i<rows.length();i++){org.json.JSONObject r=rows.getJSONObject(i);if(r.getString("kind").equals("diary")&&java.util.Arrays.asList("pending","planned","retry").contains(r.getString("state")))next=Math.min(next,r.getLong("writeAt"));}if(next==Long.MAX_VALUE){jobs.cancel(DIARY);return;}long now=System.currentTimeMillis();next=Math.max(next,now+((WritingEngine.chatting()||WritingEngine.running.get())?300000:60000));
            if(jobs.schedule(new JobInfo.Builder(DIARY,new ComponentName(c,WritingJob.class)).setPersisted(true).setMinimumLatency(next-now).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build())!=JobScheduler.RESULT_SUCCESS)throw new Exception("系统暂未接受日记整理任务");
        }catch(Exception e){error(c,"日记整理任务未成功安排；原事件与尝试次数保留");android.util.Log.w("EidolonWriting","Diary schedule failed",e);}
    }
    static void error(Context c,String text){c.getSharedPreferences("settings",0).edit().putString("writingMorningError",text).apply();}
    @Override public boolean onStartJob(JobParameters params){final java.util.concurrent.atomic.AtomicBoolean canceled=new java.util.concurrent.atomic.AtomicBoolean();stopped=canceled;task=new Thread(()->{try{
        if(!enabled(this))return;LettersEngine.refresh(this);if(Thread.currentThread().isInterrupted())return;
        try(ChatStore s=new ChatStore(this)){ZonedDateTime now=ZonedDateTime.now(ScheduleData.zone(s.personal()));if(now.getHour()>=DailyScheduleClock.HOUR&&!getSharedPreferences("settings",0).edit().putString("writingMorningDay",now.toLocalDate().toString()).putString("writingMorningZone",now.getZone().getId()).putLong("writingMorningAt",System.currentTimeMillis()).remove("writingMorningError").remove("writingMorningRetry").commit())throw new Exception("检查状态保存失败");}
        scheduleDiary(this);
    }catch(Exception e){error(this,"本日写作安排尚未核对完成；已有信件与日记保留");getSharedPreferences("settings",0).edit().putLong("writingMorningRetry",System.currentTimeMillis()+900000).apply();android.util.Log.w("EidolonWriting","Morning check failed",e);}finally{if(!canceled.get()){jobFinished(params,false);schedule(this);}}},"eidolon-writing-morning");task.start();return true;}
    @Override public boolean onStopJob(JobParameters params){java.util.concurrent.atomic.AtomicBoolean ownStop=stopped;if(ownStop!=null)ownStop.set(true);Thread own=task;if(own!=null)own.interrupt();return true;}
}
