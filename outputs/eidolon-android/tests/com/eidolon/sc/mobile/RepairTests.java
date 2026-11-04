package com.eidolon.sc.mobile;
import org.json.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Real compatibility/rule/slot code with fake transports only. No phone or providers. */
public final class RepairTests {
 static int count;static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);count++;System.out.println("PASS "+msg);}
 public static void main(String[] args)throws Exception{
  check(ReplyText.clean("a<br>b<BR/>c&lt;br&gt;d").equals("a\nb\nc\nd"),"known break spellings become literal newlines");
  check(ReplyText.clean("a\n\n\n\n\n b").equals("a\n\n b"),"accidental blank runs bounded, normal paragraph retained");
  String code="`<br>`\n```xml\n<sticker_id>s-example</sticker_id>\n```\n> quoted <br>";check(ReplyText.clean(code).equals(code),"code fences, inline code and quoted examples retained verbatim");
  String stamp="> 【发送时间：2026-11-04 12:00:00 +08:00】 是引用\n`【发送时间：2026-11-04 12:00:00 +08:00】`";check(ReplyText.clean(stamp).equals(stamp),"transport-looking dates inside explicit quotes/code stay literal");
  String attack="<img src=x onerror=alert(1)><script>x</script>";check(ReplyText.clean(attack).equals(attack),"arbitrary HTML remains inert text rather than being interpreted or broadly stripped");
  String id="s-"+"a".repeat(64),raw="<ja>元気<br>だよ</ja><zh>很好<br/>呀</zh>\n<sticker_id>"+id+"</sticker_id>";
  JSONArray pool=new JSONArray().put(MediaTests.entry("a".repeat(64)));MediaReply.Result valid=MediaReply.parse(raw,pool,new JSONObject(),true,false);
  check(valid.blocks.length()==2&&valid.blocks.getJSONObject(1).getString("type").equals("sticker")&&!valid.text.contains(id),"legacy directive resolves only from the already admitted candidate pool");
  MediaReply.Result denied=MediaReply.parse(raw,new JSONArray(),new JSONObject(),true,false);check(!denied.text.contains(id)&&!denied.text.contains("sticker_id")&&denied.blocks.length()==0,"disallowed or missing legacy sticker leaves normal bilingual text only");
  check(ReplyText.clean("hello\n"+id+"\nworld").equals("hello\n\nworld"),"reserved bare identifier removed only as a standalone transport line");
  check(ReplyText.clean("example `"+id+"`").contains(id),"identifier within an explicit code example remains visible");
  check(!FishClient.speech(raw).contains(id)&&FishClient.speech(raw).contains("元気\nだよ"),"TTS uses normalized dialogue and never speaks sticker payload");
  check(!ReplyMarkup.speech("`"+id+"`").contains(id),"internal IDs are excluded from speech even in code samples");
  JSONObject wrapped=MediaTests.reply(new JSONArray().put(new JSONObject().put("ja","元気<br>だよ").put("zh","你好<br>呀")));check(MediaReply.parse(wrapped.toString(),pool,new JSONObject(),true,false).text.contains("元気\nだよ"),"structured text accepts known breaks without HTML execution");
  check(SocialFailure.classify(new ModelClient.ServiceException(429),false,"request").getString("category").equals("service"),"service throttling is distinct from user configuration guesses");
  check(SocialFailure.classify(new java.net.SocketTimeoutException("secret-url-and-key"),false,"request").getString("category").equals("timeout"),"timeouts classified without retaining transport secrets");
  check(!SocialFailure.classify(new java.net.UnknownHostException("private-host"),false,"request").toString().contains("private-host"),"diagnostics never persist raw URL/exception messages");
  check(SocialFailure.classify(new JSONException("private response"),false,"parse").getString("category").equals("format"),"returned JSON format failure is recognizable");
  check(SocialFailure.classify(new Exception("x"),true,"request").getString("category").equals("cancelled"),"explicit cancellation classified separately");
  check(SocialFailure.classify(new SocialFailure.State("expired","任务已过有效期"),false,"commit").getString("category").equals("expired"),"expiry is distinct from network failure");
  ModelClient automatic=new ModelClient();ScheduleEngine.active=automatic;ScheduleEngine.manualTicket="queued";ScheduleEngine.activeManualTicket="";long epoch=ScheduleEngine.cancellation.get();ScheduleEngine.cancelManual("queued");check(!automatic.cancelled&&ScheduleEngine.cancellation.get()==epoch,"cancelling queued manual request cannot cancel an automatic schedule which won admission");
  ScheduleEngine.manualTicket="new";ScheduleEngine.activeManualTicket="new";ScheduleEngine.cancelManual("old");check(!automatic.cancelled,"late stop for old manual identity cannot cancel a new owner");ScheduleEngine.cancelManual("new");check(automatic.cancelled,"matching active manual identity stops its own client");ScheduleEngine.active=null;ScheduleEngine.manualTicket="";ScheduleEngine.activeManualTicket="";
  AtomicInteger live=new AtomicInteger(),max=new AtomicInteger();CountDownLatch four=new CountDownLatch(4),release=new CountDownLatch(1);ExecutorService workers=Executors.newFixedThreadPool(6);List<Future<?>> futures=new ArrayList<>();
  for(int i=0;i<4;i++)futures.add(workers.submit(()->{try(RequestSlots.Lease own=RequestSlots.acquire(()->false)){int n=live.incrementAndGet();max.accumulateAndGet(n,Math::max);four.countDown();release.await();live.decrementAndGet();}catch(Exception e){throw new RuntimeException(e);}}));
  check(four.await(5,TimeUnit.SECONDS),"four independent fake requests coexist rather than taking a global writing lock");
  AtomicBoolean cancelled=new AtomicBoolean();AtomicInteger opened=new AtomicInteger();Future<?> waiting=workers.submit(()->{try(RequestSlots.Lease own=RequestSlots.acquire(cancelled::get)){opened.incrementAndGet();}catch(java.io.IOException expected){}});cancelled.set(true);waiting.get(5,TimeUnit.SECONDS);
  check(opened.get()==0&&live.get()==4,"cancelling a queued request opens no connection and leaves four owners active");release.countDown();for(Future<?> f:futures)f.get(5,TimeUnit.SECONDS);
  check(max.get()==4&&RequestSlots.permits.availablePermits()==4,"global upper bound and lease release survive concurrency");
  try(RequestSlots.Lease one=RequestSlots.acquire(()->false)){one.close();one.close();}check(RequestSlots.permits.availablePermits()==4,"double-finally cancellation cannot leak or multiply permits");workers.shutdown();
  try(RequestSlots.Lease bg1=RequestSlots.acquire(()->false,false);RequestSlots.Lease bg2=RequestSlots.acquire(()->false,false);RequestSlots.Lease chat1=RequestSlots.acquire(()->false,true);RequestSlots.Lease chat2=RequestSlots.acquire(()->false,true)){
   check(RequestSlots.background.availablePermits()==0&&RequestSlots.permits.availablePermits()==0,"two background requests leave two distinct slots available for chat");
  }
  modelClients();
  System.out.println("Passed "+count+" repair/content/concurrency/diagnostic checks. No real API or Android IME execution.");
 }
 static void modelClients()throws Exception{
  Map<String,FakeConnection> connections=new ConcurrentHashMap<>();CountDownLatch started=new CountDownLatch(2);
  java.net.URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new java.net.URLStreamHandler(){protected java.net.URLConnection openConnection(java.net.URL url){FakeConnection c=new FakeConnection(url,started);connections.put(url.getHost(),c);return c;}}:null);
  ModelClient schedule=new ModelClient(),chat=new ModelClient();chat.foreground=true;ExecutorService pool=Executors.newFixedThreadPool(2);
  Future<String> first=pool.submit(()->schedule.chat("https://schedule.example/v1","fake",new JSONObject(),s->{})),second=pool.submit(()->chat.chat("https://chat.example/v1","fake",new JSONObject(),s->{}));
  check(started.await(5,TimeUnit.SECONDS),"actual ModelClient instances reach fake HTTP concurrently for schedule and chat");
  schedule.cancel();try{first.get(5,TimeUnit.SECONDS);throw new AssertionError("cancelled client returned");}catch(ExecutionException expected){check(true,"cancelled owner cannot return a late successful response");}
  check(!chat.cancelled&&!second.isDone()&&!connections.get("chat.example").closed,"cancelling schedule leaves independent chat client/connection running");
  connections.get("chat.example").release.countDown();check(second.get(5,TimeUnit.SECONDS).equals("完成"),"other client completes with its own result after independent cancellation");
  check(connections.size()==2&&RequestSlots.permits.availablePermits()==4&&RequestSlots.background.availablePermits()==2,"no automatic transport retries and all foreground/background slots released");pool.shutdown();
 }
 static final class FakeConnection extends java.net.HttpURLConnection{
  final CountDownLatch started,release=new CountDownLatch(1);volatile boolean closed;
  FakeConnection(java.net.URL url,CountDownLatch started){super(url);this.started=started;}
  public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){closed=true;release.countDown();}
  public java.io.OutputStream getOutputStream(){return new java.io.ByteArrayOutputStream();}
  public int getResponseCode()throws java.io.IOException{started.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new java.io.IOException("fake timeout");}catch(InterruptedException e){throw new java.io.IOException(e);}if(closed)throw new java.io.IOException("cancelled fake transport");return 200;}
  public java.io.InputStream getInputStream()throws java.io.IOException{return new java.io.ByteArrayInputStream(("data: {\"choices\":[{\"delta\":{\"content\":\"完成\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 }
}
