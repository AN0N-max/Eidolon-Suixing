package com.eidolon.sc.mobile;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.*;

/** Cache-only preview followed by explicit additive import through the existing media store. */
final class StickerPackImport {
    private static File folder(Context c)throws IOException{File dir=new File(c.getCacheDir(),"sticker-packs");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("表情包缓存不可用");return dir;}
    private static File file(Context c,String token)throws Exception{if(token==null||!token.matches("[a-f0-9-]{36}"))throw new IOException("预览已失效，请重新选择表情包");return new File(folder(c),token+".zip");}
    static JSONObject stage(Context c,Uri uri)throws Exception{
        File dir=folder(c);File[] old=dir.listFiles();if(old!=null)for(File f:old)if(f.getName().matches("[a-f0-9-]{36}\\.zip")&&System.currentTimeMillis()-f.lastModified()>86400000L)f.delete();
        String token=UUID.randomUUID().toString();File target=file(c,token);boolean ready=false;
        try{
            try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(target)){if(in==null)throw new IOException("无法读取所选文件");byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>StickerPack.MAX_ARCHIVE)throw new IOException("表情包超过32MB");out.write(b,0,n);}}
            StickerPack p=StickerPack.inspect(target);p.known(MediaActions.known(c));JSONArray preview=new JSONArray();
            for(int i=0;i<p.stickers.length();i++){
                JSONObject s=p.stickers.getJSONObject(i);byte[] raw=p.image(i);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,o);
                if(o.outWidth<1||o.outHeight<1||(long)o.outWidth*o.outHeight>80000000L||!Arrays.asList("image/png","image/jpeg","image/webp","image/gif").contains(o.outMimeType))throw new IOException("图片损坏、格式不支持或像素过大："+s.getString("name"));
                JSONObject row=new JSONObject(s.toString());row.remove("sha256");row.remove("file");row.put("preview","/sticker-pack/"+token+"/"+i+"/thumb.png");preview.put(row);
            }
            int disabling=0;try(ChatStore db=new ChatStore(c);Cursor rows=db.getReadableDatabase().rawQuery("SELECT body FROM stickers",null)){while(rows.moveToNext())if(StickerPack.shouldDisable(new JSONObject(rows.getString(0)),p.manifest.optJSONArray("disableSeries")))disabling++;}
            ready=true;return new JSONObject().put("token",token).put("name",p.manifest.getString("name")).put("description",p.manifest.optString("description")).put("items",preview).put("profiles",p.profiles.length()).put("disableSeries",p.manifest.optJSONArray("disableSeries")==null?new JSONArray():p.manifest.getJSONArray("disableSeries")).put("disableCount",disabling);
        }finally{if(!ready)target.delete();}
    }
    static InputStream thumbnail(Context c,String token,int index)throws Exception{
        // Do not rehash the full archive for each thumbnail. Inspect validates the file once at stage/commit.
        File zip=file(c,token);byte[] data;
        try(java.util.zip.ZipFile z=new java.util.zip.ZipFile(zip)){
            JSONObject root;try(InputStream in=z.getInputStream(z.getEntry("manifest.json"))){root=new JSONObject(new String(StickerPack.read(in,1024*1024),java.nio.charset.StandardCharsets.UTF_8));}
            JSONArray rows=root.getJSONArray("stickers");if(index<0||index>=rows.length())throw new IOException("预览不存在");String path=rows.getJSONObject(index).getString("file");StickerPack.path(path);
            try(InputStream in=z.getInputStream(z.getEntry(path))){data=StickerPack.read(in,MediaStore.MAX_IMPORT);}
        }
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);if(o.outWidth<1||o.outHeight<1)throw new IOException("预览图片无效");o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>256)o.inSampleSize*=2;
        Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length,o);if(b==null)throw new IOException("无法生成预览");try{ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);return new ByteArrayInputStream(out.toByteArray());}finally{b.recycle();}
    }
    static JSONObject commit(Context c,ChatStore s,JSONObject args)throws Exception{
        File archive=file(c,args.getString("token"));StickerPack pack=StickerPack.inspect(archive);Set<String> known=MediaActions.known(c);pack.known(known);
        SQLiteDatabase db=s.getWritableDatabase();Set<String> created=new HashSet<>();int added=0,skipped=0,updated=0,profiles=0,disabled=0;boolean success=false;
        db.beginTransaction();try{
            for(int i=0;i<pack.stickers.length();i++){
                JSONObject spec=pack.stickers.getJSONObject(i),image=MediaStore.importImage(c,s,pack.image(i));String hash=image.getString("asset"),id="s-"+hash;
                // Added assets are tracked by importImage before their first registration below.
                if(image.optBoolean("newAsset"))created.add(hash);
                JSONObject old=null;try{old=MediaStore.sticker(s,id);}catch(IOException missing){}
                if(old!=null){JSONObject merged=StickerPack.merge(old,spec,args.optBoolean("updateExisting"));MediaStore.saveSticker(s,merged);if(args.optBoolean("updateExisting"))updated++;else skipped++;continue;}
                JSONObject row=MediaStore.addSticker(s,hash);for(String k:StickerPack.TEXT)row.put(k,spec.optString(k));
                row.put("enabled",spec.optBoolean("enabled",true)).put("favorite",spec.optBoolean("favorite",false)).put("characters",spec.optJSONArray("characters")==null?new JSONArray():spec.getJSONArray("characters"));
                if(spec.has("series"))row.put("series",spec.getString("series"));row.put("packId",pack.manifest.getString("id"));MediaStore.saveSticker(s,row);added++;
            }
            if(args.optBoolean("profiles",true))for(int i=0;i<pack.profiles.length();i++){
                JSONObject p=pack.profiles.getJSONObject(i);String who=p.getString("character");boolean exists;try(Cursor r=db.rawQuery("SELECT 1 FROM media_profiles WHERE character=?",new String[]{who})){exists=r.moveToFirst();}
                if(!exists){MediaStore.profile(s,who,p.getJSONObject("profile"));profiles++;}else{JSONObject old=MediaStore.profile(s,who),merged=StickerPack.profileSupplement(old,p.getJSONObject("profile"));if(!old.toString().equals(merged.toString())){MediaStore.profile(s,who,merged);profiles++;}}
            }
            if(args.optBoolean("disableExcluded")){List<JSONObject> disabling=new ArrayList<>();try(Cursor rows=db.rawQuery("SELECT body FROM stickers",null)){while(rows.moveToNext()){JSONObject row=new JSONObject(rows.getString(0));if(StickerPack.shouldDisable(row,pack.manifest.optJSONArray("disableSeries")))disabling.add(row);}}for(JSONObject row:disabling){row.put("enabled",false);MediaStore.saveSticker(s,row);disabled++;}}
            db.setTransactionSuccessful();success=true;
        }finally{
            db.endTransaction();if(!success){db.beginTransaction();try{for(String hash:created)if(!MediaStore.exists(s,hash)){MediaStore.file(c,hash,false).delete();MediaStore.file(c,hash,true).delete();}db.setTransactionSuccessful();}finally{db.endTransaction();}}
        }
        archive.delete();return new JSONObject().put("added",added).put("skipped",skipped).put("updated",updated).put("profiles",profiles).put("disabled",disabled);
    }
    static void cancel(Context c,String token)throws Exception{file(c,token).delete();}
    private StickerPackImport(){}
}
