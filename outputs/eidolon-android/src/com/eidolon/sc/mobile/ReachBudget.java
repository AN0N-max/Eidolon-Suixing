package com.eidolon.sc.mobile;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Each explicit model request is reserved durably before calling the service. */
final class ReachBudget {
    static JSONObject state(String raw,String today)throws Exception{
        if(raw.isEmpty())return new JSONObject().put("day",today).put("used",0);
        JSONObject old=new JSONObject(raw);String day=old.getString("day");java.time.LocalDate.parse(day);
        double used=old.getDouble("used");if(!Double.isFinite(used)||used<0||used!=Math.rint(used)||used>1000000)throw new Exception("搭话请求计数无效");
        if(today.compareTo(day)>0)return new JSONObject().put("day",today).put("used",0);
        return old;
    }
    static int used(SharedPreferences p,ReachConfig config,long now){try{return state(p.getString("reachRequestBudget",""),ReachPolicy.date(config,now)).getInt("used");}catch(Exception e){return config.integer("attemptLimit");}}
    static synchronized boolean reserve(SharedPreferences p,ReachConfig config,long now)throws Exception{
        JSONObject row=state(p.getString("reachRequestBudget",""),ReachPolicy.date(config,now));int used=row.getInt("used");if(used>=config.integer("attemptLimit"))return false;
        row.put("used",used+1);if(!p.edit().putString("reachRequestBudget",row.toString()).commit())throw new java.io.IOException("无法保存搭话请求计数，本次未调用模型");return true;
    }
    private ReachBudget(){}
}
