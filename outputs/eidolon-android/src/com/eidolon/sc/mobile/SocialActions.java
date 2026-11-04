package com.eidolon.sc.mobile;
import android.content.*;import org.json.*;import java.util.*;import java.io.*;import java.util.concurrent.*;

/** Isolated local actions; selecting/reading pages never asks the language model. */
final class SocialActions {
 static final ExecutorService IO=Executors.newFixedThreadPool(2);
 static final Set<String> ACTIONS=new HashSet<>(Arrays.asList("socialDiagnose","socialGet","socialRead","socialLike","socialComment","socialDeleteComment","socialSettingsGet","socialSettingsSave","socialGenerate","socialCheck","socialRetry","socialMapOpen","socialSourceOpen","weatherGet","weatherSave","weatherRefresh","citySearch","citySave","cityClear"));
 static Object run(Context c,ChatStore s,String action,JSONObject args)throws Exception {
  long now=System.currentTimeMillis();
  if(action.equals("socialDiagnose"))return new SocialEngine().diagnose(c,s,args.getString("character"));
  if(action.equals("socialGet")){int offset=args.optInt("offset");if(offset<0||offset>5000)throw new IOException("分页位置无效");return SocialStore.timeline(s,now,offset);}
  if(action.equals("socialRead")){SocialStore.markRead(s,args.optJSONArray("posts")==null?new JSONArray():args.getJSONArray("posts"),args.optJSONArray("replies")==null?new JSONArray():args.getJSONArray("replies"),now);return new JSONObject().put("unseen",SocialStore.unseen(s,now));}
  if(action.equals("socialLike"))return SocialStore.like(s,args.getString("id"),args.getBoolean("liked"),now);
  if(action.equals("socialComment")){SocialStore.addComment(s,args.getString("id"),args.getString("text"),now,c.getSharedPreferences("settings",0).getBoolean("affinity",true));SocialScheduler.refresh(c);return SocialStore.shown(s,SocialStore.post(s,args.getString("id")),now);}
  if(action.equals("socialDeleteComment")){String id=args.getString("id");SocialStore.deleteComment(s,id,now);SocialEngine.cancelComment(id);return new JSONObject();}
  if(action.equals("socialSettingsGet"))return SocialStore.status(s,now);
  if(action.equals("socialSettingsSave")){
   Set<String> known=new HashSet<>();JSONArray chars=WritingEngine.characters(c);for(int i=0;i<chars.length();i++)known.add(chars.getJSONObject(i).getString("id"));
   SocialStore.saveConfig(s,args,known,now);SocialEngine own=SocialEngine.active;if(own!=null)own.stop();SocialScheduler.refresh(c);if(args.optBoolean("enabled"))SocialScheduler.check(c);return SocialStore.status(s,now);
  }
  if(action.equals("socialGenerate")){
   if(!args.optBoolean("confirmed"))throw new IOException("请先确认本次模型调用");SharedPreferences p=c.getSharedPreferences("settings",0);if(p.getString("url","").trim().isEmpty()||p.getString("model","").trim().isEmpty()||!Vault.configured(c,"key"))throw new IOException("请先在模型与接口配置聊天模型");
   String id=SocialEngine.manual(c,s,args.getString("character"),now);SocialScheduler.refresh(c);SocialScheduler.check(c);return SocialStore.status(s,now).put("queued",id);
  }
  if(action.equals("socialRetry")){
   if(!args.optBoolean("confirmed"))throw new IOException("请先确认本次模型调用");
   synchronized(SocialStore.LOCK){SocialStore.requireEnabled(s);JSONObject old=SocialStore.task(s,args.getString("id"));if(old==null||!Arrays.asList("FAILED","SKIPPED","CANCELLED").contains(old.optString("state")))throw new IOException("这条任务当前不能重试");
    if(old.getString("kind").equals("post"))SocialEngine.manual(c,s,old.getString("character"),now);
    else{String target=old.getString("target");JSONObject comment=SocialStore.commentById(s,target);if(comment==null||comment.optJSONObject("reply")!=null)throw new IOException("评论已删除或已经收到回复");
     if(SocialStore.count(s,"SELECT count(*) FROM social_tasks WHERE target=? AND kind='reply' AND state IN ('PENDING','RUNNING')",target)>0)throw new IOException("这条评论已有回复任务");
     if(SocialStore.used(s,SocialStore.budgetDay(s,now,false),"reply")>=SocialStore.config(s).getInt("repliesPerDay"))throw new IOException("今日回复请求额度已用完，失败与中断也计入额度");
     SocialStore.addTask(s,old.getString("character"),"reply",target,SocialData.day(now),now,now+2*SocialData.HOUR,new JSONObject().put("manual",true));}
   }SocialScheduler.refresh(c);SocialScheduler.check(c);return SocialStore.status(s,now);
  }
  if(action.equals("socialCheck")){SocialStore.requireEnabled(s);SocialScheduler.check(c);return SocialStore.status(s,now);}
  if(action.equals("socialMapOpen")){SocialStore.requireEnabled(s);JSONObject p=SocialStore.post(s,args.getString("id"));if(p.getLong("published")>now||p.optJSONObject("place")==null)throw new IOException("这条动态没有已发布地点");return new JSONObject().put("url",PlacePool.map(p.getJSONObject("place"),SocialStore.config(s).getString("map")));}
  if(action.equals("socialSourceOpen")){String source=args.getString("source"),url=source.equals("meteo")?"https://open-meteo.com/":source.equals("qweather")?"https://www.qweather.com/":source.equals("osm")?"https://www.openstreetmap.org/copyright":source.equals("upstream")?"https://github.com/Sallyn0225/eidolon-sc":"";if(url.isEmpty())throw new IOException("未知数据来源");return new JSONObject().put("url",url);}
  if(action.equals("weatherGet"))return WeatherRuntime.view(c,s);
  if(action.equals("weatherSave"))return WeatherRuntime.save(c,s,args);
  if(action.equals("weatherRefresh")){WeatherRuntime.known(c,s,true);return WeatherRuntime.view(c,s);}
  if(action.equals("citySearch")){long revision=SocialStore.weatherConfig(s).getLong("revision");JSONArray cities=WeatherRuntime.search(c,s,args.getString("query"));if(SocialStore.weatherConfig(s).getLong("revision")!=revision)throw new IOException("天气服务配置已改变，请重新查找");return new JSONObject().put("cities",cities).put("revision",revision);}
  if(action.equals("citySave")){synchronized(WeatherRuntime.CONFIG_LOCK){if(args.getLong("revision")!=SocialStore.weatherConfig(s).getLong("revision"))throw new IOException("天气服务配置已改变，请重新查找城市");JSONObject city=SocialData.city(args.getJSONObject("city"));SocialStore.meta(s,"city",city);WeatherRuntime.retainCityCache(s,SocialStore.weatherConfig(s),city);return WeatherRuntime.view(c,s);}}
  if(action.equals("cityClear")){synchronized(WeatherRuntime.CONFIG_LOCK){SocialStore.meta(s,"city",new JSONObject());WeatherRuntime.retainCityCache(s,SocialStore.weatherConfig(s),null);return WeatherRuntime.view(c,s);}}
  throw new IOException("未知朋友圈或天气操作");
 }
}
