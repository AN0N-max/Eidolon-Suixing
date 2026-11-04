package com.eidolon.sc.mobile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Pure validation before any restore writes. Credentials and pending requests are excluded. */
final class SocialBackup {
 static long integer(JSONObject o,String key,long min,long max,boolean required)throws Exception {
  if(!o.has(key)&&!required)return min;
  Object value=o.opt(key);
  if(!(value instanceof Number))throw new IOException("朋友圈备份数字无效："+key);
  double n=((Number)value).doubleValue();long result=((Number)value).longValue();
  if(!Double.isFinite(n)||n!=result||result<min||result>max)throw new IOException("朋友圈备份数字越界："+key);
  return result;
 }
 static long stamp(JSONObject o,String k,boolean required)throws Exception{return integer(o,k,0,4102444800000L,required);}
 static String id(JSONObject o,String key)throws Exception {
  String s=SocialData.text(o,key,100,true);
  if(!s.matches("[A-Za-z0-9_.:/-]+"))throw new IOException("朋友圈备份编号无效");return s;
 }
 static void day(String value)throws Exception {LocalDate d=LocalDate.parse(value);if(d.getYear()<1900||d.getYear()>2100)throw new IOException("朋友圈备份日期越界");}
 static void keys(JSONObject o,String...allowed)throws Exception {
  Set<String> set=new HashSet<>(Arrays.asList(allowed));for(Iterator<String> i=o.keys();i.hasNext();)if(!set.contains(i.next()))throw new IOException("朋友圈备份含未知字段");
 }
 static void bools(JSONObject o,String...names)throws Exception {for(String n:names)if(o.has(n)&&!(o.opt(n) instanceof Boolean))throw new IOException("朋友圈备份开关无效");}
 static JSONObject weather(JSONObject o)throws Exception {
  keys(o,"enabled","tokyo","user","host","auth","kid","developer","project","revision");bools(o,"enabled","tokyo","user");
  if(o.has("host"))WeatherClient.host(SocialData.text(o,"host",200,false));
  if(o.has("auth")&&!Arrays.asList("jwt","key").contains(o.getString("auth")))throw new IOException("天气认证方式无效");
  for(String k:new String[]{"kid","developer","project"})if(o.has(k)){String v=SocialData.text(o,k,100,false);if(v.contains("\n")||v.contains("\r"))throw new IOException("天气凭据编号无效");}
  integer(o,"revision",0,1000000000000L,false);return new JSONObject(o.toString());
 }
 static void reads(JSONObject r,Map<String,Long> posts,Map<String,Long> replies)throws Exception {
  keys(r,"posts","replies");for(String k:new String[]{"posts","replies"}) {
   JSONObject map=r.optJSONObject(k);if(r.has(k)&&map==null)throw new IOException("朋友圈阅读状态无效");if(map==null)continue;
   if(map.length()>5000)throw new IOException("朋友圈阅读状态过多");Map<String,Long> source=k.equals("posts")?posts:replies;
   for(Iterator<String> i=map.keys();i.hasNext();){String id=i.next();long at=stamp(map,id,true);if(!source.containsKey(id)||source.get(id)!=at)throw new IOException("朋友圈阅读引用无效");}
  }
 }
 static void validate(JSONObject data,Set<String> known)throws Exception {
  JSONObject root=data.getJSONObject("social");if(root.toString().length()>6*1024*1024)throw new IOException("朋友圈备份过大");
  keys(root,"moments","weather","city","reads","budgetClock","posts","comments","circle","usage","plans");
  JSONObject cfg=root.getJSONObject("moments");keys(cfg,"enabled","perDay","repliesPerDay","keepDays","map","characters","revision","allCharacters");bools(cfg,"enabled","allCharacters");
  for(String k:new String[]{"perDay","repliesPerDay","keepDays"})if(cfg.has(k))integer(cfg,k,0,90,true);
  integer(cfg,"revision",0,1000000000000L,false);JSONObject normalized=SocialData.settings(cfg);Set<String> selected=new HashSet<>();
  JSONArray roles=normalized.getJSONArray("characters");for(int i=0;i<roles.length();i++){String who=roles.getString(i);if(!known.contains(who)||!selected.add(who))throw new IOException("朋友圈参与角色无效");}
  weather(root.getJSONObject("weather"));if(root.getJSONObject("city").length()>0)SocialData.city(root.getJSONObject("city"));
  JSONObject budget=root.getJSONObject("budgetClock");keys(budget,"day");if(budget.has("day"))day(budget.getString("day"));
  Map<String,Long> posts=new HashMap<>(),replies=new HashMap<>();Set<String> ids=new HashSet<>(),roleDays=new HashSet<>();
  JSONArray a=root.getJSONArray("posts");if(a.length()>5000)throw new IOException("动态过多");
  for(int i=0;i<a.length();i++) {
   JSONObject p=a.getJSONObject(i);String id=id(p,"id"),who=p.getString("character"),date=p.getString("day");day(date);SocialData.storedPair(p);bools(p,"liked");
   if(!known.contains(who)||posts.put(id,stamp(p,"published",true))!=null||!roleDays.add(who+"/"+date))throw new IOException("动态角色或编号重复");
   JSONObject place=p.optJSONObject("place");if(place!=null){SocialData.coordinate(place.getDouble("lat"),place.getDouble("lon"));SocialData.text(place,"name",120,true);SocialData.text(place,"id",100,true);}
  }
  a=root.getJSONArray("comments");if(a.length()>5000)throw new IOException("评论过多");Set<String> commented=new HashSet<>();
  for(int i=0;i<a.length();i++) {
   JSONObject c=a.getJSONObject(i);String id=id(c,"id"),post=id(c,"post");
   if(!posts.containsKey(post)||!ids.add(id)||!commented.add(post))throw new IOException("评论引用无效");SocialData.comment(c.getString("text"));stamp(c,"at",true);
   if(c.has("reply")){SocialData.storedPair(c.getJSONObject("reply"));replies.put(id,stamp(c,"replyAt",true));}else if(c.has("replyAt"))throw new IOException("评论回复时间缺少正文");
  }
  a=root.getJSONArray("circle");if(a.length()>80000)throw new IOException("互动过多");ids.clear();
  for(int i=0;i<a.length();i++) {
   JSONObject c=a.getJSONObject(i);if(!posts.containsKey(id(c,"post"))||!known.contains(c.getString("character"))||!ids.add(id(c,"id"))||!Arrays.asList("like","comment").contains(c.getString("kind")))throw new IOException("互动引用无效");
   stamp(c,"shown",true);if(c.getString("kind").equals("comment"))SocialData.storedPair(c);
  }
  reads(root.getJSONObject("reads"),posts,replies);
  a=root.getJSONArray("usage");if(a.length()>50000)throw new IOException("请求记录过多");ids.clear();
  for(int i=0;i<a.length();i++) {JSONObject c=a.getJSONObject(i);keys(c,"id","day","kind","character","at");if(!ids.add(id(c,"id"))||!known.contains(c.getString("character"))||!Arrays.asList("post","reply").contains(c.getString("kind")))throw new IOException("请求记录无效");day(c.getString("day"));stamp(c,"at",true);}
  a=root.getJSONArray("plans");if(a.length()>50000)throw new IOException("计划记录过多");ids.clear();
  for(int i=0;i<a.length();i++) {JSONObject c=a.getJSONObject(i);keys(c,"day","at");String day=c.getString("day");day(day);if(!ids.add(day))throw new IOException("计划日期重复");stamp(c,"at",true);}
 }
}
