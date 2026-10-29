package com.eidolon.sc.mobile;

import org.json.*;
import java.util.*;

/** Hibi relationship doors. Only a delivered, versioned chat reply consumes a scene turn. */
final class HeartScenes {
    static final String[] TIERS={"close","intimate"};
    static int threshold(String tier){return tier.equals("close")?65:85;}
    static JSONObject root(JSONObject d)throws Exception{JSONObject r=d.optJSONObject("heartScenes");if(r==null){r=new JSONObject().put("enabled",false).put("paused",false).put("epoch",0).put("characters",new JSONObject());d.put("heartScenes",r);}return r;}
    static boolean on(JSONObject d){JSONObject r=d.optJSONObject("heartScenes");return r!=null&&r.optBoolean("enabled")&&!r.optBoolean("paused");}
    static void configure(JSONObject d,boolean enabled,boolean paused)throws Exception{
        JSONObject r=root(d);if(r.getBoolean("enabled")!=enabled||r.getBoolean("paused")!=paused)r.put("epoch",r.getLong("epoch")+1);
        r.put("enabled",enabled).put("paused",paused);
        if(enabled){JSONObject scores=d.getJSONObject("affinity");for(Iterator<String> i=scores.keys();i.hasNext();){String who=i.next();JSONObject rows=rows(d,who);for(String t:TIERS)if(scores.optDouble(who)>=threshold(t))rows.put(t,new JSONObject().put("turns",6).put("done",true));}}
    }
    static JSONObject rows(JSONObject d,String who)throws Exception{JSONObject all=root(d).getJSONObject("characters"),rows=all.optJSONObject(who);if(rows==null){rows=new JSONObject();all.put(who,rows);}return rows;}
    static void gate(JSONObject d,String who,double before)throws Exception{
        if(!on(d))return;JSONObject scores=d.getJSONObject("affinity"),rows=rows(d,who);double after=scores.optDouble(who,50);
        for(String t:TIERS){int start=threshold(t);JSONObject row=rows.optJSONObject(t);if(before<start&&(after>=start||(row==null&&after>=start-.01))&&(row==null||!row.optBoolean("done"))){scores.put(who,start-.01);if(row==null)rows.put(t,new JSONObject().put("turns",0).put("done",false));return;}}
    }
    static JSONObject claim(JSONObject d,String who)throws Exception{
        if(!on(d)||d.optJSONObject("relations")!=null&&d.getJSONObject("relations").optJSONObject(who)!=null&&d.getJSONObject("relations").getJSONObject(who).optBoolean("cooling"))return null;
        double score=d.getJSONObject("affinity").optDouble(who,50);JSONObject rows=rows(d,who);
        for(String t:TIERS){JSONObject r=rows.optJSONObject(t);if(r!=null&&!r.optBoolean("done")&&score>=threshold(t)-5&&score<threshold(t))return new JSONObject().put("tier",t).put("turns",r.getInt("turns")).put("epoch",root(d).getLong("epoch"));}return null;
    }
    static void delivered(JSONObject latest,JSONObject merged,String who,JSONObject token)throws Exception{
        // Scene state never comes from a stale generated personal-data copy.
        merged.put("heartScenes",new JSONObject(root(latest).toString()));
        JSONObject current=claim(latest,who);if(token!=null&&current!=null&&current.optString("tier").equals(token.optString("tier"))&&current.optInt("turns")==token.optInt("turns")&&current.optLong("epoch")==token.optLong("epoch")){
            JSONObject relation=merged.optJSONObject("relations");JSONObject r=relation==null?null:relation.optJSONObject(who);double score=merged.getJSONObject("affinity").optDouble(who,50);String tier=token.getString("tier");
            if((r==null||!r.optBoolean("cooling"))&&score>=threshold(tier)-5){JSONObject row=rows(merged,who).getJSONObject(tier);int turns=row.getInt("turns")+1;row.put("turns",turns);if(turns>=6){row.put("done",true);merged.getJSONObject("affinity").put(who,Math.max(score,threshold(tier)+1));}}
        }
        JSONObject scores=merged.getJSONObject("affinity");for(Iterator<String> i=scores.keys();i.hasNext();){String id=i.next();gate(merged,id,latest.getJSONObject("affinity").optDouble(id,50));}
    }
    static String prompt(JSONObject h,JSONObject token,String gender)throws Exception{
        if(token==null)return "";String tier=token.getString("tier");int turn=token.getInt("turns")+1;String opening=h==null?"":h.optJSONObject("scenes")==null?"":h.getJSONObject("scenes").optString(tier);
        String text="\n重要的一幕：这一段现在是第"+turn+"轮，共约6轮。"+(turn==1?"这轮主动把心里重要的事说出口。":turn>=6?"这是最后一轮，认真收尾，表达更深的信赖。":"接着这件事聊，认真回应制作人刚才的话。")+opening+"。按角色性格表达，不提分数、门槛或演戏，不编造共同经历。";
        if(h==null||h.optInt("age",0)<18)text+="亲近只表达信赖与依赖，不表达恋爱。";
        else if(tier.equals("intimate")&&h.optJSONObject("romance")!=null)text+=h.getJSONObject("romance").optString("intimate_"+("female".equals(gender)?"female":"male"));
        return text;
    }
    static void validate(JSONObject d)throws Exception{
        if(!d.has("heartScenes"))return;JSONObject r=d.getJSONObject("heartScenes");r.getBoolean("enabled");r.getBoolean("paused");if(r.getLong("epoch")<0)throw new Exception("关键剧情版本无效");JSONObject all=r.getJSONObject("characters");if(all.length()>100)throw new Exception("关键剧情角色过多");for(Iterator<String> i=all.keys();i.hasNext();){String who=i.next();if(!who.matches("[a-z0-9_-]{1,80}"))throw new Exception("关键剧情角色无效");JSONObject rows=all.getJSONObject(who);for(Iterator<String> j=rows.keys();j.hasNext();){String t=j.next();if(!Arrays.asList(TIERS).contains(t))throw new Exception("关键剧情阶段无效");JSONObject row=rows.getJSONObject(t);int n=row.getInt("turns");if(n<0||n>6||row.getBoolean("done")!=(n==6))throw new Exception("关键剧情轮次无效");}}
    }
    private HeartScenes(){}
}
