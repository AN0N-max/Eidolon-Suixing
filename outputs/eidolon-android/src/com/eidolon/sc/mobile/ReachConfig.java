package com.eidolon.sc.mobile;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Iterator;
import java.io.IOException;

/** Validated, versioned local reach-out settings. Old preferences remain readable. */
final class ReachConfig {
    static final String KEY="reachSettingsV1";
    private final JSONObject data;
    ReachConfig(JSONObject options)throws Exception{
        data=defaults();
        if(options!=null)for(Iterator<String> it=data.keys();it.hasNext();){String k=it.next();if(options.has(k))data.put(k,options.get(k));}
        for(String k:new String[]{"sound","vibrate","quietEnabled","dailyRespectsQuiet","notifications","privacy","restrictCharacters"})if(!(data.get(k) instanceof Boolean))throw new IOException("主动搭话开关格式不正确");
        for(String k:new String[]{"hours","trustMin","trustMax","closeMin","closeMax","intimateMin","intimateMax"})range(k,.25,720,false);
        for(String k:new String[]{"trust","close","intimate"})if(number(k+"Min")>number(k+"Max"))throw new IOException("关系间隔的最短时间不能大于最长时间");
        range("planGapMinutes",15,240,true);range("recentMinutes",5,1440,true);range("expiryMinutes",15,240,true);
        range("dailyLimit",1,30,true);range("characterDailyLimit",1,30,true);range("attemptLimit",1,100,true);
        range("contactDays",1,365,true);range("minAffinity",0,100,true);range("spreadMinutes",0,240,true);
        range("frontSeconds",15,3600,true);range("backgroundMinutes",15,1440,true);range("historyLimit",4,100,true);range("memoryLimit",0,20,true);
        range("width",240,420,true);range("popupSeconds",2,60,false);range("retryMinutes",0,1440,true);
        if(!java.util.Arrays.asList("relation","interval","daily").contains(text("mode"))||!java.util.Arrays.asList("top","center","bottom").contains(text("vertical"))||!java.util.Arrays.asList("left","center","right").contains(text("side")))throw new IOException("搭话方式或弹窗位置不正确");
        for(String k:new String[]{"time","quietStart","quietEnd"})if(!text(k).matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))throw new IOException("时间应为 HH:mm，例如 22:00");
        if(text("quietStart").equals(text("quietEnd")))throw new IOException("免打扰开始和结束不能相同；全天允许请关闭免打扰开关");
        if(!(data.get("characters") instanceof org.json.JSONArray)||jsonArray("characters").length()>100||!(data.get("characterLimits") instanceof JSONObject))throw new IOException("角色范围格式不正确");
        java.util.Set<String> seen=new java.util.HashSet<>();for(int i=0;i<jsonArray("characters").length();i++){Object id=jsonArray("characters").get(i);if(!(id instanceof String)||!((String)id).matches("[a-z0-9-]+")||!seen.add((String)id))throw new IOException("角色范围包含无效或重复角色");}
        JSONObject limits=object("characterLimits");for(Iterator<String> it=limits.keys();it.hasNext();){String who=it.next();Object n=limits.get(who);if(!who.matches("[a-z0-9-]+")||!(n instanceof Number)||((Number)n).doubleValue()!=Math.rint(((Number)n).doubleValue())||((Number)n).doubleValue()<1||((Number)n).doubleValue()>30)throw new IOException("单角色次数应为1到30");}
        try{java.time.ZoneId.of(text("zone"));}catch(Exception e){throw new IOException("时区无效，例如 Asia/Shanghai 或 Asia/Tokyo");}
    }
    private void range(String k,double lo,double hi,boolean whole)throws Exception{
        Object raw=data.get(k);if(!(raw instanceof Number))throw new IOException("主动搭话数值格式不正确："+k);
        double n=((Number)raw).doubleValue();if(!Double.isFinite(n)||n<lo||n>hi||whole&&n!=Math.rint(n))throw new IOException("主动搭话数值超出范围："+k+"（"+lo+"–"+hi+"）");
    }
    static JSONObject defaults()throws Exception{return new JSONObject()
        .put("mode","relation").put("hours",12).put("time","12:00").put("zone","Asia/Shanghai")
        .put("notifications",true).put("privacy",false).put("restrictCharacters",false).put("characters",new org.json.JSONArray()).put("characterLimits",new JSONObject()).put("planGapMinutes",30).put("recentMinutes",30).put("expiryMinutes",90)
        .put("dailyLimit",3).put("characterDailyLimit",3).put("attemptLimit",10).put("contactDays",7).put("minAffinity",40)
        .put("trustMin",20).put("trustMax",36).put("closeMin",8).put("closeMax",16).put("intimateMin",3).put("intimateMax",6)
        .put("quietEnabled",true).put("quietStart","22:00").put("quietEnd","08:00").put("dailyRespectsQuiet",false).put("spreadMinutes",120)
        .put("frontSeconds",60).put("backgroundMinutes",15).put("historyLimit",30).put("memoryLimit",3).put("retryMinutes",0)
        .put("width",340).put("vertical","top").put("side","center").put("sound",true).put("vibrate",true).put("popupSeconds",6.5);}
    static ReachConfig read(SharedPreferences p){
        try{String raw=p.getString(KEY,"");if(!raw.isEmpty())return new ReachConfig(new JSONObject(raw));}catch(Exception ignored){}
        try{return new ReachConfig(new JSONObject().put("mode",p.getString("reachMode","relation")).put("hours",p.getInt("reachHours",12)).put("time",p.getString("reachTime","12:00")).put("width",p.getInt("reachWidth",340)).put("vertical",p.getString("reachVertical","top")).put("side",p.getString("reachSide","center")).put("sound",p.getBoolean("reachSound",true)).put("vibrate",p.getBoolean("reachVibrate",true)));}catch(Exception ignored){}
        try{return new ReachConfig(null);}catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    ReachConfig updated(JSONObject changes)throws Exception{JSONObject next=json();for(Iterator<String> it=changes.keys();it.hasNext();){String k=it.next();if(next.has(k))next.put(k,changes.get(k));}return new ReachConfig(next);}
    JSONObject json()throws Exception{return new JSONObject(data.toString());}
    int integer(String k){return data.optInt(k);}
    double number(String k){return data.optDouble(k);}
    String text(String k){return data.optString(k);}
    boolean flag(String k){return data.optBoolean(k);}
    org.json.JSONArray jsonArray(String k){return data.optJSONArray(k);}
    JSONObject object(String k){return data.optJSONObject(k);}
    boolean quietApplies(){return flag("quietEnabled")&&(!text("mode").equals("daily")||flag("dailyRespectsQuiet"));}
}
