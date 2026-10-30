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
            android.content.pm.ResolveInfo home=c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            java.util.Set<String> targets=new java.util.LinkedHashSet<>();targets.add("com.vivo.abe");targets.add("com.bbk.launcher2");targets.add("com.vivo.launcher");
            if(home!=null&&home.activityInfo!=null&&home.activityInfo.packageName!=null&&!home.activityInfo.packageName.equals("android"))targets.add(home.activityInfo.packageName);
            Intent intent=new Intent("launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM");
            intent.putExtra("packageName",c.getPackageName()).putExtra("className",new ComponentName(c,WebActivity.class).getClassName()).putExtra("notificationNum",count);
            for(String target:targets)c.sendBroadcast(new Intent(intent).setPackage(target));lastCount=count;lastAt=now;result="兼容更新请求已提交，是否显示由系统桌面决定";
        }catch(RuntimeException e){lastCount=-1;lastAt=now;result="系统未接受兼容更新，仍使用标准通知角标";android.util.Log.w("EidolonBadge","Vivo bridge unavailable",e);}
    }
    static JSONObject status(Context c,ChatStore s)throws Exception{
        NotificationManager manager=c.getSystemService(NotificationManager.class);NotificationChannel channel=manager.getNotificationChannel(BadgeChannels.id(c,UnreadNotifications.CHANNEL));
        JSONArray active=new JSONArray();for(android.service.notification.StatusBarNotification item:manager.getActiveNotifications()){
            Notification n=item.getNotification();if(n.extras==null||n.extras.getString(UnreadNotifications.CHARACTER,"").isEmpty())continue;
            NotificationChannel actual=manager.getNotificationChannel(n.getChannelId());
            active.put(new JSONObject().put("channel",n.getChannelId()).put("number",n.number).put("badge",actual!=null&&actual.canShowBadge()).put("enabled",actual!=null&&actual.getImportance()!=NotificationManager.IMPORTANCE_NONE));
        }
        boolean known=true;try{c.getPackageManager().getPermissionInfo("com.vivo.notification.permission.BADGE_ICON",0);}catch(android.content.pm.PackageManager.NameNotFoundException e){known=false;}
        android.content.pm.ResolveInfo home=c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        String launcher=home!=null&&home.activityInfo!=null?home.activityInfo.packageName:"系统未提供桌面包信息";
        return new JSONObject().put("unread",UnreadState.counts(s).getInt("total")).put("notifications",manager.areNotificationsEnabled()).put("channelBadge",channel==null||channel.canShowBadge())
            .put("activeMessageNotifications",active).put("homePackage",launcher).put("vivoBadgePermissionKnown",known).put("vivoBadgePermission",known&&c.checkSelfPermission("com.vivo.notification.permission.BADGE_ICON")==android.content.pm.PackageManager.PERMISSION_GRANTED)
            .put("launcherBadgeKnown",false).put("channelEnabled",channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE)
            .put("vivo",LauncherBadgeRules.vivo(Build.MANUFACTURER,Build.BRAND)).put("lastAt",lastAt).put("submittedCount",lastCount).put("status",result);
    }
    static JSONObject resync(Context c)throws Exception{
        synchronized(UnreadNotifications.LOCK){lastCount=-1;try(ChatStore s=new ChatStore(c)){UnreadNotifications.sync(c,s);return status(c,s);}}
    }
    private LauncherBadge(){}
}
