package com.eidolon.sc.mobile;
import android.app.job.*;
public final class WritingJob extends JobService {
    private final java.util.Map<Integer,Thread> tasks=new java.util.concurrent.ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters params){Thread task=new Thread(()->{try{LettersEngine.refresh(this);if(params.getJobId()==WritingMorningJob.DIARY)WritingEngine.runDiary(this);else if(ScheduleEngine.run(this)==null)WritingEngine.run(this);}catch(Exception e){android.util.Log.w("EidolonWriting","Background task failed",e);}finally{boolean active=tasks.remove(params.getJobId(),Thread.currentThread());if(active){jobFinished(params,false);if(params.getJobId()==WritingMorningJob.DIARY)WritingMorningJob.scheduleDiary(this);}}},"eidolon-writing");if(tasks.putIfAbsent(params.getJobId(),task)!=null)return false;task.start();return true;}
    @Override public boolean onStopJob(JobParameters params){Thread own=tasks.remove(params.getJobId());if(own!=null){own.interrupt();if(WritingEngine.owner==own)WritingEngine.cancel();}if(params.getJobId()!=WritingMorningJob.DIARY)ScheduleEngine.cancel();return params.getJobId()==WritingMorningJob.DIARY;}
}
