package com.eidolon.sc.mobile;
import org.json.*;import java.io.*;import java.net.*;

/** Allowlisted diagnostics only: no raw responses, prompts, URLs, keys or private comments. */
final class SocialFailure {
 static final class State extends IOException{final String category;State(String c,String message){super(message);category=c;}}
 static boolean attempted(String stage){return stage.equals("request")||stage.equals("parse")||stage.equals("commit");}
 static String location(Exception e){for(StackTraceElement frame:e.getStackTrace())if(frame.getClassName().startsWith("com.eidolon.sc.mobile."))return frame.getClassName().substring("com.eidolon.sc.mobile.".length())+"."+frame.getMethodName()+":"+frame.getLineNumber();return "";}
 static String field(Exception e){if(!(e instanceof JSONException))return "";String text=e.getMessage()==null?"":e.getMessage();for(String key:new String[]{"id","character","name","sheet","facts","body","plan","slots","start","end","date","kind","title","events","profiles","places","lat","lon","latitude","longitude","producer","firstMeetings","birthdays","holidays","revision","expires","state","ja","zh","placeId","likes","comments"})if(text.equals("No value for "+key)||text.contains("[\""+key+"\"] not found")||text.matches("(?s).* at "+key+" of type .*"))return key;return "";}
 static boolean provenUnsent(JSONObject d){return d!=null&&(Boolean.FALSE.equals(d.opt("requestAttempted"))||d.optString("stage").equals("prepare")&&d.optString("category").equals("format")&&d.optString("exception").equals("JSONException"));}
 static JSONObject present(JSONObject task)throws Exception{
  JSONObject out=new JSONObject(task.toString()),d=out.optJSONObject("diagnostic");if(d!=null&&d.optString("stage").equals("prepare")&&d.optString("category").equals("format")){
   d.put("category","local_data").put("requestAttempted",false).put("legacy",true);out.put("error","旧记录：准备动态资料时发生结构错误，尚未进入聊天模型请求；原记录未能定位具体字段。可手动重试，旧记录保留");
  }else if(d==null&&out.optString("error").contains("生成未完成")){out.put("error","旧记录：当时未保存详细失败阶段，不能据此认定模型配置错误。请先检查本地资料，再明确手动重试；原记录保留");}return out;
 }
 static JSONObject classify(Exception e,boolean stopped,String stage)throws Exception{
  String category="validation",reason="返回内容未通过动态校验";int status=0;
  if(stopped){category="cancelled";reason="任务已取消，结果未投放";}
  else if(e instanceof State){category=((State)e).category;reason=e.getMessage();}
  else if(e instanceof ModelClient.ServiceException){status=((ModelClient.ServiceException)e).status;category="service";reason=status==429?"服务请求限流（HTTP429）":status==401||status==403?"服务拒绝认证或权限不足（HTTP"+status+"）":status==402?"服务额度不足（HTTP402）":"服务返回错误（HTTP"+status+"）";}
  else if(e instanceof ModelClient.OutputLimitException){category="response_length";reason="模型返回达到长度限制，内容不完整";}
  else if(e instanceof RequestSlots.Busy){category="concurrency";reason="并发请求已满，本次尚未连接服务";}
  else if(e.getClass().getSimpleName().startsWith("SQLite")){category="storage";reason="本地保存或事务未完成";}
  else if(stage.startsWith("prepare")){category="local_data";reason="准备动态资料时本地内容不可用，尚未调用聊天模型";}
  else if(e instanceof SocialReply.Invalid){category="format";reason=e.getMessage();}
  else if(e instanceof JSONException){boolean response=stage.equals("parse");category=response?"format":stage.equals("commit")?"storage":"local_data";reason=response?"服务返回的动态JSON结构无效":stage.equals("commit")?"动态保存时本地字段不完整":"准备动态资料时本地字段不完整，尚未调用聊天模型";}
  else if(e instanceof SocketTimeoutException){category="timeout";reason="服务连接或读取超时，是否计费未知";}
  else if(e instanceof UnknownHostException||e instanceof ConnectException||e instanceof javax.net.ssl.SSLException){category="network";reason="网络、域名解析或安全连接失败";}
  else if(e instanceof IOException&&stage.equals("request")){category="network";reason="服务连接或响应读取中断，是否计费未知";}
  else if(e instanceof IOException){String m=e.getMessage()==null?"":e.getMessage();if(m.contains("JSON")||m.contains("结构")){category="format";reason="模型返回格式不符合动态结构";}else if(m.contains("连接")||m.contains("Connection")||m.contains("stream")){category="network";reason="服务连接中断，是否计费未知";}}
  return new JSONObject().put("code",e instanceof SocialReply.Invalid?((SocialReply.Invalid)e).code:category).put("requestAttempted",!(e instanceof RequestSlots.Busy)&&attempted(stage)).put("field",field(e)).put("location",location(e)).put("category",category).put("stage",stage).put("httpStatus",status).put("exception",e.getClass().getSimpleName()).put("message",reason+"；未自动重试");
 }
}
