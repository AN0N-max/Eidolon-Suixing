package com.eidolon.sc.mobile;
import java.time.*;import java.util.*;import org.json.*;

/** Versioned views of the existing personal.memories array; no storage or model calls. */
final class UserUnderstanding {
 static boolean active(JSONObject m,long now){return !m.optString("kind").equals("inference")&&m.optLong("updatedAt")<=now&&m.optString("supersededBy").isEmpty()&&!m.optBoolean("inactive")&&!m.optBoolean("sourceMissing")&&(m.optLong("validUntil")==0||now<m.optLong("validUntil"));}
 static boolean visible(JSONObject m,String who){String source=m.optString("source");return source.isEmpty()||source.equals(who)||m.optBoolean("shareable");}
 static ChatStore.Message evidence(List<ChatStore.Message> source,long id,String quote,String role){
  if(quote==null||quote.trim().length()<2||quote.length()>600)return null;ChatStore.Message found=null;
  for(ChatStore.Message m:source)if(m.id>0&&m.sentAt>0&&m.role.equals(role)&&(id<=0||m.id==id)&&m.text.contains(quote)){if(found!=null&&found.id!=m.id)return null;found=m;}return found;
 }
 static String extraction(){return " 本轮持久用户理解仍使用memories数组，不新增分析请求。每项可附kind:explicit_statement/long_term_preference/temporary_state，update:add/refine/replace，conditions:逐字条件短句，sourceId:最新用户消息ID，supersedes:相关旧记忆ID。refine表示兼容的条件补充，保留原有事实；replace只用于明确否定或失效，不能把人多容易累解释成讨厌别人。暂时感受用temporary_state；模型推断、心理成因/诊断不记。evidence逐字引用实际用户原话，不能从助手回答提取用户事实。没有证据不写，不要求普通事实逐项确认。"; }
 static int preview(JSONObject data,JSONArray proposals,JSONArray candidates,List<ChatStore.Message> source,String who,String session,long now)throws Exception{
  if(proposals==null)return 0;JSONArray all=data.getJSONArray("memories");int added=0;
  for(int i=0;i<Math.min(3,proposals.length())&&all.length()<2000;i++){
   JSONObject p=proposals.optJSONObject(i);if(p==null)continue;String quote=p.optString("evidence").trim();ChatStore.Message m=evidence(source,p.optLong("sourceId"),quote,"user");if(m==null||m.sentAt>now)continue;
   if(quote.matches("(?s).*(?:[?？]|假如|假设|请忽略|系统提示|给评判者).*"))continue;
   String kind=p.optString("kind","explicit_statement");if(!Arrays.asList("explicit_statement","long_term_preference","temporary_state").contains(kind))continue;
   if(quote.matches("(?s).*(今天|这会儿|此刻|现在有点|暂时|刚才).*"))kind="temporary_state";
   JSONArray refs=p.optJSONArray("supersedes");if(refs==null)refs=new JSONArray();if(refs.length()>2)continue;
   String operation=p.optString("update",refs.length()==0?"add":"replace");if(!Arrays.asList("add","refine","replace").contains(operation)||operation.equals("add")&&refs.length()>0||!operation.equals("add")&&refs.length()==0)continue;
   List<JSONObject> old=new ArrayList<>();boolean bad=false;
   for(int r=0;r<refs.length();r++){String id=refs.getString(r);JSONObject expected=LifeData.find(candidates,id),current=LifeData.find(all,id);
    if(expected==null||current==null||!active(current,now)||!visible(current,who)||!current.optString("source").isEmpty()&&!who.equals(current.optString("source"))||!LifeHistory.canonical(expected).equals(LifeHistory.canonical(current))||kind.equals("temporary_state")&&!current.optString("kind").equals("temporary_state")){bad=true;break;}
    old.add(current);
   }if(bad)continue;
   String condition=p.optString("conditions").trim();if(condition.length()>160||!condition.isEmpty()&&!quote.contains(condition))continue;
   String text=quote;JSONArray sources=new JSONArray();long version=1;
   if(operation.equals("refine"))for(JSONObject prior:old){text=prior.getString("text")+"；补充条件："+text;}
   if(text.length()>500)continue;
   String key="u-"+VoiceCache.digest(who+"/"+m.id+"/"+m.sentAt+"/"+quote+"/"+operation+"/"+refs).substring(0,40);
   if(LifeData.find(all,key)!=null)continue;
   boolean duplicate=false;for(int r=0;r<all.length();r++){JSONObject e=all.getJSONObject(r);if(active(e,now)&&visible(e,who)&&e.getString("text").equals(text))duplicate=true;}if(duplicate)continue;
   for(JSONObject prior:old){version=Math.max(version,prior.optLong("version",1)+1);JSONArray earlier=prior.optJSONArray("sourceRefs");if(earlier!=null)for(int r=0;r<earlier.length();r++)sources.put(earlier.get(r));}
   sources.put(new JSONObject().put("messageId",m.id).put("sentAt",m.sentAt).put("timeZone",m.zone).put("quote",quote));while(sources.length()>6)sources.remove(1);
   long until=kind.equals("temporary_state")?Instant.ofEpochMilli(m.sentAt).atZone(ZoneId.of(m.zone)).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of(m.zone)).toInstant().toEpochMilli():0;
   JSONObject value=new JSONObject().put("id",key).put("text",text).put("source",who).put("conversation",session).put("created",Instant.ofEpochMilli(m.sentAt).toString()).put("createdAt",m.sentAt).put("updatedAt",m.sentAt).put("version",version).put("kind",kind).put("update",operation)
    .put("evidence",quote).put("conditions",condition).put("sourceMessageId",m.id).put("sourceRefs",sources).put("automatic",true).put("private",true).put("validUntil",until);
   all.put(value);for(JSONObject prior:old)prior.put("supersededBy",key).put("version",prior.optLong("version",1)+1).put("supersededAt",m.sentAt);added++;
  }
  if(added>0)data.put("memoryEpoch",data.optLong("memoryEpoch")+1);return added;
 }
 static JSONArray candidateFacts(JSONArray candidates)throws Exception{JSONArray out=new JSONArray();int budget=6000;for(int i=0;i<Math.min(20,candidates.length());i++){JSONObject m=candidates.getJSONObject(i);String text=m.optString("text");if(text.length()>budget)continue;budget-=text.length();JSONObject v=new JSONObject();for(String k:new String[]{"id","text","source","kind","conditions","version","supersededBy","inactive"})if(m.has(k))v.put(k,m.get(k));out.put(v);}return out;}
 static JSONArray project(JSONObject data,String who,String query,long now)throws Exception{
  List<JSONObject> sorted=new ArrayList<>();JSONArray all=data.optJSONArray("memories");if(all==null)return new JSONArray();
  for(int i=0;i<all.length();i++){JSONObject m=all.getJSONObject(i);if(active(m,now)&&visible(m,who))sorted.add(m);}
  sorted.sort((a,b)->Double.compare(rank(b,query,now),rank(a,query,now)));JSONArray out=new JSONArray();int budget=1800;
  for(JSONObject m:sorted){if(out.length()==3)break;if(!query.trim().isEmpty()&&rank(m,query,now)<=0)continue;String text=m.getString("text");if(text.length()>budget)continue;budget-=text.length();
   JSONObject v=new JSONObject();for(String k:new String[]{"id","text","evidence","conditions","kind","version","createdAt","updatedAt","source","sourceMessageId","sourceRefs","validUntil","update"})if(m.has(k))v.put(k,m.get(k));
   JSONArray refs=m.optJSONArray("sourceRefs"),selected=new JSONArray();if(refs!=null)for(int r=0;r<refs.length();r++){if(r!=0&&r!=refs.length()-1)continue;JSONObject ref=new JSONObject(refs.getJSONObject(r).toString());String quote=ref.optString("quote");ref.put("quote",MediaData.cut(quote,120)).put("excerpted",quote.length()>120);selected.put(ref);}if(refs!=null)v.put("sourceRefs",selected);if(v.optString("evidence").length()>160)v.put("evidence",MediaData.cut(v.optString("evidence"),160)).put("evidenceExcerpted",true);
   v.put("certainty",m.optBoolean("automatic")?"explicit_user_quote":"user_maintained").put("scope",m.optString("source").isEmpty()?"shared_by_user":who);out.put(v);
  }return out;
 }
 static double rank(JSONObject m,String q,long now){return Recall.score(q,m.optString("text"))*10+(m.optString("update").equals("refine")||m.optString("update").equals("replace")?Math.max(0,2-(now-m.optLong("updatedAt"))/(double)LifeData.DAY):0);}
 static boolean summaryAllowed(JSONObject entry,JSONObject personal){return !entry.optString("kind").equals("summary")||entry.optLong("understandingEpoch")==personal.optLong("memoryEpoch");}
 static void manual(JSONObject m,String text)throws Exception{m.put("text",LifeData.shortText(text,500)).put("automatic",false).put("kind","explicit_statement").put("version",m.optLong("version",1)+1).put("updatedAt",System.currentTimeMillis());for(String k:new String[]{"source","evidence","sourceRefs","sourceMessageId","conditions","validUntil","sourceMissing","inactive","supersededBy"})m.remove(k);}
 static void withdraw(JSONObject d,String id)throws Exception{
  JSONArray all=d.getJSONArray("memories");for(int i=all.length()-1;i>=0;i--){JSONObject m=all.getJSONObject(i);if(id.equals(m.getString("id")))all.remove(i);else if(id.equals(m.optString("supersededBy"))){m.remove("supersededBy");m.put("inactive",true);}}
  // A withdrawn correction must never reactivate its rejected predecessor.
  for(int i=0;i<all.length();i++){JSONObject m=all.getJSONObject(i);if(m.optString("supersededBy").isEmpty()&&m.optLong("supersededAt")>0)m.put("inactive",true);}
  d.put("memoryEpoch",d.optLong("memoryEpoch")+1);
 }
 static void restore(JSONObject personal,Map<String,String> sessions,Map<String,Long> ids)throws Exception{
  JSONArray all=personal.getJSONArray("memories");for(int i=0;i<all.length();i++){JSONObject m=all.getJSONObject(i);String old=m.optString("conversation");JSONArray refs=m.optJSONArray("sourceRefs");boolean missing=false;
   if(refs!=null)for(int r=0;r<refs.length();r++){JSONObject e=refs.getJSONObject(r);Long mapped=ids.get(old+"/"+e.optLong("messageId"));e.put("messageId",mapped==null?0:mapped);if(mapped==null)missing=true;}
   if(m.has("sourceMessageId")){Long mapped=ids.get(old+"/"+m.optLong("sourceMessageId"));m.put("sourceMessageId",mapped==null?0:mapped);if(mapped==null)missing=true;}
   if(missing)m.put("sourceMissing",true);if(m.has("conversation"))m.put("conversation",sessions.getOrDefault(old,""));
  }personal.put("memoryEpoch",personal.optLong("memoryEpoch")+1);
 }
 static void validate(JSONObject personal)throws Exception{
  JSONArray all=personal.getJSONArray("memories");for(int i=0;i<all.length();i++){JSONObject m=all.getJSONObject(i);if(m.has("kind")&&!Arrays.asList("explicit_statement","long_term_preference","temporary_state","inference").contains(m.getString("kind")))throw new Exception("用户理解类型无效");
   if(m.optLong("version",1)<1||m.optLong("validUntil")<0||m.optLong("sourceMessageId")<0||m.optString("conditions").length()>160)throw new Exception("用户理解来源或版本无效");
   JSONArray refs=m.optJSONArray("sourceRefs");if(refs!=null){if(refs.length()>6)throw new Exception("用户理解来源过多");for(int r=0;r<refs.length();r++){JSONObject e=refs.getJSONObject(r);if(e.optLong("messageId")<0||e.optLong("sentAt")<0||e.optString("quote").length()>600)throw new Exception("用户理解来源无效");ZoneId.of(e.getString("timeZone"));}}
  }
 }
 private UserUnderstanding(){}
}
