package com.eidolon.sc.mobile;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;

/** User-started local test: stays alive briefly when the Activity leaves the screen. */
public final class ReplyTestService extends Service {
    private static final String CHANNEL="reply-local-test";
    private static final int NOTIFICATION=9284;
    private final Handler main=new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wake;
    private final java.util.Set<String> queued=new java.util.HashSet<>();
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"本地延迟测试",NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null,null);channel.enableVibration(false);manager.createNotificationChannel(channel);
        Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Eidolon · 本地延迟测试").setContentText("测试即将送达，可以切换到其他应用；此任务随后自动结束。")
            .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        else startForeground(NOTIFICATION,notification);
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Eidolon:reply-test");
        wake.setReferenceCounted(false);wake.acquire(8000L+Math.max(7000L,(long)(ReachConfig.read(getSharedPreferences("settings",0)).number("popupSeconds")*1000)+500L));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        final String id=intent==null?null:intent.getStringExtra("id");
        if(id==null||id.isEmpty()){stopSelfResult(startId);return START_NOT_STICKY;}
        if(!queued.add(id))return START_NOT_STICKY;
        long wait=0;
        try(ChatStore store=new ChatStore(this)){
            ChatStore.PendingReply row=store.pending(id);
            if(row==null||!row.test||!"waiting".equals(row.status)){stopSelfResult(startId);return START_NOT_STICKY;}
            wait=Math.max(0,row.dueAt-System.currentTimeMillis());
        }
        // The wake lock protects this bounded delay from screen-off sleep.
        main.postDelayed(()->new Thread(()->{
            try(ChatStore store=new ChatStore(this)){
                ChatStore.PendingReply row=store.pending(id);
                if(row!=null&&row.test){org.json.JSONObject message=store.deliverPending(id,System.currentTimeMillis());if(message!=null)ReplyManager.show(this,message,true);}
            }catch(Exception e){android.util.Log.e("EidolonDelay","Local test failed",e);}
            // Keep an overlay alive for its normal display duration, then stop.
            finally{main.postDelayed(()->stopSelfResult(startId),Math.max(7000L,(long)(ReachConfig.read(getSharedPreferences("settings",0)).number("popupSeconds")*1000)+500L));}
        },"eidolon-local-reply-test").start(),Math.min(wait,3000L));
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId){stopSelf();}
    @Override public void onTimeout(int startId,int fgsType){stopSelf();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
