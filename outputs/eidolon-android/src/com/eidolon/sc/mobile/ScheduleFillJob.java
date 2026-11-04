package com.eidolon.sc.mobile;
import android.app.job.*;import java.util.concurrent.atomic.AtomicBoolean;

public final class ScheduleFillJob extends JobService {
 private volatile Thread task;private volatile AtomicBoolean stopped;
 @Override public boolean onStartJob(JobParameters params){AtomicBoolean stop=new AtomicBoolean();stopped=stop;task=new Thread(()->{
  try{ScheduleEngine.run(this);}catch(Exception e){android.util.Log.w("EidolonSchedule","Role attempt stopped ("+e.getClass().getSimpleName()+")");}
  finally{if(!stop.get())jobFinished(params,false);ScheduleQueue.refresh(this);ScheduleQueue.changed();}
 },"eidolon-schedule-fill");task.start();return true;}
 @Override public boolean onStopJob(JobParameters params){AtomicBoolean stop=stopped;if(stop!=null)stop.set(true);Thread own=task;if(own!=null){own.interrupt();if(ScheduleEngine.owner==own)ScheduleEngine.cancel();}return false;}
}
