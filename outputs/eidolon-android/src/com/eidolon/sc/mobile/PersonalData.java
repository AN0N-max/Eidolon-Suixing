package com.eidolon.sc.mobile;

import org.json.*;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Local personal data. No API credentials, model outputs or executable content. */
final class PersonalData {
    static String today(){return LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();}
    static JSONObject fresh() throws Exception {
        return new JSONObject().put("day",today()).put("draws",1).put("inventory",new JSONObject())
            .put("affinity",new JSONObject()).put("memories",new JSONArray()).put("profile","").put("producer",ProducerProfile.empty()).put("giftsSent",0);
    }
    static void validate(JSONObject d) throws Exception {
        if(d.toString().length()>3000000)throw new Exception("个人资料过大");
        LocalDate.parse(d.getString("day"));
        if(d.getInt("draws")<0||d.getInt("draws")>100000||d.getInt("giftsSent")<0)throw new Exception("礼物数据无效");
        if(d.getString("profile").length()>2000)throw new Exception("档案过长");
        if(d.has("producer"))ProducerProfile.validate(d.getJSONObject("producer"));
        JSONObject inventory=d.getJSONObject("inventory"),affinity=d.getJSONObject("affinity");
        for(Iterator<String> it=inventory.keys();it.hasNext();){String k=it.next();if(!k.matches("[a-z0-9_-]{1,80}")||inventory.getInt(k)<0||inventory.getInt(k)>100000)throw new Exception("背包无效");}
        for(Iterator<String> it=affinity.keys();it.hasNext();){String k=it.next();if(!k.matches("[a-z0-9_-]{1,80}")||!Double.isFinite(affinity.getDouble(k))||affinity.getDouble(k)<0||affinity.getDouble(k)>100)throw new Exception("好感数据无效");}
        JSONArray memories=d.getJSONArray("memories");if(memories.length()>2000)throw new Exception("最多 2000 条记忆");
        CharacterPrompts.validate(d,null);AppealData.validate(d);HeartScenes.validate(d);Progression.validate(d);PromiseData.validate(d,null);Anniversaries.validate(d,null);
        Set<String> ids=new HashSet<>();
        for(int i=0;i<memories.length();i++){JSONObject m=memories.getJSONObject(i);if(!ids.add(m.getString("id"))||m.getString("id").length()>80||m.getString("text").trim().isEmpty()||m.getString("text").length()>500)throw new Exception("记忆格式无效");}
        Map<String,String> links=new HashMap<>();for(int i=0;i<memories.length();i++){JSONObject m=memories.getJSONObject(i);String next=m.optString("supersededBy");if(!next.isEmpty()){if(!ids.contains(next))throw new Exception("记忆替代引用无效");links.put(m.getString("id"),next);}if(m.optString("evidence").length()>6000||m.optString("source").length()>80)throw new Exception("记忆来源过长");}
        for(String id:links.keySet()){Set<String> seen=new HashSet<>();String next=id;while(links.containsKey(next)){if(!seen.add(next))throw new Exception("记忆替代存在循环");next=links.get(next);}}
    }
    static void refresh(JSONObject d,String day) throws Exception {
        Progression.refreshDaily(d,day);
        long elapsed=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(d.getString("day")),LocalDate.parse(day));
        if(elapsed<=0)return; // Turning the clock backwards never grants additional draws.
        d.put("draws",Math.max(d.getInt("draws"),Math.min(7,d.getInt("draws")+elapsed)));d.put("day",day);

    }
    static String tier(int score){return score<20?"冷淡":score<40?"疏远":score<65?"信任":score<85?"亲近":"亲密";}
    static JSONArray draw(JSONObject d,JSONArray catalog,boolean all,SecureRandom random)throws Exception{
        int n=d.getInt("draws");if(n<1)throw new Exception("今天没有抽取次数了");if(!all)n=1;else n=Math.min(n,100);
        JSONArray result=new JSONArray();JSONObject bag=d.getJSONObject("inventory");
        for(int i=0;i<n;i++){
            int roll=random.nextInt(100);String id="reset-card";
            if(roll!=0){String rarity=roll<70?"common":roll<95?"uncommon":"rare";List<String> pool=new ArrayList<>();
                for(int j=0;j<catalog.length();j++){JSONObject g=catalog.getJSONObject(j);if(!g.optBoolean("retired")&&rarity.equals(g.getString("rarity")))pool.add(g.getString("id"));}
                if(pool.isEmpty())throw new Exception("礼物目录缺失");id=pool.get(random.nextInt(pool.size()));}
            bag.put(id,Math.min(100000,bag.optInt(id)+1));result.put(id);
        }d.put("draws",d.getInt("draws")-n);Progression.happened(d,null,0,0,n,today());return result;
    }
    static int give(JSONObject d,JSONObject gift,JSONObject character)throws Exception{
        String id=gift.getString("id"),who=character.getString("id");JSONObject bag=d.getJSONObject("inventory");
        if(bag.optInt(id)<=0)throw new Exception("背包中没有这件礼物");
        String rarity=gift.getString("rarity");int amount=rarity.equals("rare")?7:rarity.equals("uncommon")?5:3;
        JSONArray liked=character.optJSONArray("liked_gifts");if(liked!=null)for(int i=0;i<liked.length();i++)if(id.equals(liked.getString(i))){amount+=3;break;}
        JSONObject relation=Progression.relation(d,who,System.currentTimeMillis());JSONObject a=d.getJSONObject("affinity");double before=a.optDouble(who,50);if(relation.getBoolean("cooling"))amount=Math.min(3,amount);
        double after=Math.min(100,before+amount);if(after>=10)relation.put("cooling",false);a.put(who,after);HeartScenes.gate(d,who,before);after=a.getDouble(who);bag.put(id,bag.getInt(id)-1);d.put("giftsSent",d.getInt("giftsSent")+1);Progression.happened(d,who,0,1,0,today());return (int)Math.round(after-before);
    }
    static void reset(JSONObject d)throws Exception{
        JSONObject bag=d.getJSONObject("inventory");if(bag.optInt("reset-card")<1)throw new Exception("没有重置卡");
        JSONObject a=d.getJSONObject("affinity");boolean any=false;for(Iterator<String> it=a.keys();it.hasNext();){String id=it.next();Progression.relation(d,id,System.currentTimeMillis());if(a.getDouble(id)<40){a.put(id,50);Progression.relation(d,id,System.currentTimeMillis()).put("cooling",false);any=true;}}if(!any)throw new Exception("现在没有低于信任的角色");bag.put("reset-card",bag.getInt("reset-card")-1);
    }
}
