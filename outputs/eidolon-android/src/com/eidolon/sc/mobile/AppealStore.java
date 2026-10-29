package com.eidolon.sc.mobile;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

/** The refund, once-only message mark and shared daily quota are committed atomically. */
final class AppealStore {
    static void create(SQLiteDatabase db){db.execSQL("ALTER TABLE messages ADD COLUMN judgment TEXT NOT NULL DEFAULT ''");}
    static long latest(ChatStore s,String who){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT coalesce(max(id),0) FROM messages WHERE character=? AND role IN ('user','assistant')",new String[]{who})){c.moveToFirst();return c.getLong(0);}}
    static String raw(ChatStore s,String who,String session,long id){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT judgment FROM messages WHERE id=? AND character=? AND conversation=? AND role='assistant' AND reach_at=0",new String[]{""+id,who,session})){return c.moveToFirst()?c.getString(0):"";}}
    static JSONObject offer(ChatStore s,JSONObject personal,String who,String session,boolean enabled)throws Exception{
        if(!enabled)return new JSONObject();
        long id=latest(s,who);String raw=raw(s,who,session,id);if(raw.isEmpty())return new JSONObject();JSONObject record=new JSONObject(raw);if(record.optDouble("hurt")<=0)return new JSONObject();
        String reason=s.hasBlockingPending(who)?"请先处理当前待回复消息":AppealData.reason(personal,who,session,record,WritingData.today(personal).toString(),enabled);
        return new JSONObject().put("messageId",id).put("available",reason.isEmpty()).put("reason",reason).put("appealed",record.optBoolean("appealed")).put("left",AppealData.left(personal,WritingData.today(personal).toString()));
    }
    static double appeal(ChatStore s,String who,String session,long id,boolean enabled)throws Exception{
        SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
            if(s.hasBlockingPending(who))throw new Exception("请先处理当前待回复消息");if(latest(s,who)!=id)throw new Exception("只能申诉角色最新一条回复");String raw=raw(s,who,session,id);if(raw.isEmpty())throw new Exception("这条回复没有可核对的聊天扣分记录");
            JSONObject record=new JSONObject(raw),personal=s.personal();double restored=AppealData.apply(personal,who,session,record,WritingData.today(personal).toString(),enabled);
            ContentValues v=new ContentValues();v.put("judgment",record.toString());if(db.update("messages",v,"id=? AND judgment=?",new String[]{""+id,raw})!=1)throw new Exception("这条回复已更新，请刷新后重试");s.savePersonal(personal);db.setTransactionSuccessful();return restored;
        }finally{db.endTransaction();}
    }
    private AppealStore(){}
}
