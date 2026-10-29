package com.eidolon.sc.mobile;

import android.app.job.*;
import android.content.*;

/** Independent network generation and offline delivery; persistent, non-exact one-shot jobs. */
public final class LetterJob extends JobService {
    static final int WRITE=286,DELIVER=287;
    static final java.util.Set<Integer> running=java.util.concurrent.ConcurrentHashMap.newKeySet();
    final java.util.Map<Integer,Thread> tasks=new java.util.concurrent.ConcurrentHashMap<>();
    static void cancel(Context c){JobScheduler scheduler=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);if(scheduler!=null){scheduler.cancel(WRITE);scheduler.cancel(DELIVER);}}
    static void schedule(Context c,int id,long at,boolean network){
        if(running.contains(id))return;
        JobScheduler scheduler=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);if(scheduler==null)return;
        if(at==Long.MAX_VALUE){scheduler.cancel(id);return;}
        try{
            long delay=Math.max(network?60000L:0L,at-System.currentTimeMillis());
            JobInfo.Builder b=new JobInfo.Builder(id,new ComponentName(c,LetterJob.class)).setPersisted(true).setMinimumLatency(delay);
            if(network)b.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            if(scheduler.schedule(b.build())!=JobScheduler.RESULT_SUCCESS)c.getSharedPreferences("settings",0).edit().putString("letterError","系统暂未接受来信任务，打开应用后会继续检查。").apply();
        }catch(SecurityException|IllegalArgumentException e){c.getSharedPreferences("settings",0).edit().putString("letterError","系统暂未接受来信任务，打开应用后会继续检查。").apply();android.util.Log.w("EidolonLetters","Letter job rejected",e);}
    }
    @Override public boolean onStartJob(JobParameters params){
        if(!running.add(params.getJobId()))return false;
        Thread task=new Thread(()->{
            try{LettersEngine.refresh(this);if(params.getJobId()==WRITE&&!Thread.currentThread().isInterrupted())WritingEngine.run(this);}
            catch(Exception e){android.util.Log.w("EidolonLetters","Letter job unfinished",e);}
            finally{tasks.remove(params.getJobId());jobFinished(params,false);running.remove(params.getJobId());LettersEngine.reschedule(this);}
        },"eidolon-letter-"+params.getJobId());tasks.put(params.getJobId(),task);task.start();return true;
    }
    @Override public boolean onStopJob(JobParameters params){Thread t=tasks.get(params.getJobId());if(t!=null)t.interrupt();return false;}
}
