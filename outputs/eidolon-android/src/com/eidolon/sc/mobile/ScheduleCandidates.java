package com.eidolon.sc.mobile;
import android.content.*;import java.time.*;import java.util.*;import org.json.*;

/** Read-only admission/diagnostics. Waiting delivery is not active model work. */
final class ScheduleCandidates {
 static boolean occupied(ChatStore s){return ScheduleEngine.running.get();}
 static String intrinsic(Context c,ChatStore s,JSONObject personal,String who,long now)throws Exception{
  ZonedDateTime at=Instant.ofEpochMilli(now).atZone(TemporalRuntime.zone(c,personal,who));if(at.isBefore(DailyScheduleClock.boundary(at.toLocalDate(),at.getZone())))return ScheduleQueueRules.intrinsic(at,now,false,0,false,false);
  List<ChatStore.Conversation> conversations=s.conversations(who);boolean talked=!conversations.isEmpty()&&ScheduleStore.talked(s,who),ended=!conversations.isEmpty()&&Progression.session(personal,conversations.get(0).id).optBoolean("ended");
  String day=at.toLocalDate().toString();return ScheduleQueueRules.intrinsic(at,now,talked,s.lastActivity(who),ended,ScheduleStore.entry(s,who,day)!=null||ScheduleAttempts.get(s,who,day)!=null);
 }
 static List<JSONObject> list(Context c,ChatStore s,JSONObject personal,JSONArray chars,long now)throws Exception{
  List<JSONObject> out=new ArrayList<>();for(int i=0;i<chars.length();i++){JSONObject p=chars.getJSONObject(i);if(intrinsic(c,s,personal,p.getString("id"),now).isEmpty())out.add(p);}
  out.sort((a,b)->Long.compare(s.lastActivity(b.optString("id")),s.lastActivity(a.optString("id"))));return out;
 }
 static boolean configured(Context c){SharedPreferences p=c.getSharedPreferences("settings",0);String url=p.getString("backgroundUrl","").trim(),model=p.getString("backgroundModel",p.getString("analysisModel","")).trim();return !(url.isEmpty()?p.getString("url","").trim():url).isEmpty()&&!(model.isEmpty()?p.getString("model","").trim():model).isEmpty()&&(Vault.configured(c,"backgroundKey")||url.isEmpty()&&Vault.configured(c,"key"));}
 static JSONObject status(Context c,ChatStore s,JSONObject personal,String who,String day,long now)throws Exception{
  SharedPreferences prefs=c.getSharedPreferences("settings",0);JSONObject row=ScheduleStore.entry(s,who,day),attempt=ScheduleAttempts.get(s,who,day);String code,message;
  if(!prefs.getBoolean("hibiSchedules",false)){code="off";message="角色行程已关闭。";}
  else if(row!=null&&row.optJSONObject("plan")!=null){code="ready";message="这一天的行程已保存。";}
  else if(attempt!=null&&attempt.optString("state").equals("RUNNING")){code="running";message="正在安排行程，请等待本次结果。";}
  else if(attempt!=null||row!=null){code="failed";message=attempt!=null?attempt.optString("error"):row.optString("error");if(message.isEmpty())message="这一天已有生成请求记录，未自动重试；可以明确选择重新安排。";}
  else {
   code=intrinsic(c,s,personal,who,now);switch(code){
    case "before_day":message="03:00 前保留上一日安排，03:00 后检查新一天。";break;
    case "unchatted":message="尚无这名角色的聊天记录，先和她聊一轮。";break;
    case "inactive":message="最近七天没有交流，不自动调用模型；仍可手动安排。";break;
    case "closed":message="最近会话已结束，不自动安排；可以新建会话或手动安排。";break;
    case "clock":message="交流时间记录在未来，请核对手机时间。";break;
    default:
     if(!configured(c)){code="model";message="请先配置聊天或后台模型。";}
     else {ZoneId zone=ScheduleData.zone(personal);String today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString();int used=ScheduleAttempts.count(s,today,zone),limit=prefs.getInt("hibiScheduleLimit",3);
      if(used>=limit){code="limit";message="今天已安排或尝试 "+used+" 位角色，达到自动上限 "+limit+"；失败不会自动重复请求。";}
      else if(occupied(s)){code="busy";message="上一项行程正在生成，完成后继续逐位安排；聊天可同时进行。";}
      else{code="queued";message=prefs.getString("schedulePrepareError",prefs.getString("scheduleQueueError",""));if(message.isEmpty())message="已符合自动安排条件，等待系统执行；可直接手动安排。";}
     }break;
   }
  }return new JSONObject().put("code",code).put("message",message);
 }
}
