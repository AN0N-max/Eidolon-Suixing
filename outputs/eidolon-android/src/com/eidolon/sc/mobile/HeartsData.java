package com.eidolon.sc.mobile;

import org.json.*;
import java.util.*;

/** Hibi hearts are reference data; score changes remain exclusively in Progression. */
final class HeartsData {
    static final List<String> TIERS=Arrays.asList("cold","distant","trust","close","intimate");
    static String tier(double score){return score<20?"cold":score<40?"distant":score<65?"trust":score<85?"close":"intimate";}
    static void validate(JSONObject h)throws Exception{
        int age=h.getInt("age");if(age<=0||age>=100)throw new Exception("角色关系档案年龄无效");
        for(String key:Arrays.asList("pleased","hurt")){JSONArray a=h.getJSONArray(key);if(a.length()==0||a.length()>30)throw new Exception("角色关系参考无效");for(int i=0;i<a.length();i++)if(a.getString(i).trim().isEmpty()||a.getString(i).length()>2000)throw new Exception("角色关系参考文本无效");}
        JSONObject tiers=h.getJSONObject("tiers");if(tiers.length()!=5)throw new Exception("角色关系档位不完整");for(String t:TIERS)if(tiers.getString(t).trim().isEmpty())throw new Exception("角色关系档位为空");
        JSONObject romance=h.optJSONObject("romance");if(romance!=null){if(age<18&&romance.length()>0)throw new Exception("未成年角色关系档案不能包含恋爱配置");for(Iterator<String> it=romance.keys();it.hasNext();){String key=it.next();if(!Arrays.asList("close_male","close_female","intimate_male","intimate_female").contains(key)||romance.getString(key).trim().isEmpty())throw new Exception("角色恋爱配置无效");}}
    }
    static String judge(JSONObject h)throws Exception{
        if(h==null)return "";
        return "\n角色专属关系参考（Hibi 她的心）：特别让她开心的："+joined(h.getJSONArray("pleased"))+"。特别让她不舒服、受伤的："+joined(h.getJSONArray("hurt"))+"。这些只是参考，仍按原五档标准判断；不要因为说中一条就判最高或最低，不改变越界及操控判断。";
    }
    static JSONObject questions(JSONObject h,JSONObject original)throws Exception{
        JSONObject out=new JSONObject(original.toString()),affinity=out.optJSONObject("affinity");if(h!=null&&affinity!=null)affinity.put("instructions",affinity.optString("instructions")+judge(h));return out;
    }
    static String reply(JSONObject h,double score,String gender)throws Exception{
        if(h==null)return "";String tier=tier(score);
        String text="\n角色当前关系表现（Hibi 她的心）："+h.getJSONObject("tiers").getString(tier)+"只影响现在如何对待制作人，不改变原作经历；自然表达，不照念档案，不编造升档原因。";
        if(tier.equals("close")||tier.equals("intimate")){
            if(h.getInt("age")<18)text+="这份亲近是信赖和依赖，不是恋爱。";
            else{JSONObject romance=h.optJSONObject("romance");String style=romance==null?"":romance.optString(tier+(gender.equals("female")?"_female":"_male"));if(!style.isEmpty())text+="\n"+style+"只允许按角色性格表达心动和喜欢，性方面的要求仍不接受；除恋爱这一处，人设的其他硬性规则照旧。";}
        }
        return text;
    }
    private static String joined(JSONArray a)throws Exception{StringBuilder text=new StringBuilder();for(int i=0;i<a.length();i++){if(i>0)text.append('；');text.append(a.getString(i));}return text.toString();}
    private HeartsData(){}
}
