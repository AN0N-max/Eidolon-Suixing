package com.eidolon.sc.mobile;
import android.content.Context;import androidx.work.*;
/** Retention continues while moments are hidden; maintenance never calls a service. */
public final class SocialCleanupWorker extends Worker {
 public SocialCleanupWorker(Context c,WorkerParameters p){super(c,p);}
 @Override public Result doWork(){try(ChatStore s=new ChatStore(getApplicationContext())){long now=System.currentTimeMillis();SocialStore.cleanup(s,now);SocialStore.expire(s,now);return Result.success();}catch(Exception e){return Result.failure();}}
}
