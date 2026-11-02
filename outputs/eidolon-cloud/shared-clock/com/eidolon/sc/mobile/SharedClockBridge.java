package com.eidolon.sc.mobile;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Transport adapter only. DailyScheduleClock is compiled from the existing Android source unchanged. */
public final class SharedClockBridge {
    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException();
            ZonedDateTime now = Instant.ofEpochMilli(Long.parseLong(args[0])).atZone(ZoneId.of(args[1]));
            System.out.println("{\"schema\":1,\"displayDay\":\"" + DailyScheduleClock.displayDay(now)
                    + "\",\"nextBoundaryMs\":" + DailyScheduleClock.nextBoundary(now) + "}");
        } catch (RuntimeException error) {
            System.err.println("shared_clock_invalid_input");
            System.exit(2);
        }
    }
}
