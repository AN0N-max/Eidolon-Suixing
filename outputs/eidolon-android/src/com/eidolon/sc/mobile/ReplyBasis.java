package com.eidolon.sc.mobile;

import java.util.*;import java.time.*;import org.json.*;

/** One inspectable evidence/task/topic contract shared by chat, reach-outs and writing.
 * This class projects existing documents; it owns no database, network client or reward system. */
final class ReplyBasis {
 static String task(String user,boolean proactive,JSONObject answers){
  if(proactive)return "share";
  if(yes(answers,"turn_correction"))return "correct";
  if(yes(answers,"turn_detail"))return "explain";
  if(EventContext.CORRECTION.matcher(user).find())return "correct";
  if(user.matches("(?s).*(讲什么|什么内容|大概讲|为什么|怎么回事|具体|细节|解释).*"))return "explain";
  return user.matches("(?s).*[?？].*")?"answer":"respond";
 }
 static boolean yes(JSONObject answers,String key){try{return JevClient.probability(answers,key)>=.7;}catch(Exception e){return false;}}
 static JSONObject topic(JSONObject value)throws Exception{
  if(!value.optString("source").equals("virtual_original"))throw new Exception("原创梗概必须明确标记虚拟原创来源");
  JSONObject out=new JSONObject().put("source","virtual_original");
  for(String k:new String[]{"title","synopsis","characters","conflict","progress","feeling"}){
   Object raw=value.opt(k);if(raw!=null&&!(raw instanceof String))throw new Exception("梗概字段必须是文本");String s=value.optString(k).trim();int limit=k.equals("synopsis")?500:k.equals("title")?80:180;
   if(s.length()>limit||Arrays.asList("title","synopsis").contains(k)&&s.isEmpty())throw new Exception("原创梗概为空或过长");if(!s.isEmpty())out.put(k,s);
  }return out;
 }
 static JSONObject build(JSONObject temporal,JSONObject life,List<ChatStore.Message> history,String user,boolean proactive,JSONArray retrieved,JSONObject answers)throws Exception{
  long now=temporal.optLong("timestamp");String zone=temporal.optString("timezone","UTC"),mode=task(user,proactive,answers);JSONArray events=new JSONArray(),raw=life.optJSONArray("allEvents");
  if(raw==null)raw=life.optJSONArray("events");if(raw!=null)for(int i=0;i<raw.length();i++){
   JSONObject e=raw.getJSONObject(i);if(e.optString("kind").equals("conversation"))events.put(EventContext.view(e,now,zone));
   else if(e.optLong("start")>0){JSONObject v=EventContext.view(e,now,zone).put("subject","character");if(e.optLong("at")>0)v.put("actualAt",e.getLong("at"));events.put(v);}
  }
  JSONObject selected=new JSONObject().put("source","conversation").put("kind","ordinary_response").put("task",mode).put("eventId","").put("sensitiveToCurrentState",false);
  JSONObject chosen=null;double best=-Double.MAX_VALUE;
  for(int i=0;i<events.length();i++){
   JSONObject e=events.getJSONObject(i);String phase=e.optString("conversationStatus");
   if(proactive&&(phase.equals("cancelled")||phase.equals("future_plan")||phase.equals("missed")||phase.equals("source_missing")||phase.equals("time_unknown")||phase.equals("elapsed_unconfirmed")||e.optBoolean("alreadyAsked")))continue;
   double score=Recall.score(user,e.optString("title"))*100;
   if(proactive){if(e.optString("subject").equals("character")&&e.optString("id").equals(life.optJSONObject("actual_simulated_activity")==null?"":life.getJSONObject("actual_simulated_activity").optString("eventId")))score+=25;
    if(e.optString("subject").equals("user")&&(phase.equals("inferred_completed")||phase.equals("completed")||phase.equals("elapsed_result_unknown")))score+=18;
   }
   if(user.matches("(?s).*(台本|剧本|脚本).*" )&&e.optString("eventType").equals("script"))score+=30;
   long at=e.optLong("mentionedAt",e.optLong("expectedStart"));score+=Math.max(0,6-(now-at)/(double)EventContext.DAY);
   if(score>best){best=score;chosen=e;}
  }
  if(chosen!=null&&(proactive||best>8))selected.put("eventId",chosen.getString("id")).put("eventVersion",chosen.optLong("eventVersion",1)).put("viewVersion",chosen.getString("viewVersion"))
   .put("kind",chosen.optString("subject").equals("user")?"follow_user_event":"share_character_activity").put("event",chosen)
   .put("source",chosen.optString("source")).put("sensitiveToCurrentState",chosen.optString("conversationStatus").equals("in_progress")||chosen.optString("conversationStatus").equals("within_expected_window"));
  if(proactive&&selected.optString("eventId").isEmpty()){
   JSONObject activity=temporal.optJSONObject("planned_activity"),previous=temporal.optJSONObject("previous_planned_activity");
   selected.put("kind","schedule_grounded_share").put("source","stored_plan").put("sensitiveToCurrentState",true)
    .put("current",activity==null?JSONObject.NULL:activity).put("previous",previous==null?JSONObject.NULL:previous).put("detailPolicy","only_known_activity_no_invented_sensory_detail");
  }
  if(user.matches("(?s).*(台本|剧本|脚本).*")){
   JSONObject material=null;String source="";
   if(material==null)for(int i=history.size()-1;i>=0;i--){ChatStore.Message m=history.get(i);if(m.role.equals("user")&&m.text.matches("(?s).*(台本内容[:：]|剧本正文[:：]|以下是.*台本|原文[:：]).*")){material=new JSONObject().put("sourceMessageId",m.id).put("text",MediaData.cut(m.text,2400));source="user_material";break;}}
   if(material==null&&retrieved!=null)for(int ri=0;ri<retrieved.length();ri++){JSONObject r=retrieved.getJSONObject(ri);String content=r.optString("title")+" "+r.optString("text");if(!content.matches("(?s).*(台本|剧本|脚本|脚本正文).*"))continue;material=new JSONObject().put("title",r.optString("title")).put("text",MediaData.cut(r.optString("text"),2400)).put("referenceId",r.optString("id"));source="imported_corpus";material.put("provenance","local_import_not_independently_verified_canon");break;}
   if(material==null&&chosen!=null&&chosen.optJSONObject("topic")!=null){material=chosen.getJSONObject("topic");source=material.optString("source");}
   if(material==null&&temporal.optJSONObject("planned_activity")!=null&&temporal.getJSONObject("planned_activity").optJSONObject("topic")!=null){material=temporal.getJSONObject("planned_activity").getJSONObject("topic");source=material.optString("source");}
   boolean allow=user.matches("(?s).*(原创|你自己写|虚拟.*台本|编一个台本).*" )&&chosen!=null&&chosen.optString("subject").equals("character");
   selected.put("kind",material==null?"missing_material":"explain_material").put("source",material==null?"missing":source).put("material",material==null?JSONObject.NULL:material)
    .put("missingFields",material==null?new JSONArray().put("正文或明确梗概").put("人物及冲突"):new JSONArray()).put("allowOriginal",allow)
    .put("clarification",material==null?"你指的是哪部原作的台本，还是想聊虚拟日常里的原创台本？":"");
   mode=material==null&&!allow?"clarify":"explain";selected.put("task",mode);
  }
  JSONObject out=new JSONObject().put("schema",1).put("now",now).put("timeZone",zone).put("task",mode).put("events",events).put("selectedTopic",selected)
   .put("planRevision",temporal.optString("planRevision")).put("previousActivity",temporal.opt("previous_planned_activity")).put("currentActivity",temporal.opt("planned_activity"))
   .put("nextActivity",temporal.opt("next_planned_activity")).put("recentExpressions",life.optJSONArray("recentExpressions")==null?new JSONArray():life.getJSONArray("recentExpressions"))
   .put("additionalJudgeRequests",0).put("inferenceEffects","conversation_only_no_reward_affinity_or_memory_settlement");
  return out;
 }
 static String prompt(JSONObject basis){return "\nBEGIN_EIDOLON_REPLY_BASIS\n"+basis+"\nEND_EIDOLON_REPLY_BASIS\n本轮先完成task：解释时先回答内容与细节，纠错时承认记录并采用新状态，分享时只围绕selectedTopic的一件有依据的事。推定完成可自然追问体验，不能说用户已确认；elapsed_result_unknown只问感受，不默认获胜通过。按事件的eventDate/dateLabel引用过去，mentionedAt不是发生时间。缺资料时准确指出缺少什么并提出一个具体澄清问题，不用撒娇、道歉或让用户替你想台词回避问题。虚拟原创必须明确来源，不能冒充原作；仅allowOriginal=true时可在同一请求中准备topic_update。不同角色保留各自口吻；用户未提供的童年、家庭或人格成因不要推断，也不编造角色亲历来套安慰结构。先回应当前问题，再自然表达必要感受，不套固定段落、不强行短句。";}
 static void attach(JSONObject request,JSONObject basis)throws Exception{JSONObject system=request.getJSONArray("messages").getJSONObject(0);system.put("content",system.getString("content")+prompt(basis));}
 static void finish(String raw,String answer,JSONObject basis,JSONObject effects)throws Exception{
  String value=raw.trim();if(value.startsWith("```")){int n=value.indexOf('\n');if(n>0&&value.endsWith("```"))value=value.substring(n+1,value.length()-3).trim();}
  effects.put("replyBasis",basis);if(value.startsWith("{")){JSONObject data=new JSONObject(value);effects.put("claims",claims(data,answer));JSONObject topic=topicUpdate(data,basis,answer);if(topic!=null)effects.put("topicUpdate",topic);}else effects.put("claims",new JSONArray());
 }
 static JSONArray projectedEvents(JSONObject state,String user,long now)throws Exception{
  List<JSONObject> all=new ArrayList<>();JSONArray events=state.getJSONArray("events");for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);if(!state.optBoolean("enabled")&&!e.optString("kind").equals("conversation"))continue;all.add(e);}
  all.sort((a,b)->Double.compare(rawScore(b,user,now),rawScore(a,user,now)));JSONArray out=new JSONArray();for(int i=0;i<Math.min(12,all.size());i++)out.put(new JSONObject(all.get(i).toString()));return out;
 }
 static double rawScore(JSONObject e,String user,long now){return Recall.score(user,e.optString("title"))*100+(e.optLong("start")<=now&&now<e.optLong("end")?20:0)+(e.optString("kind").equals("conversation")?5:0)+Math.max(0,5-(now-Math.max(e.optLong("sourceMentionedAt"),e.optLong("start")))/(double)EventContext.DAY);}
 static JSONArray claims(JSONObject raw,String answer)throws Exception{
  JSONArray input=raw.optJSONArray("claims"),out=new JSONArray();if(input==null)return out;if(input.length()>6)throw new Exception("当下自述标注过多");
  for(int i=0;i<input.length();i++){JSONObject c=input.getJSONObject(i);String kind=c.optString("kind"),tense=c.optString("tense"),quote=c.optString("quote");if(!Arrays.asList("sleep","meal","home","departure","activity").contains(kind)||!Arrays.asList("now","recent","past","future").contains(tense)||quote.length()<2||quote.length()>300||!answer.contains(quote))throw new Exception("当下自述缺少正文对应依据");out.put(new JSONObject().put("kind",kind).put("tense",tense).put("quote",quote).put("eventId",c.optString("eventId")));}return out;
 }
 static JSONObject topicUpdate(JSONObject raw,JSONObject basis,String answer)throws Exception{
  JSONObject proposed=raw.optJSONObject("topic_update"),selected=basis==null?null:basis.optJSONObject("selectedTopic");if(proposed==null)return null;
  if(selected==null||!selected.optBoolean("allowOriginal")||!selected.optString("eventId").equals(proposed.optString("eventId")))throw new Exception("本轮没有可关联的原创台本授权");
  JSONObject value=topic(proposed);String quote=proposed.optString("quote");if(quote.length()<2||quote.length()>300||!answer.contains(quote))throw new Exception("原创梗概缺少本次正文引用");return value.put("eventId",selected.getString("eventId")).put("eventVersion",selected.optLong("eventVersion",1));
 }
 static String revalidate(JSONObject staged,JSONObject current,long now){
  if(staged==null||current==null)return "";JSONObject topic=staged.optJSONObject("selectedTopic");if(topic==null)return "";
  if(topic.optBoolean("sensitiveToCurrentState")&&!staged.optString("planRevision").equals(current.optString("planRevision")))return "日程版本已改变，原当下话题不再适用";
  String id=topic.optString("eventId");if(!id.isEmpty()){
   JSONObject fresh=LifeData.find(current.optJSONArray("events"),id);if(fresh==null)return "话题事件已变化或不再可用";
   if(fresh.optLong("eventVersion",1)!=topic.optLong("eventVersion",1))return "话题事件已有新的纠正";
   if(topic.optBoolean("sensitiveToCurrentState")&&!fresh.optString("viewVersion").equals(topic.optString("viewVersion")))return "投放已跨过原活动时间窗口";
  }else if(topic.optBoolean("sensitiveToCurrentState")&&!activityKey(staged.optJSONObject("currentActivity")).equals(activityKey(current.optJSONObject("currentActivity"))))return "投放已跨过原日程活动窗口";return "";
 }
 static String activityKey(JSONObject a){return a==null?"":a.optString("name")+"/"+a.optString("start")+"/"+a.optString("end");}
 static boolean delivered(JSONObject state,JSONObject effect,String answer,long messageId,long now)throws Exception{
  if(effect==null)return false;boolean changed=false;JSONObject basis=effect.optJSONObject("replyBasis"),selected=basis==null?null:basis.optJSONObject("selectedTopic");
  if(selected!=null&&!selected.optString("eventId").isEmpty()){
   JSONObject e=EventContext.byId(state,selected.getString("eventId"));if(e!=null&&e.optLong("eventVersion",1)==selected.optLong("eventVersion",1)&&e.optLong("lastAskedMessageId")!=messageId&&answer.matches("(?s).*[?？].*")){e.put("lastAskedAt",now).put("lastAskedVersion",selected.optString("viewVersion")).put("lastAskedMessageId",messageId);changed=true;}
  }
  JSONObject update=effect.optJSONObject("topicUpdate");if(update!=null){JSONObject e=EventContext.byId(state,update.optString("eventId"));if(e!=null&&e.optLong("eventVersion",1)==update.optLong("eventVersion",1)){JSONObject t=new JSONObject(update.toString());t.remove("eventId");t.remove("eventVersion");e.put("topic",t).put("eventVersion",e.optLong("eventVersion",1)+1);changed=true;}}
  JSONArray claims=effect.optJSONArray("claims");if(claims!=null&&claims.length()>0){JSONArray recent=state.optJSONArray("recentExpressions");if(recent==null)recent=new JSONArray();for(int i=0;i<claims.length();i++){JSONObject c=claims.getJSONObject(i);boolean duplicate=false;for(int r=0;r<recent.length();r++)if(recent.getJSONObject(r).optLong("messageId")==messageId&&recent.getJSONObject(r).optString("quote").equals(c.getString("quote")))duplicate=true;if(duplicate)continue;recent.put(new JSONObject().put("messageId",messageId).put("at",now).put("kind",c.getString("kind")).put("tense",c.getString("tense")).put("quote",c.getString("quote")));}while(recent.length()>8)recent.remove(0);state.put("recentExpressions",recent);changed=true;}
  return changed;
 }
 private ReplyBasis(){}
}
