package com.eidolon.sc.mobile;
import java.io.IOException;import java.time.*;import java.util.regex.*;import org.json.*;
/** Conservative no-network guard. Ambiguous greetings, quotations, naps and future plans pass. */
final class TimeConsistency {
 static String reason(String answer,JSONObject snapshot,long now){
  if(snapshot==null)return "";ZoneId zone;try{zone=ZoneId.of(snapshot.getString("timezone"));}catch(Exception e){return "";}
  int hour=Instant.ofEpochMilli(now).atZone(zone).getHour();JSONObject actual=snapshot.optJSONObject("actual_state"),next=snapshot.optJSONObject("next_planned_activity");
  boolean explicit=actual!=null&&actual.optBoolean("confirmed");JSONObject activity=snapshot.optJSONObject("planned_activity");boolean plannedLunch=activity!=null&&activity.optString("name").matches(".*(午餐|午饭).*" )&&activity.optLong("start")<=now&&now<activity.optLong("end");boolean explicitLunch=explicit&&actual.optString("name").matches(".*(夜班|午餐|午饭).*" );
  for(DialogueText.Part p:DialogueText.parse(answer)){
   if(p.literal||p.lang.equals("ja"))continue;
   // A quoted clause or a future/conditional sentence is not a claim about one's present action.
   for(String line:p.text.split("[。！？!?\\n]")){
    if(line.matches(".*[「」『』“”\"].*")||line.matches(".*(明天|后天|到时候|如果|假如|昨天|昨晚|之前|那天|午睡|补觉|夜班).*"))continue;
    if(!plannedLunch&&!explicitLunch&&(hour<9||hour>=17)&&line.matches(".*(?:我现在|现在我)(?:正|正在|就|要|准备|打算|去|吃|先|，|,|\\s){0,8}(?:吃|买|准备)(?:个|一下|点|好)?午[饭餐].*"))return "角色的当下用餐表述与角色时段冲突";
    if(next!=null&&!next.optBoolean("is_near",true)&&next.optString("name").matches(".*(午餐|午饭).*" )&&line.matches(".*我(?:现在|接下来)(?:就|马上)(?:要|准备)?去?吃午[饭餐].*"))return "角色把距离较远的午餐计划说成了马上行动";
    if(!explicit&&snapshot.optString("planned_state").equals("activity")&&snapshot.optJSONObject("planned_activity")!=null&&snapshot.optJSONObject("planned_activity").optString("name").matches(".*(上课|课程|演出|会议).*" )&&line.matches(".*(?:我现在|现在我)(?:正|正在|已经|要|准备|就|去|，|\\s){0,8}(?:睡觉|入睡|睡着).*"))return "角色明确的当下睡眠表述与当前活动冲突";
   }
  }return "";
 }
 static void require(String text,JSONObject snapshot,long now)throws IOException{String error=reason(text,snapshot,now);if(!error.isEmpty())throw new IOException(error+"；未自动重新生成，请手动重试");}
 private TimeConsistency(){}
}
