package com.eidolon.sc.mobile;
public class ReplyWorkTests {
 static int n;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);n++;System.out.println("PASS "+name);}
 static String action(String state,boolean test,long sent,long due,boolean immediate,long now){return ReplyWorkPolicy.action(state,test,sent,due,immediate,now);}
 public static void main(String[] args){
  check(action("collecting",false,10000,0,false,13999).equals("wait"),"background guard preserves four-second input merge");
  check(action("collecting",false,10000,0,false,14000).equals("generate"),"collection becomes eligible at exact merge boundary");
  check(action("collecting",false,10000,0,false,9999).equals("wait"),"clock rollback does not prematurely generate");
  check(action("generating",false,0,0,false,99999).equals("wait"),"guard never restarts an already generating batch");
  check(action("waiting",false,0,20000,false,19999).equals("wait"),"prepared reply cannot be delivered early");
  check(action("waiting",false,0,20000,false,20000).equals("deliver"),"prepared reply is eligible at planned time");
  check(action("waiting",false,0,9999999,true,20000).equals("deliver"),"receive-now overrides future planned time");
  check(action("waiting",false,0,9999999,false,20000).equals("drop"),"overnight waiting does not keep bounded guard alive");
  check(action("cancelled",false,0,0,true,20000).equals("drop"),"cancelled batches cannot be delivered by the guard");
  check(action("failed",false,0,0,true,20000).equals("drop"),"failed batch needs explicit retry");
  check(action("waiting",true,0,0,true,20000).equals("drop"),"official guard never consumes the local test task");
  check(ReplyWorkPolicy.localTestAction("waiting",5000,4999).equals("wait"),"explicit guard test respects its planned time");
  check(ReplyWorkPolicy.localTestAction("waiting",5000,5000).equals("deliver"),"explicit guard test delivers at due boundary");
  check(ReplyWorkPolicy.localTestAction("collecting",5000,6000).equals("drop"),"local guard test never invokes generation");
  check(ReplyWorkPolicy.localTestAction("cancelled",5000,6000).equals("drop"),"cancelled local guard test cannot deliver");
  System.out.println("Passed "+n+" background reply timing checks. No Android runtime or API.");
 }
}
