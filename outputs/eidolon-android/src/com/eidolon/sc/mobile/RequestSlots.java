package com.eidolon.sc.mobile;
import java.io.IOException;import java.util.concurrent.*;import java.util.function.BooleanSupplier;

/** Shared HTTP bound, independent client cancellation; no lock is held during a DB transaction. */
final class RequestSlots {
 static final int LIMIT=4;static final Semaphore permits=new Semaphore(LIMIT,true),background=new Semaphore(2,true);
 static final class Busy extends IOException{Busy(){super("并发请求已满，尚未连接服务；请稍后明确重试");}}
 static final class Lease implements AutoCloseable{private boolean released;final boolean bg;Lease(boolean background){bg=background;}public synchronized void close(){if(!released){released=true;permits.release();if(bg)RequestSlots.background.release();}}}
 static Lease acquire(BooleanSupplier cancelled)throws IOException{return acquire(cancelled,true);}
 static Lease acquire(BooleanSupplier cancelled,boolean foreground)throws IOException{
  boolean bg=false;try{if(!foreground){await(background,cancelled);bg=true;}await(permits,cancelled);return new Lease(bg);}catch(IOException e){if(bg)background.release();throw e;}
 }
 private static void await(Semaphore semaphore,BooleanSupplier cancelled)throws IOException{
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
  try{while(!cancelled.getAsBoolean()){if(semaphore.tryAcquire(100,TimeUnit.MILLISECONDS)){if(cancelled.getAsBoolean()){semaphore.release();throw new IOException("已停止");}return;}if(System.nanoTime()>=end)throw new Busy();}}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("请求等待已取消");}throw new IOException("已停止");
 }
 private RequestSlots(){}
}
