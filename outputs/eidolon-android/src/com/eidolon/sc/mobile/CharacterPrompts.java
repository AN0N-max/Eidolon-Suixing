package com.eidolon.sc.mobile;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.InputStream;
import java.util.*;

/** Local per-character overrides. Built-in assets are never edited. */
final class CharacterPrompts {
    static final int LIMIT=20000;
    private static JSONArray defaults;
    static synchronized JSONObject original(Context c,String who)throws Exception{
        if(defaults==null)try(InputStream in=c.getAssets().open("characters.json")){defaults=new JSONArray(new String(StoryFiles.read(in,4*1024*1024),"UTF-8"));}
        for(int i=0;i<defaults.length();i++)if(who.equals(defaults.getJSONObject(i).getString("id")))return new JSONObject(defaults.getJSONObject(i).toString());
        throw new Exception("未知角色");
    }
    private static JSONObject row(JSONObject personal,String who){JSONObject all=personal.optJSONObject("rolePrompts");return all==null?null:all.optJSONObject(who);}
    static JSONObject apply(JSONObject original,JSONObject personal)throws Exception{
        JSONObject out=new JSONObject(original.toString()),r=row(personal,original.getString("id"));
        if(r!=null)for(String key:new String[]{"sheet","facts"})if(r.has(key))out.put(key,r.getString(key));
        return out;
    }
    static JSONObject view(JSONObject original,JSONObject personal)throws Exception{
        JSONObject effective=apply(original,personal),r=row(personal,original.getString("id"));
        return new JSONObject().put("character",original.getString("id")).put("name",original.getString("name"))
            .put("sheet",effective.optString("sheet")).put("facts",effective.optString("facts"))
            .put("defaultSheet",original.optString("sheet")).put("defaultFacts",original.optString("facts"))
            .put("revision",r==null?0:r.optLong("revision")).put("updatedAt",r==null?0:r.optLong("updatedAt"))
            .put("modified",r!=null&&(r.has("sheet")||r.has("facts"))).put("limit",LIMIT);
    }
    static void validate(JSONObject personal,Set<String> known)throws Exception{
        if(!personal.has("rolePrompts"))return;JSONObject all=personal.getJSONObject("rolePrompts");
        if(all.length()>100||all.toString().length()>1500000)throw new Exception("角色提示词总量过大");
        for(Iterator<String> it=all.keys();it.hasNext();){String who=it.next();if(!who.matches("[a-z0-9_-]{1,80}")||known!=null&&!known.contains(who))throw new Exception("提示词含未知角色");
            JSONObject r=all.getJSONObject(who);Object revision=r.get("revision"),at=r.get("updatedAt");
            if(!(revision instanceof Number)||((Number)revision).doubleValue()!=((Number)revision).longValue()||((Number)revision).longValue()<1||((Number)revision).longValue()>1000000000L)throw new Exception("提示词版本无效");
            if(!(at instanceof Number)||((Number)at).doubleValue()!=((Number)at).longValue()||((Number)at).longValue()<0||((Number)at).longValue()>253402300799999L)throw new Exception("提示词时间无效");
            for(Iterator<String> keys=r.keys();keys.hasNext();){String key=keys.next();if(!Arrays.asList("revision","updatedAt","sheet","facts").contains(key))throw new Exception("提示词字段无效");}
            for(String key:new String[]{"sheet","facts"})if(r.has(key))text(r.get(key),key);
        }
    }
    private static String text(Object value,String key)throws Exception{
        if(!(value instanceof String))throw new Exception("提示词必须是文本");String s=(String)value;
        if(s.length()>LIMIT||s.indexOf('\0')>=0||key.equals("sheet")&&s.trim().isEmpty())throw new Exception(key.equals("sheet")?"角色设定应为1–20000字":"补充资料最多20000字");return s;
    }
    /** Pure operation for validation and optimistic revision checks before the transaction saves. */
    static JSONObject change(JSONObject personal,JSONObject original,JSONObject request,boolean reset,long now)throws Exception{
        JSONObject out=new JSONObject(personal.toString());String who=original.getString("id");JSONObject current=view(original,out);
        Object expected=request.get("revision");if(!(expected instanceof Number)||((Number)expected).doubleValue()!=current.getLong("revision"))throw new Exception("角色提示词已更新，请重新打开后再编辑");
        String sheet=reset?original.optString("sheet"):text(request.get("sheet"),"sheet"),facts=reset?original.optString("facts"):text(request.get("facts"),"facts");
        if(sheet.equals(current.getString("sheet"))&&facts.equals(current.getString("facts")))return out;
        JSONObject all=out.optJSONObject("rolePrompts");if(all==null){all=new JSONObject();out.put("rolePrompts",all);}
        JSONObject next=new JSONObject().put("revision",current.getLong("revision")+1).put("updatedAt",now);
        if(!sheet.equals(original.optString("sheet")))next.put("sheet",sheet);if(!facts.equals(original.optString("facts")))next.put("facts",facts);all.put(who,next);
        PersonalData.validate(out);return out;
    }
    static JSONObject save(Context c,ChatStore store,JSONObject request,boolean reset)throws Exception{
        JSONObject base=original(c,request.getString("character"));SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{JSONObject next=change(store.personal(),base,request,reset,System.currentTimeMillis());store.savePersonal(next);db.setTransactionSuccessful();return view(base,next);}finally{db.endTransaction();}
    }
    private CharacterPrompts(){}
}
