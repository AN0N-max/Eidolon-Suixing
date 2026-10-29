package com.eidolon.sc.mobile;

import org.json.JSONObject;

/** A local conversation event, kept separate from model-generated dialogue. */
final class GiftRecord {
    static JSONObject create(String id,String giftName,String characterName,double before,double after,boolean thawed,long at)throws Exception{
        String first=PersonalData.tier((int)before),last=PersonalData.tier((int)after);
        String note=after<=before?"已经送给"+characterName:thawed?characterName+"愿意和你说话了":
            !first.equals(last)?"和"+characterName+"更近了一步："+last:characterName+"有点开心";
        return new JSONObject().put("kind","gift").put("giftId",id).put("giftName",giftName)
            .put("note",note).put("at",at).put("delta",after-before);
    }
    static JSONObject parse(String body)throws Exception{
        JSONObject event=new JSONObject(body);
        String id=event.getString("giftId"),name=event.getString("giftName"),note=event.getString("note");
        double delta=event.getDouble("delta");
        if(!"gift".equals(event.getString("kind"))||!id.matches("[a-z0-9_-]{1,80}")||name.isEmpty()||name.length()>100||
            note.isEmpty()||note.length()>160||event.getLong("at")<=0||!Double.isFinite(delta)||delta<0||delta>10)
            throw new Exception("礼物记录无效");
        return event;
    }
    static String context(String body)throws Exception{
        JSONObject event=parse(body);
        return "（已发生的送礼记录：制作人送给你一份"+event.getString("giftName")+"。"+event.getString("note")+"。）";
    }
}
