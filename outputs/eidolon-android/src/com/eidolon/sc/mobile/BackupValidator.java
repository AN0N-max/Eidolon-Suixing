package com.eidolon.sc.mobile;

import org.json.*;
import java.util.Set;

final class BackupValidator {
    static void validate(JSONObject data, Set<String> characters) throws Exception {
        if(!"eidolon-android-chats".equals(data.optString("format")) || data.optInt("version")<1||data.optInt("version")>13)throw new Exception("不支持的备份格式或版本");
        if(data.optInt("version")>=13)MemoryNotebook.validateBackup(data,characters);
        if(data.optInt("version")>=9)LetterPlanData.validateBackup(data,characters);
        if(data.optInt("version")>=10)DiaryData.validateAttempts(data,characters);
        if(data.optInt("version")>=2)PromiseData.validate(data.getJSONObject("personal"),characters);
        if(data.optInt("version")>=7)ScheduleData.validateBackup(data,characters);
        if(data.optInt("version")>=6)WritingData.validateBackup(data,characters);
        if(data.optInt("version")>=2){PersonalData.validate(data.getJSONObject("personal"));CharacterPrompts.validate(data.getJSONObject("personal"),characters);Anniversaries.validate(data.getJSONObject("personal"),characters);}
        if(data.optInt("version")>=3){JSONArray stories=data.getJSONArray("stories");if(stories.length()>10000)throw new Exception("剧情数量过多");int length=0;for(int i=0;i<stories.length();i++){JSONObject story=stories.getJSONObject(i);StoryFiles.validate(story);length+=story.toString().length();if(length>3500000)throw new Exception("剧情备份过大");JSONArray cast=story.getJSONArray("cast");for(int j=0;j<cast.length();j++)if(!characters.contains(cast.getString(j)))throw new Exception("剧情含未知角色");}}
        JSONArray sessions=data.getJSONArray("conversations");
        if(sessions.length()>10000)throw new Exception("备份会话数量过多");
        int total=0;java.util.Set<String> judgmentKeys=new java.util.HashSet<>();java.util.Set<Long> messageIds=new java.util.HashSet<>();
        for(int i=0;i<sessions.length();i++) {
            JSONObject s=sessions.getJSONObject(i);
            if(!characters.contains(s.getString("character")))throw new Exception("备份含未知角色");
            if(s.getString("title").length()>500)throw new Exception("备份标题过长");
            JSONArray rows=s.getJSONArray("messages");
            if(rows.length()>100000)throw new Exception("备份对话不完整");
            int turnRows=0;
            for(int j=0;j<rows.length();j++) {
                JSONObject row=rows.getJSONObject(j);String role=row.getString("role"),text=row.getString("text");
                if(data.optInt("version")>=5&&role.equals("gift")){
                    if(turnRows%2!=0||row.optBoolean("reachOut"))throw new Exception("送礼记录位置错误");
                    GiftRecord.parse(text);
                }
                else if(data.optInt("version")>=4&&row.optBoolean("reachOut")){if(!role.equals("assistant")||turnRows%2!=0)throw new Exception("搭话消息位置错误");}
                else{if(!(turnRows%2==0?"user":"assistant").equals(role))throw new Exception("备份消息顺序错误");turnRows++;}
                if(data.optInt("version")>=12&&(!(row.opt("unread") instanceof Boolean)||row.optBoolean("unread")&&!role.equals("assistant")))throw new Exception("未读标记格式或消息角色错误");
                if(data.optInt("version")>=13){if(row.getLong("id")<=0||!messageIds.add(row.getLong("id")))throw new Exception("备份消息编号重复或无效");Object at=row.opt("sentAt");if(!(at instanceof Number)||((Number)at).doubleValue()!=((Number)at).longValue()||((Number)at).longValue()<0||((Number)at).longValue()>253402300799999L)throw new Exception("消息时间格式错误");java.time.ZoneId.of(row.getString("timeZone"));}
                if(row.has("judgment")){if(data.optInt("version")<11||!role.equals("assistant")||row.optBoolean("reachOut"))throw new Exception("申诉记录不能附在这种消息上");JSONObject judgment=row.getJSONObject("judgment");AppealData.validateRecord(judgment);if(!judgmentKeys.add(judgment.getString("key")))throw new Exception("申诉消息凭据重复");}
                if(text.length()>64000 || text.trim().isEmpty())throw new Exception("备份消息不合法");
                total+=text.length();if(total>4000000)throw new Exception("备份超过 400 万字限制");
            }
            if(turnRows%2!=0)throw new Exception("备份对话不完整");
        }
    }
}
