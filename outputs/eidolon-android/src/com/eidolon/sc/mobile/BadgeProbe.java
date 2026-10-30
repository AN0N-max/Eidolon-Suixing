package com.eidolon.sc.mobile;
import android.app.*;import android.content.*;
/** Local, explicitly started notification probe. Never writes chats/unread or calls a model. */
final class BadgeProbe {
 static final String TAG="eidolon-badge-local-probe",CHANNEL="eidolon-badge-probe-v1";static final int ID=9751;
 static void show(Context c,boolean independent){
  NotificationManager n=c.getSystemService(NotificationManager.class);String id=independent?CHANNEL:BadgeChannels.id(c,UnreadNotifications.CHANNEL);
  if(n.getNotificationChannel(id)==null){NotificationChannel ch=new NotificationChannel(id,independent?"本地角标测试":"未读消息角标",NotificationManager.IMPORTANCE_DEFAULT);ch.setShowBadge(true);ch.setSound(null,null);ch.enableVibration(false);n.createNotificationChannel(ch);}
  Intent open=new Intent(c,WebActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(c,ID,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  n.notify(TAG,ID,new Notification.Builder(c,id).setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle("Eidolon · 本地角标测试").setContentText("本地测试1条通知；不写入聊天、不调用模型。可在角标页清除，5分钟后自动结束。").setNumber(1).setBadgeIconType(Notification.BADGE_ICON_SMALL).setCategory(Notification.CATEGORY_MESSAGE).setOnlyAlertOnce(true).setAutoCancel(true).setTimeoutAfter(300000).setContentIntent(pi).build());
 }
 static void clear(Context c){c.getSystemService(NotificationManager.class).cancel(TAG,ID);LauncherBadge.refresh(c);}
 private BadgeProbe(){}
}
