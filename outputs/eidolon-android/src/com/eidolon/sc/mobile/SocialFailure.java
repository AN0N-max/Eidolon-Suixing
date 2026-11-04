package com.eidolon.sc.mobile;
import org.json.*;import java.io.*;import java.net.*;

/** Allowlisted diagnostics only: no raw responses, prompts, URLs, keys or private comments. */
final class SocialFailure {
 static final class State extends IOException{final String category;State(String c,String message){super(message);category=c;}}
 static JSONObject classify(Exception e,boolean stopped,String stage)throws Exception{
  String category="validation",reason="返回内容未通过动态校验";int status=0;
  if(stopped){category="cancelled";reason="任务已取消，结果未投放";}
  else if(e instanceof State){category=((State)e).category;reason=e.getMessage();}
  else if(e instanceof ModelClient.ServiceException){status=((ModelClient.ServiceException)e).status;category="service";reason=status==429?"服务请求限流（HTTP429）":status==401||status==403?"服务拒绝认证或权限不足（HTTP"+status+"）":status==402?"服务额度不足（HTTP402）":"服务返回错误（HTTP"+status+"）";}
  else if(e instanceof ModelClient.OutputLimitException){category="response_length";reason="模型返回达到长度限制，内容不完整";}
  else if(e instanceof RequestSlots.Busy){category="concurrency";reason="并发请求已满，本次尚未连接服务";}
  else if(e.getClass().getSimpleName().startsWith("SQLite")){category="storage";reason="本地保存或事务未完成";}
  else if(e instanceof JSONException){category="format";reason="模型返回不是完整的动态JSON结构";}
  else if(e instanceof SocketTimeoutException){category="timeout";reason="服务连接或读取超时，是否计费未知";}
  else if(e instanceof UnknownHostException||e instanceof ConnectException||e instanceof javax.net.ssl.SSLException){category="network";reason="网络、域名解析或安全连接失败";}
  else if(e instanceof IOException&&stage.equals("request")){category="network";reason="服务连接或响应读取中断，是否计费未知";}
  else if(e instanceof IOException){String m=e.getMessage()==null?"":e.getMessage();if(m.contains("JSON")||m.contains("结构")){category="format";reason="模型返回格式不符合动态结构";}else if(m.contains("连接")||m.contains("Connection")||m.contains("stream")){category="network";reason="服务连接中断，是否计费未知";}}
  return new JSONObject().put("category",category).put("stage",stage).put("httpStatus",status).put("exception",e.getClass().getSimpleName()).put("message",reason+"；未自动重试");
 }
}
