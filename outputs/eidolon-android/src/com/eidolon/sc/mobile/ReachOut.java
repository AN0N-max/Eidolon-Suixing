package com.eidolon.sc.mobile;
import android.content.*;import android.app.*;import android.app.job.*;import android.database.Cursor;import android.graphics.Bitmap;import android.graphics.BitmapFactory;import org.json.*;import java.util.*;
final class ReachOut {
 static volatile ModelClient active;
 static boolean generating(){return !WebActivity.activeGenerations.isEmpty()||ReplyFallback.generating()||WritingEngine.running.get()||NotebookEngine.running.get();}
 static void cancel(){ModelClient c=active;if(c!=null)c.cancel();}
 static volatile boolean foreground=false,chatBusy=false;
 static final java.util.concurrent.atomic.AtomicBoolean running=new java.util.concurrent.atomic.AtomicBoolean();
 static void schedule(Context c){ReachScheduler.refresh(c);}
 // Legacy callers now only request a durable planner check; no second AI execution path.
 static JSONObject run(Context c,boolean background)throws Exception{ReachScheduler.refresh(c);return null;}
 static void status(android.content.SharedPreferences prefs,String message){prefs.edit().putString("reachLastStatus",ReplyTrace.time(System.currentTimeMillis())+" · "+message).apply();}
 static boolean awaitingReply(ChatStore store,String session){try(Cursor row=store.getReadableDatabase().rawQuery("SELECT coalesce(max(CASE WHEN reach_at>0 THEN id END),0),coalesce(max(CASE WHEN role='user' THEN id END),0) FROM messages WHERE conversation=?",new String[]{session})){row.moveToFirst();return row.getLong(0)>row.getLong(1);}}
 static int sent(ChatStore store,ReachConfig config,String who,long now){String sql="SELECT count(*) FROM messages WHERE reach_at>=?";String[] args={""+ReachPolicy.dayStart(config,now)};if(who!=null){sql+=" AND character=?";args=new String[]{args[0],who};}try(Cursor count=store.getReadableDatabase().rawQuery(sql,args)){count.moveToFirst();return count.getInt(0);}}
 static boolean quotaAllowed(ChatStore store,ReachConfig config,String who,long now){return sent(store,config,null,now)<config.integer("dailyLimit")&&(who==null||sent(store,config,who,now)<ReachPlanRules.limit(config,who));}
 static JSONObject options(Context context,ChatStore store)throws Exception{android.content.SharedPreferences p=context.getSharedPreferences("settings",0);ReachConfig c=ReachConfig.read(p);long now=System.currentTimeMillis();return c.json().put("overlayAllowed",OverlayBanner.allowed(context)).put("sentToday",sent(store,c,null,now)).put("attemptsToday",ReachBudget.used(p,c,now)).put("lastStatus",p.getString("reachLastStatus","")).put("quotaDate",ReachPolicy.date(c,now));}
 static String preview(JSONObject message){
  String raw=message.optString("text"),text="";
  java.util.regex.Matcher zh=java.util.regex.Pattern.compile("<zh>(.*?)</zh>",java.util.regex.Pattern.DOTALL).matcher(raw);
  while(zh.find())text+=zh.group(1)+" ";
  return (text.isEmpty()?raw:text).replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").trim();
 }
 static Bitmap avatar(Context c,String who){
  if(!who.matches("[a-z0-9-]+"))return null;
  try(java.io.InputStream in=c.getAssets().open("avatars/"+who+".jpg")){return BitmapFactory.decodeStream(in);}catch(Exception e){return null;}
 }
 static boolean notify(Context c,JSONObject message,boolean overlayShown){try{synchronized(UnreadNotifications.LOCK){return notifyLocked(c,message,overlayShown);}}finally{if(!message.optBoolean("test"))LauncherBadge.refresh(c);}}
 private static boolean notifyLocked(Context c,JSONObject message,boolean overlayShown){
  NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  if(message.optBoolean("proactive")&&!ReachConfig.read(c.getSharedPreferences("settings",0)).flag("notifications"))return false;
  String channel=ReachAlert.channel(c,nm,overlayShown);if(!nm.areNotificationsEnabled()||nm.getNotificationChannel(channel).getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
  PendingIntent open=ChatEntry.pending(c,message.optString("character"),message.optString("session"));
  String text=ReachConfig.read(c.getSharedPreferences("settings",0)).flag("privacy")?"有一条新的角色消息":preview(message);int unread=0;try(ChatStore store=new ChatStore(c)){unread=UnreadState.count(store,message.optString("character"));}
  if(unread<=0&&!message.optBoolean("test")){nm.cancel(message.optString("character").hashCode());return false;}
  Notification.Builder builder=new Notification.Builder(c,channel).setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle(message.optString("name")).setContentText(text).setSubText("Eidolon 随行").setStyle(new Notification.BigTextStyle().bigText(text)).setNumber(unread).setCategory(Notification.CATEGORY_MESSAGE).setContentIntent(open).setAutoCancel(false).setVisibility(Notification.VISIBILITY_PRIVATE).setWhen(System.currentTimeMillis()).setShowWhen(true);
  android.os.Bundle badgeMeta=new android.os.Bundle();badgeMeta.putString(UnreadNotifications.CHARACTER,message.optString("character"));badgeMeta.putString(UnreadNotifications.SESSION,message.optString("session"));builder.addExtras(badgeMeta).setBadgeIconType(Notification.BADGE_ICON_SMALL);
  Bitmap face=avatar(c,message.optString("character"));if(face!=null)builder.setLargeIcon(face);
  try{nm.notify(message.optString("character").hashCode(),builder.build());return true;}catch(SecurityException e){android.util.Log.w("EidolonReach","Notification permission denied",e);return false;}
 }
}
