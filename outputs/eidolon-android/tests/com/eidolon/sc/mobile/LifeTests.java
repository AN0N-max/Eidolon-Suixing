package com.eidolon.sc.mobile;
import java.time.*;import java.util.*;import org.json.*;
public class LifeTests {
 static int count;interface Work{void run()throws Exception;}
 static void check(boolean v,String n){if(!v)throw new AssertionError(n);count++;System.out.println("PASS "+n);}
 static void reject(Work w,String n)throws Exception{try{w.run();}catch(Exception good){check(true,n);return;}throw new AssertionError(n);}
 static final ZoneId Z=ZoneId.of("Asia/Shanghai");
 static Clock clock(String local){return Clock.fixed(LocalDateTime.parse(local).atZone(Z).toInstant(),ZoneOffset.UTC);}
 static JSONObject fresh(String who,String intro,String at)throws Exception{return LifeData.fresh(who,new JSONObject().put("intro",intro),clock(at));}
 static JSONObject entry(String day,boolean goal,boolean obstacle)throws Exception{
  JSONObject plan=new JSONObject("{wake:'07:00',sleep:'23:00',slots:[{start:'08:00',end:'09:40',activity:'练习发声'},{start:'12:00',end:'13:00',activity:'午休'},{start:'15:00',end:'16:00',activity:'整理练习笔记'}],twists:[]}");
  JSONObject continuity=new JSONObject().put("goalSlot",0);if(goal)continuity.put("goal",new JSONObject("{title:'完成自己的练习作品',interest:'creative',steps:[{title:'整理初稿',external:false},{title:'调整作品',external:false},{title:'等待比赛结果',external:true}]}"));if(obstacle)continuity.put("obstacle","练习有一处暂时卡住了");plan.put("continuity",continuity);
  return new JSONObject().put("character","mano").put("date",day).put("createdAt",clock(day+"T07:00:00").millis()).put("plan",ScheduleData.normalize(plan));
 }
 static JSONObject next(JSONObject s,String at,JSONObject today,JSONObject yesterday)throws Exception{return LifeData.advance(s,clock(at),Z,today,yesterday,false);}
 static JSONObject slot(JSONObject s,String day){return LifeData.find(s.optJSONArray("events"),"slot/mano/"+day+"/0");}
 public static void main(String[] args)throws Exception{
  JSONObject d1=entry("2026-10-30",true,true),s=fresh("mano","温柔、慢热","2026-10-30T07:00:00");
  s=next(s,"2026-10-30T08:00:00",d1,null);check(slot(s,"2026-10-30").getString("status").equals("in_progress")&&s.getJSONObject("actual").getDouble("progress")==0,"activity exact start enters explicit virtual execution");
  s=next(s,"2026-10-30T08:55:00",d1,null);check(s.getJSONArray("events").length()==6&&s.getJSONObject("emotion").getDouble("valence")<0,"observed obstacle creates causal issue and inertial mood");int rows=s.getJSONArray("events").length();double mood=s.getJSONObject("emotion").getDouble("valence");
  s=next(s,"2026-10-30T08:55:00",d1,null);check(s.getJSONArray("events").length()==rows&&s.getJSONObject("emotion").getDouble("valence")==mood,"duplicate job does not duplicate events or emotion impact");
  s=next(s,"2026-10-30T09:40:00",d1,null);check(s.isNull("actual")&&LifeData.goal(s).getInt("progress")==1,"exact end completes started simulation and one goal step");
  check(s.getJSONObject("profile").getDouble("confidence")==50,"single event never rewrites personality");
  JSONObject d2=entry("2026-10-31",false,false);s=next(new JSONObject(s.toString()),"2026-10-31T08:30:00",d2,d1);s=next(s,"2026-10-31T09:40:00",d2,d1);
  check(LifeData.goal(s).getInt("progress")==2&&s.getJSONArray("evolutionLog").length()==1&&s.getJSONObject("profile").getDouble("confidence")==50.5,"cross-day causal progress survives restart and evolves only slowly");
  JSONObject issue=LifeData.find(s.getJSONArray("events"),"slot/mano/2026-10-30/0/issue");check(issue.getString("status").equals("completed")&&!issue.optString("resolvedBy").isEmpty(),"later related activity resolves earlier issue with causal source");
  JSONObject d3=entry("2026-11-01",false,false);s=next(s,"2026-11-01T08:30:00",d3,d2);s=next(s,"2026-11-01T09:40:00",d3,d2);
  check(slot(s,"2026-11-01").getString("status").equals("awaiting_outcome")&&LifeData.goal(s).getInt("progress")==2,"external outcomes are not invented from elapsed plans");
  LifeData.completeGoalStep(s,slot(s,"2026-11-01"),Z,clock("2026-11-01T10:00:00").millis(),true);check(s.getJSONArray("goals").getJSONObject(0).getString("status").equals("completed")&&s.getJSONArray("evolutionLog").length()==1,"explicit confirmation completes goal without fast repeated evolution");
  LifeData.validate(s,Collections.singleton("mano"));check(s.getJSONArray("memories").length()>0,"event-linked episodic todo and semantic memory validates");
  JSONObject offline=fresh("mano","温柔","2026-10-30T07:00:00");offline=next(offline,"2026-10-31T18:00:00",d2,d1);int completed=0;for(int i=0;i<offline.getJSONArray("events").length();i++)if(offline.getJSONArray("events").getJSONObject(i).getString("status").equals("completed"))completed++;check(completed==0&&offline.isNull("actual"),"24h absence and missed background work never fabricate unstarted completion");
  JSONObject paused=fresh("mano","温柔","2026-10-30T07:00:00");paused=LifeData.advance(paused,clock("2026-10-30T08:30:00"),Z,d1,null,true);check(slot(paused,"2026-10-30").getString("status").equals("paused")&&paused.isNull("actual"),"temporary explicit activity overrides planned virtual execution");
  long before=s.getLong("observedAt");JSONObject rollback=next(s,"2026-10-29T07:00:00",null,null);check(rollback.getBoolean("clockRollback")&&rollback.getLong("observedAt")==before,"clock rollback never advances life or amplifies decay");
  JSONObject emotional=fresh("mano","温柔","2026-10-30T07:00:00");long t=clock("2026-10-30T07:00:00").millis();LifeData.influence(emotional,"evidence",-.3,t);LifeData.influence(emotional,"evidence",-.3,t);check(emotional.getJSONObject("emotion").getDouble("valence")==-.3,"same emotion evidence applied once");LifeData.decay(emotional,t+6*LifeData.HOUR);check(Math.abs(emotional.getJSONObject("emotion").getDouble("valence")+.3/Math.E)<.00001,"emotion decays gradually over six real hours");
  check(fresh("meguru","开朗、活泼","2026-10-30T07:00:00").getJSONObject("profile").getInt("expressiveness")!=fresh("mano","温柔、慢热","2026-10-30T07:00:00").getJSONObject("profile").getInt("expressiveness"),"stable persona source affects expression without random identity changes");
  JSONObject temporal=new JSONObject().put("timestamp",t).put("behavior",new JSONObject().put("availability","low").put("interruptibility","low"));JSONObject behavior=BehaviorPlanner.plan(emotional,temporal,"为什么会这样？",false);check(behavior.getString("goal").equals("answer")&&behavior.getString("length").equals("brief"),"user question and low interruptibility affect actual behavior contract");check(BehaviorPlanner.plan(emotional,temporal,"我有点难过",false).getString("goal").equals("acknowledge_feelings"),"distress chooses acknowledgment rather than compulsory advice");check(BehaviorPlanner.plan(emotional,temporal,"晚安，回头聊",false).getString("goal").equals("close_topic"),"ending intent respects closure rather than asking another question");
  JSONObject snap=new JSONObject().put("enabled",true).put("revision",2).put("now",t).put("validUntil",t+60000).put("events",s.getJSONArray("events"));String event=s.getJSONArray("events").getJSONObject(0).getString("id");
  JSONObject response=new JSONObject().put("eidolon_chat",1).put("messages",new JSONArray().put(new JSONObject().put("ja","練習したよ").put("zh","今天练习了一下")).put(new JSONObject().put("ja","少し休むね").put("zh","稍微休息一下"))).put("mentions",new JSONArray().put(new JSONObject().put("id",event).put("quote","今天练习了一下")));
  ChatEnvelope.Result result=ChatEnvelope.parse(response.toString(),snap,true,false);check(result.text.contains(ChatEnvelope.BREAK)&&result.effects.getJSONArray("mentions").length()==1,"one model response yields paired short bubbles and source-linked mentions");check(!FishClient.speech(result.text).contains("今天")&&FishClient.speech(result.text).contains("練習したよ"),"multi-bubble voice still speaks Japanese only");
  check(!MessageTime.content(new ChatStore.Message("assistant",result.text)).contains("eidolon-bubble"),"bubble transport marker does not enter subsequent model dialogue");
  JSONObject request=ModelClient.request("fake",LifeStore.prompt(snap,false),Collections.emptyList(),"今天怎么样？");check(request.getJSONArray("messages").length()==2&&request.toString().contains("eidolon_chat"),"behavior and single-call output contract reach final model request");
  response.getJSONArray("mentions").getJSONObject(0).put("id","other-role/fake");check(ChatEnvelope.parse(response.toString(),snap,true,false).effects.getJSONArray("mentions").length()==0,"unknown or other-role life references rejected");
  reject(()->ChatEnvelope.parse("{broken",snap,true,false),"malformed structure cannot leak JSON as character speech");reject(()->ChatEnvelope.parse(response.toString(),snap,true,true),"proactive cannot send multiple unsolicited bubbles");check(ChatEnvelope.parse("<ja>こんにちは</ja><zh>你好</zh>",snap,true,false).text.contains("你好"),"legacy text-only providers remain compatible");
  JSONObject invalid=new JSONObject(s.toString());invalid.getJSONArray("events").getJSONObject(0).put("parent",invalid.getJSONArray("events").getJSONObject(0).getString("id"));reject(()->LifeData.validate(invalid,Collections.singleton("mano")),"cyclic event backup rejected");
  final JSONObject finished=s;reject(()->LifeData.validate(finished,Collections.singleton("meguru")),"unknown-role life backup rejected");
  JSONObject off=new JSONObject(s.toString()).put("enabled",false);check(next(off,"2026-11-10T12:00:00",null,null).getLong("observedAt")==off.getLong("observedAt"),"disabled life makes no hidden progress or model request");
  JSONObject backup=new JSONObject().put("characterLife",new JSONArray().put(s));LifeStore.validateBackup(backup,Collections.singleton("mano"));check(true,"v14 life payload validates without credentials or Android runtime");
  JSONObject bedtime=next(fresh("mano","温柔","2026-10-30T07:00:00"),"2026-10-30T23:01:00",d1,null);check(bedtime.isNull("actual"),"23:01 plan does not imply immediate sleep");
  bedtime=next(bedtime,"2026-10-30T23:30:00",d1,null);check(bedtime.getJSONObject("actual").getString("name").equals("睡眠"),"night simulation begins only after grace and observation");
  bedtime.put("awakeUntil",clock("2026-10-31T02:10:00").millis());bedtime=next(bedtime,"2026-10-31T02:00:00",null,d1);check(bedtime.isNull("actual"),"recent messages keep continuous night chat awake");
  JSONObject projected=LifeStore.project(finished,new JSONObject().put("timestamp",clock("2026-11-01T10:00:00").millis()).put("planned_state","asleep").put("behavior",new JSONObject().put("availability","low")),"练习",false,clock("2026-11-01T10:00:00").millis(),null);
  check(projected.getJSONObject("behavior").getDouble("energy")<=30&&projected.toString().length()<8000,"final life context reflects night energy and bounded token input");
  ChatStore.Message delayed=new ChatStore.Message(42,"assistant","<zh>今天练习了</zh>",t+2*LifeData.DAY,"Asia/Shanghai",t);check(MessageTime.context(Collections.singletonList(delayed)).contains("2026-10-30 07:00:00"),"delayed content keeps its real formation timestamp in model context");
  check(LifeStore.restoredRef("message/12",Collections.singletonMap("12",99L)).equals("message/99"),"backup remaps life message provenance");
  check(LifeStore.restoredRef("message/12",Collections.emptyMap()).startsWith("imported_missing/"),"missing source never points at an unrelated imported message");
  System.out.println("PASS "+count+" life/behavior/protocol contracts with injected clocks and fake responses; no paid model calls.");
  if(args.length>0)java.nio.file.Files.write(java.nio.file.Paths.get(args[0]),s.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
}
