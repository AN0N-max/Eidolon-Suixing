package com.eidolon.sc.mobile;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Offline sticker package v1/v2. No extraction paths, executable content or remote downloads. */
final class StickerPack {
    static final int MAX_ARCHIVE=32*1024*1024, MAX_TOTAL=48*1024*1024, MAX_ITEMS=400;
    static final String[] TEXT={"name","category","description","emotion","intent","tone","ocr","meme"};
    final File archive;
    final JSONObject manifest;
    final JSONArray stickers,profiles;
    private StickerPack(File file,JSONObject manifest)throws Exception{
        archive=file;this.manifest=manifest;stickers=manifest.getJSONArray("stickers");profiles=manifest.optJSONArray("profiles")==null?new JSONArray():manifest.getJSONArray("profiles");
    }
    static void path(String name)throws IOException{
        if(name==null||!name.matches("[A-Za-z0-9][A-Za-z0-9_./-]{0,180}")||name.contains("//"))throw new IOException("包内文件名无效");
        for(String part:name.split("/"))if(part.equals(".")||part.equals(".."))throw new IOException("包内路径无效");
    }
    static byte[] read(InputStream in,int limit)throws IOException{
        if(in==null)throw new IOException("文件不可读取");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n,total=0;
        while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("表情包文件超过大小限制");out.write(b,0,n);}return out.toByteArray();
    }
    static String string(JSONObject o,String key,int max,boolean required)throws Exception{
        if(!o.has(key)){if(required)throw new IOException("表情包缺少字段："+key);return "";}
        Object value=o.get(key);if(!(value instanceof String)||((String)value).length()>max||required&&((String)value).trim().isEmpty())throw new IOException("表情包字段无效："+key);return (String)value;
    }
    static void strings(JSONObject o,String key,int count,int length)throws Exception{
        if(!o.has(key))return;JSONArray a=o.getJSONArray(key);if(a.length()>count)throw new IOException("表情包列表过长："+key);
        Set<String> seen=new HashSet<>();for(int i=0;i<a.length();i++){Object v=a.get(i);if(!(v instanceof String)||((String)v).isEmpty()||((String)v).length()>length||!seen.add((String)v))throw new IOException("表情包列表无效："+key);}
    }
    static StickerPack inspect(File archive)throws Exception{
        if(!archive.isFile()||archive.length()==0||archive.length()>MAX_ARCHIVE)throw new IOException("请选择不超过32MB的表情包ZIP文件");
        try(ZipFile zip=new ZipFile(archive)){
            Map<String,ZipEntry> files=new HashMap<>();Enumeration<? extends ZipEntry> entries=zip.entries();int count=0,total=0;
            while(entries.hasMoreElements()){
                ZipEntry e=entries.nextElement();if(++count>MAX_ITEMS+32)throw new IOException("包内文件过多");path(e.getName());if(e.isDirectory())continue;
                if(files.put(e.getName(),e)!=null)throw new IOException("包内文件重名");
                if(!e.getName().equals("manifest.json")&&!e.getName().matches("images/[A-Za-z0-9_-]+\\.(png|jpg|jpeg|webp|gif)"))throw new IOException("包内仅允许manifest.json和images目录中的图片");
            }
            ZipEntry manifest=files.get("manifest.json");if(manifest==null)throw new IOException("没有找到manifest.json，请选择Eidolon表情包");
            JSONObject root;try(InputStream in=zip.getInputStream(manifest)){byte[] b=read(in,1024*1024);total+=b.length;root=new JSONObject(new String(b,StandardCharsets.UTF_8));}
            if(!"eidolon-sticker-pack".equals(root.optString("format"))||!(root.get("version") instanceof Number)||(root.getDouble("version")!=1&&root.getDouble("version")!=2))throw new IOException("不支持的表情包格式或版本");
            strings(root,"disableSeries",20,80);if(root.getInt("version")==1&&root.has("disableSeries"))throw new IOException("停用旧系列需要version 2表情包及1.24以上应用");
            string(root,"id",100,true);string(root,"name",120,true);string(root,"description",2000,false);
            StickerPack pack=new StickerPack(archive,root);if(pack.stickers.length()<1||pack.stickers.length()>MAX_ITEMS)throw new IOException("每包应有1至400张表情");
            Set<String> used=new HashSet<>(),hashes=new HashSet<>();used.add("manifest.json");
            for(int i=0;i<pack.stickers.length();i++){
                JSONObject s=pack.stickers.getJSONObject(i);if(s.toString().length()>9000)throw new IOException("单张表情参数过长");String file=string(s,"file",180,true);path(file);
                ZipEntry image=files.get(file);if(image==null||!file.startsWith("images/")||!used.add(file))throw new IOException("表情图片缺失或重复引用");
                String hash=string(s,"sha256",64,true);if(!MediaData.hash(hash)||!hashes.add(hash))throw new IOException("图片哈希无效或包内有重复图片");
                for(String key:TEXT)string(s,key,key.equals("description")||key.equals("ocr")||key.equals("meme")?1500:120,key.equals("name"));
                string(s,"series",80,false);if(root.getInt("version")==2)string(s,"series",80,true);if(MediaData.contains(root.optJSONArray("disableSeries"),s.optString("series")))throw new IOException("包内图片属于本包声明停用的系列");
                strings(s,"characters",60,100);for(String key:new String[]{"enabled","favorite"})if(s.has(key)&&!(s.get(key) instanceof Boolean))throw new IOException("表情开关无效");
                byte[] data;try(InputStream in=zip.getInputStream(image)){data=read(in,MediaStoreLimits.MAX_INPUT);}
                total+=data.length;if(total>MAX_TOTAL)throw new IOException("表情包解压内容超过48MB");if(!MediaData.hash(data).equals(hash))throw new IOException("图片校验失败："+s.getString("name"));
            }
            if(used.size()!=files.size())throw new IOException("包内存在未登记文件");
            if(pack.profiles.length()>60)throw new IOException("角色偏好过多");Set<String> roles=new HashSet<>();
            for(int i=0;i<pack.profiles.length();i++){
                JSONObject row=pack.profiles.getJSONObject(i);String who=string(row,"character",100,true);if(!roles.add(who))throw new IOException("角色偏好重复");JSONObject p=row.getJSONObject("profile");
                if(!Arrays.asList("off","low","normal","high").contains(string(p,"frequency",10,true)))throw new IOException("表情频率无效");
                for(String k:new String[]{"preferred","blocked","emotions","preferredSeries","blockedSeries"})strings(p,k,20,80);if(root.getInt("version")==1&&(p.has("preferredSeries")||p.has("blockedSeries")))throw new IOException("系列偏好需要version 2表情包及1.24以上应用");string(p,"style",1000,false);
            }
            return pack;
        }
    }
    void known(Set<String> known)throws Exception{
        for(int i=0;i<stickers.length();i++){JSONArray a=stickers.getJSONObject(i).optJSONArray("characters");if(a!=null)for(int j=0;j<a.length();j++)if(!known.contains(a.getString(j)))throw new IOException("表情权限中含本机不存在的角色");}
        for(int i=0;i<profiles.length();i++)if(!known.contains(profiles.getJSONObject(i).getString("character")))throw new IOException("偏好中含本机不存在的角色");
    }
    byte[] image(int index)throws Exception{
        if(index<0||index>=stickers.length())throw new IOException("表情编号无效");JSONObject s=stickers.getJSONObject(index);
        try(ZipFile zip=new ZipFile(archive);InputStream in=zip.getInputStream(zip.getEntry(s.getString("file")))){byte[] data=read(in,MediaStoreLimits.MAX_INPUT);if(!MediaData.hash(data).equals(s.getString("sha256")))throw new IOException("图片内容已变化，请重新选择包");return data;}
    }
    static JSONObject merge(JSONObject existing,JSONObject incoming,boolean update)throws Exception{
        JSONObject row=new JSONObject(existing.toString());if(incoming.has("series")&&(update||!row.has("series")))row.put("series",incoming.getString("series"));if(!update)return row;
        // Updating semantics never resets user permissions, favorites, enablement or usage history.
        for(String key:TEXT)row.put(key,incoming.optString(key));return row;
    }
    static JSONObject profileSupplement(JSONObject existing,JSONObject incoming)throws Exception{
        if(existing==null)return new JSONObject(incoming.toString());JSONObject row=new JSONObject(existing.toString());
        for(String key:new String[]{"preferredSeries","blockedSeries"})if(!row.has(key)&&incoming.has(key))row.put(key,new JSONArray(incoming.getJSONArray(key).toString()));return row;
    }
    static boolean shouldDisable(JSONObject row,JSONArray excluded){return row.optBoolean("enabled",true)&&MediaData.contains(excluded,MediaData.series(row));}
    /** Kept Android-independent so the production archive parser runs in JVM regression tests. */
    private static final class MediaStoreLimits {static final int MAX_INPUT=12*1024*1024;}
}
