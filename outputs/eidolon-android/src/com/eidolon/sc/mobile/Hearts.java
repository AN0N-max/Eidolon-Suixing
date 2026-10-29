package com.eidolon.sc.mobile;

import android.content.Context;
import org.json.*;
import java.io.InputStream;
import java.util.Iterator;

/** Shared heart data for Activity generation, recovery, reach-outs and local writing. */
final class Hearts {
    private static JSONObject cached;
    static synchronized JSONObject profile(Context c,String who)throws Exception{
        if(cached==null){JSONObject root;try(InputStream in=c.getAssets().open("hearts.json")){root=new JSONObject(new String(StoryFiles.read(in,1024*1024),"UTF-8"));}JSONObject rows=root.getJSONObject("profiles");for(Iterator<String> keys=rows.keys();keys.hasNext();)HeartsData.validate(rows.getJSONObject(keys.next()));cached=rows;}
        JSONObject row=cached.optJSONObject(who);return row==null?null:new JSONObject(row.toString());
    }
    static boolean enabled(Context c){return c.getSharedPreferences("settings",0).getBoolean("hibiHearts",true);}
    static String judge(Context c,String who)throws Exception{return enabled(c)?HeartsData.judge(profile(c,who)):"";}
    static JSONObject questions(Context c,String who,JSONObject q)throws Exception{return enabled(c)?HeartsData.questions(profile(c,who),q):q;}
    static String reply(Context c,String who,JSONObject personal)throws Exception{
        if(!enabled(c)||!c.getSharedPreferences("settings",0).getBoolean("affinity",true))return "";
        JSONObject producer=personal.optJSONObject("producer");return HeartsData.reply(profile(c,who),personal.getJSONObject("affinity").optDouble(who,50),producer==null?"":producer.optString("gender"));
    }
    static JSONObject view(Context c,String who,JSONObject personal)throws Exception{
        JSONObject h=profile(c,who);if(h==null)return new JSONObject().put("available",false);
        return new JSONObject().put("available",true).put("enabled",enabled(c)&&c.getSharedPreferences("settings",0).getBoolean("affinity",true)).put("age",h.getInt("age")).put("pleased",h.getJSONArray("pleased")).put("hurt",h.getJSONArray("hurt")).put("behavior",h.getJSONObject("tiers").getString(HeartsData.tier(personal.getJSONObject("affinity").optDouble(who,50))));
    }
    private Hearts(){}
}
