package com.eidolon.sc.mobile;

import android.content.Context;
import org.json.JSONArray;

/** Local timing diagnostics; never stores message text, model URLs or credentials. */
final class ReplyTrace {
    static String time(long at){return new java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.ROOT).format(new java.util.Date(at));}
    static synchronized void record(Context c,String stage){
        String line=time(System.currentTimeMillis())+" · "+stage;
        android.util.Log.i("EidolonDelay",line);
        try{android.content.SharedPreferences p=c.getSharedPreferences("settings",0);JSONArray old=read(c),next=new JSONArray();
            for(int i=Math.max(0,old.length()-39);i<old.length();i++)next.put(old.getString(i));next.put(line);
            p.edit().putString("replyTimingTrace",next.toString()).apply();
        }catch(Exception ignored){}
    }
    static JSONArray read(Context c){try{return new JSONArray(c.getSharedPreferences("settings",0).getString("replyTimingTrace","[]"));}catch(Exception e){return new JSONArray();}}
    private ReplyTrace(){}
}
