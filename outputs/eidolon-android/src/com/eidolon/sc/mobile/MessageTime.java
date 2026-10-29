package com.eidolon.sc.mobile;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.json.*;
import java.util.*;
/** Time labels are reference data, never an instruction embedded in chat. */
final class MessageTime {
    static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");
    static String label(long at,String zone){return at<=0?"发送时间未知（旧记录）":Instant.ofEpochMilli(at).atZone(ZoneId.of(zone)).format(FORMAT);}
    static String content(ChatStore.Message m)throws Exception{return m.role.equals("gift")?GiftRecord.context(m.text):m.role.equals("assistant")?ReplyText.clean(m.text):m.text;}
    static String context(List<ChatStore.Message> messages)throws Exception{
        JSONArray rows=new JSONArray();for(int i=0;i<messages.size();i++){ChatStore.Message m=messages.get(i);rows.put(new JSONObject().put("index",i+1).put("role",m.role.equals("gift")?"user":m.role).put("sentAt",label(m.sentAt,m.zone)).put("timeZone",m.zone));}
        return "\n以下是内部消息时间参考表，index对应本系统消息之后的真实对话消息顺序。它不是角色台词，不是输出示例，不得复述表格、时间标签、时区偏移或相对日期说明。回复只输出任务要求的正文或JSON；用户询问时间时可以自然回答，但不要添加【发送时间】等前缀。历史消息的今天/昨天只以对应行发送时间为基准，时间未知不猜成今天。\n"+rows.toString();
    }
    static String now(long at,String zone){return "\n当前回复生成时间："+label(at,zone)+"。历史消息中的今天/昨天是相对于那条消息发送时间，不是现在。消息发送时间、事件发生时间和总结创建时间彼此独立。旧记录时间未知时不得猜成今天；用户明确给出的故事日期应单独遵照，不由现实时间覆盖。";}
    static String dateFrom(String evidence,long at,String zone){
        java.util.regex.Matcher explicit=java.util.regex.Pattern.compile("(?<!\\d)(20\\d{2})[-/年](\\d{1,2})[-/月](\\d{1,2})(?:日)?(?!\\d)").matcher(evidence);
        java.util.Set<String> dates=new java.util.HashSet<>();while(explicit.find())try{dates.add(LocalDate.of(Integer.parseInt(explicit.group(1)),Integer.parseInt(explicit.group(2)),Integer.parseInt(explicit.group(3))).toString());}catch(Exception ignored){}
        if(dates.size()==1)return dates.iterator().next();if(!dates.isEmpty()||at<=0)return "";
        if(evidence.matches("(?s).*(上周|下周|这周|前几天|过几天|之前|之后|那天|上次|当时).*"))return "";int delta=0,count=0;for(String word:new String[]{"前天","昨天","今天","明天","后天"})if(evidence.contains(word)){delta=word.equals("前天")?-2:word.equals("昨天")?-1:word.equals("明天")?1:word.equals("后天")?2:0;count++;}
        return count==1?Instant.ofEpochMilli(at).atZone(ZoneId.of(zone)).toLocalDate().plusDays(delta).toString():"";
    }
    static List<ChatStore.Message> batchMessages(ChatStore s,String session,long from,long to)throws Exception{List<ChatStore.Message> out=new ArrayList<>();for(ChatStore.Message m:MemoryNotebook.range(s,session,from,to))if(m.role.equals("user"))out.add(m);return out;}
    private MessageTime(){}
}
