package com.kislov.timesheets.service;

import com.kislov.timesheets.domain.Break;
import com.kislov.timesheets.domain.DayEntry;
import com.kislov.timesheets.domain.Timesheet;
import com.kislov.timesheets.domain.TimesheetCalculator;
import com.kislov.timesheets.domain.TimesheetConfig;
import com.kislov.timesheets.domain.TimesheetException;
import com.kislov.timesheets.domain.TimesheetView;
import com.kislov.timesheets.storage.TimesheetStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.function.UnaryOperator;

/**
 * All the ways the timesheet changes. Each mutation loads the file, applies one change, saves, and
 * hands back the fresh view so the UI can simply replace what it shows.
 */
public class TimesheetService {

    private final TimesheetStore store;
    private final Clock clock;
    private final TimesheetCalculator calculator = new TimesheetCalculator();

    public TimesheetService(TimesheetStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public synchronized TimesheetView view() {
        return calculator.view(store.load(), now());
    }

    public synchronized Timesheet timesheet() {
        return store.load();
    }

    public synchronized TimesheetView updateConfig(TimesheetConfig config) {
        return mutate(t -> t.withConfig(config));
    }

    public synchronized TimesheetView saveDay(DayEntry entry) {
        return mutate(t -> t.withDay(entry));
    }

    public synchronized TimesheetView deleteDay(LocalDate date) {
        return mutate(t -> {
            if (t.day(date).isEmpty()) {
                throw new TimesheetException("Nothing recorded on " + date);
            }
            return t.withoutDay(date);
        });
    }

    /** Sets the free-text note for a day; blank text removes it. */
    public synchronized TimesheetView saveNote(LocalDate date, String text) {
        return mutate(t -> t.withNote(date, text));
    }

    /** Stamps the current time as the start of today. */
    public synchronized TimesheetView startDay() {
        return mutate(t -> {
            var today = today();
            var existing = t.day(today);
            if (existing.isPresent()) {
                var entry = existing.get();
                if (entry.inProgress()) {
                    throw new TimesheetException("Today already started at " + entry.start());
                }
                if (entry.isWork()) {
                    throw new TimesheetException("Today already finished at " + entry.finish() + "; edit it instead");
                }
                throw new TimesheetException("Today is recorded as " + entry.type().label() + "; change the day type first");
            }
            return t.withDay(DayEntry.work(today, nowTime(), null, null));
        });
    }

    public synchronized TimesheetView startBreak() {
        return mutate(t -> {
            var entry = running(t);
            if (entry.onBreak()) {
                throw new TimesheetException("Already on a break");
            }
            var breaks = new ArrayList<>(entry.breaks());
            breaks.add(new Break(nowTime(), null, null));
            return t.withDay(entry.withBreaks(breaks));
        });
    }

    public synchronized TimesheetView endBreak() {
        return mutate(t -> {
            var entry = running(t);
            if (!entry.onBreak()) {
                throw new TimesheetException("Not on a break");
            }
            var time = nowTime();
            var breaks = entry.breaks().stream().map(b -> b.inProgress() ? b.endedAt(time) : b).toList();
            return t.withDay(entry.withBreaks(breaks));
        });
    }

    /** Stamps the current time as the finish of today, closing any open break with it. */
    public synchronized TimesheetView endDay() {
        return mutate(t -> t.withDay(running(t).finishedAt(nowTime())));
    }

    private DayEntry running(Timesheet t) {
        var entry = t.day(today()).orElseThrow(() -> new TimesheetException("Today has not started yet"));
        if (!entry.inProgress()) {
            throw new TimesheetException(entry.isWork()
                    ? "Today already finished at " + entry.finish()
                    : "Today is recorded as " + entry.type().label());
        }
        return entry;
    }

    private TimesheetView mutate(UnaryOperator<Timesheet> change) {
        var updated = change.apply(store.load());
        store.save(updated);
        return calculator.view(updated, now());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalTime nowTime() {
        return LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    }
}
