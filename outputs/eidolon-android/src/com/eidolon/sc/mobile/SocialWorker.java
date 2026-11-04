package com.eidolon.sc.mobile;
import android.content.Context;import androidx.work.*;
public final class SocialWorker extends Worker {
 private final SocialEngine engine=new SocialEngine();
 public SocialWorker(Context c,WorkerParameters p){super(c,p);}
 @Override public Result doWork(){try{engine.run(getApplicationContext());return Result.success();}catch(Exception e){return Result.failure();}finally{SocialScheduler.refresh(getApplicationContext());WebActivity a=WebActivity.foregroundActivity;if(a!=null)a.runOnUiThread(()->{if(!a.destroyed)a.event("socialChanged","");});}}
 @Override public void onStopped(){engine.stop();}
}
