package com.eidolon.sc.mobile;

import android.content.*;

/** Re-enqueues durable chat work after reboot, clock/time-zone edits or app upgrades. */
public final class ReplyRescheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null)return;String action=intent.getAction();if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)&&!Intent.ACTION_TIME_CHANGED.equals(action)&&!Intent.ACTION_TIMEZONE_CHANGED.equals(action))return;
        LauncherBadge.refresh(context);ReachScheduler.refresh(context);
        PendingResult pending=goAsync();new Thread(()->{try(ChatStore store=new ChatStore(context)){for(ChatStore.PendingReply row:store.pendingReplies()){
            if(row.status.equals("generating")){store.requeueInterrupted(row.id,row.revision,System.currentTimeMillis());row=store.pending(row.id);}
            if(row.status.equals("collecting")){if(!PendingReplyJob.schedule(context,row.id,System.currentTimeMillis()+30000L,true))store.markFailed(row.id,row.revision,"系统暂未恢复回复生成任务。打开对话后可重试。");}
            else if(row.status.equals("waiting")){if(!PendingReplyJob.schedule(context,row.id,row.immediate?System.currentTimeMillis():row.dueAt,false))store.markScheduleFailed(row.id,row.revision,"系统暂未接受回复提醒任务。打开对话后点重试重新安排。");}
        }WritingEngine.schedule(context);LettersEngine.refresh(context);}catch(Exception e){android.util.Log.e("EidolonDelay","Unable to restore pending replies",e);}finally{pending.finish();}},"eidolon-reply-restore").start();
    }
}
