package com.eidolon.sc.mobile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Executes the production ZIP parser and semantic merge, without Android or provider calls. */
public final class StickerPackTests {
    static int checks;
    static final byte[] IMAGE=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jgFEAAAAASUVORK5CYII=");
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    static JSONObject root()throws Exception{return new JSONObject().put("format","eidolon-sticker-pack").put("version",1).put("id","test-pack").put("name","测试包").put("stickers",new JSONArray().put(new JSONObject().put("file","images/test.png").put("sha256",MediaData.hash(IMAGE)).put("name","收到").put("description","点头赞同").put("characters",new JSONArray().put("mano")))).put("profiles",new JSONArray().put(new JSONObject().put("character","mano").put("profile",new JSONObject().put("frequency","low").put("preferred",new JSONArray().put("日常问候")))));}
    static File zip(JSONObject root,Map<String,byte[]> extras)throws Exception{File f=File.createTempFile("eidolon-pack-test-",".zip");try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){z.putNextEntry(new ZipEntry("manifest.json"));z.write(root.toString().getBytes(StandardCharsets.UTF_8));z.closeEntry();Map<String,byte[]> files=new LinkedHashMap<>();files.put("images/test.png",IMAGE);files.putAll(extras);for(Map.Entry<String,byte[]> e:files.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}}return f;}
    static void reject(JSONObject root,Map<String,byte[]> extras,String name)throws Exception{File f=zip(root,extras);try{boolean failed=false;try{StickerPack.inspect(f);}catch(Exception expected){failed=true;}check(failed,name);}finally{f.delete();}}
    static void reject(JSONObject root,String name)throws Exception{reject(root,Collections.emptyMap(),name);}
    public static void main(String[] args)throws Exception{
        File valid=zip(root(),Collections.emptyMap());try{StickerPack p=StickerPack.inspect(valid);p.known(Collections.singleton("mano"));check(p.stickers.length()==1&&Arrays.equals(p.image(0),IMAGE),"valid archive verifies and returns exact image bytes");boolean bad=false;try{p.image(-1);}catch(Exception expected){bad=true;}check(bad,"preview index is bounded");bad=false;try{p.known(Collections.singleton("hiori"));}catch(Exception expected){bad=true;}check(bad,"unknown role permissions are rejected before import");}finally{valid.delete();}
        reject(root().put("version",3),"unsupported schema version rejected");reject(root().put("format","other"),"foreign ZIP manifest rejected");reject(root().put("name",""),"blank package name rejected");
        JSONObject d=root();d.getJSONArray("stickers").getJSONObject(0).put("file","../outside.png");reject(d,"path traversal rejected");
        reject(root(),Collections.singletonMap("images/../outside.png",IMAGE),"unsafe unreferenced ZIP path rejected");
        reject(root(),Collections.singletonMap("extra.txt",new byte[]{1}),"undeclared content rejected");
        d=root();d.getJSONArray("stickers").getJSONObject(0).put("file","images/missing.png");reject(d,"missing image rejected");
        d=root();d.getJSONArray("stickers").getJSONObject(0).put("sha256",String.join("",Collections.nCopies(64,"a")));reject(d,"tampered image digest rejected");
        d=root();d.getJSONArray("stickers").put(new JSONObject(d.getJSONArray("stickers").getJSONObject(0).toString()));reject(d,"duplicate image reference rejected");
        d=root();d.getJSONArray("stickers").getJSONObject(0).put("enabled","true");reject(d,"non-boolean enablement rejected");
        d=root();d.getJSONArray("stickers").getJSONObject(0).put("description",String.join("",Collections.nCopies(1501,"长")));reject(d,"overlong semantic field rejected");
        d=root();d.getJSONArray("profiles").getJSONObject(0).getJSONObject("profile").put("frequency","always");reject(d,"unsupported profile frequency rejected");
        d=root();d.getJSONArray("profiles").put(new JSONObject(d.getJSONArray("profiles").getJSONObject(0).toString()));reject(d,"duplicate role profile rejected");
        d=root();d.getJSONArray("stickers").getJSONObject(0).put("characters",new JSONArray().put("mano").put("mano"));reject(d,"duplicate role permission rejected");
        reject(root(),Collections.singletonMap("images/test.png",new byte[12*1024*1024+1]),"expanded image size limit enforced while reading");
        File huge=File.createTempFile("eidolon-size-test-",".zip");try{try(RandomAccessFile f=new RandomAccessFile(huge,"rw")){f.setLength(StickerPack.MAX_ARCHIVE+1L);}boolean fail=false;try{StickerPack.inspect(huge);}catch(Exception expected){fail=true;}check(fail,"archive size bound checked before ZIP parsing");}finally{huge.delete();}
        JSONObject v2=root().put("version",2).put("disableSeries",new JSONArray().put("旧系列"));v2.getJSONArray("stickers").getJSONObject(0).put("series","大耳狗");v2.getJSONArray("profiles").getJSONObject(0).getJSONObject("profile").put("preferredSeries",new JSONArray().put("大耳狗")).put("blockedSeries",new JSONArray().put("旧系列"));
        File seriesPack=zip(v2,Collections.emptyMap());try{StickerPack p=StickerPack.inspect(seriesPack);check(p.manifest.getInt("version")==2&&p.stickers.getJSONObject(0).getString("series").equals("大耳狗"),"v2 retains explicit series preferences and excluded-series metadata");}finally{seriesPack.delete();}
        d=new JSONObject(v2.toString());d.getJSONArray("stickers").getJSONObject(0).remove("series");reject(d,"v2 requires explicit nonempty series");
        d=new JSONObject(v2.toString());d.getJSONArray("stickers").getJSONObject(0).put("series","旧系列");reject(d,"included and excluded series cannot overlap");
        d=new JSONObject(v2.toString());d.put("disableSeries",new JSONArray().put("旧系列").put("旧系列"));reject(d,"duplicate excluded series rejected");
        d=new JSONObject(v2.toString());d.getJSONArray("profiles").getJSONObject(0).getJSONObject("profile").put("preferredSeries",new JSONArray().put(7));reject(d,"non-string series preference rejected");
        d=new JSONObject(v2.toString()).put("version",1);reject(d,"v1 cannot silently ignore v2 disable-series instructions");
        d=root();d.getJSONArray("profiles").getJSONObject(0).getJSONObject("profile").put("preferredSeries",new JSONArray().put("大耳狗"));reject(d,"v1 cannot silently ignore v2 preferred series");
        JSONObject old=new JSONObject().put("id","s-existing").put("name","旧名").put("description","旧语义").put("enabled",false).put("favorite",true).put("characters",new JSONArray().put("mano")).put("uses",123).put("lastUsed",456);
        JSONObject incoming=new JSONObject().put("name","新名").put("description","新语义").put("enabled",true).put("characters",new JSONArray()).put("uses",0);
        check(StickerPack.merge(old,incoming,false).toString().equals(old.toString()),"skip duplicate preserves all existing fields");JSONObject merged=StickerPack.merge(old,incoming,true);check(merged.getString("name").equals("新名")&&merged.getString("description").equals("新语义"),"explicit update replaces semantics");check(!merged.getBoolean("enabled")&&merged.getBoolean("favorite")&&merged.getInt("uses")==123&&merged.getInt("lastUsed")==456&&merged.getJSONArray("characters").getString(0).equals("mano"),"semantic update preserves permission favorite enablement and statistics");check(old.getString("name").equals("旧名"),"merge does not mutate source row");
        if(args.length>0){StickerPack p=StickerPack.inspect(new File(args[0]));check(p.stickers.length()==(args.length>1?Integer.parseInt(args[1]):144)&&p.profiles.length()==29,"delivered curated package passes production parser with all expected images and 29 profiles");}
        System.out.println("Sticker package checks: "+checks);
    }
}
