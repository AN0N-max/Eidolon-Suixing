package com.eidolon.sc.mobile;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

/** Uses the phone's notification tone and respects silent and Do Not Disturb modes. */
final class ReachAlert {
    static String channel(Context c,NotificationManager manager,boolean quiet){
        SharedPreferences p=c.getSharedPreferences("settings",0);
        boolean sound=!quiet&&p.getBoolean("reachSound",true),vibrate=!quiet&&p.getBoolean("reachVibrate",true);
        String id=quiet?"reach-history-v2":"reach-message-v2-"+(sound?"s":"-")+(vibrate?"v":"-");
        id=BadgeChannels.id(c,id);NotificationChannel existing=manager.getNotificationChannel(id);
        if(existing==null){
            NotificationChannel channel=new NotificationChannel(id,quiet?"角色消息记录":"角色消息提醒",quiet?NotificationManager.IMPORTANCE_LOW:NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(true);
            channel.setDescription(quiet?"悬浮消息已显示后的低打扰记录":"主动搭话的声音和振动随应用内设置改变");
            channel.setSound(sound?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION):null,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            channel.enableVibration(vibrate);
            if(vibrate)channel.setVibrationPattern(new long[]{0,100,60,70});
            manager.createNotificationChannel(channel);
        }
        return id;
    }
    static void signal(Context c){
        SharedPreferences p=c.getSharedPreferences("settings",0);
        AudioManager audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(audio==null||manager==null)return;
        int interruption=manager.getCurrentInterruptionFilter();
        if(interruption!=NotificationManager.INTERRUPTION_FILTER_ALL)return;
        int mode=audio.getRingerMode();if(mode==AudioManager.RINGER_MODE_SILENT)return;
        if(p.getBoolean("reachVibrate",true))try{
            Vibrator vibrator=(Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);
            if(vibrator!=null&&vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,100,60,70},-1));
        }catch(RuntimeException e){Log.w("EidolonReach","Vibration unavailable",e);}
        if(mode==AudioManager.RINGER_MODE_NORMAL&&p.getBoolean("reachSound",true))try{
            Ringtone tone=RingtoneManager.getRingtone(c,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if(tone!=null){tone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());tone.play();}
        }catch(RuntimeException e){Log.w("EidolonReach","Notification tone unavailable",e);}
    }
}
