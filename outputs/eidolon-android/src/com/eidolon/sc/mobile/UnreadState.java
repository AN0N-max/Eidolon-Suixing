package com.eidolon.sc.mobile;

import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;
import org.json.*;

/** Chat unread belongs to messages, independent of notification permission and delivery. */
final class UnreadState {
    static void create(android.database.sqlite.SQLiteDatabase db){db.execSQL("ALTER TABLE messages ADD COLUMN unread INTEGER NOT NULL DEFAULT 0");db.execSQL("CREATE INDEX messages_unread ON messages(unread,character)");}
    static JSONObject counts(ChatStore s)throws Exception{JSONObject counts=new JSONObject();int total=0;try(Cursor c=s.getReadableDatabase().rawQuery("SELECT character,count(*) FROM messages WHERE role='assistant' AND unread=1 GROUP BY character",null)){while(c.moveToNext()){counts.put(c.getString(0),c.getInt(1));total+=c.getInt(1);}}return new JSONObject().put("characters",counts).put("total",total);}
    static int count(ChatStore s,String who){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT count(*) FROM messages WHERE character=? AND role='assistant' AND unread=1",new String[]{who})){c.moveToFirst();return c.getInt(0);}}
    static void read(Context c,ChatStore s,String who,String session,JSONArray ids)throws Exception{
        synchronized(UnreadNotifications.LOCK){
        if(ids.length()>200)throw new Exception("一次只能确认200条可见消息");android.database.sqlite.SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{for(int i=0;i<ids.length();i++){Object id=ids.get(i);if(!(id instanceof Number)||((Number)id).longValue()<=0||((Number)id).doubleValue()!=((Number)id).longValue())throw new Exception("已读消息编号无效");db.execSQL("UPDATE messages SET unread=0 WHERE id=? AND character=? AND conversation=? AND role='assistant' AND unread=1",new Object[]{((Number)id).longValue(),who,session});}db.setTransactionSuccessful();}finally{db.endTransaction();}
        if(count(s,who)==0)((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(who.hashCode());
        UnreadNotifications.sync(c,s);
        }
    }
    private UnreadState(){}
}
