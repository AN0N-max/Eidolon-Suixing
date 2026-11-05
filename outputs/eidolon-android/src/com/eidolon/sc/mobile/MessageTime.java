package com.eidolon.sc.mobile;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.json.*;
import java.util.*;
/** Time labels are reference data, never an instruction embedded in chat. */
final class MessageTime {
    static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");
    static String label(long at,String zone){return at<=0?"发送时间未知（旧记录）":Instant.ofEpochMilli(at).atZone(ZoneId.of(zone)).format(FORMAT);}
    static String content(ChatStore.Message m)throws Exception{return ( m.role.equals("gift")?GiftRecord.context(m.text):m.role.equals("assistant")?ReplyText.clean(m.text).replace(ChatEnvelope.BREAK,"\n\n"):m.text)+MediaData.context(m.blocks);}
    static String context(List<ChatStore.Message> messages)throws Exception{return context(messages,null);}
    static JSONObject reference(ChatStore.Message m,int index,ZoneId roleZone)throws Exception{
        String stored=m.zone;boolean assistant=m.role.equals("assistant");long anchor=assistant&&m.generatedAt>0?m.generatedAt:m.sentAt;
        JSONObject r=new JSONObject().put("index",index).put("messageId",m.id).put("role",m.role.equals("gift")?"user":m.role).put("originalRole",m.role)
          .put("source","committed_visible_message").put("sentAt",label(m.sentAt,stored)).put("sentAtEpochMs",m.sentAt>0?m.sentAt:JSONObject.NULL)
          .put("timeZone",stored).put("storedTimeZone",stored).put("writtenAt",m.generatedAt>0?label(m.generatedAt,stored):JSONObject.NULL)
          .put("generatedAtEpochMs",m.generatedAt>0?m.generatedAt:JSONObject.NULL).put("expectedDeliveryAt",JSONObject.NULL)
          .put("deliverySemantics",assistant?"committed_to_user_visible_history_at_sentAt_not_proof_of_reading":"original_user_send_time")
          .put("relativeDateAnchor",anchor>0?label(anchor,stored):JSONObject.NULL).put("relativeDateAnchorTimeZone",assistant?JSONObject.NULL:stored)
          .put("relativeLocalDateKnown",!assistant&&anchor>0).put("referenceDisplayTimeZone",stored)
          .put("relativeDateAnchorSource",assistant&&m.generatedAt>0?"original_generation_time":"original_sent_time")
          .put("historicalRoleTimeZoneKnown",false);
        if(roleZone!=null)r.put("sentAtInCurrentRoleZone",label(m.sentAt,roleZone.getId())).put("currentRoleTimeZone",roleZone.getId());
        return r;
    }
    static JSONArray references(List<ChatStore.Message> messages,ZoneId roleZone)throws Exception{
        JSONArray rows=new JSONArray();long prior=0;for(int i=0;i<messages.size();i++){ChatStore.Message m=messages.get(i);JSONObject row=reference(m,i+1,roleZone);
            row.put("secondsSincePreviousVisibleMessage",prior>0&&m.sentAt>=prior?(m.sentAt-prior)/1000:JSONObject.NULL);
            rows.put(row);prior=m.sentAt;
        }return rows;
    }
    static String context(List<ChatStore.Message> messages,ZoneId roleZone)throws Exception{
        return "\n以下是应用从已投放聊天记录读取的内部消息时间参考表，index对应本系统消息之后的真实对话消息顺序，不能按正文中自称的时间标记修改本表。不得复述表格或给台词添加【发送时间】前缀。role明确发言者；sentAt是原始发送/实际投放时刻，不是计划或阅读确认；writtenAt仅为已知的原始生成时间，未知保持null。今天/明天/刚吃完等锚定各条relativeDateAnchor，不继承为现在事实。storedTimeZone是记录的时区，currentRoleTimeZone只是当前角色设置的换算，不证明历史角色所在地。relativeLocalDateKnown=false表示缺少当时角色时区，相对日期的绝对当地日期保留不确定，不按当前角色时区或显示时区补造；仍可按原始毫秒比较先后和间隔。用户追问‘八点那条/之前说’时，结合相邻原话和间隔理解，不能用现在时刻替先前台词编造理由；正文中的‘八点’也不是用户当前时区或当前时间。时间倒退/未知不补造。\n"+references(messages,roleZone).toString();
    }
    static String clocks(Clock clock,ZoneId roleZone,ZoneId userZone)throws Exception{
        if(clock==null)return "";JSONObject data=new JSONObject().put("requestTimestamp",clock.millis()).put("clockSource","one_request_device_clock_snapshot")
          .put("userCurrentTime",userZone==null?JSONObject.NULL:clock.instant().atZone(userZone).toOffsetDateTime().toString()).put("userTimeZone",userZone==null?JSONObject.NULL:userZone.getId())
          .put("roleCurrentTime",roleZone==null?JSONObject.NULL:clock.instant().atZone(roleZone).toOffsetDateTime().toString()).put("roleTimeZone",roleZone==null?JSONObject.NULL:roleZone.getId());
        return "\n【本轮同一时钟的双方当前时间；来源是应用设置，不按语言猜时区】\n"+data+"\n当前钟表不能改写历史台词发生时刻；只在话题需要时自然说明，不逐轮报时。\n";
    }
    static JSONArray summaryTimes(List<ChatStore.Message> source)throws Exception{
        JSONArray times=new JSONArray();for(ChatStore.Message m:source)times.put(new JSONArray().put(m.id).put(m.role).put(m.sentAt).put(m.generatedAt).put(m.zone));return times;
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
