package com.eidolon.sc.mobile;
import android.content.*;
import android.database.Cursor;
import org.json.*;
import java.util.*;
/** Read-only, page-scoped UI data. No model calls or decryption. */
final class UiSnapshots {
    static JSONObject read(Context context,ChatStore store,JSONArray characters,JSONArray gifts,JSONObject calendar,String who,String conversation,boolean busy,boolean initialConversation,String route)throws Exception{
        SharedPreferences prefs=context.getSharedPreferences("settings",0);UiReadScope scope=new UiReadScope(route);JSONObject current=who.isEmpty()?null:new JSONObject().put("id",who);
        JSONObject personal=store.personal();PersonalData.refresh(personal,PersonalData.today());Progression.upgrade(personal);for(int i=0;i<characters.length();i++)Progression.relation(personal,characters.getJSONObject(i).getString("id"),System.currentTimeMillis());
        JSONArray roster=null;if(scope.roster||"bootstrap".equals(route))roster=roster(store,characters);
        JSONObject result=new JSONObject().put("_uiPatch",true).put("personal",personal).put("busy",busy).put("achievementCatalog",Progression.catalog());
        if(scope.full||route.equals("bootstrap")||route.equals("gifts")||route.equals("promises"))result.put("gifts",gifts);
        if(scope.full||route.equals("bootstrap")||route.equals("calendar"))result.put("calendar",calendar);
        if(roster!=null)result.put("characters",roster);
        if(scope.stats)result.put("stats",store.stats());
        if(scope.full||"stories".equals(route))result.put("storyCount",store.storyCount());
        result.put("current",who.isEmpty()?JSONObject.NULL:who).put("session",conversation);
        result.put("config",new JSONObject().put("url",prefs.getString("url","")).put("model",prefs.getString("model","")).put("hasKey",Vault.configured(context,"key")).put("bilingual",prefs.getBoolean("bilingual",true)));
        result.put("producerAvatar",ProducerAvatar.url(context));
        result.put("unread",UnreadState.counts(store));result.put("initialRoute",initialConversation?"chat":"roster");
        result.put("reachEnabled",prefs.getBoolean("reachEnabled",false));
        result.put("notificationsAllowed",context.getSystemService(android.app.NotificationManager.class).areNotificationsEnabled());
        if(scope.reach)result.put("reachOptions",ReachOut.options(context,store));
        if(scope.full||route.equals("replyDelaySettings"))result.put("replyOptions",new JSONObject().put("enabled",prefs.getBoolean("replyDelay.enabled",false)).put("sleep",prefs.getString("replyDelay.sleep","wake")).put("backgroundGuard",prefs.getBoolean("replyBackgroundGuard",true)).put("guardRunning",ReplyWorkService.running).put("guardError",prefs.getString("replyGuardError","")).put("deliveryTrace",prefs.getString("replyDeliveryTrace","")) .put("timingTrace",ReplyTrace.read(context)).put("overlayAllowed",OverlayBanner.allowed(context)).put("restMin",prefs.getInt("replyDelay.restMin",5)).put("restMax",prefs.getInt("replyDelay.restMax",25)).put("mealMin",prefs.getInt("replyDelay.mealMin",30)).put("mealMax",prefs.getInt("replyDelay.mealMax",120)).put("workMin",prefs.getInt("replyDelay.workMin",120)).put("workMax",prefs.getInt("replyDelay.workMax",480)).put("defaultMin",prefs.getInt("replyDelay.defaultMin",5)).put("defaultMax",prefs.getInt("replyDelay.defaultMax",25)));
        result.put("modelServices",new JSONObject().put("background",service(context,prefs,"background")).put("judge",service(context,prefs,"judge")));
        result.put("features",new JSONObject().put("autoMemory",prefs.getBoolean("autoMemory",true)).put("affinity",prefs.getBoolean("affinity",true)).put("hibiHearts",prefs.getBoolean("hibiHearts",true)).put("story",prefs.getBoolean("story",true)).put("analysisModel",prefs.getString("analysisModel","")));
        result.put("storyRetrieval",StoryRetrieval.read(prefs).json());
        result.put("hideJapanese",prefs.getBoolean("hideJapanese",false));
        result.put("hideVoiceIcon",prefs.getBoolean("hideVoiceIcon",false));
        result.put("hideReplyWaiting",prefs.getBoolean("hideReplyWaiting",false));
        if(scope.writings)result.put("writings",WritingStore.visible(store,personal,prefs.getBoolean("affinity",true)));
        result.put("writingUnread",writingCounts(store,personal,prefs.getBoolean("affinity",true)));
        result.put("writingOptions",new JSONObject().put("letters",prefs.getBoolean("hibiLetters",false)).put("diary",prefs.getBoolean("hibiDiary",false)).put("error",prefs.getString("writingError","")).put("letterError",prefs.getString("letterError","")));
        result.put("promiseOptions",new JSONObject().put("enabled",prefs.getBoolean("hibiPromises",false)).put("automatic",prefs.getBoolean("hibiPromiseAuto",false)).put("today",WritingData.today(personal).toString()));
        result.put("scheduleOptions",new JSONObject().put("enabled",prefs.getBoolean("hibiSchedules",false)).put("rest",prefs.getBoolean("hibiRest",true)).put("limit",prefs.getInt("hibiScheduleLimit",3)));
        if(scope.chat&&current!=null)result.put("currentSchedule",ScheduleRuntime.state(context,store,personal,current.getString("id"),System.currentTimeMillis()));
        result.put("deviceTimeZone",java.time.ZoneId.systemDefault().getId());
        result.put("meetings",Anniversaries.summary(personal,WritingData.today(personal)));
        result.put("chatAppearance",new JSONObject().put("avatar",prefs.getInt("chatAvatar",42)).put("chinese",prefs.getInt("chatChinese",18)).put("japanese",prefs.getInt("chatJapanese",13)).put("mine",prefs.getInt("chatMine",17)));
        if(scope.chat&&current!=null){result.put("sessionState",Progression.session(personal,conversation));JSONArray rows=new JSONArray();for(ChatStore.Message m:store.sessionHistory(conversation,200))rows.put(new JSONObject().put("id",m.id).put("role",m.role).put("text",m.role.equals("assistant")?ReplyText.visible(m.text):m.text).put("sentAt",m.sentAt).put("generatedAt",m.generatedAt).put("timeZone",m.zone));result.put("appeal",AppealStore.offer(store,personal,current.getString("id"),conversation,prefs.getBoolean("affinity",true))).put("current",current.getString("id")).put("messages",rows).put("session",conversation);result.put("pendingReply",JSONObject.NULL);ChatStore.PendingReply pending=store.pending(conversation);if(pending!=null&&!pending.test&&!pending.status.equals("cancelled"))result.put("pendingReply",new JSONObject().put("id",pending.id).put("revision",pending.revision).put("status",pending.status).put("dueAt",pending.dueAt).put("lastAt",pending.lastAt).put("immediate",pending.immediate).put("error",pending.error).put("remainingMs",Math.max(0,pending.dueAt-System.currentTimeMillis())));}
        JSONObject visiblePersonal=personal;visiblePersonal.remove("rolePrompts");visiblePersonal.remove("sessions");if(!scope.full&&!route.equals("memories")&&!route.equals("stats"))visiblePersonal.remove("memories");result.put("personal",visiblePersonal);
        return result;
    }

    static final java.util.concurrent.ExecutorService READS=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"eidolon-ui-data"));
    static JSONArray roster(ChatStore store,JSONArray characters)throws Exception{
        Map<String,JSONObject> latest=new HashMap<>();Map<String,Long> activities=new HashMap<>();
        StringBuilder selected=new StringBuilder();String[] args=new String[characters.length()];for(int i=0;i<characters.length();i++){if(i>0)selected.append(" UNION ALL ");selected.append("SELECT ? AS character");args[i]=characters.getJSONObject(i).getString("id");}
        if(args.length==0)return new JSONArray();
        String sql="SELECT w.character,m.role,m.body FROM ("+selected+") w JOIN messages m ON m.id=(SELECT id FROM messages WHERE character=w.character ORDER BY id DESC LIMIT 1)";
        try(Cursor rows=store.getReadableDatabase().rawQuery(sql,args)){while(rows.moveToNext())latest.put(rows.getString(0),new JSONObject().put("role",rows.getString(1)).put("text",rows.getString(2)));}
        String activitySql="SELECT c.character,max(c.updated) FROM conversations c WHERE EXISTS(SELECT 1 FROM messages m WHERE m.conversation=c.id) GROUP BY c.character";
        try(Cursor rows=store.getReadableDatabase().rawQuery(activitySql,null)){while(rows.moveToNext())activities.put(rows.getString(0),rows.getLong(1));}
        JSONArray out=new JSONArray();for(int i=0;i<characters.length();i++){JSONObject c=characters.getJSONObject(i),row=new JSONObject();for(Iterator<String> it=c.keys();it.hasNext();){String key=it.next();if(!key.equals("sheet")&&!key.equals("facts"))row.put(key,c.get(key));}String who=c.getString("id");JSONObject last=latest.get(who);String preview=c.optString("intro");if(last!=null){String role=last.getString("role"),text=last.getString("text");preview=role.equals("gift")?"🎁 你送出"+GiftRecord.parse(text).getString("giftName"):MainActivity.display(role.equals("assistant")?ReplyText.visible(text):text);}row.put("preview",preview).put("lastActivity",activities.getOrDefault(who,0L));out.put(row);}return out;
    }
    static JSONObject writingCounts(ChatStore store,JSONObject personal,boolean affinity)throws Exception{
        int letter=0,diary=0;JSONObject relations=personal.optJSONObject("relations");
        String sql="SELECT kind,character,is_read,length(secret)>0,secret_read FROM writings";
        try(Cursor rows=store.getReadableDatabase().rawQuery(sql,null)){while(rows.moveToNext()){
            String kind=rows.getString(0),who=rows.getString(1);JSONObject relation=relations==null?null:relations.optJSONObject(who);String shown=DiaryData.shown(personal.getJSONObject("affinity").optDouble(who,50),relation!=null&&relation.optBoolean("cooling"),affinity);
            if(UiReadScope.writingUnread(kind,rows.getInt(2)!=0,rows.getInt(3)!=0,rows.getInt(4)!=0,shown,affinity)){if(kind.equals("diary"))diary++;else letter++;}
        }}return new JSONObject().put("letter",letter).put("diary",diary);
    }
    static JSONObject service(Context context,SharedPreferences prefs,String role)throws Exception{
        String provider=role.equals("judge")?judgeProvider(prefs):"compatible";
        String url=prefs.getString(role+"Url",""),model=prefs.getString(role+"Model",role.equals("background")?prefs.getString("analysisModel",""):"");
        if(role.equals("judge")&&provider.equals("jev")){if(url.isEmpty())url=JevClient.DEFAULT_URL;if(model.isEmpty())model=JevClient.DEFAULT_MODEL;}
        if(role.equals("judge")&&provider.equals("bailian")&&model.isEmpty())model="decision-model-preview";
        return new JSONObject().put("url",url).put("model",model).put("provider",provider).put("hasKey",Vault.configured(context,role+"Key"));
    }
    static String judgeProvider(SharedPreferences prefs){
        String provider=prefs.getString("judgeProvider","compatible"),url=prefs.getString("judgeUrl","").trim(),model=prefs.getString("judgeModel","").trim();
        if(provider.equals("compatible")&&model.startsWith("decision-model")&&url.matches("https://[A-Za-z0-9-]+\\.(cn-beijing|ap-southeast-1)\\.maas\\.aliyuncs\\.com/compatible-mode/v1/?"))return "bailian";return provider;
    }
    /** Lifecycle maintenance is transactional and never runs while painting a page. */
    static void maintain(Context context,JSONArray characters){Context c=context.getApplicationContext();READS.execute(()->{try(ChatStore s=new ChatStore(c)){
        LettersEngine.deliverAvailable(c,s);android.database.sqlite.SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
            JSONObject personal=s.personal();String before=personal.toString();PersonalData.refresh(personal,PersonalData.today());s.seedProgress(personal);
            for(int i=0;i<characters.length();i++)Progression.relation(personal,characters.getJSONObject(i).getString("id"),System.currentTimeMillis());
            if(!before.equals(personal.toString()))s.savePersonal(personal);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }catch(Exception e){android.util.Log.w("EidolonUI","Local maintenance failed",e);}});}
    private UiSnapshots(){}
}
