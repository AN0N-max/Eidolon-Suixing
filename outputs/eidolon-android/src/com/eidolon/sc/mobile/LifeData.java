package com.eidolon.sc.mobile;

import java.time.*;
import java.util.*;
import org.json.*;

/** Bounded deterministic virtual-life state machine. No network, device actions or invented user facts. */
final class LifeData {
    static final int MAX_EVENTS=96,MAX_GOALS=4,MAX_MEMORIES=48;
    static final long HOUR=3600000L,DAY=24*HOUR;
    static JSONObject fresh(String who,JSONObject persona,Clock clock)throws Exception{
        String intro=persona.optString("intro");
        int express=intro.matches(".*(开朗|活泼|元气|热情).*")?75:55;
        if(intro.matches(".*(温柔|慢热|安静|内向).*"))express=35;
        return new JSONObject().put("schema",1).put("character",who).put("revision",0).put("enabled",true).put("evolution",true).put("eventOnlyReach",false)
            .put("observedAt",clock.millis()).put("zone","").put("emotion",new JSONObject().put("valence",0.0).put("energy",70.0).put("at",clock.millis()).put("source","").put("label","平静"))
            .put("profile",new JSONObject().put("expressiveness",express).put("coreSource","CharacterPrompts").put("confidence",50.0).put("lastEvolutionDay","").put("interests",new JSONObject()))
            .put("events",new JSONArray()).put("goals",new JSONArray()).put("memories",new JSONArray()).put("influences",new JSONArray()).put("evolutionLog",new JSONArray());
    }
    static String shortText(String value,int max)throws Exception{String v=value.trim();if(v.isEmpty()||v.length()>max)throw new Exception("生活记录文本为空或过长");return v;}
    static JSONObject find(JSONArray rows,String id){if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&id.equals(r.optString("id")))return r;}return null;}
    static String day(long at,ZoneId zone){return Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toString();}
    static JSONObject event(String id,String title,String kind,long start,long end,String parent)throws Exception{
        return new JSONObject().put("id",id).put("title",shortText(title,160)).put("kind",kind).put("status","planned").put("origin","simulated_life").put("start",start).put("end",end).put("at",0).put("parent",parent).put("importance",kind.equals("goal")?90:40).put("mentionedAt",0).put("messageRef","").put("source","persisted_schedule");
    }
    static void remember(JSONObject state,JSONObject e,String layer,long now)throws Exception{
        JSONArray rows=state.getJSONArray("memories");String id=e.getString("id")+"/"+layer;JSONObject m=find(rows,id);
        if(m==null){m=new JSONObject().put("id",id).put("eventId",e.getString("id")).put("layer",layer).put("createdAt",now).put("importance",e.optInt("importance",40));rows.put(m);}
        m.put("text",e.getString("title")).put("at",e.optLong("at")).put("status",e.getString("status")).put("source",e.optString("source")).put("confirmed",!e.optString("status").equals("uncertain")).put("updatedAt",now);
        if(rows.length()>MAX_MEMORIES){JSONArray keep=new JSONArray();for(int i=rows.length()-MAX_MEMORIES;i<rows.length();i++)keep.put(rows.get(i));state.put("memories",keep);}
    }
    static void influence(JSONObject s,String key,double value,long now)throws Exception{
        JSONArray seen=s.getJSONArray("influences");for(int i=0;i<seen.length();i++)if(key.equals(seen.getString(i)))return;
        JSONObject emotion=s.getJSONObject("emotion");double aged=Math.exp(-Math.max(0,emotion.optLong("at")-now)/(6.0*HOUR));emotion.put("valence",clamp(emotion.optDouble("valence")+Math.max(-.35,Math.min(.35,value))*aged,-1,1)).put("source",key).put("at",Math.max(emotion.optLong("at"),now));seen.put(key);
        if(seen.length()>256){JSONArray next=new JSONArray();for(int i=seen.length()-256;i<seen.length();i++)next.put(seen.get(i));s.put("influences",next);}
    }
    static double clamp(double n,double min,double max){return Math.max(min,Math.min(max,n));}
    static void decay(JSONObject s,long now)throws Exception{
        JSONObject e=s.getJSONObject("emotion");long before=e.getLong("at");if(now<=before)return;
        double hours=Math.min(24*30,(now-before)/(double)HOUR);e.put("valence",e.getDouble("valence")*Math.exp(-hours/6));
        e.put("energy",70+(e.getDouble("energy")-70)*Math.exp(-hours/8)).put("at",now);
    }
    static void seedGoal(JSONObject s,JSONObject spec,long now)throws Exception{
        if(spec==null)return;
        JSONArray goals=s.getJSONArray("goals");for(int i=0;i<goals.length();i++)if(goals.getJSONObject(i).optString("status").equals("active"))return;
        String title=shortText(spec.getString("title"),100);for(int i=0;i<goals.length();i++)if(goals.getJSONObject(i).getString("title").equals(title))return;
        JSONArray steps=spec.getJSONArray("steps");if(steps.length()<2||steps.length()>5)throw new Exception("长期目标需要2至5个阶段");JSONArray normalized=new JSONArray();
        for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);String name=shortText(step.getString("title"),100);normalized.put(new JSONObject().put("title",name).put("external",step.optBoolean("external",true)||name.matches(".*(结果|获奖|录取|通过考试|交易|转账|发布|报名|提交作品).*")));}
        if(goals.length()>=MAX_GOALS){String archived=goals.getJSONObject(0).getString("id");JSONArray next=new JSONArray(),events=new JSONArray(),memories=new JSONArray();Set<String> removed=new HashSet<>();removed.add(archived);for(int i=1;i<goals.length();i++)next.put(goals.get(i));JSONArray old=s.getJSONArray("events");boolean change=true;while(change){change=false;for(int i=0;i<old.length();i++){JSONObject e=old.getJSONObject(i);if(removed.contains(e.optString("parent"))&&removed.add(e.getString("id")))change=true;}}for(int i=0;i<old.length();i++)if(!removed.contains(old.getJSONObject(i).getString("id")))events.put(old.get(i));JSONArray ms=s.getJSONArray("memories");for(int i=0;i<ms.length();i++)if(!removed.contains(ms.getJSONObject(i).optString("eventId")))memories.put(ms.get(i));s.put("events",events).put("memories",memories).put("goals",next);goals=next;}
        String id="goal/"+UUID.randomUUID();JSONObject g=new JSONObject().put("id",id).put("title",title).put("steps",normalized).put("progress",0).put("status","active").put("lastDay","").put("interest",interest(spec.optString("interest")));
        goals.put(g);JSONObject root=event(id,title,"goal",now,now+30*DAY,"").put("status","open").put("at",now).put("source","generated_virtual_goal");s.getJSONArray("events").put(root);remember(s,root,"todo",now);
    }
    static String interest(String s){return Arrays.asList("practice","creative","outdoors","social","rest").contains(s)?s:"practice";}
    static JSONObject continuity(JSONObject raw,int slots)throws Exception{
        int slot=raw.getInt("goalSlot");if(slot<0||slot>=slots)throw new Exception("目标关联时段无效");JSONObject out=new JSONObject().put("goalSlot",slot);
        if(raw.optJSONObject("goal")!=null){JSONObject g=raw.getJSONObject("goal");JSONArray steps=g.getJSONArray("steps"),clean=new JSONArray();if(steps.length()<2||steps.length()>5)throw new Exception("目标阶段数量无效");for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);clean.put(new JSONObject().put("title",shortText(step.getString("title"),100)).put("external",step.getBoolean("external")));}out.put("goal",new JSONObject().put("title",shortText(g.getString("title"),100)).put("interest",interest(g.optString("interest"))).put("steps",clean));}
        if(!raw.optString("obstacle").isEmpty())out.put("obstacle",shortText(raw.getString("obstacle"),100));return out;
    }
    static JSONObject goal(JSONObject s){JSONArray a=s.optJSONArray("goals");if(a!=null)for(int i=0;i<a.length();i++)if(a.optJSONObject(i).optString("status").equals("active"))return a.optJSONObject(i);return null;}
    static void ingest(JSONObject s,JSONObject entry,ZoneId zone,long now)throws Exception{
        if(entry==null||entry.optJSONObject("plan")==null||!s.optBoolean("enabled"))return;
        JSONObject plan=entry.getJSONObject("plan"),continuity=plan.optJSONObject("continuity");long created=entry.optLong("createdAt",now);LocalDate date=LocalDate.parse(entry.getString("date"));
        if(continuity!=null&&created<=now)seedGoal(s,continuity.optJSONObject("goal"),Math.max(created,now));
        JSONObject g=goal(s);JSONArray slots=plan.getJSONArray("slots"),events=s.getJSONArray("events");
        for(int i=0;i<slots.length();i++){
            String id="slot/"+s.getString("character")+"/"+date+"/"+i;JSONObject existing=find(events,id);if(existing!=null){if(existing.optString("status").equals("planned")&&!existing.optString("zone").equals(zone.getId())){JSONObject slot=slots.getJSONObject(i);existing.put("start",TemporalData.boundary(date,slot.getString("start"),zone)).put("end",TemporalData.boundary(date,slot.getString("end"),zone)).put("zone",zone.getId());}continue;}
            JSONObject slot=slots.getJSONObject(i);long start=TemporalData.boundary(date,slot.getString("start"),zone),end=TemporalData.boundary(date,slot.getString("end"),zone);
            if(end<now-7*DAY)continue;
            JSONObject e=event(id,slot.getString("activity"),"activity",start,end,"").put("zone",zone.getId());
            if(g!=null&&continuity!=null&&continuity.optInt("goalSlot",-1)==i){e.put("goalId",g.getString("id")).put("goalStep",g.getInt("progress"));if(!continuity.optString("obstacle").isEmpty())e.put("obstacle",continuity.getString("obstacle"));}
            if(end<=now)e.put("status","missed").put("source","unobserved_plan");
            events.put(e);
        }
        String nightId="night/"+s.getString("character")+"/"+date;
        if(find(events,nightId)==null){int minute=ScheduleData.minute(plan.getString("sleep"),false);long start=TemporalData.boundary(minute<240?date.plusDays(1):date,plan.getString("sleep"),zone)+15*60000L,end=TemporalData.boundary(date.plusDays(1),plan.getString("wake"),zone);if(end>start&&end>=now-7*DAY){JSONObject sleep=event(nightId,"睡眠","activity",start,end,"").put("zone",zone.getId()).put("night",true);if(end<=now)sleep.put("status","missed").put("source","unobserved_plan");events.put(sleep);}}
    }
    static void completeGoalStep(JSONObject s,JSONObject e,ZoneId zone,long now,boolean confirmed)throws Exception{
        JSONObject g=find(s.getJSONArray("goals"),e.optString("goalId"));if(g==null||!g.getString("status").equals("active")||g.optString("lastDay").equals(day(now,zone)))return;
        int index=g.getInt("progress");if(e.has("goalStep")&&e.getInt("goalStep")!=index)return;JSONObject step=g.getJSONArray("steps").getJSONObject(index);
        if(step.getBoolean("external")&&!confirmed){e.put("status","awaiting_outcome");remember(s,e,"todo",now);return;}
        g.put("progress",index+1).put("lastDay",day(now,zone));String id=g.getString("id")+"/step/"+index;
        JSONObject child=event(id,step.getString("title"),"goal_step",now,now,g.getString("id")).put("status","completed").put("at",now).put("source",confirmed?"explicit_user_confirmation":"simulation_rule");s.getJSONArray("events").put(child);remember(s,child,"episodic",now);
        influence(s,id,.15,now);
        if(index+1>=g.getJSONArray("steps").length()){g.put("status","completed");JSONObject root=find(s.getJSONArray("events"),g.getString("id"));if(root!=null){root.put("status","completed").put("at",now);remember(s,root,"todo",now);remember(s,root,"episodic",now);}}
        evolve(s,g,child,zone,now);
    }
    static void evolve(JSONObject s,JSONObject g,JSONObject evidence,ZoneId zone,long now)throws Exception{
        JSONObject p=s.getJSONObject("profile");String date=day(now,zone);if(!s.optBoolean("evolution")||date.equals(p.optString("lastEvolutionDay")))return;
        JSONArray log=s.getJSONArray("evolutionLog");long last=log.length()==0?0:log.getJSONObject(log.length()-1).optLong("at");if(last>0&&now-last<7*DAY)return;
        // At least two independently completed steps on distinct dates are required.
        Set<String> days=new HashSet<>();JSONArray events=s.getJSONArray("events");for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);if(e.optString("kind").equals("goal_step")&&e.optString("status").equals("completed")&&e.optLong("at")<=now)days.add(day(e.getLong("at"),zone));}if(days.size()<2)return;
        double before=p.getDouble("confidence"),after=Math.min(80,before+.5);String tag=g.getString("interest");JSONObject interests=p.getJSONObject("interests");interests.put(tag,Math.min(20,interests.optInt(tag)+1));p.put("confidence",after).put("lastEvolutionDay",date);
        log.put(new JSONObject().put("at",now).put("before",before).put("after",after).put("eventId",evidence.getString("id")).put("reason","跨日持续推进虚拟目标"));if(log.length()>24){JSONArray next=new JSONArray();for(int i=log.length()-24;i<log.length();i++)next.put(log.get(i));s.put("evolutionLog",next);}
        remember(s,evidence,"semantic",now);
    }
    static JSONObject advance(JSONObject state,Clock clock,ZoneId zone,JSONObject today,JSONObject yesterday,boolean interrupted)throws Exception{
        JSONObject s=new JSONObject(state.toString());long now=clock.millis(),previous=s.getLong("observedAt");if(now<previous&&previous-now<=120000)return s;s.put("clockRollback",now<previous);
        if(now<previous||!s.getBoolean("enabled"))return s;
        decay(s,now);ingest(s,yesterday,zone,now);ingest(s,today,zone,now);
        JSONArray events=s.getJSONArray("events");JSONObject actual=null;int original=events.length();
        for(int i=0;i<original;i++){
            JSONObject e=events.getJSONObject(i);if(!e.getString("kind").equals("activity"))continue;
            String status=e.getString("status");long start=e.getLong("start"),end=e.getLong("end");
            if(status.equals("in_progress")&&now>=end){e.put("status","completed").put("at",end).put("source","simulation_rule");remember(s,e,"episodic",now);influence(s,e.getString("id"),.04,end);double delta=e.optBoolean("night")?12:e.getString("title").matches(".*(休息|午休|散步|吃饭|早餐|晚餐).*")?6:-4;JSONObject em=s.getJSONObject("emotion");em.put("energy",clamp(em.getDouble("energy")+delta*Math.exp(-Math.max(0,now-end)/(8.0*HOUR)),0,100));completeGoalStep(s,e,zone,end,false);
                for(int j=0;j<original;j++){JSONObject issue=events.getJSONObject(j);if(issue.optString("kind").equals("issue")&&issue.optString("status").equals("open")&&!e.optString("goalId").isEmpty()&&issue.optString("goalId").equals(e.optString("goalId"))&&issue.optLong("at")<start){issue.put("status","completed").put("resolvedBy",e.getString("id")).put("resolvedAt",end);remember(s,issue,"todo",now);influence(s,issue.getString("id")+"/resolved",.12,end);}}
            }
            else if((status.equals("planned")||status.equals("paused"))&&now>=end){e.put("status","missed").put("source","unobserved_plan");}
            else if(start<=now&&now<end&&(status.equals("planned")||status.equals("paused")||status.equals("in_progress"))){
                if(interrupted||e.optBoolean("night")&&now<s.optLong("awakeUntil")){e.put("status","paused");continue;}
                if(!status.equals("in_progress"))e.put("at",now);e.put("status","in_progress").put("source","observed_virtual_activity");actual=e;
                int openIssues=0;for(int j=0;j<events.length();j++)if(events.getJSONObject(j).optString("kind").equals("issue")&&events.getJSONObject(j).optString("status").equals("open"))openIssues++;
                if(openIssues<8&&!e.optString("obstacle").isEmpty()&&now-start>=(end-start)/2&&find(events,e.getString("id")+"/issue")==null){JSONObject issue=event(e.getString("id")+"/issue",e.getString("obstacle"),"issue",now,now,e.getString("id")).put("status","open").put("at",now).put("goalId",e.optString("goalId")).put("source","observed_virtual_activity").put("importance",70);events.put(issue);remember(s,issue,"todo",now);influence(s,issue.getString("id"),-.18,now);}
            }
        }
        JSONObject emotion=s.getJSONObject("emotion");String label=emotion.getDouble("valence")<-.25?"低落":emotion.getDouble("valence")>.25?"愉快":"平静";emotion.put("label",label);
        s.put("actual",actual==null?JSONObject.NULL:new JSONObject().put("eventId",actual.getString("id")).put("name",actual.getString("title")).put("start",actual.getLong("start")).put("end",actual.getLong("end")).put("state","in_progress").put("source","virtual_simulation").put("progress",(now-actual.getLong("start"))/(double)(actual.getLong("end")-actual.getLong("start"))));
        s.put("observedAt",now).put("zone",zone.getId());prune(s,now);return s;
    }
    static void prune(JSONObject s,long now)throws Exception{
        JSONArray rows=s.getJSONArray("events");
        List<JSONObject> keep=new ArrayList<>();Set<String> protectedIds=new HashSet<>();JSONArray goals=s.getJSONArray("goals");for(int i=0;i<goals.length();i++)protectedIds.add(goals.getJSONObject(i).getString("id"));
        for(int i=0;i<rows.length();i++){JSONObject e=rows.getJSONObject(i);if(protectedIds.contains(e.getString("id"))||Arrays.asList("open","awaiting_outcome","in_progress","planned","paused").contains(e.getString("status")))keep.add(e);}
        for(int j=0;j<keep.size();j++){JSONObject parent=find(rows,keep.get(j).optString("parent"));if(parent!=null&&!keep.contains(parent))keep.add(parent);}
        for(int i=rows.length()-1;i>=0&&keep.size()<MAX_EVENTS;i--){JSONObject e=rows.getJSONObject(i);long age=now-Math.max(e.optLong("at"),e.optLong("end"));if(e.optString("status").equals("missed")&&age>7*DAY||e.optInt("importance")<60&&age>45*DAY)continue;JSONObject parent=find(rows,e.optString("parent"));if(parent!=null&&!keep.contains(parent)){if(keep.size()+2>MAX_EVENTS)continue;keep.add(parent);}if(!keep.contains(e))keep.add(e);}
        JSONArray next=new JSONArray();for(int i=0;i<Math.min(MAX_EVENTS,keep.size());i++)next.put(keep.get(i));s.put("events",next);
        JSONArray memories=s.getJSONArray("memories"),valid=new JSONArray();for(int i=0;i<memories.length();i++){JSONObject m=memories.getJSONObject(i);if(find(next,m.optString("eventId"))!=null)valid.put(m);}s.put("memories",valid);
    }
    static JSONObject cue(JSONObject s,long now){JSONArray rows=s.optJSONArray("events");JSONObject best=null;if(!s.optBoolean("enabled")||rows==null)return null;for(int i=0;i<rows.length();i++){JSONObject e=rows.optJSONObject(i);if(e.optLong("mentionedAt")>0||e.optLong("at")<=0||e.optLong("at")>now||now-e.optLong("at")>3*DAY||!Arrays.asList("completed","awaiting_outcome","open").contains(e.optString("status")))continue;if(best==null||e.optInt("importance")>best.optInt("importance"))best=e;}return best;}
    static JSONArray relevant(JSONObject s,String query,long now)throws Exception{
        List<JSONObject> list=new ArrayList<>();JSONArray a=s.getJSONArray("events");for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);if(e.optLong("at")>now||!Arrays.asList("completed","open","in_progress","awaiting_outcome").contains(e.optString("status")))continue;list.add(e);}
        list.sort((a1,b)->Double.compare(relevance(b,query,now),relevance(a1,query,now)));JSONArray out=new JSONArray();for(int i=0;i<Math.min(4,list.size());i++)out.put(new JSONObject(list.get(i).toString()));return out;
    }
    static double relevance(JSONObject e,String query,long now){return Recall.score(query,e.optString("title"))*100+e.optInt("importance")/10.0+Math.max(0,5-(now-e.optLong("at"))/(double)DAY);}
    static void validate(JSONObject s,Set<String> known)throws Exception{
        if(s.toString().length()>160000||s.getInt("schema")!=1||!known.contains(s.getString("character"))||s.getLong("revision")<0)throw new Exception("生活档案格式无效");
        if(!(s.get("enabled") instanceof Boolean)||!(s.get("evolution") instanceof Boolean)||!(s.get("eventOnlyReach") instanceof Boolean))throw new Exception("生活开关无效");
        if(s.getLong("observedAt")<0)throw new Exception("生活时间无效");if(!s.getString("zone").isEmpty())ZoneId.of(s.getString("zone"));
        JSONObject mood=s.getJSONObject("emotion"),profile=s.getJSONObject("profile");for(String k:new String[]{"valence","energy"}){double v=mood.getDouble(k);if(!Double.isFinite(v)||v<(k.equals("valence")?-1:0)||v>(k.equals("valence")?1:100))throw new Exception("情绪范围无效");}if(mood.getLong("at")<0||profile.getDouble("confidence")<0||profile.getDouble("confidence")>100||!Double.isFinite(profile.getDouble("confidence")))throw new Exception("人格状态无效");
        if(profile.getInt("expressiveness")<0||profile.getInt("expressiveness")>100||mood.optString("source").length()>200||!Arrays.asList("平静","低落","愉快").contains(mood.getString("label")))throw new Exception("情绪或表达状态无效");JSONObject interests=profile.getJSONObject("interests");if(interests.length()>5)throw new Exception("兴趣状态过多");for(Iterator<String> it=interests.keys();it.hasNext();){String key=it.next();if(!interest(key).equals(key)||interests.getInt(key)<0||interests.getInt(key)>20)throw new Exception("兴趣状态无效");}
        JSONArray events=s.getJSONArray("events"),goals=s.getJSONArray("goals"),memories=s.getJSONArray("memories");if(events.length()>MAX_EVENTS||goals.length()>MAX_GOALS||memories.length()>MAX_MEMORIES||s.getJSONArray("influences").length()>256||s.getJSONArray("evolutionLog").length()>24)throw new Exception("生活记录过多");
        Set<String> ids=new HashSet<>();for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);if(!ids.add(shortText(e.getString("id"),180)))throw new Exception("生活事件重复");shortText(e.getString("title"),160);if(!Arrays.asList("planned","in_progress","paused","completed","missed","open","awaiting_outcome","cancelled","uncertain").contains(e.getString("status"))||!Arrays.asList("activity","goal","goal_step","issue").contains(e.getString("kind"))||!e.getString("origin").equals("simulated_life")||e.getLong("start")<0||e.getLong("end")<e.getLong("start")||e.getLong("at")<0||e.getLong("mentionedAt")<0)throw new Exception("生活事件状态无效");}
        for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);if(e.optString("source").length()>100||e.optString("messageRef").length()>200||e.optString("mentionQuote").length()>500||e.getLong("at")>253402300799999L||e.getLong("end")>253402300799999L)throw new Exception("生活来源或时间无效");Set<String> seen=new HashSet<>();String parent=e.optString("parent");while(!parent.isEmpty()){if(!ids.contains(parent)||!seen.add(parent)||parent.equals(e.getString("id")))throw new Exception("生活因果链引用无效");parent=find(events,parent).optString("parent");}}
        for(int i=0;i<goals.length();i++){JSONObject g=goals.getJSONObject(i);if(!ids.contains(g.getString("id")))throw new Exception("目标缺少根事件");JSONArray steps=g.getJSONArray("steps");if(steps.length()<2||steps.length()>5||g.getInt("progress")<0||g.getInt("progress")>steps.length()||!Arrays.asList("active","completed","cancelled").contains(g.getString("status")))throw new Exception("目标阶段无效");for(int j=0;j<steps.length();j++){shortText(steps.getJSONObject(j).getString("title"),100);steps.getJSONObject(j).getBoolean("external");}}
        Set<String> memoryIds=new HashSet<>();for(int i=0;i<memories.length();i++){JSONObject m=memories.getJSONObject(i);if(!memoryIds.add(m.getString("id"))||!ids.contains(m.getString("eventId"))||!Arrays.asList("episodic","semantic","todo").contains(m.getString("layer")))throw new Exception("生活记忆来源无效");shortText(m.getString("text"),160);}
    }
    private LifeData(){}
}
