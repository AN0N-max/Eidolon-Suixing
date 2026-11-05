package com.eidolon.sc.mobile;
import android.content.*;import org.json.*;import java.io.IOException;import java.util.*;

/** Non-UI media actions run on the existing native worker, with no browser network access. */
final class MediaActions {
    static Set<String> known(Context c)throws Exception{Set<String> out=new HashSet<>();JSONArray a=WritingEngine.characters(c);for(int i=0;i<a.length();i++)out.add(a.getJSONObject(i).getString("id"));return out;}
    static String draftKey(String session){return "mediaDraft/"+session;}
    static JSONArray draft(Context c,String session){return MediaData.array(c.getSharedPreferences("settings",0).getString(draftKey(session),"[]"));}
    static void draft(Context c,String session,JSONArray a)throws Exception{if(!c.getSharedPreferences("settings",0).edit().putString(draftKey(session),a.toString()).commit())throw new IOException("图片草稿保存失败");}
    static Object run(Context c,ChatStore s,String action,JSONObject a,ModelClient client)throws Exception{
        if(action.equals("mediaLibrary"))return MediaStore.library(s,MediaData.cut(a.optString("query"),200),a.optString("filter"),Math.max(0,a.optInt("offset")));
        if(action.equals("mediaStickerDetail"))return MediaStore.stickerDetail(s,a.optString("id"),a.optString("asset"));
        if(action.equals("mediaUpdate"))return MediaStore.updateSticker(s,a,known(c));
        if(action.equals("mediaLibraryAdd"))return MediaStore.addSticker(s,a.getString("asset"));
        if(action.equals("mediaSettings"))return MediaContext.settings(c,a);
        if(action.equals("mediaAnalyze"))return MediaContext.analyzeOne(c,s,a.getString("asset"),(u,k,b)->client.chat(u,k,b,ignored->{}));
        if(action.equals("mediaProfile")){String who=a.getString("character");if(!known(c).contains(who))throw new IOException("角色不存在");return MediaStore.profile(s,who,a);}
        if(action.equals("mediaDraft")){String session=a.getString("session");if(a.has("clear"))draft(c,session,new JSONArray());else if(a.has("content"))draft(c,session,MediaStore.userBlocks(s,a.getJSONArray("content")));return draft(c,session);}
        if(action.equals("mediaCleanup")){
            // Delete only assets not referenced by history, catalogue, or any persisted preview draft.
            android.database.sqlite.SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{Set<String> referenced=new HashSet<>();try(android.database.Cursor r=s.getReadableDatabase().rawQuery("SELECT content FROM messages WHERE content<>'[]'",null)){while(r.moveToNext()){JSONArray blocks=MediaData.array(r.getString(0));for(int i=0;i<blocks.length();i++)referenced.add(blocks.getJSONObject(i).optString("asset"));}}
            try(android.database.Cursor r=s.getReadableDatabase().rawQuery("SELECT asset FROM stickers",null)){while(r.moveToNext())referenced.add(r.getString(0));}
            for(Map.Entry<String,?> entry:c.getSharedPreferences("settings",0).getAll().entrySet())if(entry.getKey().startsWith("mediaDraft/")){JSONArray blocks=MediaData.array(String.valueOf(entry.getValue()));for(int i=0;i<blocks.length();i++)referenced.add(blocks.getJSONObject(i).optString("asset"));}
            List<String> remove=new ArrayList<>();try(android.database.Cursor r=s.getReadableDatabase().rawQuery("SELECT id FROM media_assets",null)){while(r.moveToNext())if(!referenced.contains(r.getString(0)))remove.add(r.getString(0));}
            for(String id:remove){s.getWritableDatabase().delete("media_assets","id=?",new String[]{id});java.io.File image=MediaStore.file(c,id,false),thumb=MediaStore.file(c,id,true);image.delete();thumb.delete();}db.setTransactionSuccessful();return new JSONObject().put("removed",remove.size());}finally{db.endTransaction();}
        }
        throw new IOException("未知媒体操作");
    }
}
