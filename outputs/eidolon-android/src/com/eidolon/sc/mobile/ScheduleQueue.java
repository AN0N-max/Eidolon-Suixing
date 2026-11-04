package com.eidolon.sc.mobile;
import android.app.job.*;import android.content.*;import android.os.PersistableBundle;import java.time.*;import org.json.*;
import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;

/** One role per bounded job, then check the remaining eligible roles. No blind paid retry. */
final class ScheduleQueue {
 static final int JOB=292;private static final ExecutorService CONTROL=Executors.newSingleThreadExecutor();
 private static final AtomicBoolean QUEUED=new AtomicBoolean(),DIRTY=new AtomicBoolean(),FORCE=new AtomicBoolean();
 static void recheck(Context c){FORCE.set(true);refresh(c);}
 static void refresh(Context context){final Context c=context.getApplicationContext();DIRTY.set(true);if(!QUEUED.compareAndSet(false,true))return;
  CONTROL.execute(()->{try{do{DIRTY.set(false);update(c,FORCE.getAndSet(false));}while(DIRTY.get());}finally{QUEUED.set(false);if(DIRTY.get())refresh(c);}});
 }
 static void update(Context c,boolean force){try(ChatStore s=new ChatStore(c)){
  JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(force)jobs.cancel(JOB);SharedPreferences p=c.getSharedPreferences("settings",0);
  if(!p.getBoolean("hibiSchedules",false)||!ScheduleCandidates.configured(c)){jobs.cancel(JOB);p.edit().remove("scheduleQueueNext").apply();return;}
  long now=System.currentTimeMillis(),next=Long.MAX_VALUE;JSONObject personal=s.personal();JSONArray chars=WritingEngine.characters(c);ZoneId producer=ScheduleData.zone(personal);String budgetDay=Instant.ofEpochMilli(now).atZone(producer).toLocalDate().toString();
  boolean room=ScheduleAttempts.count(s,budgetDay,producer)<p.getInt("hibiScheduleLimit",3);
  if(!room)next=Instant.ofEpochMilli(now).atZone(producer).toLocalDate().plusDays(1).atStartOfDay(producer).toInstant().toEpochMilli();
  if(room&&!ScheduleCandidates.list(c,s,personal,chars,now).isEmpty())next=ScheduleQueueRules.retryAt(now,ScheduleCandidates.occupied(s));
  else for(int i=0;i<chars.length();i++){String who=chars.getJSONObject(i).getString("id");if(!ScheduleStore.talked(s,who)||s.lastActivity(who)<now-7L*SocialData.DAY)continue;ZonedDateTime at=Instant.ofEpochMilli(now).atZone(TemporalRuntime.zone(c,personal,who));next=Math.min(next,DailyScheduleClock.nextBoundary(at));}
  if(next==Long.MAX_VALUE){jobs.cancel(JOB);return;}next=Math.max(now+1000,next);JobInfo previous=jobs.getPendingJob(JOB);
  if(!force&&previous!=null&&previous.getExtras().getLong("targetAt",Long.MAX_VALUE)<=next+1000)return;
  PersistableBundle extras=new PersistableBundle();extras.putLong("targetAt",next);
  if(jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(c,ScheduleFillJob.class)).setPersisted(true).setMinimumLatency(next-now).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setExtras(extras).build())!=JobScheduler.RESULT_SUCCESS)throw new Exception("系统未接受行程任务");
  p.edit().putLong("scheduleQueueNext",next).remove("scheduleQueueError").apply();
 }catch(Exception e){c.getSharedPreferences("settings",0).edit().putString("scheduleQueueError","自动行程检查未成功安排；打开应用后会再核对").apply();android.util.Log.w("EidolonSchedule","Local queue check failed ("+e.getClass().getSimpleName()+")");}}
 static void changed(){WebActivity a=WebActivity.foregroundActivity;if(a!=null)a.runOnUiThread(()->{if(!a.destroyed)a.event("scheduleQueueChanged","");});}
}
