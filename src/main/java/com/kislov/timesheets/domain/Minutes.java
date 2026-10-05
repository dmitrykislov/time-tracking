package com.kislov.timesheets.domain;

import java.time.Duration;
import java.time.LocalTime;

/** Small helpers for the minute arithmetic the whole domain runs on. */
public final class Minutes {
    private Minutes() {}

    /** Minutes from {@code from} to {@code to}, never negative. */
    public static long between(LocalTime from, LocalTime to) {
        return Math.max(0, Duration.between(from, to).toMinutes());
    }

    /** Converts decimal hours (e.g. 7.6) to whole minutes. */
    public static long fromHours(double hours) {
        return Math.round(hours * 60);
    }
}
