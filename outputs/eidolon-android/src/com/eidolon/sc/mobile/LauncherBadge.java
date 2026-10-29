package com.eidolon.sc.mobile;
import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Vivo broadcast protocol reference: ShortcutBadger VivoHomeBadger, Copyright 2014 Leo Lin, Apache-2.0.
 * See assets/licenses/shortcutbadger-notice.txt. Only this app's identity and unread count are sent. */
final class LauncherBadge {
    static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor(r->new Thread(r,"eidolon-launcher-badge"));
    private static final AtomicBoolean queued=new AtomicBoolean();
    private static volatile int lastCount=-1;private static volatile long lastAt;private static volatile String result="尚未提交桌面更新";
    static void refresh(Context context){Context c=context.getApplicationContext();if(!queued.compareAndSet(false,true))return;
        EXECUTOR.execute(()->{queued.set(false);try(ChatStore s=new ChatStore(c)){UnreadNotifications.sync(c,s);}catch(Exception e){android.util.Log.w("EidolonBadge","Unread refresh failed",e);}});
    }
    static void apply(Context c,int unread){
        if(!LauncherBadgeRules.vivo(Build.MANUFACTURER,Build.BRAND))return;
        NotificationManager manager=c.getSystemService(NotificationManager.class);int count=LauncherBadgeRules.count(unread,manager!=null&&manager.areNotificationsEnabled());long now=System.currentTimeMillis();
        if(lastCount==count&&now>=lastAt&&now-lastAt<10000)return;
        try{
            Intent intent=new Intent("launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM").setPackage("com.vivo.launcher");
            intent.putExtra("packageName",c.getPackageName()).putExtra("className",new ComponentName(c,WebActivity.class).getClassName()).putExtra("notificationNum",count);
            c.sendBroadcast(intent);lastCount=count;lastAt=now;result="兼容更新请求已提交，是否显示由系统桌面决定";
        }catch(RuntimeException e){lastCount=-1;lastAt=now;result="系统未接受兼容更新，仍使用标准通知角标";android.util.Log.w("EidolonBadge","Vivo bridge unavailable",e);}
    }
    static JSONObject status(Context c,ChatStore s)throws Exception{
        NotificationManager manager=c.getSystemService(NotificationManager.class);NotificationChannel channel=manager.getNotificationChannel(UnreadNotifications.CHANNEL);
        return new JSONObject().put("unread",UnreadState.counts(s).getInt("total")).put("notifications",manager.areNotificationsEnabled()).put("channelBadge",channel==null||channel.canShowBadge())
            .put("vivo",LauncherBadgeRules.vivo(Build.MANUFACTURER,Build.BRAND)).put("lastAt",lastAt).put("submittedCount",lastCount).put("status",result);
    }
    static JSONObject resync(Context c)throws Exception{
        synchronized(UnreadNotifications.LOCK){lastCount=-1;try(ChatStore s=new ChatStore(c)){UnreadNotifications.sync(c,s);return status(c,s);}}
    }
    private LauncherBadge(){}
}
