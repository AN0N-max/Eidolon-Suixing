package com.eidolon.sc.mobile;

import android.Manifest;import android.content.*;import android.content.pm.PackageManager;import android.location.*;import android.os.*;
import java.io.IOException;import java.util.concurrent.atomic.AtomicInteger;import org.json.*;

/** Foreground, explicit coarse-location action. Only the selected city's centre reaches UI/storage. */
final class CityLocator {
 interface Completion{void done(JSONObject city,String error);}
 private static final AtomicInteger CODES=new AtomicInteger(42000);
 private final WebActivity activity;private final Handler main=new Handler(Looper.getMainLooper());
 private LocationManager manager;private LocationListener listener;private Completion completion;private volatile long epoch;private long permissionEpoch,configRevision;
 private int permissionCode=-1;private boolean resolving;private Runnable timeout;
 CityLocator(WebActivity activity){this.activity=activity;}
 long revision(){return configRevision;}
 void begin(Completion callback)throws Exception {
  if(completion!=null||permissionCode!=-1)throw new IOException("请先完成或关闭当前定位权限请求");
  if(!activity.visiblePage.equals("citySettings"))throw new IOException("请从所在城市设置主动使用位置");
  try(ChatStore s=new ChatStore(activity)){
   JSONObject cfg=SocialStore.weatherConfig(s);WeatherClient.requiredHost(cfg);
   if(!Vault.configured(activity,WeatherClient.credentialSlot(cfg,cfg.getString("auth").equals("key")?"apiKey":"privateKey")))throw new IOException("请先在天气与城市服务保存和风凭据");
   configRevision=cfg.getLong("revision");
  }
  long ticket=++epoch;completion=callback;resolving=false;
  if(activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
   permissionEpoch=ticket;permissionCode=CODES.getAndUpdate(n->n>=65000?42000:n+1);
   try{activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},permissionCode);}catch(Exception e){permissionCode=-1;finish(ticket,null,"位置权限请求未完成，请手动搜索城市");}
  }else locate(ticket);
 }
 boolean permission(int code,int[] results){
  if(code!=permissionCode)return false;permissionCode=-1;long ticket=permissionEpoch;
  if(ticket!=epoch||completion==null)return true;
  if(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED){finish(ticket,null,"未允许位置权限，可手动搜索城市");return true;}
  main.post(()->{if(ticket==epoch&&completion!=null)locate(ticket);});return true;
 }
 void locate(long ticket){
  if(ticket!=epoch||completion==null)return;
  if(activity.destroyed||!activity.visiblePage.equals("citySettings")){cancel();return;}
  try{
   manager=(LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
   if(manager==null||!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){finish(ticket,null,"系统网络定位不可用，可手动搜索城市");return;}
   timeout=()->finish(ticket,null,"获取位置超时，可手动搜索城市");main.postDelayed(timeout,15000);
   Location last=manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
   if(last!=null){long age=SystemClock.elapsedRealtimeNanos()-last.getElapsedRealtimeNanos();if(age>=0&&age<=10L*60*1000000000L&&last.hasAccuracy()&&last.getAccuracy()<=50000){accept(ticket,last);return;}}
   listener=new LocationListener(){
    @Override public void onLocationChanged(Location location){accept(ticket,location);}
    @Override public void onProviderDisabled(String provider){finish(ticket,null,"系统定位已关闭，可手动搜索城市");}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
   };
   manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,listener,Looper.getMainLooper());
  }catch(SecurityException e){finish(ticket,null,"位置权限已取消，可手动搜索城市");}catch(Exception e){finish(ticket,null,"无法取得位置，可手动搜索城市");}
 }
 void accept(long ticket,Location location){
  if(ticket!=epoch||completion==null||resolving)return;resolving=true;stopUpdates();
  final double latitude=location.getLatitude(),longitude=location.getLongitude();final long revision=configRevision;
  WeatherRuntime.FETCH.execute(()->{
   JSONObject city=null;String error=null;
   try{if(ticket!=epoch)return;try(ChatStore s=new ChatStore(activity)){if(SocialStore.weatherConfig(s).getLong("revision")!=revision)throw new IOException("天气服务配置已改变，请重新查找");}
    city=WeatherRuntime.nearest(activity.getApplicationContext(),latitude,longitude);
    try(ChatStore s=new ChatStore(activity)){if(SocialStore.weatherConfig(s).getLong("revision")!=revision)throw new IOException("天气服务配置已改变，请重新查找");}
    if(city==null)error="附近没有可用城市，请手动搜索";
   }catch(Exception e){error="城市定位未完成，请手动搜索或检查天气服务配置";}
   final JSONObject result=city;final String failure=error;main.post(()->finish(ticket,result,failure));
  });
 }
 void stopUpdates(){if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}if(manager!=null&&listener!=null)try{manager.removeUpdates(listener);}catch(Exception ignored){}listener=null;}
 void finish(long ticket,JSONObject city,String error){if(ticket!=epoch||completion==null)return;stopUpdates();Completion done=completion;completion=null;epoch++;done.done(city,error);}
 void cancel(){stopUpdates();Completion done=completion;completion=null;epoch++;resolving=false;if(done!=null)done.done(null,"城市查找已取消");}
 void pause(){if(permissionCode==-1)cancel();}
}
