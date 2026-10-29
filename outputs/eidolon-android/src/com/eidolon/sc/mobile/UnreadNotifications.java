package com.eidolon.sc.mobile;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import org.json.*;
import java.util.*;

/** Standard Android badge counts. One notification per role, no duplicate summary count. */
final class UnreadNotifications {
    static final Object LOCK=new Object();
    static final String CHARACTER="eidolonUnreadCharacter",SESSION="eidolonUnreadSession",CHANNEL="eidolon-unread-count-v1";
    static void sync(Context c,ChatStore store){synchronized(LOCK){try{
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);JSONObject unread=UnreadState.counts(store);JSONObject counts=unread.getJSONObject("characters");LauncherBadge.apply(c,unread.getInt("total"));
        Map<String,StatusBarNotification> current=new HashMap<>();for(StatusBarNotification active:manager.getActiveNotifications()){Bundle extras=active.getNotification().extras;String who=extras==null?"":extras.getString(CHARACTER,"");if(who.isEmpty()&&extras!=null&&Notification.CATEGORY_MESSAGE.equals(active.getNotification().category)&&"Eidolon 随行".equals(extras.getString(Notification.EXTRA_SUB_TEXT))){for(Iterator<String> keys=counts.keys();keys.hasNext();){String key=keys.next();if(key.hashCode()==active.getId()){who=key;break;}}if(who.isEmpty()){manager.cancel(active.getId());continue;}}if(!who.isEmpty()){if(counts.optInt(who)==0)manager.cancel(active.getId());else current.put(who,active);}}
        if(!manager.areNotificationsEnabled())return;NotificationChannel channel=manager.getNotificationChannel(CHANNEL);if(channel==null){channel=new NotificationChannel(CHANNEL,"未读消息角标",NotificationManager.IMPORTANCE_LOW);channel.setShowBadge(true);channel.setSound(null,null);channel.enableVibration(false);channel.setDescription("静默保留未读数量，不重复播放消息提醒；桌面角标由启动器控制");manager.createNotificationChannel(channel);}
        boolean quietAllowed=channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
        String sql="SELECT m.character,m.conversation,m.sent_at FROM messages m WHERE m.id IN (SELECT max(id) FROM messages WHERE role='assistant' AND unread=1 GROUP BY character)";
        try(Cursor rows=store.getReadableDatabase().rawQuery(sql,null)){while(rows.moveToNext()){
            String who=rows.getString(0),session=rows.getString(1);int number=counts.optInt(who);if(number<=0)continue;StatusBarNotification existing=current.get(who);
            // Older versions did not tag their message notifications. Adopt by role ID.
            if(existing==null)for(StatusBarNotification item:manager.getActiveNotifications())if(item.getId()==who.hashCode()&&Notification.CATEGORY_MESSAGE.equals(item.getNotification().category)){existing=item;break;}
            Bundle meta=new Bundle();meta.putString(CHARACTER,who);meta.putString(SESSION,session);Notification.Builder builder;
            if(existing!=null){Bundle old=existing.getNotification().extras;if(existing.getNotification().number==number&&old!=null&&who.equals(old.getString(CHARACTER))&&session.equals(old.getString(SESSION))&&(existing.getNotification().flags&Notification.FLAG_AUTO_CANCEL)==0)continue;builder=Notification.Builder.recoverBuilder(c,existing.getNotification()).setNumber(number).setOnlyAlertOnce(true).setAutoCancel(false).setContentIntent(ChatEntry.pending(c,who,session)).addExtras(meta);}
            else{
                if(!quietAllowed)continue;
                if(!ReachConfig.read(c.getSharedPreferences("settings",0)).flag("notifications")){try(Cursor ordinary=store.getReadableDatabase().rawQuery("SELECT 1 FROM messages WHERE character=? AND role='assistant' AND unread=1 AND reach_at=0 LIMIT 1",new String[]{who})){if(!ordinary.moveToFirst())continue;}}
                JSONObject role=CharacterPrompts.original(c,who);builder=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle(role.getString("name")).setContentText(number+"条未读消息，点此查看").setCategory(Notification.CATEGORY_MESSAGE).setNumber(number).setBadgeIconType(Notification.BADGE_ICON_SMALL).setOnlyAlertOnce(true).setAutoCancel(false).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(ChatEntry.pending(c,who,session)).addExtras(meta);if(rows.getLong(2)>0)builder.setWhen(rows.getLong(2));android.graphics.Bitmap face=ReachOut.avatar(c,who);if(face!=null)builder.setLargeIcon(face);
            }
            manager.notify(who.hashCode(),builder.build());
        }}
    }catch(Exception e){android.util.Log.w("EidolonUnread","Badge notification sync unavailable",e);}}}
    private UnreadNotifications(){}
}
