package com.eidolon.sc.mobile;
import java.util.*;import org.json.*;

/** Inspectable task and evidence selection, rather than an instruction without actual material. */
final class InformationTurn {
 static JSONObject project(String user,List<ChatStore.Message> history,JSONArray retrieved,JSONObject selected)throws Exception{
  String task=ReplyBasis.task(user,false,null);boolean close=user.matches("(?s).*(先这样|回头聊|再见|不用回|晚安).*"),feeling=!user.matches("(?s).*[?？].*")&&user.matches("(?s).*(难过|开心|累了|害怕|想你|喜欢你|烦|寂寞).*"),question=task.equals("explain")||task.equals("answer");
  JSONArray materials=new JSONArray(),updates=new JSONArray();if(retrieved!=null)for(int i=0;i<retrieved.length()&&materials.length()<3;i++){JSONObject r=retrieved.getJSONObject(i);materials.put(new JSONObject().put("source","imported_corpus").put("provenance","local_import_not_independently_verified_canon").put("referenceId",r.optString("id")).put("title",MediaData.cut(r.optString("title"),150)).put("text",MediaData.cut(r.optString("text"),1400)));}
  List<ChatStore.Message> relevant=new ArrayList<>();for(ChatStore.Message m:history)if(m.role.equals("user")&&m.id>0&&m.text.length()>3&&(Recall.score(user,m.text)>0||m.text.matches("(?s).*(正文[:：]|资料[:：]|原文[:：]|补充|不是.{0,20}只是|条件是|改成|其实).*")))relevant.add(m);
  for(int i=Math.max(0,relevant.size()-3);i<relevant.size();i++){ChatStore.Message m=relevant.get(i);JSONObject fact=new JSONObject().put("source","user_message").put("sourceMessageId",m.id).put("at",m.sentAt).put("timeZone",m.zone).put("text",MediaData.cut(m.text,1000));if(m.text.matches("(?s).*(补充|不是.{0,20}只是|条件是|改成|其实).*"))updates.put(fact);else if(materials.length()<4)materials.put(fact);}
  if(selected!=null&&selected.optJSONObject("material")!=null)materials.put(new JSONObject().put("source",selected.optString("source")).put("material",selected.getJSONObject("material")));
  JSONArray gaps=new JSONArray();if(question&&materials.length()==0&&(selected==null||selected.optJSONObject("event")==null))gaps.put("当前未检索到问题对应的正文、具体资料或结果；一般人设只能支持意见，不能冒充事实");
  return new JSONObject().put("task",close?"natural_close":feeling?"receive_feeling":task).put("currentUser",MediaData.cut(user,3000)).put("availableMaterial",materials).put("recentConditionUpdates",updates).put("missingEvidence",gaps).put("sourcePolicy","user_material/imported_corpus/virtual_original/unknown_separate").put("clarificationLimit",1).put("requiresQuestionEnding",false).put("requiresAction",false);
 }
 private InformationTurn(){}
}
