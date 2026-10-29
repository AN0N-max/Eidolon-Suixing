package com.eidolon.sc.mobile;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

final class ChatStore extends SQLiteOpenHelper {
    private final Context appContext;
    // Only the private process owner opens eidolon.db. Callers are short-lived leases.
    private static ChatStore databaseOwner;
    private final boolean ownsDatabase;
    private volatile boolean closed;
    private static synchronized ChatStore shared(Context app){if(databaseOwner==null)databaseOwner=new ChatStore(app,true);return databaseOwner;}
    private void requireOpen(){if(closed)throw new IllegalStateException("ChatStore lease is closed");}
    @Override public SQLiteDatabase getWritableDatabase(){requireOpen();return ownsDatabase?super.getWritableDatabase():shared(appContext).getWritableDatabase();}
    @Override public SQLiteDatabase getReadableDatabase(){requireOpen();return ownsDatabase?super.getReadableDatabase():shared(appContext).getReadableDatabase();}
    @Override public synchronized void close(){if(!ownsDatabase)closed=true;}

    static final class Message {
        final long id,sentAt;
        final String role, text,zone;
        Message(String role, String text) { this(0,role,text); }
        Message(long id,String role,String text) { this(id,role,text,0,"Asia/Shanghai"); }
        Message(long id,String role,String text,long at,String zone){this.id=id;this.role=role;this.text=text;this.sentAt=at;this.zone=zone.isEmpty()?"Asia/Shanghai":zone;}
    }
    static final class Conversation {
        final String id, title;
        Conversation(String id, String title) { this.id=id; this.title=title; }
    }
    static final class PendingReply {
        final String id,character,session,status,answer,effects,error,userText;
        final long revision,firstUserId,lastUserId,lastAt,dueAt;
        final boolean test,immediate;
        PendingReply(String id,String character,String session,long revision,long firstUserId,long lastUserId,long lastAt,String status,String answer,String effects,long dueAt,String error,boolean test,boolean immediate,String userText){this.id=id;this.character=character;this.session=session;this.revision=revision;this.firstUserId=firstUserId;this.lastUserId=lastUserId;this.lastAt=lastAt;this.status=status;this.answer=answer;this.effects=effects;this.dueAt=dueAt;this.error=error;this.test=test;this.immediate=immediate;this.userText=userText;}
    }
    ChatStore(Context c){this(c,false);}
    private ChatStore(Context c,boolean owner){super(c.getApplicationContext(), "eidolon.db", null, 13);appContext=c.getApplicationContext();ownsDatabase=owner;if(owner)super.setWriteAheadLoggingEnabled(true);}

    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, character TEXT NOT NULL, role TEXT NOT NULL, body TEXT NOT NULL, conversation TEXT NOT NULL)");
        db.execSQL("CREATE INDEX character_messages ON messages(character,id)");
        createConversations(db);
        createPersonal(db);
        createStories(db);
        createReach(db);
        WritingStore.create(db);
        ScheduleStore.create(db);
        createPending(db);
        LetterPlanStore.create(db);
        DiaryStore.create(db);
        AppealStore.create(db);
        ReachPlanStore.create(db);UnreadState.create(db);MemoryNotebook.create(db);
    }
    private void createConversations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE conversations(id TEXT PRIMARY KEY, character TEXT NOT NULL, title TEXT NOT NULL, updated INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX conversation_messages ON messages(conversation,id)");
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN conversation TEXT NOT NULL DEFAULT ''");
            createConversations(db);
            db.execSQL("INSERT INTO conversations SELECT 'legacy-' || character,character,'原有聊天',MAX(id) FROM messages GROUP BY character");
            db.execSQL("UPDATE messages SET conversation='legacy-' || character");
        }
        if(oldVersion<3)createPersonal(db);
        if(oldVersion<4)createStories(db);
        if(oldVersion<5)createReach(db);
        if(oldVersion<6)WritingStore.create(db);
        if(oldVersion<7)ScheduleStore.create(db);
        if(oldVersion<8)createPending(db);
        if(oldVersion<9)LetterPlanStore.create(db);
        if(oldVersion<10)DiaryStore.create(db);
        if(oldVersion<11)AppealStore.create(db);
        if(oldVersion<12){ReachPlanStore.create(db);UnreadState.create(db);}
        if(oldVersion<13){MemoryNotebook.create(db);if(oldVersion>=12)db.execSQL("ALTER TABLE reach_tasks ADD COLUMN opportunity INTEGER NOT NULL DEFAULT 0");}
    }
    private void createPending(SQLiteDatabase db){db.execSQL("CREATE TABLE pending_replies(id TEXT PRIMARY KEY,character TEXT NOT NULL,session TEXT NOT NULL,revision INTEGER NOT NULL,first_user_id INTEGER NOT NULL,last_user_id INTEGER NOT NULL,last_at INTEGER NOT NULL,status TEXT NOT NULL,answer TEXT NOT NULL DEFAULT '',effects TEXT NOT NULL DEFAULT '',due_at INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '',test INTEGER NOT NULL DEFAULT 0,immediate INTEGER NOT NULL DEFAULT 0)");db.execSQL("CREATE INDEX pending_due ON pending_replies(status,due_at)");}
    PendingReply beginPendingUser(String who,String session,String text,long now)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT character FROM conversations WHERE id=?",new String[]{session})){if(!c.moveToFirst()||!who.equals(c.getString(0)))throw new IOException("该对话已删除");}
            JSONObject personal=personal();if(noteMeeting(personal,who,now))savePersonal(personal);
            long first=0,revision=1;boolean test=false;
            try(Cursor c=db.rawQuery("SELECT revision,first_user_id,test FROM pending_replies WHERE id=?",new String[]{session})){if(c.moveToFirst()){revision=c.getLong(0)+1;first=c.getLong(1);test=c.getInt(2)!=0;}}
            boolean empty;try(Cursor c=db.rawQuery("SELECT count(*) FROM messages WHERE conversation=? AND role='user'",new String[]{session})){c.moveToFirst();empty=c.getInt(0)==0;}
            ContentValues msg=new ContentValues();msg.put("character",who);msg.put("conversation",session);msg.put("role","user");msg.put("body",text);stamp(msg,now);long userId=db.insertOrThrow("messages",null,msg);if(first==0)first=userId;
            ContentValues update=new ContentValues();update.put("updated",now);if(empty)update.put("title",text.substring(0,text.offsetByCodePoints(0,Math.min(28,text.codePointCount(0,text.length())))));db.update("conversations",update,"id=?",new String[]{session});
            ContentValues row=new ContentValues();row.put("id",session);row.put("character",who);row.put("session",session);row.put("revision",revision);row.put("first_user_id",first);row.put("last_user_id",userId);row.put("last_at",now);row.put("status","collecting");row.put("answer","");row.put("effects","");row.put("due_at",0);row.put("error","");row.put("test",test?1:0);row.put("immediate",0);db.insertWithOnConflict("pending_replies",null,row,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();return pending(session);
        }finally{db.endTransaction();}
    }
    PendingReply createReplyTest(String who,String session,String id,long now)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{ContentValues row=new ContentValues();row.put("id",id);row.put("character",who);row.put("session",session);row.put("revision",1);row.put("first_user_id",0);row.put("last_user_id",0);row.put("last_at",now);row.put("status","collecting");row.put("test",1);db.insertWithOnConflict("pending_replies",null,row,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();return pending(id);}finally{db.endTransaction();}
    }
    PendingReply pending(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id,character,session,revision,first_user_id,last_user_id,last_at,status,answer,effects,due_at,error,test,immediate FROM pending_replies WHERE id=?",new String[]{id})){return c.moveToFirst()?pending(c):null;}}
    private PendingReply pending(Cursor c){String user="";if(c.getLong(4)>0)try(Cursor m=getReadableDatabase().rawQuery("SELECT group_concat(body,char(10)) FROM messages WHERE conversation=? AND role='user' AND id>=? AND id<=? ORDER BY id",new String[]{c.getString(2),""+c.getLong(4),""+c.getLong(5)})){if(m.moveToFirst())user=m.getString(0)==null?"":m.getString(0);}return new PendingReply(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getLong(5),c.getLong(6),c.getString(7),c.getString(8),c.getString(9),c.getLong(10),c.getString(11),c.getInt(12)!=0,c.getInt(13)!=0,user);}
    List<PendingReply> pendingReplies(){List<PendingReply> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,character,session,revision,first_user_id,last_user_id,last_at,status,answer,effects,due_at,error,test,immediate FROM pending_replies",null)){while(c.moveToNext())out.add(pending(c));}return out;}
    List<Message> historyBefore(String session,long firstUserId,int limit){List<Message> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,role,body,sent_at,time_zone FROM messages WHERE conversation=? AND id<? ORDER BY id DESC LIMIT ?",new String[]{session,""+firstUserId,""+limit})){while(c.moveToNext())out.add(0,new Message(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4)));}return out;}
    boolean pendingState(String id,long revision,String status){ContentValues v=new ContentValues();v.put("status",status);v.put("error","");return getWritableDatabase().update("pending_replies",v,"id=? AND revision=? AND status IN ('collecting','generating','waiting','failed')",new String[]{id,""+revision})==1;}
    boolean markGenerating(String id,long revision){ContentValues v=new ContentValues();v.put("status","generating");v.put("error","");return getWritableDatabase().update("pending_replies",v,"id=? AND revision=? AND status='collecting'",new String[]{id,""+revision})==1;}
    boolean markReady(String id,long revision,String answer,JSONObject effects,long dueAt){ContentValues v=new ContentValues();v.put("status","waiting");v.put("answer",answer);v.put("effects",effects.toString());v.put("due_at",dueAt);v.put("error","");return getWritableDatabase().update("pending_replies",v,"id=? AND revision=? AND status='generating'",new String[]{id,""+revision})==1;}
    void markFailed(String id,long revision,String error){ContentValues v=new ContentValues();v.put("status","failed");v.put("error",error==null?"回复生成失败":error);getWritableDatabase().update("pending_replies",v,"id=? AND revision=? AND status IN ('collecting','generating')",new String[]{id,""+revision});}
    void markScheduleFailed(String id,long revision,String error){ContentValues v=new ContentValues();v.put("status","failed");v.put("error",error==null?"系统未能安排回复提醒":error);getWritableDatabase().update("pending_replies",v,"id=? AND revision=? AND status='waiting' AND answer<>''",new String[]{id,""+revision});}
    boolean retryReady(String id,long revision,long dueAt){ContentValues v=new ContentValues();v.put("status","waiting");v.put("due_at",dueAt);v.put("error","");return getWritableDatabase().update("pending_replies",v,"id=? AND revision=? AND status='failed' AND answer<>''",new String[]{id,""+revision})==1;}
    void immediate(String id){ContentValues v=new ContentValues();v.put("immediate",1);v.put("due_at",0);getWritableDatabase().update("pending_replies",v,"id=?",new String[]{id});}
    void cancelPending(String id){getWritableDatabase().execSQL("UPDATE pending_replies SET revision=revision+1,status='cancelled',answer='',effects='',due_at=0,error='' WHERE id=? AND status IN ('collecting','generating','waiting','failed')",new Object[]{id});}
    void requeueInterrupted(String id,long revision,long now){ContentValues v=new ContentValues();v.put("revision",revision+1);v.put("status","collecting");v.put("error","");getWritableDatabase().update("pending_replies",v,"id=? AND revision=? AND status='generating'",new String[]{id,""+revision});}
    boolean hasBlockingPending(String character){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM pending_replies WHERE character=? AND test=0 AND status IN ('collecting','generating','waiting','failed') LIMIT 1",new String[]{character})){return c.moveToFirst();}}
    void retryPending(String id,long now){getWritableDatabase().execSQL("UPDATE pending_replies SET revision=revision+1,status='collecting',error='',answer='',effects='',due_at=0 WHERE id=? AND status='failed'",new Object[]{id});}
    JSONObject deliverPending(String id,long now)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{PendingReply p=pending(id);if(p==null||!p.status.equals("waiting")||(!p.immediate&&p.dueAt>now))return null;
            if(p.test){db.delete("pending_replies","id=? AND revision=? AND status='waiting'",new String[]{id,""+p.revision});db.setTransactionSuccessful();return new JSONObject().put("test",true).put("character",p.character).put("session",p.session).put("name","本地延迟测试").put("text","测试回复：根据当前页面状态与本地任务调度，延迟回复链路正常。此测试没有调用 API，也没有写入聊天记录或关系。");}
            if(lastUserId(p.session)!=p.lastUserId){db.delete("pending_replies","id=? AND revision=?",new String[]{id,""+p.revision});db.setTransactionSuccessful();return null;}
            String answer=ReplyText.clean(p.answer);if(answer.replaceAll("(?s)</?(?:ja|zh)>","").trim().isEmpty()){ContentValues bad=new ContentValues();bad.put("status","failed");bad.put("answer","");bad.put("effects","");bad.put("error","回复只有内部时间标记，没有可用台词，请重试");db.update("pending_replies",bad,"id=? AND revision=? AND status='waiting'",new String[]{id,""+p.revision});db.setTransactionSuccessful();ReplyManager.changed(id);return null;}
            if(db.delete("pending_replies","id=? AND revision=? AND status='waiting'",new String[]{id,""+p.revision})!=1)return null;
            JSONObject effects=new JSONObject(p.effects),generated=effects.getJSONObject("personal"),base=effects.optJSONObject("base"),latestPersonal=personal(),personal=base==null?generated:PersonalMerge.merge(base,generated,latestPersonal);HeartScenes.delivered(latestPersonal,personal,p.character,Progression.session(personal,p.session).optBoolean("ended")?null:effects.optJSONObject("heartScene"));AppealData.delivered(latestPersonal,personal,p.character,effects.optString("appealExplanation"));JSONObject judgment=AppealData.record(effects,now);if(judgment!=null)Progression.session(personal,p.session).put("lastJudgmentKey",judgment.getString("key"));Progression.happened(personal,p.character,0,0,0,PersonalData.today());String milestone="";if(base!=null){int before=base.getJSONObject("affinity").optInt(p.character,50),after=personal.getJSONObject("affinity").optInt(p.character,before);milestone=(before<85&&after>=85)?"今天和制作人的关系进入亲密阶段。":(before<65&&after>=65)?"今天和制作人的关系进入亲近阶段。":"";}ContentValues msg=new ContentValues();msg.put("character",p.character);msg.put("conversation",p.session);msg.put("role","assistant");msg.put("body",answer);stamp(msg,now);msg.put("unread",1);if(judgment!=null)msg.put("judgment",judgment.toString());db.insertOrThrow("messages",null,msg);
            ContentValues update=new ContentValues();update.put("updated",now);db.update("conversations",update,"id=?",new String[]{p.session});savePersonal(personal);commitmentEvents(p.character,personal,effects.optJSONArray("events"));JSONObject moment=effects.optJSONObject("diaryMoment");if(moment!=null&&appContext.getSharedPreferences("settings",0).getBoolean("hibiDiary",false)&&appContext.getSharedPreferences("settings",0).getBoolean("autoMemory",true)&&(base==null||base.optLong("memoryEpoch")==personal.optLong("memoryEpoch")))WritingStore.event(this,p.character,moment.getString("date"),moment.getString("text"));if(!milestone.isEmpty())WritingStore.event(this,p.character,WritingData.today(personal).toString(),milestone);ScheduleRuntime.contact(appContext,this,personal,p.character,p.lastAt);if(appContext.getSharedPreferences("settings",0).getBoolean("timelineAuto",false))MemoryNotebook.deliver(this,effects.optJSONArray("timeEvents"),effects.optLong("notebookEpoch",-1));db.setTransactionSuccessful();
            return new JSONObject().put("test",false).put("character",p.character).put("session",p.session).put("name",effects.optString("name")).put("text",answer).put("dueAt",p.dueAt);
        }finally{db.endTransaction();}
    }
    void deletePending(String id){getWritableDatabase().delete("pending_replies","id=?",new String[]{id});}
    private void createReach(SQLiteDatabase db){db.execSQL("ALTER TABLE messages ADD COLUMN reach_at INTEGER NOT NULL DEFAULT 0");db.execSQL("CREATE TABLE reach_schedule(session TEXT PRIMARY KEY,contact INTEGER NOT NULL,due INTEGER NOT NULL)");}
    long lastId(String session){try(Cursor c=getReadableDatabase().rawQuery("SELECT coalesce(max(id),0) FROM messages WHERE conversation=?",new String[]{session})){c.moveToFirst();return c.getLong(0);}}
    long lastUserId(String session){try(Cursor c=getReadableDatabase().rawQuery("SELECT coalesce(max(id),0) FROM messages WHERE conversation=? AND role='user'",new String[]{session})){c.moveToFirst();return c.getLong(0);}}
    boolean addReach(String who,String session,long contact,String text,long now){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{if(lastId(session)!=contact)return false;try(Cursor c=db.rawQuery("SELECT id FROM conversations WHERE character=? ORDER BY updated DESC,rowid DESC LIMIT 1",new String[]{who})){if(!c.moveToFirst()||!session.equals(c.getString(0)))return false;}
        ContentValues v=new ContentValues();v.put("character",who);v.put("conversation",session);v.put("role","assistant");v.put("body",text);stamp(v,now);v.put("reach_at",now);db.insertOrThrow("messages",null,v);ContentValues updated=new ContentValues();updated.put("updated",now);db.update("conversations",updated,"id=?",new String[]{session});db.setTransactionSuccessful();return true;}finally{db.endTransaction();}}
    boolean addPromiseReach(String who,String session,long contact,String text,long now,JSONObject cue,boolean points,ReachConfig config,long revision)throws Exception{
        return addPlannedReach(who,session,contact,text,now,cue,points,config,revision,"","");
    }
    boolean addPlannedReach(String who,String session,long contact,String text,long now,JSONObject cue,boolean points,ReachConfig config,long revision,String task,String token)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            if(!task.isEmpty()&&!ReachPlanStore.isRunning(this,task,token))return false;
            android.content.SharedPreferences prefs=appContext.getSharedPreferences("settings",0);
            if(lastId(session)!=contact||!prefs.getBoolean("reachEnabled",false)||prefs.getLong("reachSettingsRevision",0)!=revision||!ReachOut.quotaAllowed(this,config,who,now)){db.setTransactionSuccessful();return false;}
            try(Cursor c=db.rawQuery("SELECT id FROM conversations WHERE character=? ORDER BY updated DESC,rowid DESC LIMIT 1",new String[]{who})){if(!c.moveToFirst()||!session.equals(c.getString(0))){db.setTransactionSuccessful();return false;}}
            JSONObject personal=personal();if(Progression.session(personal,session).optBoolean("ended")||Progression.relation(personal,who,now).optBoolean("cooling")||(prefs.getBoolean("affinity",true)?personal.getJSONObject("affinity").optInt(who,50):50)<config.integer("minAffinity")){db.setTransactionSuccessful();return false;}String schedule=ScheduleRuntime.state(appContext,this,personal,who,now).optString("state");if(!ReachPlanRules.suitable(ScheduleRuntime.state(appContext,this,personal,who,now))){db.setTransactionSuccessful();return false;}
            ContentValues v=new ContentValues();v.put("character",who);v.put("conversation",session);v.put("role","assistant");v.put("body",text);stamp(v,now);v.put("reach_at",now);v.put("unread",1);long messageId=db.insertOrThrow("messages",null,v);
            if(!task.isEmpty()){ContentValues done=new ContentValues();done.put("status","COMPLETED");done.put("finished",now);done.put("message_id",messageId);done.put("token","");if(db.update("reach_tasks",done,"id=? AND status='RUNNING' AND token=?",new String[]{task,token})!=1)throw new IOException("搭话任务已失效");}
            ContentValues updated=new ContentValues();updated.put("updated",now);db.update("conversations",updated,"id=?",new String[]{session});
            JSONArray events=new JSONArray();PromiseData.acknowledge(personal,cue,text,points,events);savePersonal(personal);commitmentEvents(who,personal,events);
            db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    private void createStories(SQLiteDatabase db){db.execSQL("CREATE TABLE stories(id TEXT PRIMARY KEY, title TEXT NOT NULL, format TEXT NOT NULL, cast_ids TEXT NOT NULL, body TEXT NOT NULL)");db.execSQL("CREATE VIRTUAL TABLE story_terms USING fts4(id, terms, tokenize=unicode61)");}
    int storyCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM stories",null)){c.moveToFirst();return c.getInt(0);}}
    JSONObject story(String id)throws Exception{try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM stories WHERE id=?",new String[]{id})){if(!c.moveToFirst())throw new Exception("剧情不存在");return new JSONObject(c.getString(0));}}
    void deleteStory(String id){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("stories","id=?",new String[]{id});db.delete("story_terms","id=?",new String[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    int importStories(JSONArray episodes)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int imported=0;
        try{for(int i=0;i<episodes.length();i++){
            JSONObject e=episodes.getJSONObject(i);String id=e.getString("id");if(e.optBoolean("removed")){if(!StoryFiles.failure(id))throw new Exception("删除标识无效");deleteStory(id);continue;}StoryFiles.validate(e);
            try(Cursor c=db.rawQuery("SELECT format,body FROM stories WHERE id=?",new String[]{id})){if(c.moveToFirst()&&((c.getString(0).equals("csv")&&e.getString("format").equals("json"))||c.getString(1).equals(e.toString())))continue;}
            ContentValues v=new ContentValues();v.put("id",id);v.put("title",e.getString("title"));v.put("format",e.getString("format"));JSONArray cast=e.getJSONArray("cast");String joined="|";for(int j=0;j<cast.length();j++)joined+=cast.getString(j)+"|";v.put("cast_ids",joined);v.put("body",e.toString());
            if(db.insertWithOnConflict("stories",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new Exception("剧情保存失败");
            db.delete("story_terms","id=?",new String[]{id});ContentValues terms=new ContentValues();terms.put("id",id);terms.put("terms",String.join(" ",Recall.terms(e.getString("title")+" "+e.getString("text"))));db.insertOrThrow("story_terms",null,terms);imported++;
        }if(storyCount()>10000)throw new Exception("剧情库最多 10000 段");db.setTransactionSuccessful();return imported;}finally{db.endTransaction();}
    }
    JSONArray searchStories(String query,String who,int limit)throws Exception{
        return searchStories(query,who,limit,120,limit<=2?900:2200,24);
    }
    JSONArray searchStories(String query,String who,int limit,int candidateLimit,int excerptChars,int termLimit)throws Exception{
        java.util.List<JSONObject> ranked=new java.util.ArrayList<>();
        List<String> args=new ArrayList<>();String sql="SELECT body FROM stories";
        if(!query.isEmpty()){StringBuilder match=new StringBuilder();int n=0;for(String term:Recall.terms(query)){if(n++>=termLimit)break;if(match.length()>0)match.append(" OR ");match.append('"').append(term).append('"');}if(match.length()==0)return new JSONArray();sql+=" JOIN story_terms ON stories.id=story_terms.id WHERE story_terms MATCH ?";args.add(match.toString());}
        if(!who.isEmpty()){sql+=(query.isEmpty()?" WHERE ":" AND ")+"instr(cast_ids,?)>0";args.add("|"+who+"|");}
        sql+=" ORDER BY stories.id LIMIT ?";args.add(Integer.toString(candidateLimit));
        try(Cursor c=getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))){
            while(c.moveToNext()){JSONObject e=new JSONObject(c.getString(0));String excerpt=StoryFiles.excerpt(e.getString("text"),query,Math.max(2200,excerptChars));double score=Recall.score(query,e.getString("title")+" "+excerpt);
                if(!query.isEmpty()&&score<=0)continue;
                e.put("score",score).put("text",StoryFiles.excerpt(e.getString("text"),query,excerptChars));ranked.add(e);ranked.sort((a,b)->Double.compare(b.optDouble("score"),a.optDouble("score")));if(ranked.size()>limit)ranked.remove(ranked.size()-1);
            }}JSONArray result=new JSONArray();for(JSONObject e:ranked)result.put(e);return result;
    }
    JSONArray allStories()throws Exception{JSONArray out=new JSONArray();int length=0;try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM stories ORDER BY id",null)){while(c.moveToNext()){String body=c.getString(0);length+=body.length();if(length>3500000)throw new Exception("剧情库超过备份上限，请保留原始剧情导入文件");out.put(new JSONObject(body));}}return out;}
    void seedProgress(JSONObject data)throws Exception{
        Progression.upgrade(data);JSONObject p=data.getJSONObject("progress");if(p.optBoolean("seeded"))return;JSONObject counts=p.getJSONObject("counts");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT character,count(*) FROM messages WHERE role='user' GROUP BY character",null)){while(c.moveToNext())counts.put("turns/"+c.getString(0),c.getLong(1));}
        JSONObject affinity=data.getJSONObject("affinity");for(java.util.Iterator<String> it=affinity.keys();it.hasNext();){String who=it.next();double score=affinity.getDouble(who);counts.put("tier/"+who,score>=85?2:score>=65?1:0);}
        p.put("seeded",true);Progression.unlock(data);
    }
    void commitmentEvents(String who,JSONObject personal,JSONArray events)throws Exception{
        if(events!=null)for(int i=0;i<events.length();i++)WritingStore.event(this,who,WritingData.today(personal).toString(),events.getString(i));
    }
    private boolean noteMeeting(JSONObject personal,String who,long now)throws Exception{
        boolean known;try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM messages WHERE character=? LIMIT 1",new String[]{who})){known=c.moveToFirst();}
        boolean recorded=Anniversaries.record(personal,who,now,known);java.time.LocalDate day=java.time.Instant.ofEpochMilli(now).atZone(Anniversaries.zone(personal)).toLocalDate();String event=Anniversaries.lines(personal,who,day);if(!event.isEmpty())WritingStore.event(this,who,day.toString(),event);return recorded;
    }
    void commitPromises(String who,JSONObject personal,JSONArray events)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{savePersonal(personal);commitmentEvents(who,personal,events);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    void commitTurn(String who,String session,String user,String answer,JSONObject personal,String milestone)throws Exception{commitTurn(who,session,user,answer,personal,milestone,null);}
    void commitTurn(String who,String session,String user,String answer,JSONObject personal,String milestone,JSONArray events)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{noteMeeting(personal,who,System.currentTimeMillis());saveTurn(who,session,user,answer);savePersonal(personal);commitmentEvents(who,personal,events);if(!milestone.isEmpty())WritingStore.event(this,who,WritingData.today(personal).toString(),milestone);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    String commitGift(String who,JSONObject event,JSONObject personal)throws Exception{return commitGift(who,event,personal,false,false);}
    String commitGift(String who,JSONObject event,JSONObject personal,boolean promises,boolean points)throws Exception{
        GiftRecord.parse(event.toString());
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            String session=latestConversation(who);
            noteMeeting(personal,who,event.getLong("at"));
            if(Progression.session(personal,session).optBoolean("ended"))session=newConversation(who);
            insert(db,who,session,"gift",event.toString());
            ContentValues updated=new ContentValues();updated.put("updated",event.getLong("at"));
            db.update("conversations",updated,"id=?",new String[]{session});
            JSONArray changes=new JSONArray();if(promises)PromiseData.gift(personal,who,event.getString("giftId"),WritingData.today(personal),points,changes);
            savePersonal(personal);commitmentEvents(who,personal,changes);if(event.optBoolean("liked"))WritingStore.event(this,who,WritingData.today(personal).toString(),"制作人送给我一份喜欢的礼物："+event.getString("giftName")+"。");db.setTransactionSuccessful();return session;
        }finally{db.endTransaction();}
    }
    private void createPersonal(SQLiteDatabase db) { db.execSQL("CREATE TABLE personal(id INTEGER PRIMARY KEY CHECK(id=1), body TEXT NOT NULL)"); }
    JSONObject personal() throws Exception {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM personal WHERE id=1",null)) {
            JSONObject data=c.moveToFirst()?new JSONObject(c.getString(0)):PersonalData.fresh();if(data.has("heartScenes")){android.content.SharedPreferences prefs=appContext.getSharedPreferences("settings",0);HeartScenes.configure(data,HeartScenes.root(data).optBoolean("enabled"),!prefs.getBoolean("affinity",true)||!prefs.getBoolean("hibiHearts",true));}return data;
        }
    }
    void savePersonal(JSONObject data) throws Exception {
        PersonalData.validate(data);ContentValues v=new ContentValues();v.put("id",1);v.put("body",data.toString());
        if(getWritableDatabase().insertWithOnConflict("personal",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new Exception("保存失败");
    }
    JSONObject stats() throws Exception {
        JSONObject result=new JSONObject();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*),count(DISTINCT character),coalesce(sum(length(body)),0) FROM messages WHERE role='user'",null)){c.moveToFirst();result.put("turns",c.getLong(0)).put("characters",c.getInt(1)).put("words",c.getLong(2));}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM conversations",null)){c.moveToFirst();result.put("sessions",c.getLong(0));}return result;
    }
    List<Conversation> conversations(String character) {
        List<Conversation> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title FROM conversations WHERE character=? ORDER BY updated DESC,rowid DESC",new String[]{character})) {
            while(c.moveToNext())result.add(new Conversation(c.getString(0),c.getString(1)));
        }
        return result;
    }
    String newConversation(String character) {
        String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();
        v.put("id",id);v.put("character",character);v.put("title","新对话");v.put("updated",System.currentTimeMillis());
        getWritableDatabase().insertOrThrow("conversations",null,v);return id;
    }
    String latestConversation(String character) {
        List<Conversation> all=conversations(character);
        return all.isEmpty()?newConversation(character):all.get(0).id;
    }
    long lastActivity(String character) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT coalesce(max(c.updated),0) FROM conversations c WHERE c.character=? AND EXISTS(SELECT 1 FROM messages m WHERE m.conversation=c.id)",new String[]{character})) { c.moveToFirst();return c.getLong(0); }
    }
    List<Message> sessionHistory(String conversation, int limit) {
        List<Message> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,role,body,sent_at,time_zone FROM (SELECT id,role,body,sent_at,time_zone FROM messages WHERE conversation=? ORDER BY id DESC LIMIT ?) ORDER BY id",new String[]{conversation,Integer.toString(limit)})) {
            while(c.moveToNext())result.add(new Message(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4)));
        }
        return result;
    }
    String assistantText(long id,String conversation,String character)throws IOException{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM messages WHERE id=? AND conversation=? AND character=? AND role='assistant'",new String[]{Long.toString(id),conversation,character})){
            if(!c.moveToFirst())throw new IOException("这条角色消息已不存在");return c.getString(0);
        }
    }
    void replaceAssistantText(long id,String conversation,String character,String oldText,String newText)throws IOException{
        ContentValues values=new ContentValues();values.put("body",newText);
        if(getWritableDatabase().update("messages",values,"id=? AND conversation=? AND character=? AND role='assistant' AND body=?",new String[]{Long.toString(id),conversation,character,oldText})!=1)
            throw new IOException("消息已变化，请刷新对话后重试");
    }
    List<Message> history(String character, int limit) {
        List<Message> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT role,body,sent_at,time_zone FROM (SELECT id,role,body,sent_at,time_zone FROM messages WHERE character=? ORDER BY id DESC LIMIT ?) ORDER BY id", new String[]{character, Integer.toString(limit)})) {
            while(c.moveToNext()) result.add(new Message(0,c.getString(0),c.getString(1),c.getLong(2),c.getString(3)));
        }
        return result;
    }
    void saveTurn(String character, String conversation, String user, String assistant) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            boolean empty;try(Cursor first=db.rawQuery("SELECT count(*) FROM messages WHERE conversation=? AND role='user'",new String[]{conversation})){first.moveToFirst();empty=first.getInt(0)==0;}
            ContentValues update=new ContentValues();update.put("updated",System.currentTimeMillis());
            if(empty)update.put("title",user.substring(0,user.offsetByCodePoints(0,Math.min(28,user.codePointCount(0,user.length())))));
            if(db.update("conversations",update,"id=? AND character=?",new String[]{conversation,character})!=1)throw new IllegalStateException("Conversation missing");
            insert(db, character, conversation, "user", user); insert(db, character, conversation, "assistant", assistant);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private void insert(SQLiteDatabase db, String character, String conversation, String role, String body) {
        ContentValues values = new ContentValues(); values.put("character", character); values.put("role", role); values.put("body", body);
        values.put("conversation",conversation);stamp(values,System.currentTimeMillis());
        db.insertOrThrow("messages", null, values);
    }
    private void stamp(ContentValues v,long at){v.put("sent_at",at);String zone=java.time.ZoneId.systemDefault().getId();try{zone=ScheduleData.zone(personal()).getId();}catch(Exception ignored){}v.put("time_zone",zone);}
    void deleteConversation(String conversation) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{db.execSQL("UPDATE reach_tasks SET status='SKIPPED',token='',error='会话已删除' WHERE session=? AND status IN ('PENDING','RUNNING')",new Object[]{conversation});db.delete("pending_replies","id=?",new String[]{conversation});db.delete("messages","conversation=?",new String[]{conversation});db.delete("conversations","id=?",new String[]{conversation});db.setTransactionSuccessful();}finally{db.endTransaction();}PendingReplyJob.cancel(appContext,conversation);UnreadNotifications.sync(appContext,this);
    }
    JSONObject exportChats() throws Exception {
        SQLiteDatabase db=getReadableDatabase();db.beginTransaction();
        try {
            JSONArray sessions=new JSONArray();int characters=0;
            try(Cursor c=db.rawQuery("SELECT id,character,title FROM conversations ORDER BY updated",null)) {
                while(c.moveToNext()) {
                    JSONArray rows=new JSONArray();
                    try(Cursor m=db.rawQuery("SELECT role,body,reach_at,judgment,unread,sent_at,time_zone,id FROM messages WHERE conversation=? ORDER BY id",new String[]{c.getString(0)})) {
                        while(m.moveToNext()) {
                            characters+=m.getString(1).length();if(characters>4000000)throw new Exception("聊天备份超过本版本的 400 万字限制");
                            JSONObject row=new JSONObject().put("role",m.getString(0)).put("text",m.getString(1)).put("reachOut",m.getLong(2)>0).put("unread",m.getInt(4)!=0).put("sentAt",m.getLong(5)).put("timeZone",m.getString(6)).put("id",m.getLong(7));if(!m.getString(3).isEmpty())row.put("judgment",new JSONObject(m.getString(3)));rows.put(row);
                        }
                    }
                    sessions.put(new JSONObject().put("id",c.getString(0)).put("character",c.getString(1)).put("title",c.getString(2)).put("messages",rows));
                }
            }
            return new JSONObject().put("format","eidolon-android-chats").put("version",13).put("conversations",sessions).put("personal",personal()).put("stories",allStories()).put("writings",WritingStore.all(this)).put("writingEvents",WritingStore.events(this)).put("schedules",ScheduleStore.all(this)).put("letterPlans",LetterPlanStore.all(this)).put("diaryAttempts",DiaryStore.attempts(this)).put("notebook",MemoryNotebook.all(this));
        }finally{db.endTransaction();}
    }
    int importChats(JSONObject data, java.util.Set<String> knownCharacters) throws Exception {
        BackupValidator.validate(data,knownCharacters);
        WritingStore.epoch.incrementAndGet();
        synchronized(ReachPlans.LOCK){ReachOut.cancel();appContext.getSharedPreferences("settings",0).edit().putLong("reachSettingsRevision",appContext.getSharedPreferences("settings",0).getLong("reachSettingsRevision",0)+1).commit();ReachPlanStore.stop(this,"备份恢复后旧搭话计划已失效",System.currentTimeMillis());}
        JSONArray sessions=data.getJSONArray("conversations");SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            java.util.Map<String,String> mapping=new java.util.HashMap<>();java.util.Map<String,Long> messageMapping=new java.util.HashMap<>();
            for(int i=0;i<sessions.length();i++) {
                JSONObject session=sessions.getJSONObject(i);String character=session.getString("character"),id=newConversation(character);
                mapping.put(session.optString("id"),id);
                ContentValues title=new ContentValues();title.put("title",session.getString("title"));db.update("conversations",title,"id=?",new String[]{id});
                JSONArray rows=session.getJSONArray("messages");
                for(int j=0;j<rows.length();j++){JSONObject row=rows.getJSONObject(j);insert(db,character,id,row.getString("role"),row.getString("text"));if(data.optInt("version")>=13)messageMapping.put(session.optString("id")+"/"+row.getLong("id"),lastId(id));db.execSQL("UPDATE messages SET sent_at=?,time_zone=? WHERE id=last_insert_rowid()",new Object[]{data.optInt("version")>=13?row.optLong("sentAt"):0,data.optInt("version")>=13?row.optString("timeZone","Asia/Shanghai"):"Asia/Shanghai"});if(data.optInt("version")>=12&&row.optBoolean("unread"))db.execSQL("UPDATE messages SET unread=1 WHERE id=last_insert_rowid() AND role='assistant'");if(row.optBoolean("reachOut"))db.execSQL("UPDATE messages SET reach_at=1 WHERE id=last_insert_rowid()");if(data.optInt("version")>=11&&row.optJSONObject("judgment")!=null){ContentValues savedJudgment=new ContentValues();savedJudgment.put("judgment",row.getJSONObject("judgment").toString());db.update("messages",savedJudgment,"id=last_insert_rowid()",null);}}
            }
            if(data.optInt("version")>=2){JSONObject p=new JSONObject(data.getJSONObject("personal").toString());Progression.upgrade(p);JSONObject old=p.getJSONObject("sessions"),remapped=new JSONObject();for(java.util.Iterator<String> it=old.keys();it.hasNext();){String id=it.next();if(mapping.containsKey(id))remapped.put(mapping.get(id),old.getJSONObject(id));}p.put("sessions",remapped);
                JSONArray memories=p.getJSONArray("memories");for(int i=0;i<memories.length();i++){JSONObject m=memories.getJSONObject(i);if(m.has("conversation"))m.put("conversation",mapping.getOrDefault(m.optString("conversation"),""));}savePersonal(p);}
            if(data.optInt("version")>=3)importStories(data.getJSONArray("stories"));
            if(data.optInt("version")>=6){
                // Match personal-data replacement semantics during restore.
                db.delete("writings",null,null);db.delete("writing_events",null,null);
                JSONArray writings=data.getJSONArray("writings");for(int i=0;i<writings.length();i++)WritingStore.save(this,writings.getJSONObject(i));
                JSONArray events=data.getJSONArray("writingEvents");for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);WritingStore.event(this,e.getString("character"),e.getString("date"),e.getString("text"));}
            }
            if(data.optInt("version")>=7){db.delete("schedules",null,null);JSONArray plans=data.getJSONArray("schedules");for(int i=0;i<plans.length();i++)ScheduleStore.save(this,plans.getJSONObject(i),-1);}
            // Old backups have no staged letters; never retain a request from replaced data.
            db.delete("letter_plans",null,null);
            if(data.optInt("version")>=9){JSONArray plans=data.getJSONArray("letterPlans");for(int i=0;i<plans.length();i++)LetterPlanStore.insert(this,plans.getJSONObject(i),WritingStore.epoch.get());}
            db.delete("diary_attempts",null,null);
            if(data.optInt("version")>=10){JSONArray attempts=data.getJSONArray("diaryAttempts");for(int i=0;i<attempts.length();i++)DiaryStore.restoreAttempt(this,attempts.getJSONObject(i));}
            MemoryNotebook.restore(this,data,mapping,messageMapping);db.setTransactionSuccessful();return sessions.length();
        }finally{db.endTransaction();}
    }
}
