package com.eidolon.sc.mobile;
import org.json.*;import java.time.*;import java.util.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;import org.bouncycastle.crypto.signers.Ed25519Signer;import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;

/** Offline original-rule/provider-format/backup fixtures. Never opens a connection. */
public final class SocialRulesTests {
 static int count;interface Action{void run()throws Exception;}
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);count++;System.out.println("PASS "+message);}
 static void rejects(Action a,String message)throws Exception{try{a.run();}catch(Exception expected){check(true,message);return;}throw new AssertionError(message);}
 static JSONObject personal(int score,boolean cooling)throws Exception{return new JSONObject().put("affinity",new JSONObject().put("mano",score)).put("relations",new JSONObject().put("mano",new JSONObject().put("cooling",cooling)));}
 static JSONObject root(long now)throws Exception {
  JSONObject pair=new JSONObject().put("ja","こんにちは").put("zh","你好"),post=new JSONObject(pair.toString()).put("id","post-1").put("character","mano").put("day","2026-11-04").put("published",now).put("liked",false);
  JSONObject comment=new JSONObject().put("id","comment-1").put("post","post-1").put("text","可爱").put("at",now).put("reply",pair).put("replyAt",now);
  JSONObject social=new JSONObject().put("moments",new JSONObject()).put("weather",new JSONObject()).put("city",new JSONObject()).put("budgetClock",new JSONObject()).put("reads",new JSONObject().put("posts",new JSONObject().put("post-1",now)).put("replies",new JSONObject().put("comment-1",now)))
   .put("posts",new JSONArray().put(post)).put("comments",new JSONArray().put(comment)).put("circle",new JSONArray()).put("usage",new JSONArray()).put("plans",new JSONArray());return new JSONObject().put("social",social);
 }
 public static void main(String[] args)throws Exception {
  long now=Instant.parse("2026-11-04T03:00:00Z").toEpochMilli();Set<String> known=Collections.singleton("mano");
  check(!SocialData.settings(new JSONObject()).getBoolean("enabled"),"new feature defaults off");
  check(SocialData.chance(personal(40,false),"mano",true)==.25&&SocialData.chance(personal(65,false),"mano",true)==.5&&SocialData.chance(personal(85,false),"mano",true)==.8,"native relation tiers retain original posting probabilities");
  check(!SocialData.eligible(personal(39,false),"mano",true)&&!SocialData.eligible(personal(90,true),"mano",true),"below trust and cooling roles are excluded");
  for(int score:new int[]{40,65,85}){int min=score==40?30:score==65?15:5,max=score==40?120:score==65?60:30;for(int seed=0;seed<100;seed++){long delay=(SocialData.replyAt(now,personal(score,false),"mano",true,new Random(seed))-now)/60000;check(delay>=min&&delay<=max,"bounded original comment delay "+score+"/"+seed);}}
  rejects(()->SocialData.comment("a".repeat(141)),"comment codepoint limit enforced");check(SocialData.comment("😀".repeat(140)).codePointCount(0,280)==140,"emoji comment uses Unicode codepoints");
  check(SocialData.drawTime("2026-11-04",SocialData.end("2026-11-04"),new Random(2))==0,"expired day does not catch up");
  for(int seed=0;seed<100;seed++){long due=SocialData.drawTime("2026-11-04",now,new Random(seed));check(due>now&&due<SocialData.end("2026-11-04"),"day draw stays after planning and before midnight "+seed);}
  JSONObject p=SocialData.post("{\"ja\":\"朝だね\",\"zh\":\"早上好\",\"placeId\":\"made-up\",\"likes\":[\"a\",\"a\",\"outsider\"],\"comments\":[{\"character\":\"outsider\",\"ja\":\"x\",\"zh\":\"x\"}]}",Collections.singleton("a"),Collections.emptySet());
  check(p.getString("placeId").isEmpty()&&p.getJSONArray("likes").length()==1&&p.getJSONArray("comments").length()==0,"unknown places/outsiders and duplicate likes never publish");
  SocialBackup.validate(root(now),known);check(true,"canonical social backup including visible-ID acknowledgements validates");
  JSONObject invalid=root(now);invalid.getJSONObject("social").getJSONObject("reads").getJSONObject("posts").put("offscreen",now);rejects(()->SocialBackup.validate(invalid,known),"unknown read IDs rejected");
  JSONObject badTime=root(now);badTime.getJSONObject("social").getJSONArray("posts").getJSONObject(0).put("published",1.5);rejects(()->SocialBackup.validate(badTime,known),"fractional backup timestamps rejected");
  JSONObject secret=root(now);secret.getJSONObject("social").getJSONObject("weather").put("privateKey","not-a-key");rejects(()->SocialBackup.validate(secret,known),"credentials cannot enter weather backup");
  JSONObject duplicate=root(now);duplicate.getJSONObject("social").getJSONArray("posts").put(new JSONObject(duplicate.getJSONObject("social").getJSONArray("posts").getJSONObject(0).toString()).put("id","post-2"));rejects(()->SocialBackup.validate(duplicate,known),"same role/day duplicate rejected before restore");
  JSONObject unknown=root(now);unknown.getJSONObject("social").getJSONObject("moments").put("characters",new JSONArray().put("outsider"));rejects(()->SocialBackup.validate(unknown,known),"unknown participation ID rejected");
  for(String host:new String[]{"http://x.qweatherapi.com","x.qweatherapi.com.evil.example","https://user@x.qweatherapi.com","https://x.qweatherapi.com/private","https://x.qweatherapi.com:443","https://x.qweatherapi.com/?x=1"})rejects(()->WeatherClient.host(host),"reject unsafe API host "+host);
  check(WeatherClient.host("test.qweatherapi.com").equals("https://test.qweatherapi.com"),"tenant host canonicalized to HTTPS");check(WeatherClient.location(35.123456,139.23456).equals("139.23,35.12"),"city lookup exposes only rounded city-scale coordinates in lon/lat order");
  JSONObject current=new JSONObject().put("code","200").put("now",new JSONObject().put("icon","302").put("temp","18").put("obsTime","2026-11-04T12:00:00+09:00"));JSONObject weather=WeatherData.qnow(current,now);
  check(WeatherData.wet(weather)&&weather.getString("kind").equals("雷雨"),"QWeather thunderstorms parse from provider string numbers");
  JSONObject old=new JSONObject(current.toString());old.getJSONObject("now").put("obsTime","2026-11-03T12:00:00+09:00");rejects(()->WeatherData.qnow(old,now),"stale provider observation is not current weather");
  check(WeatherData.context(null,null,null,now,0,false).isEmpty(),"missing weather omits context rather than inventing facts");
  JSONObject city=new JSONObject().put("name","上海").put("latitude",31.23).put("longitude",121.47);String context=WeatherData.context(weather,city,null,now,now,false);
  check(context.contains("东京（角色这边）")&&context.contains("上海")&&!context.contains("制作人那边："),"Tokyo weather is not attributed to a distant user city");
  JSONObject tokyoCity=new JSONObject().put("name","东京").put("latitude",35.6895).put("longitude",139.6917);
  check(WeatherData.context(null,tokyoCity,weather,now,now,false).contains("18℃"),"same-city user weather remains available when Tokyo provider is unavailable");
  check(WeatherData.context(weather,tokyoCity,weather,now,now,false).split("18℃",-1).length==2,"same-city snapshots are not duplicated");
  check(WeatherRuntime.sameCity(tokyoCity,new JSONObject(tokyoCity.toString()).put("name","東京都"))&&!WeatherRuntime.sameCity(tokyoCity,city)&&WeatherRuntime.sameCity(null,null),"city identity uses canonical coordinates, not translated labels");
  String keep="00000000-0000-0000-0000-000000000001",oldSet="00000000-0000-0000-0000-000000000002";
  List<String> obsolete=WeatherRuntime.obsoleteCredentials(Arrays.asList("weather.privateKey/"+keep,"weather.apiKey/"+oldSet,"configured/weather.privateKey/"+oldSet,"key","configured/key","fishKey","unrelated/privateKey/"+oldSet),keep);
  check(obsolete.size()==2&&obsolete.contains("weather.apiKey/"+oldSet)&&obsolete.contains("configured/weather.privateKey/"+oldSet),"weather credential cleanup is bounded to obsolete weather slots and flags");
  check(!WeatherRuntime.cacheKey(new JSONObject().put("revision",1),city,false).equals(WeatherRuntime.cacheKey(new JSONObject().put("revision",1),tokyoCity,false))&&!WeatherRuntime.cacheKey(new JSONObject().put("revision",1),city,false).equals(WeatherRuntime.cacheKey(new JSONObject().put("revision",2),city,false)),"cache identity isolates cities and configuration revisions");
  JSONObject partial=new JSONObject();try{WeatherData.qdaily(partial,new JSONObject().put("code","200").put("daily",new JSONArray().put(new JSONObject().put("fxDate","2026-11-04").put("tempMax",20))),"2026-11-04");}catch(Exception expected){}
  check(!partial.has("high")&&!partial.has("low"),"malformed optional forecast cannot introduce a fictitious zero-temperature range");
  JSONObject attemptRoot=new JSONObject().put("scheduleAttempts",new JSONArray().put(new JSONObject().put("character","mano").put("day","2026-11-04").put("at",now).put("state","RUNNING").put("error","")));
  ScheduleAttempts.validate(attemptRoot,known);check(true,"unknown-result schedule attempt can be exported without a live token");
  JSONObject badAttempt=new JSONObject(attemptRoot.toString());badAttempt.getJSONArray("scheduleAttempts").getJSONObject(0).put("state","RETRY");rejects(()->ScheduleAttempts.validate(badAttempt,known),"invalid restored schedule states are rejected");
  JSONArray places=new JSONObject(Files.readString(Path.of(args[0]))).getJSONArray("places");
  check(places.length()==406,"original406 place records loaded");
  for(int i=0;i<places.length();i++){JSONObject place=places.getJSONObject(i);SocialData.coordinate(place.getDouble("lat"),place.getDouble("lon"));}
  check(PlacePool.slot(5).isEmpty()&&PlacePool.slot(11).equals("morning")&&PlacePool.slot(12).equals("afternoon")&&PlacePool.slot(23).equals("evening"),"original place time slots preserved");
  JSONArray picked=PlacePool.draw(places,"mano",new JSONArray().put("park"),Collections.emptySet(),now,true,null,new Random(42));
  check(picked.length()<=4,"place candidates capped at four");for(int i=0;i<picked.length();i++)check(PlacePool.INDOOR.contains(picked.getJSONObject(i).getString("category"))&&!picked.getJSONObject(i).getString("category").equals("venue"),"rain chooses indoor candidates, no unrelated venue "+i);
  JSONObject site=new JSONObject().put("id","test").put("name","会场").put("lat",35.6).put("lon",139.6).put("category","venue").put("area","千葉県浦安市");
  check(PlacePool.draw(new JSONArray().put(site),"mano",null,Collections.emptySet(),now,false,null,Collections.singleton("会场"),new Random(1)).length()==1,"matching anniversary venue admitted first");
  check(PlacePool.draw(new JSONArray().put(site),"mano",null,Collections.singleton("test"),now,false,null,Collections.singleton("会场"),new Random(1)).length()==0,"recently used anniversary venue still excluded");
  check(PlacePool.map(site,"amap").contains("coordinate=wgs84")&&PlacePool.map(site,"google").contains("35.6,139.6"),"external map URLs preserve WGS84 coordinates");
  List<String> circle=SocialCircle.draw(Arrays.asList("a","b","c"),Arrays.asList("b","d","e"),Arrays.asList("a","b","c","d","e","f","g","h"),Collections.singleton("d"),false,new Random(3));
  check(circle.size()>=4&&circle.size()<=6&&circle.get(0).equals("d")&&new HashSet<>(circle).size()==circle.size(),"circle keeps known birthday first, bounded and unique");
  byte[] seed=new byte[32];Arrays.fill(seed,(byte)7);Ed25519PrivateKeyParameters key=new Ed25519PrivateKeyParameters(seed,0);
  String pem=Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(key).getEncoded()),jwt=QWeatherSigner.sign(pem,"kid-test","developer-test","project-test",now);String[] parts=jwt.split("\\.");
  JSONObject header=new JSONObject(new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8)),claims=new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8));
  check(header.getString("alg").equals("EdDSA")&&header.getString("kid").equals("kid-test")&&claims.getString("iss").equals("developer-test")&&claims.getString("sub").equals("project-test")&&claims.getLong("exp")-claims.getLong("iat")==900,"JWT claims match current official QWeather requirements");
  byte[] message=(parts[0]+"."+parts[1]).getBytes(StandardCharsets.UTF_8);Ed25519Signer verifier=new Ed25519Signer();verifier.init(false,key.generatePublicKey());verifier.update(message,0,message.length);check(verifier.verifySignature(Base64.getUrlDecoder().decode(parts[2])),"JWT Ed25519 signature independently verifies without installing a provider");
  System.out.println("Passed "+count+" offline social/weather/place/JWT/backup checks; no API, device or SQLite execution.");
 }
}
