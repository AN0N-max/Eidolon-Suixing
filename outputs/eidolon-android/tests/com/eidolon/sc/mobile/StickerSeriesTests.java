package com.eidolon.sc.mobile;
import org.json.*;import java.util.*;
/** Executes production series recognition, ordering, permissions and additive upgrade rules. */
public final class StickerSeriesTests {
 static int checks;static void check(boolean yes,String name){if(!yes)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static JSONObject sticker(String series)throws Exception{return new JSONObject().put("id","s-"+String.join("",Collections.nCopies(64,"a"))).put("asset",String.join("",Collections.nCopies(64,"a"))).put("name","收到").put("series",series).put("category","赞同回应").put("description","确认收到消息、理解与赞同").put("intent","确认收到").put("emotion","安心").put("enabled",true).put("characters",new JSONArray());}
 public static void main(String[] args)throws Exception{
  JSONObject a=sticker("兔丸"),b=sticker("大耳狗"),p=new JSONObject().put("preferredSeries",new JSONArray().put("兔丸").put("大耳狗"));
  check(StickerRank.score(a,p,"收到","")>StickerRank.score(b,p,"收到",""),"equal semantics prefer first favourite series");p.put("preferredSeries",new JSONArray().put("大耳狗").put("兔丸"));check(StickerRank.score(b,p,"收到","")>StickerRank.score(a,p,"收到",""),"reversing series preference reverses actual production ordering");
  check(StickerRank.weight(a,p)>1&&StickerRank.weight(b,p)>StickerRank.weight(a,p),"secondary series receives a smaller bounded bias");
  JSONObject wrong=sticker("大耳狗").put("name","生日快乐").put("description","生日祝福").put("intent","庆祝生日").put("emotion","开心"),night=sticker("兔丸").put("name","晚安").put("description","睡前告别说晚安").put("intent","晚安睡觉");
  check(StickerRank.score(wrong,p,"晚安睡觉","")==0&&StickerRank.score(night,p,"晚安睡觉","")>0,"unrelated favourite cannot displace an appropriate other-series night sticker");
  check(StickerRank.score(a,p," completely-unrelated-phrase ","")==0,"no lexical topic match creates no invented candidate");
  check(MediaData.series(new JSONObject().put("name","内裤兔·委屈巴巴")).equals("内裤兔"),"legacy 1.23 library series inferred from known name prefix");
  check(MediaData.series(new JSONObject().put("name","自定义·甲")).isEmpty(),"unknown custom name prefix is not assumed to be a brand");
  check(MediaData.series(new JSONObject().put("name","兔丸·收到").put("series","")).isEmpty(),"explicitly cleared series stays unassigned");
  p.put("blockedSeries",new JSONArray().put("大耳狗"));check(!MediaData.allowed(b,p,"mano")&&MediaData.allowed(a,p,"mano"),"series prohibition applies through shared generation and delivery permission check");
  a.put("enabled",false);check(!MediaData.allowed(a,p,"mano"),"disabled entry remains blocked");a.put("enabled",true).put("characters",new JSONArray().put("hiori"));check(!MediaData.allowed(a,p,"mano"),"series preference cannot bypass role permission");a.put("characters",new JSONArray());
  JSONObject old=new JSONObject().put("name","我自己起的名字").put("enabled",false).put("favorite",true).put("uses",12).put("lastUsed",345).put("characters",new JSONArray().put("mano")),incoming=sticker("兔丸");JSONObject merge=StickerPack.merge(old,incoming,false);
  check(merge.getString("series").equals("兔丸")&&merge.getString("name").equals("我自己起的名字"),"duplicate skip fills missing series without replacing custom semantics");check(!merge.getBoolean("enabled")&&merge.getBoolean("favorite")&&merge.getInt("uses")==12&&merge.getInt("lastUsed")==345&&merge.getJSONArray("characters").getString(0).equals("mano"),"series enrichment preserves enablement favourites permissions and statistics");
  old.put("series","我的分类");check(StickerPack.merge(old,incoming,false).getString("series").equals("我的分类"),"existing user series not overwritten by duplicate skip");
  JSONObject existing=new JSONObject().put("frequency","off").put("preferred",new JSONArray().put("自定义分类")).put("style","用户自定语气"),profile=new JSONObject().put("frequency","normal").put("preferredSeries",new JSONArray().put("兔丸")).put("blockedSeries",new JSONArray()).put("style","包的语气");JSONObject supplemented=StickerPack.profileSupplement(existing,profile);
  check(supplemented.getJSONArray("preferredSeries").getString(0).equals("兔丸")&&supplemented.getString("frequency").equals("off")&&supplemented.getString("style").equals("用户自定语气"),"profile upgrade fills missing series only and preserves existing off frequency and custom style");
  existing.put("preferredSeries",new JSONArray());check(StickerPack.profileSupplement(existing,profile).getJSONArray("preferredSeries").length()==0,"explicitly empty existing favourite-series choice is respected");
  check(StickerPack.profileSupplement(null,profile).getString("frequency").equals("normal"),"new role receives complete package profile");
  JSONArray excluded=new JSONArray().put("内裤兔").put("んぽちゃむ");JSONObject legacy=new JSONObject().put("name","んぽちゃむ·谢谢茶姆").put("enabled",true);check(StickerPack.shouldDisable(legacy,excluded),"selected excluded legacy series is identified for disable");check(!StickerPack.shouldDisable(sticker("兔丸"),excluded),"unrelated selected series remains untouched");legacy.put("enabled",false);check(!StickerPack.shouldDisable(legacy,excluded),"already-disabled old sticker is not counted again");
  JSONObject frozen=MediaData.snapshot(sticker("兔丸"));check(frozen.getString("series").equals("兔丸"),"message semantics freeze series identity for history and backup");
  System.out.println("Series preference checks: "+checks+"; no Android device or paid API.");
 }
}
