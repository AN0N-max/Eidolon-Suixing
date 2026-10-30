package com.eidolon.sc.mobile;
import org.json.*;
/** Cheap intent/style constraints; actual wording and final conversational act remain semantic model work. */
final class BehaviorPlanner {
 static JSONObject plan(JSONObject life,JSONObject temporal,String user,boolean proactive)throws Exception{
  JSONObject mood=life.getJSONObject("emotion"),profile=life.getJSONObject("profile"),timing=temporal.optJSONObject("behavior");String availability=timing==null?"unknown":timing.optString("availability","unknown");
  boolean question=user.matches("(?s).*[？?].*")||user.matches("(?s).*(为什么|怎么|多少|是什么|告诉我|帮我).*"),distress=user.matches("(?s).*(难过|伤心|累死|焦虑|害怕|失眠|不开心).*"),ending=user.matches("(?s).*(先这样|回头聊|拜拜|晚安|再见).*"),busy=availability.equals("low");
  String intent=proactive?"share_experience":ending?"close_topic":distress?"acknowledge_feelings":question?"answer":LifeData.cue(life,temporal.optLong("timestamp"))!=null?"continue_or_share":"natural_response";
  String length=ending||busy||mood.getDouble("energy")<35?"brief":question?"as_needed":"short_conversational";
  return new JSONObject().put("goal",intent).put("length",length).put("availability",availability).put("interruptibility",timing==null?"unknown":timing.optString("interruptibility"))
   .put("energy",mood.getDouble("energy")).put("mood",mood.getString("label")).put("mood_intensity",Math.abs(mood.getDouble("valence"))).put("conversation_willingness",ending?"respect_closure":busy?"attentive_but_limited":"engaged")
   .put("temporal_relevance",proactive||user.matches("(?s).*(现在|今天|昨天|明天|刚才|几点|在干嘛).*" )?"high":"optional").put("expressiveness",profile.getInt("expressiveness"))
   .put("response_style",busy?"brief_attentive":mood.getDouble("valence")<-.25?"quiet_but_present":profile.getInt("expressiveness")>60?"expressive":"subtle").put("permitted_acts",new JSONArray(ending?"['close_topic','brief']":distress?"['acknowledge_feelings','brief']":"['answer','natural_response','share_experience','express_opinion','playful','transition','brief']")).put("avoid_unnecessary_questions",true).put("max_bubbles",proactive?1:3).put("no_anxiety_or_manipulation",true);
 }
 static String instruction(){return "\n行为目标是表达参考，不是固定台词。先照顾用户这句话的意图，也保留角色观点与合理边界。不要客服式总结、每轮建议、无必要的反问；仅在话题相关时分享经历。低精力或忙碌影响长度和语气，不用故意冷落、催回复或制造焦虑。稳定人设优先，生活状态不能重写身份；不同的表达强度不等于统一口癖。\n";}
 private BehaviorPlanner(){}
}
