package com.eidolon.sc.mobile;
import android.content.Context;import org.json.*;import java.io.*;import java.net.*;import java.time.*;import java.util.*;

/** Native-only bounded HTTPS requests. No redirects, automatic provider fallback or key logging. */
final class WeatherClient {
 interface Transport {JSONObject get(String url,Map<String,String> headers)throws Exception;}
 static final Transport HTTPS=(url,headers)->{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(java.net.Proxy.NO_PROXY);try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(3000);c.setReadTimeout(4000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","Eidolon-Suixing/1.26");for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("天气/城市服务请求失败（HTTP "+code+"）");try(InputStream in=c.getInputStream()){return new JSONObject(new String(StoryFiles.read(in,1024*1024),"UTF-8"));}}finally{c.disconnect();}};
 static String host(String value)throws Exception{String h=value.trim();if(h.isEmpty())return "";if(!h.startsWith("https://"))h="https://"+h;URI uri=new URI(h);String name=uri.getHost();if(!"https".equals(uri.getScheme())||name==null||!name.toLowerCase(Locale.ROOT).endsWith(".qweatherapi.com")||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!Arrays.asList("","/").contains(uri.getPath()))throw new IOException("请填写和风控制台分配的 *.qweatherapi.com HTTPS API Host");return "https://"+name.toLowerCase(Locale.ROOT);}
 static String credentialSlot(JSONObject cfg,String name)throws Exception{String id=cfg.optString("credentialSet");if(!id.matches("[a-f0-9-]{36}"))throw new IOException("请在天气设置保存和风凭据");return "weather."+name+"/"+id;}
 static Map<String,String> headers(Context c,JSONObject cfg,long now)throws Exception{synchronized(WeatherRuntime.CONFIG_LOCK){
  try(ChatStore s=new ChatStore(c)){if(SocialStore.weatherConfig(s).getLong("revision")!=cfg.optLong("revision"))throw new IOException("天气配置已改变，请重新查找");}
  Map<String,String> h=new HashMap<>();if(cfg.optString("auth").equals("key")){String key=Vault.read(c,credentialSlot(cfg,"apiKey"));if(key.isEmpty())throw new IOException("请在天气设置填写API Key");h.put("X-QW-Api-Key",key);}else{String pem=Vault.read(c,credentialSlot(cfg,"privateKey"));if(pem.isEmpty())throw new IOException("请在天气设置填写Ed25519私钥");h.put("Authorization","Bearer "+QWeatherSigner.sign(pem,cfg.optString("kid"),cfg.optString("developer"),cfg.optString("project"),now));}return h;}}
 static Transport guarded(Context c,JSONObject cfg,Transport transport){return guarded(c,cfg,null,transport);}
 static Transport guarded(Context c,JSONObject cfg,JSONObject city,Transport transport){return (url,headers)->{synchronized(WeatherRuntime.CONFIG_LOCK){try(ChatStore s=new ChatStore(c)){JSONObject current=SocialStore.weatherConfig(s);if(!current.optBoolean("enabled")||current.optLong("revision")!=cfg.optLong("revision")||city!=null&&!WeatherRuntime.sameCity(city,SocialStore.city(s)))throw new IOException("天气配置或所在城市已改变");}}return transport.get(url,headers);};}
 static String encode(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
 static JSONObject q(Transport t,String host,String path,String location,Map<String,String> headers)throws Exception{return t.get(host+path+(path.contains("?")?"&":"?")+"location="+encode(location)+"&lang=zh",headers);}
 static JSONArray search(Context c,JSONObject cfg,String query,Transport t,long now)throws Exception{return WeatherData.cities(q(t,requiredHost(cfg),"/geo/v2/city/lookup?number=10",query,headers(c,cfg,now)));}
 static String requiredHost(JSONObject cfg)throws Exception{String h=host(cfg.optString("host"));if(h.isEmpty())throw new IOException("请先在天气与城市服务中配置和风API Host和凭据");return h;}
 static String location(double lat,double lon)throws Exception{SocialData.coordinate(lat,lon);return String.format(Locale.ROOT,"%.2f,%.2f",lon,lat);}
 static JSONObject tokyo(Transport t,long now)throws Exception{String url="https://api.open-meteo.com/v1/forecast?latitude=35.6895&longitude=139.6917&current=temperature_2m,weather_code&hourly=weather_code&daily=temperature_2m_max,temperature_2m_min&past_days=1&forecast_days=2&timezone=Asia%2FTokyo&timeformat=unixtime&models=jma_msm";return WeatherData.meteo(t.get(url,Collections.emptyMap()),now);}
 static JSONObject user(Context c,JSONObject cfg,JSONObject city,Transport t,long now)throws Exception{
  String host=requiredHost(cfg),loc=location(city.getDouble("latitude"),city.getDouble("longitude"));Map<String,String> h=headers(c,cfg,now);JSONObject out=WeatherData.qnow(q(t,host,"/v7/weather/now",loc,h),now);ZoneId zone=city.optString("zone").isEmpty()?ZoneId.systemDefault():ZoneId.of(city.getString("zone"));String today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString();
  try{WeatherData.qdaily(out,q(t,host,"/v7/weather/3d",loc,h),today);}catch(Exception ignored){}
  try{WeatherData.qhourly(out,q(t,host,"/v7/weather/24h",loc,h),today,now,zone);}catch(Exception ignored){}
  try{JSONObject alerts=t.get(host+"/weatheralert/v1/current/"+String.format(Locale.ROOT,"%.2f/%.2f",city.getDouble("latitude"),city.getDouble("longitude"))+"?lang=zh",h);out.put("warnings",WeatherData.warnings(alerts));}catch(Exception ignored){out.put("warningsKnown",false);}
  if(!city.optString("id").isEmpty())try{String yesterday=LocalDate.parse(today).minusDays(1).toString().replace("-","");WeatherData.qhistory(out,q(t,host,"/v7/historical/weather?date="+yesterday,city.getString("id"),h));}catch(Exception ignored){}
  return out;
 }
}
