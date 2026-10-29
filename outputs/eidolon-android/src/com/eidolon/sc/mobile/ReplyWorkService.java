package com.eidolon.sc.mobile;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded protection for a reply explicitly requested from a visible Activity. */
public final class ReplyWorkService extends Service {
    static final long LIMIT=10*60*1000L;
    static volatile boolean running;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Map<String,Long> tracked=new ConcurrentHashMap<>();
    private final Set<String> localTests=ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicBoolean checking=new java.util.concurrent.atomic.AtomicBoolean();
    private PowerManager.WakeLock wake;private volatile boolean closed;private volatile Thread taskThread;private long stopAt,quietUntil;
    static void start(WebActivity activity,String id){
        if(!activity.getSharedPreferences("settings",0).getBoolean("replyBackgroundGuard",true))return;
        try{activity.startForegroundService(new Intent(activity,ReplyWorkService.class).putExtra("id",id));activity.getSharedPreferences("settings",0).edit().remove("replyGuardError").apply();}
        catch(RuntimeException e){activity.getSharedPreferences("settings",0).edit().putString("replyGuardError","系统未允许启动后台回复保护，请保持应用可见或检查后台运行设置。").apply();android.util.Log.w("EidolonDelay","Reply foreground guard rejected",e);}
    }
    @Override public void onCreate(){super.onCreate();try{stopAt=SystemClock.elapsedRealtime()+LIMIT;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel channel=new NotificationChannel("reply-work","回复后台任务",NotificationManager.IMPORTANCE_LOW);channel.setSound(null,null);channel.enableVibration(false);nm.createNotificationChannel(channel);
        Intent open=new Intent(this,WebActivity.class);PendingIntent content=PendingIntent.getActivity(this,9285,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"reply-work").setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle("Eidolon · 正在处理你的回复").setContentText("可以切换应用；回复完成后自动结束，最长运行10分钟。").setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(9285,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(9285,n);
        running=true;ReplyTrace.record(this,"正式后台保护服务已启动");wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Eidolon:reply-work");wake.setReferenceCounted(false);wake.acquire(LIMIT+1000);main.postDelayed(()->stopSelf(),LIMIT);
        }catch(RuntimeException e){closed=true;running=false;getSharedPreferences("settings",0).edit().putString("replyGuardError","系统拒绝后台回复保护，当前任务仍由系统调度恢复。请检查通知与后台运行设置。").apply();android.util.Log.w("EidolonDelay","Reply foreground setup failed",e);stopSelf();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(closed){stopSelf();return START_NOT_STICKY;}String id=intent==null?"":intent.getStringExtra("id");if(id!=null&&!id.isEmpty()){tracked.put(id,SystemClock.elapsedRealtime()+LIMIT);if(intent.getBooleanExtra("localTest",false))localTests.add(id);}main.removeCallbacks(tick);main.post(tick);return START_NOT_STICKY;}
    private final Runnable tick=new Runnable(){public void run(){if(closed)return;if(SystemClock.elapsedRealtime()>=stopAt){stopSelf();return;}if(checking.compareAndSet(false,true))worker.execute(()->{
        taskThread=Thread.currentThread();try(ChatStore store=new ChatStore(ReplyWorkService.this)){
            if(!getSharedPreferences("settings",0).getBoolean("replyBackgroundGuard",true)){tracked.clear();return;}
            long now=System.currentTimeMillis();for(String id:new ArrayList<>(tracked.keySet())){if(closed)break;ChatStore.PendingReply row=store.pending(id);
                if(row==null){tracked.remove(id);quietUntil=SystemClock.elapsedRealtime()+Math.max(7000L,(long)(ReachConfig.read(getSharedPreferences("settings",0)).number("popupSeconds")*1000)+500L);continue;}
                String action=row.test&&localTests.contains(id)?ReplyWorkPolicy.localTestAction(row.status,row.dueAt,now):ReplyWorkPolicy.action(row.status,row.test,row.lastAt,row.dueAt,row.immediate,now);
                if(action.equals("drop")){tracked.remove(id);localTests.remove(id);if(!row.test&&row.status.equals("waiting"))ReplyTrace.record(ReplyWorkService.this,"等待时间超过短时保护范围，交由系统持久任务");}
                else if(action.equals("generate"))ReplyManager.run(ReplyWorkService.this,id);
                else if(action.equals("deliver")){ReplyManager.run(ReplyWorkService.this,id);quietUntil=SystemClock.elapsedRealtime()+Math.max(7000L,(long)(ReachConfig.read(getSharedPreferences("settings",0)).number("popupSeconds")*1000)+500L);}
            }
        }catch(Exception e){android.util.Log.w("EidolonDelay","Reply guard tick failed",e);}finally{checking.set(false);main.post(()->{if(closed)return;if(tracked.isEmpty()&&SystemClock.elapsedRealtime()>=quietUntil)stopSelf();else main.postDelayed(this,1000);});}
    });}};
    @Override public void onTimeout(int startId,int type){stopSelf();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){ReplyTrace.record(this,"正式后台保护服务结束");closed=true;running=false;main.removeCallbacksAndMessages(null);try(ChatStore store=new ChatStore(this)){for(String id:tracked.keySet()){ChatStore.PendingReply row=store.pending(id);if(row!=null&&row.status.equals("generating")&&ReplyFallback.owned(id,row.revision,taskThread)){ReplyFallback.stop(id,row.revision);store.requeueInterrupted(id,row.revision,System.currentTimeMillis());PendingReplyJob.schedule(this,id,System.currentTimeMillis()+30000L,true);}}}catch(Exception e){android.util.Log.w("EidolonDelay","Unable to stop owned reply task",e);}worker.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
