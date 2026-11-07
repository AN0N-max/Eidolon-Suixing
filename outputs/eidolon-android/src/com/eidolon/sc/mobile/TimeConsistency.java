package com.eidolon.sc.mobile;
import java.io.IOException;import java.time.*;import java.util.*;import org.json.*;

/** Validate scoped self-claims against evidence and time windows. No generation or paid retries. */
final class TimeConsistency {
 static String reason(String answer,JSONObject snapshot,long now){return reason(answer,snapshot,null,now);}
 static boolean negated(String text){return text.matches("(?s).*(还没|没有|并未|不在|尚未|まだ|していない|ていません).*");}
 static boolean nonPresent(String text){return text.matches("(?s).*(如果|假如|要是|明天|后天|昨天|昨晚|那天|以前|回忆|到时候|下周|等会儿|稍后|待会儿|昨日|明日|だったら|したら).*");}
 static boolean otherSubject(String text){return text.trim().matches("(?s)^(?:现在|刚才|这会儿)?[，,\\s]*(?:你|她|他|制作人|君|あなた|彼女|彼|プロデューサー).*" );}
 static String kind(String text){
  if(text.matches("(?s).*(钻进.{0,3}(被窝|被子)|躺(?:在|进)?(?:被窝|床)|(?:我|现在|已经).{0,4}(?:在被窝里|在被子里|在床上|躺下了)|已经上床|已经睡下|我.{0,5}(正在睡觉|已经睡着|准备睡觉)|(?:布団|ベッド).{0,4}(入った|横にな|潜り)|(?:私は|もう).{0,4}(?:寝るところ|布団の中)).*"))return "sleep";
  if(text.matches("(?s).*(?:刚|已经|正在|现在|我在|我这会儿).{0,8}(?:吃完|吃午饭|吃午餐|吃晚饭|吃早餐|吃饭|用餐).*"))return "meal";
  if(text.matches("(?s).*(?:刚|已经|现在|我).{0,6}(?:到家|回到家|帰宅した).*"))return "home";
  if(text.matches("(?s).*(?:我|刚|现在|这会儿|已经).{0,5}(?:出发了|出门了|马上出发|もう出発).*"))return "departure";
  return "";
 }
 static String supportedName(JSONObject snapshot){JSONObject actual=snapshot.optJSONObject("actual_state"),current=snapshot.optJSONObject("planned_activity");return (actual!=null&&actual.optBoolean("confirmed")?actual.optString("name"):"")+" "+(current==null?"":current.optString("name"));}
 static boolean supports(String kind,JSONObject snapshot,JSONObject basis,long now){
  JSONObject actual=snapshot.optJSONObject("actual_state");boolean override=actual!=null&&actual.optBoolean("confirmed")&&!actual.optString("name").isEmpty();String name=override?actual.optString("name"):supportedName(snapshot),mode=override?"explicit_override":snapshot.optString("planned_state");
  if(kind.equals("sleep"))return name.matches(".*(午睡|补觉|睡眠|睡觉|仮眠|夜班后休息).*" )||Arrays.asList("asleep","bedtime","woken").contains(mode);
  if(kind.equals("meal")&&name.matches(".*(早餐|午餐|晚餐|午饭|晚饭|用餐|吃饭|夜班).*"))return true;
  if(kind.equals("home")&&name.matches(".*(在家|到家|居家|家中|回家).*"))return true;
  if(kind.equals("departure")&&name.matches(".*(出发|出门|通勤|出行|移动|前往|赶路).*"))return true;
  JSONArray events=basis==null?null:basis.optJSONArray("events");if(events!=null)for(int i=0;i<events.length();i++){
   JSONObject e=events.optJSONObject(i);if(e==null||!e.optString("subject").equals("character"))continue;String phase=e.optString("conversationStatus"),type=e.optString("eventType");long end=e.optLong("expectedEnd");
   if(kind.equals("meal")&&type.equals("meal")&&Arrays.asList("completed","inferred_completed").contains(phase)&&end>0&&now>=end&&now-end<=2*EventContext.HOUR)return true;
   if(kind.equals("home")&&type.equals("commute")&&e.optString("title").contains("回家")&&Arrays.asList("completed","inferred_completed").contains(phase)&&end>0&&now-end<=2*EventContext.HOUR)return true;
  }return kind.equals("activity");
 }
 static String reason(String answer,JSONObject snapshot,JSONObject effects,long now){
  if(snapshot==null)return "";try{ZoneId.of(snapshot.getString("timezone"));}catch(Exception unknown){return "";}
  JSONObject basis=effects==null?null:effects.optJSONObject("replyBasis"),topic=basis==null?null:basis.optJSONObject("selectedTopic");
  if(topic!=null&&topic.optBoolean("sensitiveToCurrentState")&&!basis.optString("planRevision").equals(snapshot.optString("planRevision")))return "日程版本已变化，原当下话题不再适用";
  if(basis!=null&&basis.optString("task").equals("correct")&&answer.matches("(?s).*(以为|当成|勘違い).{0,10}(晚上|夜里|夜晚|夜だ|下午|白天).*"))return "纠错回复为先前错误补造了没有记录的时间误认原因";
  JSONArray claims=effects==null?null:effects.optJSONArray("claims");if(claims!=null)for(int i=0;i<claims.length();i++){
   JSONObject c=claims.optJSONObject(i);if(c==null||!Arrays.asList("now","recent").contains(c.optString("tense")))continue;String quote=c.optString("quote");if(!answer.contains(quote)||quote.isEmpty())return "当下自述引用与实际正文不符";
   if(!negated(quote)&&!nonPresent(quote)&&!otherSubject(quote)&&!supports(c.optString("kind"),snapshot,basis,now))return "角色当下自述缺少相应的日程或事件依据";
  }
  for(DialogueText.Part part:DialogueText.parse(answer)){
   if(part.literal)continue;
   for(String line:part.text.split("[。！？!?；;，,\\n]")){
    if(negated(line)||nonPresent(line)||otherSubject(line)||line.matches("(?s).*说[：:]?[“\"「『].*"))continue;
    // A future nap/reference is valid; it is not a statement that she is currently asleep.
    if(line.matches(".*(想起|想到|记得|打算|想去|等到|つもり|予定).*"))continue;
    String state=kind(line);if(!state.isEmpty()&&!supports(state,snapshot,basis,now))return "角色当下"+state+"表述与已有活动依据不一致";
    JSONObject next=snapshot.optJSONObject("next_planned_activity");if(next!=null&&!next.optBoolean("is_near",true)&&next.optString("name").matches(".*(午餐|午饭).*" )&&line.matches(".*我(?:现在|接下来)(?:就|马上)(?:要|准备)?去?吃午[饭餐].*"))return "角色把距离较远的午餐计划说成了马上行动";
    if(line.matches(".*(?:我现在|现在我)(?:正|正在|就|要|准备|打算|去|吃|先|\\s){0,8}(?:吃|买|准备)(?:个|一下|点|好)?午[饭餐].*" )&&!supports("meal",snapshot,basis,now))return "角色当下用餐表述缺少相应活动依据";
   }
  }return "";
 }
 static void require(String text,JSONObject snapshot,long now)throws IOException{require(text,snapshot,null,now);}
 static void require(String text,JSONObject snapshot,JSONObject effects,long now)throws IOException{String error=reason(text,snapshot,effects,now);if(!error.isEmpty())throw new IOException(error+"；本次未投放，未自动重新生成，请手动重试");}
 private TimeConsistency(){}
}
