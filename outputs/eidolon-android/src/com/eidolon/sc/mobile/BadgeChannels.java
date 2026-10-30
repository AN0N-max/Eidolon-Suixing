package com.eidolon.sc.mobile;
import android.app.*;import android.content.*;
/** Explicit user repair of legacy channels; copies alert settings and never deletes history. */
final class BadgeChannels {
 static String id(Context c,String legacy){if(legacy.contains("-badge-"))return legacy;int revision=c.getSharedPreferences("settings",0).getInt("badgeChannelRevision",0);return revision==0?legacy:legacy+"-badge-"+revision;}
 static void repair(Context c){
  NotificationManager manager=c.getSystemService(NotificationManager.class);android.content.SharedPreferences p=c.getSharedPreferences("settings",0);int revision=p.getInt("badgeChannelRevision",0)+1;if(revision>5)throw new IllegalStateException("请先在系统设置检查桌面角标和免打扰限制");
  for(NotificationChannel old:manager.getNotificationChannels()){
   String base=old.getId();if(base.contains("-badge-"))continue;if(!base.equals(UnreadNotifications.CHANNEL)&&!base.startsWith("reach-message-v2-")&&!base.equals("reach-history-v2"))continue;
   NotificationChannel current=manager.getNotificationChannel(id(c,base));if(current!=null)old=current;
   NotificationChannel next=new NotificationChannel(base+"-badge-"+revision,old.getName(),old.getImportance());next.setDescription(old.getDescription());next.setShowBadge(true);next.setSound(old.getSound(),old.getAudioAttributes());next.enableVibration(old.shouldVibrate());if(old.getVibrationPattern()!=null)next.setVibrationPattern(old.getVibrationPattern());next.enableLights(old.shouldShowLights());next.setLightColor(old.getLightColor());next.setLockscreenVisibility(old.getLockscreenVisibility());next.setBypassDnd(old.canBypassDnd());if(old.getGroup()!=null)next.setGroup(old.getGroup());manager.createNotificationChannel(next);
  }
  if(!p.edit().putInt("badgeChannelRevision",revision).commit())throw new IllegalStateException("角标修复设置保存失败");BadgeProbe.clear(c);
 }
 private BadgeChannels(){}
}
