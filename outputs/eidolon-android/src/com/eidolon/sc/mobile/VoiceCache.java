package com.eidolon.sc.mobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Private, persistent voice library. Completed recordings are deleted only by the user. */
final class VoiceCache {
    private static final Object LOCK = new Object();
    private static long generation;
    private final long openedGeneration;
    private final File dir;
    static final class Entry {
        final String key, text, model;
        final File file;
        final long bytes, created;
        Entry(String key, File file, String text, String model, long created) {
            this.key=key; this.file=file; this.text=text; this.model=model;
            this.bytes=file.length(); this.created=created;
        }
        double seconds() { return bytes / (44100.0 * 2); }
    }
    VoiceCache(File root) throws IOException {
        synchronized (LOCK) {
            dir = new File(root, "voice-pcm-v1");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法创建已保存语音目录");
            openedGeneration = generation;
        }
    }
    static String digest(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (byte v : bytes) b.append(String.format(Locale.ROOT, "%02x", v & 255));
        return b.toString();
    }
    static String key(String model, String request, String apiKey) throws Exception {
        return digest("pcm-v1\n" + model + "\n" + request + "\n" + digest(apiKey));
    }
    private static boolean valid(String key) { return key != null && key.matches("[a-f0-9]{64}"); }
    private File audio(String key) throws IOException {
        if (!valid(key)) throw new IOException("语音标识无效");
        return new File(dir, key + ".pcm");
    }
    File get(String key) throws IOException {
        synchronized (LOCK) {
            File f = audio(key);
            return f.isFile() && f.length()>0 && (f.length()&1)==0 && f.length()<=PcmSpool.MAX_BYTES ? f : null;
        }
    }
    File temporary() throws IOException { synchronized (LOCK) { return File.createTempFile("receiving-", ".part", dir); } }
    void commit(File source, String key) throws IOException { commit(source, key, "", ""); }
    void commit(File source, String key, String text, String model) throws IOException {
        synchronized (LOCK) {
            if (openedGeneration != generation) throw new IOException("保存已取消，请重新生成");
            if (source.length()==0 || (source.length()&1)!=0 || source.length()>PcmSpool.MAX_BYTES)
                throw new IOException("音频不完整");
            File target = audio(key);
            if (!source.renameTo(target)) throw new IOException("无法保存语音，请检查存储空间");
            target.setLastModified(System.currentTimeMillis());
            File pending = null;
            try {
                JSONObject meta = new JSONObject().put("text", text).put("model", model).put("created", target.lastModified());
                pending = File.createTempFile("metadata-", ".part", dir);
                try (FileOutputStream out = new FileOutputStream(pending)) {
                    out.write(meta.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
                }
                if (!pending.renameTo(new File(dir,key+".json"))) throw new IOException("无法保存语音说明");
            } catch (Exception e) {
                // Audio remains saved even if its optional description cannot be written.
            } finally { if (pending!=null) pending.delete(); }
        }
    }
    List<Entry> list() {
        synchronized (LOCK) {
            List<Entry> result = new ArrayList<>();
            File[] files = dir.listFiles((d,n)->n.matches("[a-f0-9]{64}\\.pcm"));
            if (files==null) return result;
            for (File file:files) {
                if (file.length()==0 || (file.length()&1)!=0 || file.length()>PcmSpool.MAX_BYTES) continue;
                String key=file.getName().substring(0,64), text="", model="";
                long created=file.lastModified();
                File meta=new File(dir,key+".json");
                if (meta.isFile() && meta.length()<128*1024) try (FileInputStream in=new FileInputStream(meta);
                        ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] b=new byte[4096]; int n;
                    while((n=in.read(b))!=-1) out.write(b,0,n);
                    JSONObject obj=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                    text=obj.optString("text",""); model=obj.optString("model",""); created=obj.optLong("created",created);
                } catch (Exception ignored) { }
                result.add(new Entry(key,file,text,model,created));
            }
            result.sort((a,b)->Long.compare(b.created,a.created));
            return result;
        }
    }
    boolean delete(String key) throws IOException {
        synchronized (LOCK) {
            File file=audio(key); generation++;
            if (file.exists()&&!file.delete()) throw new IOException("语音删除失败，请重试");
            File meta=new File(dir,key+".json");
            if (meta.exists()&&!meta.delete()) throw new IOException("语音已删除，但说明清理失败");
            return true;
        }
    }
    int clear() throws IOException {
        synchronized (LOCK) {
            generation++;
            File[] files=dir.listFiles((d,n)->n.matches("[a-f0-9]{64}\\.(pcm|json)"));
            if (files==null) return 0;
            int count=0; boolean failed=false;
            for(File file:files) {
                if(file.delete()) { if(file.getName().endsWith(".pcm"))count++; }
                else failed=true;
            }
            if(failed) throw new IOException("部分语音删除失败，请刷新后重试");
            return count;
        }
    }
    void migrate(File oldRoot) throws IOException {
        synchronized (LOCK) {
            File legacy=new File(oldRoot,"voice-pcm-v1");
            if(legacy.equals(dir))return;
            File[] files=legacy.listFiles((d,n)->n.matches("[a-f0-9]{64}\\.pcm"));
            if(files==null)return;
            for(File file:files) {
                if(file.length()==0 || (file.length()&1)!=0 || file.length()>PcmSpool.MAX_BYTES)continue;
                File target=new File(dir,file.getName());
                if(target.isFile() && target.length()>0 && (target.length()&1)==0) {
                    if(!file.delete())throw new IOException("旧语音缓存清理失败");
                } else if(!file.renameTo(target))throw new IOException("旧语音迁移失败，请重试");
            }
        }
    }
    void trim() {
        // No age/budget eviction of saved audio. Only remove abandoned incomplete files.
        synchronized (LOCK) {
            File[] partials=dir.listFiles((d,n)->n.endsWith(".part"));
            long now=System.currentTimeMillis();
            if(partials!=null) for(File f:partials) if(now-f.lastModified()>24L*60*60*1000) f.delete();
        }
    }
}
