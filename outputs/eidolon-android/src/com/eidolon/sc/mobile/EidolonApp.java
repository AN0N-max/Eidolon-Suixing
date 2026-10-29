package com.eidolon.sc.mobile;
import android.app.Application;
import androidx.work.Configuration;
/** Keep WorkManager job IDs clear of existing reply/letter/writing JobScheduler IDs. */
public final class EidolonApp extends Application implements Configuration.Provider {
    @Override public void onCreate(){super.onCreate();
        // All components use the default process. At this point no new Worker has
        // started, so RUNNING can only belong to an interrupted previous process.
        try(ChatStore s=new ChatStore(this)){ReachPlanStore.interrupted(s,System.currentTimeMillis());ReachPlans.upgradeAlgorithm(this,s);}
        catch(Exception e){android.util.Log.e("EidolonReach","Interrupted task recovery failed",e);}
    }
    @Override public Configuration getWorkManagerConfiguration(){return new Configuration.Builder().setJobSchedulerJobIdRange(20000,29999).setMinimumLoggingLevel(android.util.Log.INFO).build();}
}
