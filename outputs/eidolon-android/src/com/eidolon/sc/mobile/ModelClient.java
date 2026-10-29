package com.eidolon.sc.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.List;

final class ModelClient {
    /** Distinguish provider truncation from network, credentials and JSON failures. */
    static final class OutputLimitException extends IOException {
        OutputLimitException(){super("回复达到服务的长度限制，请缩短问题后重试");}
    }
    interface Listener { void update(String text); }
    volatile boolean cancelled;
    volatile HttpURLConnection connection;
    void cancel() { cancelled = true; HttpURLConnection c = connection; if (c != null) c.disconnect(); }
    static String endpointBase(String address) throws Exception {
        String value=address.trim();while(value.endsWith("/"))value=value.substring(0,value.length()-1);
        URI uri=new URI(value);
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
            throw new Exception("接口地址须为 HTTPS，且不能包含账号、查询参数或片段");
        return value;
    }
    static String endpoint(String address) throws Exception {
        String value = address.trim();
        while(value.endsWith("/")) value = value.substring(0, value.length()-1);
        URI uri = new URI(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new Exception("接口地址须为 HTTPS，例如 https://服务商地址/v1");
        if (!value.endsWith("/chat/completions")) value += "/chat/completions";
        return value;
    }
    static JSONArray models(String address,String key,boolean includeDecision)throws Exception {
        String base=endpointBase(address);
        if(base.endsWith("/chat/completions"))base=base.substring(0,base.length()-17);
        URI uri=new URI(base);boolean bailian=uri.getHost().endsWith("aliyuncs.com")&&base.endsWith("/compatible-mode/v1");
        String path=bailian?base.substring(0,base.length()-"/compatible-mode/v1".length())+"/api/v1/models":base+"/models";
        JSONArray result=new JSONArray();java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(int page=1;page<=(bailian?5:1);page++){
            String target=path+(bailian?"?"+(includeDecision?"":"capabilities=TG&")+"page_no="+page+"&page_size=100":"");
            HttpURLConnection c=(HttpURLConnection)new URL(target).openConnection();
            try{
                c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Accept","application/json");
                int code=c.getResponseCode();if(code<200||code>=300)throw new IOException(error(code));
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] part=new byte[8192];int n;while((n=in.read(part))!=-1){if(buffer.size()+n>2*1024*1024)throw new IOException("模型列表过大");buffer.write(part,0,n);}}
                JSONObject json=new JSONObject(buffer.toString("UTF-8"));JSONArray rows=bailian?json.optJSONObject("output")==null?null:json.getJSONObject("output").optJSONArray("models"):json.optJSONArray("data");
                if(rows==null)throw new IOException("服务未返回模型列表，请手动填写模型名称");
                for(int i=0;i<rows.length();i++){String id=rows.optJSONObject(i)==null?"":rows.getJSONObject(i).optString(bailian?"model":"id");if(!id.isEmpty()&&seen.add(id))result.put(id);}
                if(!bailian||rows.length()<100)break;
            }finally{c.disconnect();}
        }
        return result;
    }
    static JSONObject request(String model, String system, List<ChatStore.Message> history, String user) throws Exception {
        return buildRequest(model,system,history,user,java.util.Collections.emptyList());
    }
    static JSONObject conversationRequest(String model,String system,List<ChatStore.Message> history,List<ChatStore.Message> batch)throws Exception{
        if(batch.isEmpty())throw new IOException("待回复的用户消息不存在");return buildRequest(model,system,history,"",batch);
    }
    private static JSONObject buildRequest(String model,String system,List<ChatStore.Message> history,String user,List<ChatStore.Message> batch)throws Exception{
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        List<ChatStore.Message> included=new java.util.ArrayList<>();
        // Bound context by whole turns; all stored messages remain on the phone.
        int start = history.size(), budget = 24000;
        int end=history.size();
        while(end>0){int begin=end-1;while(begin>0&&!history.get(begin).role.equals("user"))begin--;int cost=0;for(int i=begin;i<end;i++)cost+=MessageTime.content(history.get(i)).length();if(cost>budget)break;budget-=cost;start=begin;end=begin;}
        for(int i=start;i<history.size();i++){
            ChatStore.Message item=history.get(i);
            if(MessageTime.content(item).trim().isEmpty())continue;
            messages.put(new JSONObject().put("role",item.role.equals("gift")?"user":item.role)
                .put("content",MessageTime.content(item)));
            included.add(item);
        }
        if(batch.isEmpty())messages.put(new JSONObject().put("role","user").put("content",user));
        else for(ChatStore.Message item:batch){messages.put(new JSONObject().put("role","user").put("content",item.text));included.add(item);}
        if(!included.isEmpty())messages.getJSONObject(0).put("content",system+MessageTime.context(included));
        return new JSONObject().put("model",model).put("messages",messages).put("stream",true);
    }
    String chat(String address, String key, JSONObject body, Listener listener) throws Exception {
        if (cancelled) throw new IOException("已停止");
        connection = (HttpURLConnection)new URL(endpoint(address)).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20000); connection.setReadTimeout(90000);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "text/event-stream");
            byte[] bytes=body.toString().getBytes("UTF-8"); connection.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=connection.getOutputStream()) { out.write(bytes); }
            int status=connection.getResponseCode();
            if (status<200 || status>=300) throw new IOException(error(status));
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                return readEvents(reader, listener);
            }
        } finally { connection.disconnect(); }
    }
    String readEvents(BufferedReader reader, Listener listener) throws Exception {
        StringBuilder text = new StringBuilder(), event = new StringBuilder();
        String line; boolean finished = false; long last = 0;
        while (!cancelled && (line=reader.readLine()) != null) {
            if (line.length()>1048576) throw new IOException("服务返回的数据过大");
            if (line.isEmpty()) {
                if (event.length()==0) continue;
                String data=event.toString(); event.setLength(0);
                if (data.equals("[DONE]")) { finished=true; break; }
                JSONObject obj=new JSONObject(data);
                if (obj.has("error")) throw new IOException("模型服务返回错误，请检查模型和账户额度");
                JSONArray choices=obj.optJSONArray("choices");
                if(choices!=null && choices.length()>0) {
                    JSONObject choice=choices.getJSONObject(0), delta=choice.optJSONObject("delta");
                    if(delta!=null && delta.opt("content") instanceof String) text.append(delta.getString("content"));
                    String reason=choice.optString("finish_reason", "");
                    if("length".equals(reason)) throw new OutputLimitException();
                    if("content_filter".equals(reason)) throw new IOException("模型服务未返回此回复");
                    if("stop".equals(reason)) finished=true;
                }
                if(text.length()>64000) throw new IOException("回复过长，请缩短问题后重试");
                long now=System.currentTimeMillis();
                if(now-last>80) {listener.update(text.toString());last=now;}
            } else if(line.startsWith("data:")) {
                if(event.length()>0) event.append('\n');
                event.append(line.substring(5).trim());
            }
        }
        if(cancelled) throw new IOException("已停止");
        if(!finished) throw new IOException("连接提前中断，请重试（本次对话未保存）");
        if(text.toString().trim().isEmpty()) throw new IOException("模型没有返回文字，请检查模型是否支持 Chat Completions 流式输出");
        listener.update(text.toString()); return text.toString();
    }
    static String error(int status) {
        if(status==401 || status==403) return "密钥无效或没有权限，请在设置中检查";
        if(status==402) return "模型账户额度不足，请检查服务商账户";
        if(status==404) return "接口地址或模型不存在，请检查设置";
        if(status==429) return "请求过于频繁或额度不足，请稍后再试";
        if(status>=300 && status<400) return "接口发生跳转，请填写服务商提供的最终 HTTPS API 地址";
        return "模型请求失败（HTTP " + status + "），请检查接口、模型和服务状态";
    }
}
