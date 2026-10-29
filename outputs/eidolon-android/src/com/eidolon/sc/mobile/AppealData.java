package com.eidolon.sc.mobile;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** Local appeal rules; no re-judging API and no inference from old messages. */
final class AppealData {
    static JSONObject root(JSONObject d)throws Exception{JSONObject r=d.optJSONObject("appeals");if(r==null){r=new JSONObject().put("day","1970-01-01").put("used",0).put("explanations",new JSONObject());d.put("appeals",r);}return r;}
    static int left(JSONObject d,String day)throws Exception{JSONObject r=root(d);return day.compareTo(r.getString("day"))>0?3:Math.max(0,3-r.getInt("used"));}
    static String pendingKey(JSONObject d,String who)throws Exception{return root(d).getJSONObject("explanations").optString(who);}
    static String prompt(JSONObject d,String who)throws Exception{return pendingKey(d,who).isEmpty()?"":"\n制作人已经解释：上一句是玩笑或被误会了，并没有那个意思。请按角色性格自然回应这次解释，可以接受、半信半疑或轻轻抱怨，再接着聊；不要提应用申诉、返还分数或判断模型。";}
    static void delivered(JSONObject latest,JSONObject merged,String who,String token)throws Exception{merged.put("appeals",new JSONObject(root(latest).toString()));if(!token.isEmpty()&&token.equals(pendingKey(latest,who)))root(merged).getJSONObject("explanations").remove(who);}
    static String reason(JSONObject d,String who,String session,JSONObject record,String day,boolean enabled)throws Exception{
        if(!enabled)return "好感评估未开启";if(record.optBoolean("appealed"))return "这条回复已经申诉过";if(record.optDouble("hurt")<=0)return "这条回复没有聊天扣分";
        JSONObject r=Progression.relation(d,who,System.currentTimeMillis()),s=Progression.session(d,session);if(r.optBoolean("cooling"))return "冷却期间暂不能申诉";if(s.optBoolean("ended"))return "已结束的会话不能申诉";
        JSONArray hurt=s.getJSONArray("hurt");if(!record.optString("key").equals(s.optString("lastJudgmentKey"))||hurt.length()==0||Math.abs(hurt.optDouble(hurt.length()-1)-record.optDouble("hurt"))>.000001)return "这条回复已不是可核对的最新判断";
        if(record.optBoolean("out")&&s.optInt("out")<1)return "越界记录已变化";return left(d,day)<=0?"今日三次申诉已用完":"";
    }
    static double apply(JSONObject d,String who,String session,JSONObject record,String day,boolean enabled)throws Exception{
        String error=reason(d,who,session,record,day,enabled);if(!error.isEmpty())throw new Exception(error);
        JSONObject r=root(d);if(day.compareTo(r.getString("day"))>0)r.put("day",day).put("used",0);r.put("used",r.getInt("used")+1);
        JSONObject s=Progression.session(d,session);JSONArray hurt=s.getJSONArray("hurt");hurt.put(hurt.length()-1,0);if(record.optBoolean("out"))s.put("out",s.getInt("out")-1);
        JSONObject scores=d.getJSONObject("affinity");double before=scores.optDouble(who,50);scores.put(who,Math.min(100,before+record.getDouble("hurt")));HeartScenes.gate(d,who,before);
        record.put("appealed",true);r.getJSONObject("explanations").put(who,record.getString("key"));return scores.getDouble(who)-before;
    }
    static JSONObject record(JSONObject effects,long now)throws Exception{
        JSONObject j=effects.optJSONObject("chatJudgment");if(j==null)return null;double hurt=j.getDouble("hurt");if(!Double.isFinite(hurt)||hurt<0||hurt>10.000001)throw new Exception("聊天扣分记录无效");return new JSONObject().put("key",UUID.randomUUID().toString()).put("hurt",Math.min(10,hurt)).put("out",j.getBoolean("out")).put("appealed",false).put("at",now);
    }
    static void validateRecord(JSONObject j)throws Exception{if(j.toString().length()>500||!j.getString("key").matches("[a-f0-9-]{36}")||!Double.isFinite(j.getDouble("hurt"))||j.getDouble("hurt")<0||j.getDouble("hurt")>10||j.getLong("at")<=0)throw new Exception("申诉消息记录无效");j.getBoolean("out");j.getBoolean("appealed");}
    static void validate(JSONObject d)throws Exception{if(!d.has("appeals"))return;JSONObject r=d.getJSONObject("appeals");LocalDate.parse(r.getString("day"));if(r.getInt("used")<0||r.getInt("used")>3)throw new Exception("申诉次数无效");JSONObject e=r.getJSONObject("explanations");if(e.length()>100)throw new Exception("申诉说明过多");for(Iterator<String> i=e.keys();i.hasNext();){String who=i.next();if(!who.matches("[a-z0-9_-]{1,80}")||!e.getString(who).matches("[a-f0-9-]{36}"))throw new Exception("申诉说明无效");}}
    private AppealData(){}
}
