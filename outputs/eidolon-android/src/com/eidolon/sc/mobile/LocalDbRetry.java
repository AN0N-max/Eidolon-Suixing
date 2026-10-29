package com.eidolon.sc.mobile;
/** Bounded retry for a rolled-back local operation. Never used around a model call. */
final class LocalDbRetry {
    interface Operation<T>{T run()throws Exception;}
    interface Busy{boolean test(Exception error);}
    interface Pause{void waitFor(long millis)throws InterruptedException;}
    static <T>T run(Operation<T> operation,Busy busy,Pause pause)throws Exception{
        for(int attempt=0;;attempt++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("Local check stopped");
            try{return operation.run();}catch(Exception e){if(e instanceof InterruptedException||!busy.test(e)||attempt>=2)throw e;pause.waitFor(attempt==0?100:300);}
        }
    }
    private LocalDbRetry(){}
}
