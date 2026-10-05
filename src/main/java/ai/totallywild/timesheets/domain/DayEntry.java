package ai.totallywild.timesheets.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One calendar day in the timesheet.
 *
 * <p>A {@link DayType#WORK} day has a start, an optional finish (null while the day is running) and
 * any number of breaks. A leave day has none of those, only an optional {@code hours} that overrides
 * the standard day length (half a sick day = 4). Free-text notes about a day live in {@link DayNote},
 * independent of whether times were recorded.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DayEntry(
        LocalDate date,
        DayType type,
        @JsonFormat(pattern = "HH:mm") LocalTime start,
        @JsonFormat(pattern = "HH:mm") LocalTime finish,
        List<Break> breaks,
        Double hours) {

    public DayEntry {
        if (date == null) {
            throw new TimesheetException("A day needs a date");
        }
        if (type == null) {
            throw new TimesheetException("A day needs a type");
        }
        breaks = breaks == null ? List.of() : List.copyOf(breaks);

        if (type == DayType.WORK) {
            validateWork(date, start, finish, breaks, hours);
        } else {
            validateLeave(type, start, finish, breaks, hours);
        }
    }

    private static void validateWork(LocalDate date, LocalTime start, LocalTime finish, List<Break> breaks, Double hours) {
        if (start == null) {
            throw new TimesheetException("A work day needs a start time");
        }
        if (hours != null) {
            throw new TimesheetException("Hours are only for leave days; a work day is measured from its times");
        }
        if (finish != null && finish.isBefore(start)) {
            throw new TimesheetException("Finish (%s) is before start (%s)".formatted(finish, start));
        }
        var sorted = new ArrayList<>(breaks);
        sorted.sort(Comparator.comparing(Break::start));
        Break previous = null;
        for (var b : sorted) {
            if (b.start().isBefore(start)) {
                throw new TimesheetException("Break at %s starts before the day starts (%s)".formatted(b.start(), start));
            }
            if (finish != null) {
                var breakEnd = b.finish() != null ? b.finish() : b.start();
                if (breakEnd.isAfter(finish)) {
                    throw new TimesheetException("Break ending %s runs past the end of the day (%s)".formatted(breakEnd, finish));
                }
                if (b.inProgress()) {
                    throw new TimesheetException("The day has finished but a break at %s is still open".formatted(b.start()));
                }
            }
            if (previous != null) {
                var previousEnd = previous.finish();
                if (previousEnd == null || b.start().isBefore(previousEnd)) {
                    throw new TimesheetException("Breaks overlap around %s".formatted(b.start()));
                }
            }
            previous = b;
        }
    }

    private static void validateLeave(DayType type, LocalTime start, LocalTime finish, List<Break> breaks, Double hours) {
        if (start != null || finish != null || !breaks.isEmpty()) {
            throw new TimesheetException("%s days have no start, finish or breaks".formatted(type.label()));
        }
        if (hours != null && hours <= 0) {
            throw new TimesheetException("Leave hours must be greater than zero");
        }
    }

    public static DayEntry work(LocalDate date, LocalTime start, LocalTime finish, List<Break> breaks) {
        return new DayEntry(date, DayType.WORK, start, finish, breaks, null);
    }

    public static DayEntry leave(LocalDate date, DayType type, Double hours) {
        return new DayEntry(date, type, null, null, null, hours);
    }

    @JsonIgnore
    public boolean isWork() {
        return type == DayType.WORK;
    }

    /** A work day that has started and not finished. */
    public boolean inProgress() {
        return isWork() && finish == null;
    }

    public boolean onBreak() {
        return inProgress() && breaks.stream().anyMatch(Break::inProgress);
    }

    /**
     * Minutes worked. {@code now} stands in for the finish while the day is running; it is ignored
     * once the day is closed.
     */
    public long workedMinutes(LocalTime now) {
        if (!isWork()) {
            return 0;
        }
        var end = finish != null ? finish : now;
        var gross = Minutes.between(start, end);
        var paused = breaks.stream().mapToLong(b -> b.minutes(end)).sum();
        return Math.max(0, gross - paused);
    }

    /** Minutes this day takes off the weekly target, given the configured standard day. */
    public long targetReductionMinutes(long standardDayMinutes) {
        if (!type.reducesTarget()) {
            return 0;
        }
        return hours != null ? Minutes.fromHours(hours) : standardDayMinutes;
    }

    public DayEntry withBreaks(List<Break> newBreaks) {
        return new DayEntry(date, type, start, finish, newBreaks, hours);
    }

    public DayEntry finishedAt(LocalTime time) {
        var closed = breaks.stream().map(b -> b.inProgress() ? b.endedAt(time) : b).toList();
        return new DayEntry(date, type, start, time, closed, hours);
    }
}
