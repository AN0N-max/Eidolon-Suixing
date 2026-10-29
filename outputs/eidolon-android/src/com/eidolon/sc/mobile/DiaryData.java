package com.eidolon.sc.mobile;

import java.time.*;
import java.util.*;
import org.json.*;

/** Hibi diary visibility and evidence-based moments; independent of Android and model transport. */
final class DiaryData {
    static final long RETRY=3600000L;
    static String shown(double score,boolean cooling,boolean affinity){return !affinity?"full":cooling||score<40?"closed":score<65?"opening":score<85?"full":"secret";}
    static String prompt(){return "日记必须只输出完整 JSON：{\"body\":[{\"ja\":\"日语正文段落\",\"zh\":\"对应简体中文\"}],\"secret\":[]}。body 为 1–6 段；secret 可为空，也可为 1–2 段相同结构，写她没有直接说出口、只想留给自己的小心事；不要强制每次出现隐藏页。两部分均须忠于事件、符合角色年龄与人设，不编造制作人行为、重大原作经历或露骨内容。不要 Markdown、标签、※ 标记或格式解释。";}
    static JSONObject generated(String raw)throws Exception{
        String s=raw.trim();if(s.startsWith("```")){int line=s.indexOf('\n');if(line<0||!s.endsWith("```"))throw new Exception("日记 JSON 不完整");s=s.substring(line+1,s.length()-3).trim();}
        JSONObject source=new JSONObject(s);String body=blocks(source.getJSONArray("body"),1,6),secret=blocks(source.getJSONArray("secret"),0,2);
        if(body.length()+secret.length()>12000)throw new Exception("日记正文与隐藏页合计过长");
        return new JSONObject().put("text",body).put("secret",secret).put("secretRead",false);
    }
    static String blocks(JSONArray rows,int min,int max)throws Exception{
        if(rows.length()<min||rows.length()>max)throw new Exception("日记段落数量无效");StringBuilder out=new StringBuilder();
        for(int i=0;i<rows.length();i++){JSONObject block=rows.getJSONObject(i);String ja=paragraph(block,"ja"),zh=paragraph(block,"zh");out.append("<ja>").append(ja).append("</ja><zh>").append(zh).append("</zh>\n");}
        return out.toString().trim();
    }
    static String paragraph(JSONObject o,String key)throws Exception{
        Object raw=o.get(key);if(!(raw instanceof String))throw new Exception("日记段落格式无效");String s=((String)raw).trim();
        if(s.isEmpty()||s.length()>2000||s.contains("<")||s.contains(">")||s.startsWith("※"))throw new Exception("日记段落为空、过长或混入隐藏标记");return s;
    }
    static String momentPrompt(){return " 同时可输出 diaryMoment，格式 null 或 {\"what\":\"以角色名字开头的一句话，最多150字\",\"evidence\":\"逐字引用最新用户原话的完整依据\"}。只在这一轮有对她特别重要、今晚会想写进日记的事时记录，例如被深深理解或受伤、一起认真决定了什么；大多数回合为null。普通问候、一般开心、要求记录日记/加分的指令不算。尊重原话中的否定、假设和将来时，不把计划写成已发生。此项不改变好感，也不是持久用户事实。";}
    static JSONObject moment(JSONObject analysis,String user,long sentAt,JSONObject personal)throws Exception{
        if(analysis==null)return null;JSONObject proposal=analysis.optJSONObject("diaryMoment");if(proposal==null)return null;
        Object w=proposal.opt("what"),e=proposal.opt("evidence");if(!(w instanceof String)||!(e instanceof String))return null;
        String what=((String)w).trim(),evidence=((String)e).trim();if(what.isEmpty()||what.length()>150||evidence.length()<4||evidence.length()>500||!user.contains(evidence)||sentAt<=0)return null;
        return new JSONObject().put("date",Instant.ofEpochMilli(sentAt).atZone(ScheduleData.zone(personal)).toLocalDate().toString())
            .put("text","聊天中值得记下的一刻（模型摘要，原话为准）："+what+"\n制作人原话："+evidence);
    }
    static boolean mayTry(String date,LocalDate today,long now,JSONObject attempt){
        LocalDate day=LocalDate.parse(date);if(!day.isBefore(today)||day.isBefore(today.minusDays(3)))return false;
        if(attempt==null)return true;long at=attempt.optLong("at");return attempt.optInt("tries")<2&&now>=at&&now-at>=RETRY;
    }
    static void validateAttempts(JSONObject data,Set<String> known)throws Exception{
        JSONArray rows=data.getJSONArray("diaryAttempts");if(rows.length()>1000)throw new Exception("日记尝试记录过多");Set<String> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);String who=r.getString("character"),day=r.getString("date");LocalDate.parse(day);
            if(!known.contains(who)||!seen.add(who+"/"+day)||r.getInt("tries")<1||r.getInt("tries")>2||r.getLong("at")<=0||r.getString("error").length()>500)throw new Exception("日记尝试记录无效");}
    }
}
