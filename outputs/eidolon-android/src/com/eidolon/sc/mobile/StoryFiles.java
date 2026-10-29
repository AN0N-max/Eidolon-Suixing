package com.eidolon.sc.mobile;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Bounded, in-memory importer for original SCTranslationData CSV and game JSON. */
final class StoryFiles {
    static final int MAX_BYTES=32*1024*1024,MAX_FILE=4*1024*1024,MAX_EPISODES=2000;
    static final String[] FAILURES={"produce_events/7\\d{3}001(02|04|06|08)","produce_events/6\\d{3}001(02|07)","produce_events/6\\d{3}003(03|04|05|06|09|10)","produce_events/1\\d{3}003(071|072|08)","produce_communication_auditions/\\d+2","produce_communication_promise_results/\\d+2"};
    static boolean failure(String id){for(String p:FAILURES)if(id.matches(p))return true;return false;}
    static byte[] read(InputStream in,int max)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("剧情文件超出大小限制");out.write(b,0,n);}return out.toByteArray();}
    static JSONArray load(InputStream in,String name,JSONArray characters)throws Exception{
        JSONArray result=new JSONArray();if(name.toLowerCase(Locale.ROOT).endsWith(".zip")){
            ZipInputStream zip=new ZipInputStream(in);ZipEntry entry;int total=0,files=0;
            while((entry=zip.getNextEntry())!=null){if(++files>5000)throw new Exception("ZIP 文件数量过多");if(entry.isDirectory())continue;
                byte[] bytes=read(zip,Math.min(MAX_FILE,MAX_BYTES-total));total+=bytes.length;
                String path=entry.getName().replace('\\','/');if(path.endsWith(".csv")||path.endsWith(".json")){JSONObject e=parse(bytes,path,characters);if(e!=null)result.put(e);}
                if(result.length()>MAX_EPISODES)throw new Exception("每次最多导入 2000 段剧情");zip.closeEntry();
            }
        }else{JSONObject e=parse(read(in,MAX_FILE),name,characters);if(e!=null)result.put(e);}return result;
    }
    static JSONObject parse(byte[] bytes,String name,JSONArray characters)throws Exception{
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();if(text.startsWith("\ufeff"))text=text.substring(1);
        name=name.replace('\\','/');String base=name.substring(name.lastIndexOf('/')+1),id="",format=base.endsWith(".csv")?"csv":"json";
        JSONArray lines=new JSONArray();
        if(format.equals("csv")){
            for(List<String> row:csv(text)){while(row.size()<4)row.add("");String kind=row.get(0).trim();
                if(kind.equals("info")){id=row.get(1).trim().replaceAll("\\.json$","");continue;}
                if(Arrays.asList("id","select","译者").contains(kind)||row.get(2).trim().isEmpty())continue;
                lines.put(new JSONObject().put("speaker",row.get(1).trim()).put("text",row.get(2).trim().replace("\\n","\n")).put("translation",row.get(3).trim().replace("\\n","\n")));
            }if(id.isEmpty())id="csv/"+name.replaceAll("\\.csv$","");
        }else{
            Matcher m=Pattern.compile("(?:^|/)([a-z]+(?:_[a-z]+)*)[/_](\\d{8,})$").matcher(name.replaceAll("\\.json$",""));if(!m.find())return null;id=m.group(1)+"/"+m.group(2);
            JSONArray frames=new JSONArray(text);for(int i=0;i<frames.length();i++){JSONObject f=frames.optJSONObject(i);if(f==null||!(f.opt("text") instanceof String)||f.getString("text").trim().isEmpty())continue;
                lines.put(new JSONObject().put("speaker",f.opt("speaker") instanceof String?f.getString("speaker").trim():"").put("text",f.getString("text").trim().replace("\r\n","\n")).put("translation",""));}
        }
        if(failure(id))return new JSONObject().put("id",id).put("removed",true);
        if(lines.length()==0)return null;
        Set<String> cast=new TreeSet<>();StringBuilder body=new StringBuilder();
        for(int i=0;i<lines.length();i++){JSONObject line=lines.getJSONObject(i);String speaker=line.getString("speaker");body.append(speaker).append(": ").append(line.getString("text")).append('\n');if(!line.getString("translation").isEmpty())body.append(line.getString("translation")).append('\n');
            for(String who:speaker.split("[＆&]"))for(int j=0;j<characters.length();j++){JSONObject c=characters.getJSONObject(j);JSONArray names=c.optJSONArray("speakers");if(names!=null)for(int k=0;k<names.length();k++)if(who.trim().equals(names.getString(k)))cast.add(c.getString("id"));}
        }
        JSONObject e=new JSONObject().put("id",id).put("title",base.replaceAll("\\.(csv|json)$","")).put("format",format).put("cast",new JSONArray(cast)).put("text",body.toString());validate(e);return e;
    }
    static List<List<String>> csv(String text)throws Exception{
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"'){if(quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else if(quoted||cell.length()==0)quoted=!quoted;else cell.append(c);}
            else if(c==','&&!quoted){row.add(cell.toString());cell.setLength(0);}else if((c=='\n'||c=='\r')&&!quoted){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(cell.toString());cell.setLength(0);rows.add(row);row=new ArrayList<>();}else cell.append(c);}
        if(quoted)throw new Exception("CSV 引号未闭合");if(cell.length()>0||!row.isEmpty()){row.add(cell.toString());rows.add(row);}return rows;
    }
    static void validate(JSONObject e)throws Exception{
        String id=e.getString("id");if(id.isEmpty()||id.length()>500||e.getString("title").length()>500||e.getString("text").isEmpty()||e.getString("text").length()>200000||failure(id))throw new Exception("剧情格式或长度无效");
        if(!Arrays.asList("csv","json").contains(e.getString("format")))throw new Exception("剧情格式无效");JSONArray cast=e.getJSONArray("cast");if(cast.length()>100)throw new Exception("剧情角色数量无效");for(int i=0;i<cast.length();i++)if(!cast.getString(i).matches("[a-z0-9_-]{1,80}"))throw new Exception("剧情角色无效");
    }
    static String excerpt(String text,String query,int limit){if(text.length()<=limit)return text;int best=0;double max=-1;for(int i=0;i<text.length();i+=500){double s=Recall.score(query,text.substring(i,Math.min(text.length(),i+1000)));if(s>max){max=s;best=i;}}int start=Math.max(0,best-150);return text.substring(start,Math.min(text.length(),start+limit));}
}
