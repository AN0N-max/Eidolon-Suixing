package com.eidolon.sc.mobile;

import java.io.IOException;

/** Disk-backed producer/consumer state. Bytes become visible only AFTER file writes. */
final class PcmSpool {
    static final long MAX_BYTES = 16L * 1024 * 1024;
    private long produced;
    private boolean done, cancelled;
    private IOException failure;
    synchronized void published(long n) throws IOException {
        if (cancelled) throw new IOException("已停止");
        if (n < produced || n > MAX_BYTES) throw new IOException("音频过大");
        produced = n;
        notifyAll();
    }
    synchronized void complete() throws IOException {
        if (produced == 0 || (produced & 1) != 0) throw new IOException("音频为空或 PCM 不完整");
        done = true;
        notifyAll();
    }
    synchronized void fail(IOException e) { failure = e; notifyAll(); }
    synchronized void cancel() { cancelled = true; notifyAll(); }
    synchronized boolean isDone() { return done; }
    synchronized long size() { return produced; }
    synchronized long await(long offset, long minimum) throws IOException, InterruptedException {
        while (!cancelled && failure == null && !done && produced - offset < minimum) wait();
        if (cancelled) throw new IOException("已停止");
        if (failure != null) throw failure;
        return (produced - offset) & ~1L;
    }
}
