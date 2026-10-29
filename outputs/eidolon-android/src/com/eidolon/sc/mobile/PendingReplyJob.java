package com.eidolon.sc.mobile;

import android.app.job.*;
import android.content.*;
import android.os.PersistableBundle;
import org.json.JSONObject;

/** One-shot, persisted generation/delivery work. No polling service or exact-alarm permission. */
public final class PendingReplyJob extends JobService {
    private final java.util.concurrent.ConcurrentHashMap<String,Thread> active=new java.util.concurrent.ConcurrentHashMap<>();
    static final String EXTRA_ID="pendingReplyId";
    static int jobId(String id){return 10000+(id.hashCode()&0x3fffffff);}
    static boolean schedule(Context context,String id,long at,boolean network){
        try{JobScheduler js=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);PersistableBundle extras=new PersistableBundle();extras.putString(EXTRA_ID,id);
            long delay=Math.max(0,at-System.currentTimeMillis());JobInfo.Builder b=new JobInfo.Builder(jobId(id),new ComponentName(context,PendingReplyJob.class)).setExtras(extras).setPersisted(true).setMinimumLatency(delay).setOverrideDeadline(delay+2*60*1000L);
            if(network)b.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);int result=js.schedule(b.build());if(result!=JobScheduler.RESULT_SUCCESS)android.util.Log.e("EidolonDelay","JobScheduler rejected pending reply job: "+result);return result==JobScheduler.RESULT_SUCCESS;
        }catch(Exception e){android.util.Log.e("EidolonDelay","Unable to schedule pending reply",e);return false;}
    }
    static void cancel(Context context,String id){try{((JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(jobId(id));}catch(Exception ignored){}}
    @Override public boolean onStartJob(JobParameters params){String id=params.getExtras().getString(EXTRA_ID,"");Thread task=new Thread(()->{try{ReplyManager.run(this,id);}catch(Exception e){android.util.Log.e("EidolonDelay","Pending reply task failed",e);try(ChatStore store=new ChatStore(this)){ChatStore.PendingReply row=store.pending(id);if(row!=null&&row.status.equals("waiting")&&!schedule(this,id,Math.max(System.currentTimeMillis()+30000L,row.dueAt),false))store.markScheduleFailed(id,row.revision,"系统暂未接受回复提醒任务。点重试重新安排，不会重新调用模型。");}catch(Exception ignored){}}finally{active.remove(id,Thread.currentThread());jobFinished(params,false);}} ,"eidolon-pending-reply");if(active.putIfAbsent(id,task)!=null)return false;task.start();return true;}
    @Override public boolean onStopJob(JobParameters params){String id=params.getExtras().getString(EXTRA_ID,"");try(ChatStore store=new ChatStore(this)){ChatStore.PendingReply row=store.pending(id);if(row!=null&&row.status.equals("generating")&&ReplyFallback.owned(id,row.revision,active.remove(id))){ReplyFallback.stop(id,row.revision);store.requeueInterrupted(id,row.revision,System.currentTimeMillis());}}catch(Exception e){android.util.Log.e("EidolonDelay","Unable to stop interrupted reply generation",e);}return true;}
}
