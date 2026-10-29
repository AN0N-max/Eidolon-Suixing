package com.eidolon.sc.mobile;
import org.json.*;
import java.time.*;
import java.util.*;
public final class ReplyTimeTests {
    static int n;static void check(boolean ok,String title){if(!ok)throw new AssertionError(title);n++;System.out.println("PASS "+title);}
    public static void main(String[] args)throws Exception{
        String full="【消息发送时间：2026-10-26 23:24:58 +08:00；相对日期仅以该消息时间为基准】\n哇……馄饨……！",shorter="【发送时间：2026-10-26 23:26:20 +08:00】\n晚安……💤";
        check(ReplyText.clean(full).equals("哇……馄饨……！"),"screenshot full transport header removed");check(ReplyText.clean(shorter).equals("晚安……💤"),"screenshot abbreviated header removed");
        check(ReplyText.clean("<ja>"+shorter+"</ja><zh>"+full+"</zh>").replace("\n","").equals("<ja>晚安……💤</ja><zh>哇……馄饨……！</zh>"),"headers inside bilingual content removed without losing either language");
        check(ReplyText.clean("[发送时间: 2026-10-26\n23:26:20 +0800]\n晚安").equals("晚安"),"ASCII brackets and line-wrapped timestamp handled");
        check(ReplyText.clean("【消息发送时间：发送时间未知（旧记录）；相对日期仅以该消息时间为基准】\n旧台词").equals("旧台词"),"unknown legacy transport label removed");
        check(ReplyText.clean("发送时间：2026-10-26 23:26:20 +08:00\n晚安").equals("晚安"),"standalone reserved time heading removed");
        check(ReplyText.clean("昨天是10月25日，明天10月27日一起看电影。现在是23:26。").equals("昨天是10月25日，明天10月27日一起看电影。现在是23:26。"),"ordinary date and clock dialogue preserved");
        check(ReplyText.clean("【发送时间：明天早上】可以吗？").equals("【发送时间：明天早上】可以吗？"),"non-transport quoted date language is not broadly deleted");
        check(ReplyText.clean(ReplyText.clean(full)).equals(ReplyText.clean(full)),"cleanup is idempotent");
        boolean rejected=false;try{ReplyText.require("<ja>【发送时间：2026-10-26 23:26:20 +08:00】</ja>");}catch(java.io.IOException e){rejected=true;}check(rejected,"metadata-only response rejected instead of committing empty dialogue");
        check(FishClient.speech("<ja>"+shorter+"</ja>").equals("晚安……💤"),"TTS input omits internal time label");
        check(BilingualReply.japanese("<ja>"+full+"</ja>").equals("哇……馄饨……！"),"translation extraction omits internal time label");
        long first=Instant.parse("2026-10-23T02:00:00Z").toEpochMilli(),last=Instant.parse("2026-10-26T15:26:00Z").toEpochMilli();
        ChatStore.Message oldUser=new ChatStore.Message(1,"user","昨天去看电影",first,"Asia/Shanghai"),oldReply=new ChatStore.Message(2,"assistant",full,first+1000,"Asia/Shanghai");
        List<ChatStore.Message> batch=Arrays.asList(new ChatStore.Message(3,"user","晚安",last,"Asia/Shanghai"),new ChatStore.Message(4,"user","明天见",last+4000,"Asia/Shanghai"));
        JSONArray messages=ModelClient.conversationRequest("m","角色规则",Arrays.asList(oldUser,oldReply),batch).getJSONArray("messages");String system=messages.getJSONObject(0).getString("content");
        check(messages.length()==5&&messages.getJSONObject(1).getString("content").equals("昨天去看电影")&&messages.getJSONObject(2).getString("content").equals("哇……馄饨……！"),"history content contains dialogue, never prefixed time samples");
        check(messages.getJSONObject(3).getString("content").equals("晚安")&&messages.getJSONObject(4).getString("content").equals("明天见"),"merged batch keeps separate original user messages");
        check(system.contains("2026-10-23")&&system.contains("2026-10-26")&&system.contains("不得复述")&&system.contains("\"index\":4"),"system reference retains every selected message timestamp and no-echo rule");
        JSONArray neutral=ModelClient.request("m","sys",Collections.singletonList(new ChatStore.Message("assistant","旧台词")),"now").getJSONArray("messages");check(neutral.getJSONObject(0).getString("content").contains("未知")&&neutral.getJSONObject(1).getString("content").equals("旧台词"),"unknown legacy dates are metadata only, never today");
        String userQuote="【发送时间：2026-10-26 23:26:20 +08:00】是什么意思？";check(MessageTime.content(new ChatStore.Message("user",userQuote)).equals(userQuote),"user-authored labels are not removed from user input");
        List<ChatStore.Message> emptyAssistant=Arrays.asList(new ChatStore.Message("user","你好"),new ChatStore.Message("assistant","【发送时间：2026-10-26 23:26:20 +08:00】"));check(ModelClient.request("m","s",emptyAssistant,"继续").getJSONArray("messages").length()==3,"empty cleaned legacy assistant message not sent as invalid API content");
        System.out.println("PASS "+n+" transport time isolation and output checks; no API or Android runtime.");
    }
}
