package com.eidolon.sc.mobile;

import android.content.Context;
import android.app.job.JobScheduler;
import org.json.JSONObject;

/** Persists one generation revision and commits its effects exactly once at delivery. */
final class ReplyManager {
    static boolean enqueue(Context c,ChatStore.PendingReply row,long at,boolean network){return PendingReplyJob.schedule(c,row.id,at,network);}
    static boolean enqueue(Context c,String id,long at,boolean network){return PendingReplyJob.schedule(c,id,at,network);}
    static long dueAt(Context c,ChatStore store,ChatStore.PendingReply row,JSONObject personal)throws Exception{
        JSONObject status=ScheduleRuntime.state(c,store,personal,row.character,row.lastAt);JSONObject plan=status.optJSONObject("plan");
        return prefs(c).getBoolean("replyDelay.enabled",false)?ReplyDelay.choose(prefs(c),status,plan,personal,row.lastAt):row.lastAt;
    }
    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("settings",0);}
    static void run(Context context,String id)throws Exception{
        ChatStore store=new ChatStore(context);try{ChatStore.PendingReply row=store.pending(id);if(row==null)return;
            if(row.test){if(row.status.equals("collecting")){store.markGenerating(id,row.revision);store.markReady(id,row.revision,"测试回复：本地延迟任务正常。未调用 API、未保存聊天内容、未更新记忆或关系。",new JSONObject().put("personal",store.personal()).put("name","本地延迟测试"),System.currentTimeMillis()+3000L);row=store.pending(id);if(!enqueue(context,row,row.dueAt,false))store.markScheduleFailed(id,row.revision,"系统未能安排本地测试提示，请重新开始测试。");return;}if(row.status.equals("waiting")){JSONObject message=store.deliverPending(id,System.currentTimeMillis());if(message!=null)show(context,message,true);}return;}
            if(row.status.equals("collecting")||row.status.equals("generating")){
                if(WebActivity.isGenerating(id,row.revision)){enqueue(context,id,System.currentTimeMillis()+30000L,true);return;}
                if(row.status.equals("generating")){if(!ReplyFallback.requeueOrphan(store,row)){enqueue(context,id,System.currentTimeMillis()+30000L,true);return;}row=store.pending(id);}
                ReplyFallback.generate(context,store,row);changed(id);row=store.pending(id);if(row!=null&&row.status.equals("waiting")){boolean scheduled=enqueue(context,row.id,row.immediate?System.currentTimeMillis():row.dueAt,false);if(!scheduled){store.markScheduleFailed(id,row.revision,"系统暂未接受回复提醒任务。点重试重新安排，不会重新调用模型。");return;}if(row.dueAt<=System.currentTimeMillis()||row.immediate){JSONObject message=store.deliverPending(id,System.currentTimeMillis());if(message!=null)show(context,message,false);}}
                return;
            }
            if(row.status.equals("waiting")){if(row.immediate||row.dueAt<=System.currentTimeMillis()){JSONObject message=store.deliverPending(id,System.currentTimeMillis());if(message!=null)show(context,message,false);}else if(!enqueue(context,id,row.dueAt,false))store.markScheduleFailed(id,row.revision,"系统暂未接受回复提醒任务。点重试重新安排，不会重新调用模型。");}
        }finally{store.close();}
    }
    static void changed(String id){new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{WebActivity a=WebActivity.foregroundActivity;if(a!=null&&!a.destroyed)a.event("replyPending",id);});}
    static void show(Context context,JSONObject message,boolean test){
        Context app=context.getApplicationContext();if(!test)LauncherBadge.refresh(app);ReplyTrace.record(app,test?"本地测试已消费，准备提醒":"正式回复已保存，准备提醒");new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
            // Recheck on the UI thread: the Activity may have paused since delivery began.
            WebActivity activity=WebActivity.foregroundActivity;
            try{if(!test&&activity!=null&&ReachOut.foreground&&activity.visibleForReply()){
                if(activity.current!=null&&activity.current.optString("id").equals(message.optString("character"))&&activity.conversation.equals(message.optString("session"))&&"chat".equals(activity.visiblePage))activity.event("refresh","");
                else {activity.event("delayedReply",message.toString());ReachOut.notify(app,message,false);}trace(app,"回复已投放到应用内");return;
            }}catch(RuntimeException e){android.util.Log.w("EidolonDelay","Visible reply route unavailable",e);}
            try{boolean notified=ReachOut.notify(app,message,false);trace(app,notified?"系统通知已提交":"通知未允许或提醒通道已关闭");}catch(RuntimeException e){android.util.Log.e("EidolonDelay","Notification unavailable",e);}
        },250L);
    }
    static void trace(Context c,String text){ReplyTrace.record(c,text);c.getSharedPreferences("settings",0).edit().putString("replyDeliveryTrace",new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(new java.util.Date())+" · "+text).apply();}
    private ReplyManager(){}
}
