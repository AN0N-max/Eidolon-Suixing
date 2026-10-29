package com.eidolon.sc.mobile;

import org.json.*;
import java.time.*;
import java.util.*;

/** Validation for local letters and diaries, including imported backups. */
final class WritingData {
    static LocalDate today(JSONObject personal){
        JSONObject p=personal.optJSONObject("producer");String id=p==null?"":p.optString("timeZone");
        return LocalDate.now(id.isEmpty()?ZoneId.systemDefault():ZoneId.of(id));
    }
    static void validate(JSONObject row)throws Exception{
        if(!Arrays.asList("letter","diary").contains(row.getString("kind"))||!row.getString("character").matches("[a-z0-9_-]{1,80}"))throw new Exception("信件或日记类型无效");
        LocalDate.parse(row.getString("date"));
        String text=row.getString("text");Object hidden=row.opt("secret");if(hidden!=null&&!(hidden instanceof String))throw new Exception("隐藏页格式无效");String secret=row.optString("secret","");
        if(text.trim().isEmpty()||text.length()+secret.length()>12000||!secret.isEmpty()&&(!row.getString("kind").equals("diary")||secret.trim().isEmpty()))throw new Exception("信件或日记内容无效");
        if(row.has("secretRead")&&!(row.get("secretRead") instanceof Boolean)||row.optBoolean("secretRead")&&secret.isEmpty())throw new Exception("隐藏页阅读状态无效");
    }
    static void validateBackup(JSONObject data,Set<String> characters)throws Exception{
        JSONArray rows=data.getJSONArray("writings"),events=data.getJSONArray("writingEvents");
        if(rows.length()>500||events.length()>1000)throw new Exception("信件日记备份数量过多");
        int size=0;Set<String> ids=new HashSet<>();
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);validate(row);if(!characters.contains(row.getString("character"))||!ids.add(key(row.getString("kind"),row.getString("character"),row.getString("date"))))throw new Exception("信件日记角色或日期重复");size+=row.getString("text").length()+row.optString("secret","").length();}
        ids.clear();for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);LocalDate.parse(e.getString("date"));if(!characters.contains(e.getString("character"))||e.getString("text").isEmpty()||e.getString("text").length()>4000||!ids.add(e.getString("character")+"/"+e.getString("date")))throw new Exception("日记事件无效");size+=e.getString("text").length();}
        if(size>2500000)throw new Exception("信件日记备份过大");
    }
    static String key(String kind,String who,String date){return kind+"/"+who+"/"+date;}
    static JSONObject visible(JSONObject stored,double score)throws Exception{
        return visible(stored,score,false,true);
    }
    static JSONObject visible(JSONObject stored,double score,boolean cooling,boolean affinity)throws Exception{
        JSONObject row=new JSONObject(stored.toString());boolean secret=!row.optString("secret","").isEmpty(),read=row.optBoolean("secretRead");
        // Private text never travels in a list/snapshot, even at intimate level.
        row.remove("secret");row.remove("secretRead");for(String key:new String[]{"locked","excerpt","hasHiddenPage","hiddenAvailable","hiddenUnread"})row.remove(key);
        if(!row.getString("kind").equals("diary"))return row;
        String shown=DiaryData.shown(score,cooling,affinity);row.put("shown",shown);
        if(shown.equals("closed"))row.put("text","").put("locked",true);
        else if(shown.equals("opening")){String text=preview(row.getString("text"));row.put("text",text.substring(0,Math.min(100,text.length()))+"…").put("excerpt",true);}
        else if(secret&&affinity){row.put("hasHiddenPage",true);if(shown.equals("secret"))row.put("hiddenAvailable",true).put("hiddenUnread",!read);}
        return row;
    }
    static String preview(String text){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?s)<zh>(.*?)</zh>").matcher(text);StringBuilder b=new StringBuilder();while(m.find())b.append(m.group(1)).append('\n');return (b.length()>0?b.toString():text).replaceAll("<[^>]+>","").trim();}
}
