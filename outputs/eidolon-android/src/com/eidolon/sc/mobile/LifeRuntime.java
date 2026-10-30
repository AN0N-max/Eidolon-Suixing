package com.eidolon.sc.mobile;
/** Local reads/edits never queue behind a model request. */
final class LifeRuntime {
 static final java.util.concurrent.ExecutorService EXECUTOR=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"eidolon-life-local");t.setDaemon(true);return t;});
 private LifeRuntime(){}
}
