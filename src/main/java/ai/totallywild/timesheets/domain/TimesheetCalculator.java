package ai.totallywild.timesheets.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Turns the stored entries into weekly and rolling numbers. Pure and deterministic: everything
 * depends only on the timesheet and the supplied "now".
 */
public final class TimesheetCalculator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM");

    public TimesheetView view(Timesheet timesheet, LocalDateTime now) {
        var config = timesheet.config();
        var today = now.toLocalDate();
        var nowTime = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        var warnings = new ArrayList<String>();

        var from = startOf(timesheet, today);
        var ignored = timesheet.days().stream().filter(d -> d.date().isBefore(from)).count();
        if (ignored > 0) {
            warnings.add("%d %s before the start date (%s) %s ignored"
                    .formatted(ignored, ignored == 1 ? "entry" : "entries", from, ignored == 1 ? "is" : "are"));
        }
        var to = Stream.of(today, lastDate(timesheet.days(), DayEntry::date), lastDate(timesheet.notes(), DayNote::date))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElseThrow();

        var weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var lastWeekStart = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        var weeks = new ArrayList<WeekStats>();
        long carry = config.openingBalanceMinutes();
        int completedWeeks = 0;
        long completedWorked = 0;
        long completedTarget = 0;
        long completedLeave = 0;
        long balanceIntoCurrentWeek = carry;
        long workedToDate = 0;
        DayStats todayStats = null;

        while (!weekStart.isAfter(lastWeekStart)) {
            var days = new ArrayList<DayStats>(7);
            long worked = 0;
            long leave = 0;
            for (int i = 0; i < 7; i++) {
                var date = weekStart.plusDays(i);
                var stats = dayStats(timesheet, config, date, from, today, nowTime, warnings);
                days.add(stats);
                worked += stats.workedMinutes();
                leave += stats.targetReductionMinutes();
                if (date.equals(today)) {
                    todayStats = stats;
                }
            }
            long target = Math.max(0, config.weeklyTargetMinutes() - leave);
            long effectiveTarget = target - carry;
            long balance = worked - target;
            long running = carry + balance;
            var weekEnd = weekStart.plusDays(6);
            var current = !today.isBefore(weekStart) && !today.isAfter(weekEnd);
            var upcoming = weekStart.isAfter(today);
            weeks.add(new WeekStats(weekStart, weekEnd, current, upcoming, List.copyOf(days), worked, leave, target,
                    carry, effectiveTarget, effectiveTarget - worked, balance, running));
            if (weekEnd.isBefore(today)) {
                completedWeeks++;
                completedWorked += worked;
                completedTarget += target;
                completedLeave += leave;
                workedToDate += worked;
            } else if (current) {
                balanceIntoCurrentWeek = carry;
                workedToDate += worked;
            }
            carry = running;
            weekStart = weekStart.plusWeeks(1);
        }

        var totals = new Totals(completedWeeks, completedWorked, completedTarget, completedLeave, balanceIntoCurrentWeek, workedToDate);
        return new TimesheetView(config, today, nowTime, todayStats, weeks.reversed(), totals, List.copyOf(warnings));
    }

    /** The first day that counts: the configured start, else the earliest of the first entry, the first note and today. */
    static LocalDate startOf(Timesheet timesheet, LocalDate today) {
        if (timesheet.config().startDate() != null) {
            return timesheet.config().startDate();
        }
        return Stream.of(today, firstDate(timesheet.days(), DayEntry::date), firstDate(timesheet.notes(), DayNote::date))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    private static <T> LocalDate firstDate(List<T> items, Function<T, LocalDate> date) {
        return items.isEmpty() ? null : date.apply(items.getFirst());
    }

    private static <T> LocalDate lastDate(List<T> items, Function<T, LocalDate> date) {
        return items.isEmpty() ? null : date.apply(items.getLast());
    }

    private static DayStats dayStats(Timesheet timesheet, TimesheetConfig config, LocalDate date, LocalDate from,
                                     LocalDate today, java.time.LocalTime nowTime, List<String> warnings) {
        var note = timesheet.note(date).map(DayNote::text).orElse(null);
        if (date.isBefore(from)) {
            var weekday = date.getDayOfWeek().getValue() <= 5;
            return new DayStats(date, null, note, 0, weekday ? config.standardDayMinutes() : 0, true, false, false, false);
        }
        var entry = timesheet.day(date).orElse(null);
        if (entry == null) {
            return new DayStats(date, null, note, 0, 0, false, false, false, false);
        }
        var unfinished = entry.inProgress() && date.isBefore(today);
        var inProgress = entry.inProgress() && !unfinished;
        if (unfinished) {
            warnings.add("%s was started at %s but never ended; add a finish time so its hours count"
                    .formatted(DAY.format(date), entry.start()));
        }
        var worked = unfinished ? 0 : entry.workedMinutes(nowTime);
        return new DayStats(date, entry, note, worked, entry.targetReductionMinutes(config.standardDayMinutes()),
                false, inProgress, inProgress && entry.onBreak(), unfinished);
    }
}
