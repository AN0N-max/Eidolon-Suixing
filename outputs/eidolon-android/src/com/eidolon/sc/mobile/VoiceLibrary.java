package com.eidolon.sc.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.*;

/** Local voice management only. No credentials or network operations. */
final class VoiceLibrary {
    private final Activity host;
    private final VoicePlayer player;
    private final Handler main=new Handler(Looper.getMainLooper());
    private VoiceLibrary(Activity host, VoicePlayer player) { this.host=host; this.player=player; }
    static void show(Context context, VoicePlayer player) {
        if(context instanceof Activity)new VoiceLibrary((Activity)context,player).load();
    }
    private boolean alive() { return !host.isFinishing()&&!host.isDestroyed(); }
    private void toast(String text) { if(alive())Toast.makeText(host,text,Toast.LENGTH_LONG).show(); }
    private interface Job { void run() throws Exception; }
    private void work(Job job) {
        new Thread(()->{try{job.run();}catch(Exception e){main.post(()->toast("操作失败，请检查存储空间后重试"));}},"eidolon-voice-library").start();
    }
    private VoiceCache storage() throws Exception {
        VoiceCache cache=new VoiceCache(host.getFilesDir());
        cache.migrate(host.getCacheDir());
        return cache;
    }
    private static String size(long bytes) { return bytes>=1024*1024?String.format(Locale.CHINA,"%.1f MB",bytes/1048576.0):String.format(Locale.CHINA,"%.0f KB",bytes/1024.0); }
    private static String when(long time) { return new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(time)); }
    private void load() {
        work(()->{
            List<VoiceCache.Entry> entries=storage().list();
            long total=0;for(VoiceCache.Entry entry:entries)total+=entry.bytes;
            final String title="已保存语音 · "+entries.size()+" 条 · "+size(total);
            main.post(()->{
                if(!alive())return;
                AlertDialog.Builder dialog=new AlertDialog.Builder(ThemePrefs.dialogContext(host)).setTitle(title).setNegativeButton("关闭",null);
                if(entries.isEmpty())dialog.setMessage("还没有保存的语音。\n在聊天中生成语音，完整接收成功后会自动保存在这里。\n已保存语音由你手动删除。");
                else {
                    String[] rows=new String[entries.size()];
                    for(int i=0;i<rows.length;i++){
                        VoiceCache.Entry entry=entries.get(i);
                        String text=entry.text.isEmpty()?"较早保存的语音":entry.text.replaceAll("\\s+"," ");
                        if(text.length()>45)text=text.substring(0,45)+"…";
                        rows[i]=text+"\n"+when(entry.created)+" · "+String.format(Locale.CHINA,"%.1f 秒",entry.seconds())+" · "+size(entry.bytes);
                    }
                    dialog.setItems(rows,(d,index)->detail(entries.get(index)));
                    dialog.setNeutralButton("全部删除",(d,w)->confirmAll());
                }
                dialog.show();
            });
        });
    }
    private void detail(VoiceCache.Entry entry) {
        if(!alive())return;
        String body=(entry.text.isEmpty()?"此条来自旧版缓存，没有保存对应台词。":entry.text)+"\n\n"+
                when(entry.created)+" · "+String.format(Locale.CHINA,"%.1f 秒",entry.seconds())+" · "+size(entry.bytes)+
                (entry.model.isEmpty()?"":"\n模型："+entry.model);
        new AlertDialog.Builder(ThemePrefs.dialogContext(host)).setTitle("已保存语音").setMessage(body)
                .setPositiveButton("播放",(d,w)->{player.playSaved(entry.file,this::toast);load();})
                .setNeutralButton("删除",(d,w)->new AlertDialog.Builder(ThemePrefs.dialogContext(host)).setTitle("删除这条语音？")
                        .setMessage("只删除保存的音频，聊天记录会保留。之后再次点击聊天中的播放按钮，需要重新生成。")
                        .setNegativeButton("取消",(x,y)->load())
                        .setPositiveButton("删除",(x,y)->{player.stop();work(()->{storage().delete(entry.key);main.post(()->{toast("语音已删除");load();});});}).show())
                .setNegativeButton("返回列表",(d,w)->load()).show();
    }
    private void confirmAll() {
        new AlertDialog.Builder(ThemePrefs.dialogContext(host)).setTitle("删除全部已保存语音？")
                .setMessage("删除所有已保存的音频，释放占用空间。聊天记录和角色设置会保留。此操作无法撤销。")
                .setNegativeButton("取消",(d,w)->load())
                .setPositiveButton("全部删除",(d,w)->{player.stop();work(()->{storage().clear();main.post(()->{toast("已清空保存的语音");load();});});}).show();
    }
}
