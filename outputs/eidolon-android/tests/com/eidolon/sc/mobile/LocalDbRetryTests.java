package com.eidolon.sc.mobile;
import java.util.*;
public final class LocalDbRetryTests {
    static class Busy extends Exception{}
    static int n;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);n++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        int[] calls={0};List<Long> waits=new ArrayList<>();
        int value=LocalDbRetry.run(()->{if(++calls[0]<3)throw new Busy();return 7;},e->e instanceof Busy,waits::add);
        check(value==7&&calls[0]==3&&waits.equals(Arrays.asList(100L,300L)),"transient local lock retries twice with bounded backoff");
        calls[0]=0;boolean failed=false;try{LocalDbRetry.run(()->{calls[0]++;throw new Busy();},e->true,m->{});}catch(Busy e){failed=true;}
        check(failed&&calls[0]==3,"persistent lock stops after three attempts");
        calls[0]=0;failed=false;try{LocalDbRetry.run(()->{calls[0]++;throw new IllegalArgumentException();},e->false,m->{});}catch(IllegalArgumentException e){failed=true;}
        check(failed&&calls[0]==1,"non-lock data errors do not get retried");
        calls[0]=0;Thread.currentThread().interrupt();failed=false;try{LocalDbRetry.run(()->++calls[0],e->true,m->{});}catch(InterruptedException e){failed=true;}finally{Thread.interrupted();}
        check(failed&&calls[0]==0,"interrupted check does not enter another transaction");
        calls[0]=0;failed=false;try{LocalDbRetry.run(()->{calls[0]++;throw new Busy();},e->true,m->{throw new InterruptedException();});}catch(InterruptedException e){failed=true;}
        check(failed&&calls[0]==1,"cancellation during backoff stops immediately");
        System.out.println("PASS "+n+" local retry rules; no model requests.");
    }
}
