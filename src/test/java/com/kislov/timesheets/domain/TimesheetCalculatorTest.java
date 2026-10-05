package com.kislov.timesheets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimesheetCalculatorTest {

    static final LocalDate MON1 = LocalDate.of(2026, 9, 14);
    static final LocalDate MON2 = MON1.plusWeeks(1);
    static final LocalDate MON3 = MON1.plusWeeks(2);
    static final LocalTime NINE = LocalTime.of(9, 0);
    static final LocalTime FIVE = LocalTime.of(17, 0);
    static final Break LUNCH = new Break(LocalTime.of(12, 0), LocalTime.of(13, 0));

    final TimesheetCalculator calculator = new TimesheetCalculator();

    /** 9-5 with an hour lunch = 7h. */
    static DayEntry sevenHours(LocalDate date) {
        return DayEntry.work(date, NINE, FIVE, List.of(LUNCH));
    }

    static DayEntry hours(LocalDate date, int hours) {
        return DayEntry.work(date, NINE, NINE.plusHours(hours), null);
    }

    static Timesheet sheet(TimesheetConfig config, DayEntry... days) {
        return new Timesheet(config, List.of(days));
    }

    static TimesheetConfig from(LocalDate start) {
        return new TimesheetConfig(40.0, 8.0, start, 0.0);
    }

    @Test
    void singleFullWeek() {
        var ts = sheet(from(MON1),
                hours(MON1, 8), hours(MON1.plusDays(1), 8), hours(MON1.plusDays(2), 8),
                hours(MON1.plusDays(3), 8), hours(MON1.plusDays(4), 8));
        var view = calculator.view(ts, MON1.plusDays(6).atTime(20, 0));

        assertThat(view.weeks()).hasSize(1);
        var week = view.weeks().getFirst();
        assertThat(week.start()).isEqualTo(MON1);
        assertThat(week.end()).isEqualTo(MON1.plusDays(6));
        assertThat(week.current()).isTrue();
        assertThat(week.days()).hasSize(7);
        assertThat(week.workedMinutes()).isEqualTo(2400);
        assertThat(week.targetMinutes()).isEqualTo(2400);
        assertThat(week.balanceMinutes()).isZero();
        assertThat(week.remainingMinutes()).isZero();
        assertThat(week.upcoming()).isFalse();
        // Sunday evening: the week is still current, so nothing is "completed" yet.
        assertThat(view.totals()).isEqualTo(new Totals(0, 0, 0, 0, 0, 2400));
    }

    @Test
    void overworkCarriesIntoNextWeek() {
        var ts = sheet(from(MON1),
                hours(MON1, 10), hours(MON1.plusDays(1), 10), hours(MON1.plusDays(2), 10),
                hours(MON1.plusDays(3), 10), hours(MON1.plusDays(4), 3),
                hours(MON2, 8));
        var view = calculator.view(ts, MON2.atTime(18, 0));

        assertThat(view.weeks()).hasSize(2);
        var current = view.weeks().get(0);
        var previous = view.weeks().get(1);

        assertThat(previous.workedMinutes()).isEqualTo(43 * 60);
        assertThat(previous.balanceMinutes()).isEqualTo(180);
        assertThat(previous.runningBalanceMinutes()).isEqualTo(180);
        assertThat(previous.current()).isFalse();

        assertThat(current.carryInMinutes()).isEqualTo(180);
        assertThat(current.targetMinutes()).isEqualTo(2400);
        assertThat(current.effectiveTargetMinutes()).isEqualTo(2400 - 180);
        assertThat(current.remainingMinutes()).isEqualTo(2400 - 180 - 480);
        assertThat(current.balanceMinutes()).isEqualTo(480 - 2400);
        assertThat(current.runningBalanceMinutes()).isEqualTo(180 + 480 - 2400);
        assertThat(view.totals()).isEqualTo(new Totals(1, 43 * 60, 2400, 0, 180, 43 * 60 + 480));
        assertThat(view.totals().balanceMinutes()).isEqualTo(current.carryInMinutes());
    }

    @Test
    void leaveReducesTargetButTimeInLieuDoesNot() {
        var ts = sheet(from(MON1),
                DayEntry.leave(MON1, DayType.PUBLIC_HOLIDAY, null),
                DayEntry.leave(MON1.plusDays(1), DayType.SICK, 4.0),
                hours(MON1.plusDays(2), 8),
                DayEntry.leave(MON1.plusDays(3), DayType.TIME_IN_LIEU, null),
                hours(MON1.plusDays(4), 8));
        var view = calculator.view(ts, MON1.plusDays(6).atTime(12, 0));
        var week = view.weeks().getFirst();

        assertThat(week.leaveMinutes()).isEqualTo(480 + 240);
        assertThat(week.targetMinutes()).isEqualTo(2400 - 720);
        assertThat(week.workedMinutes()).isEqualTo(960);
        assertThat(week.balanceMinutes()).isEqualTo(960 - 1680);

        var nextWeek = calculator.view(ts, MON2.atTime(9, 0));
        assertThat(nextWeek.totals()).isEqualTo(new Totals(1, 960, 1680, 720, 960 - 1680, 960));
    }

    @Test
    void weekOfOnlyLeaveHasZeroTargetAndZeroBalance() {
        var ts = sheet(from(MON1),
                DayEntry.leave(MON1, DayType.ANNUAL_LEAVE, null),
                DayEntry.leave(MON1.plusDays(1), DayType.ANNUAL_LEAVE, null),
                DayEntry.leave(MON1.plusDays(2), DayType.ANNUAL_LEAVE, null),
                DayEntry.leave(MON1.plusDays(3), DayType.ANNUAL_LEAVE, null),
                DayEntry.leave(MON1.plusDays(4), DayType.ANNUAL_LEAVE, null));
        var week = calculator.view(ts, MON1.plusDays(6).atTime(12, 0)).weeks().getFirst();
        assertThat(week.targetMinutes()).isZero();
        assertThat(week.balanceMinutes()).isZero();
    }

    @Test
    void targetNeverGoesNegativeWithExcessLeave() {
        var ts = sheet(new TimesheetConfig(10.0, 8.0, MON1, 0.0),
                DayEntry.leave(MON1, DayType.SICK, null),
                DayEntry.leave(MON1.plusDays(1), DayType.SICK, null));
        var week = calculator.view(ts, MON1.plusDays(6).atTime(12, 0)).weeks().getFirst();
        assertThat(week.targetMinutes()).isZero();
    }

    @Test
    void emptyWeekBetweenEntriesCountsAgainstTheBalance() {
        var ts = sheet(from(MON1), hours(MON1, 8), hours(MON3, 8));
        var view = calculator.view(ts, MON3.atTime(18, 0));
        assertThat(view.weeks()).hasSize(3);
        var middle = view.weeks().get(1);
        assertThat(middle.start()).isEqualTo(MON2);
        assertThat(middle.workedMinutes()).isZero();
        assertThat(middle.balanceMinutes()).isEqualTo(-2400);
        assertThat(view.weeks().getFirst().carryInMinutes()).isEqualTo(-2400 - 1920);
    }

    @Test
    void openingBalanceSeedsTheFirstWeek() {
        var ts = sheet(new TimesheetConfig(40.0, 8.0, MON1, 5.0), hours(MON1, 8));
        var week = calculator.view(ts, MON1.atTime(18, 0)).weeks().getFirst();
        assertThat(week.carryInMinutes()).isEqualTo(300);
        assertThat(week.effectiveTargetMinutes()).isEqualTo(2100);
        assertThat(week.remainingMinutes()).isEqualTo(2100 - 480);
        assertThat(week.runningBalanceMinutes()).isEqualTo(300 + 480 - 2400);
    }

    @Test
    void midWeekStartProratesFirstWeekAndIgnoresEarlierEntries() {
        var wednesday = MON1.plusDays(2);
        var ts = sheet(from(wednesday), hours(MON1, 8), hours(wednesday, 8), hours(wednesday.plusDays(1), 8), hours(wednesday.plusDays(2), 8));
        var view = calculator.view(ts, MON1.plusDays(6).atTime(12, 0));

        var week = view.weeks().getFirst();
        assertThat(week.days().get(0).beforeStart()).isTrue();
        assertThat(week.days().get(0).entry()).isNull();
        assertThat(week.days().get(2).beforeStart()).isFalse();
        assertThat(week.leaveMinutes()).isEqualTo(960);
        assertThat(week.targetMinutes()).isEqualTo(1440);
        assertThat(week.workedMinutes()).isEqualTo(1440);
        assertThat(week.balanceMinutes()).isZero();
        assertThat(view.warnings()).anyMatch(w -> w.contains("1 entry before the start date"));
    }

    @Test
    void withoutStartDateTheFirstEntryStartsTheClock() {
        var ts = sheet(TimesheetConfig.defaults(), hours(MON2, 8));
        var view = calculator.view(ts, MON3.atTime(12, 0));
        assertThat(view.weeks()).hasSize(2);
        assertThat(view.weeks().getLast().start()).isEqualTo(MON2);
        assertThat(view.warnings()).isEmpty();
    }

    /** With nothing recorded and no start date, the clock starts today: earlier weekdays are prorated away. */
    @Test
    void emptySheetShowsJustTheCurrentWeekProratedFromToday() {
        var thursday = MON2.plusDays(3);
        var view = calculator.view(Timesheet.empty(), thursday.atTime(9, 30));
        assertThat(view.weeks()).hasSize(1);
        var week = view.weeks().getFirst();
        assertThat(week.start()).isEqualTo(MON2);
        assertThat(week.current()).isTrue();
        assertThat(week.days()).extracting(DayStats::beforeStart).containsExactly(true, true, true, false, false, false, false);
        assertThat(view.todayStats().entry()).isNull();
        assertThat(view.today()).isEqualTo(thursday);
        assertThat(view.now()).isEqualTo(LocalTime.of(9, 30));
        assertThat(week.targetMinutes()).isEqualTo(960);
        assertThat(week.leaveMinutes()).isEqualTo(1440);
        assertThat(view.totals()).isEqualTo(new Totals(0, 0, 0, 0, 0, 0));
    }

    @Test
    void emptySheetOnMondayHasTheFullTarget() {
        var view = calculator.view(Timesheet.empty(), MON2.atTime(9, 30));
        assertThat(view.weeks().getFirst().targetMinutes()).isEqualTo(2400);
        assertThat(view.weeks().getFirst().remainingMinutes()).isEqualTo(2400);
    }

    @Test
    void futureLeaveExtendsTheWeeksShown() {
        var holiday = MON3.plusDays(1);
        var ts = sheet(from(MON1), hours(MON1, 8), DayEntry.leave(holiday, DayType.PUBLIC_HOLIDAY, null));
        var view = calculator.view(ts, MON1.atTime(18, 0));
        assertThat(view.weeks()).hasSize(3);
        assertThat(view.weeks().getFirst().start()).isEqualTo(MON3);
        assertThat(view.weeks().getFirst().current()).isFalse();
        assertThat(view.weeks().getFirst().upcoming()).isTrue();
        assertThat(view.weeks().getFirst().targetMinutes()).isEqualTo(1920);
        assertThat(view.weeks().get(1).upcoming()).isTrue();
        assertThat(view.weeks().get(2).upcoming()).isFalse();
        // Upcoming weeks never touch the rolling totals.
        assertThat(view.totals()).isEqualTo(new Totals(0, 0, 0, 0, 0, 480));
    }

    @Test
    void runningDayCountsUpToNowAndFlagsStatus() {
        var today = MON1.plusDays(1);
        var ts = sheet(from(MON1), DayEntry.work(today, NINE, null, List.of(new Break(LocalTime.of(12, 0), null))));
        var view = calculator.view(ts, today.atTime(12, 30));

        assertThat(view.todayStats().inProgress()).isTrue();
        assertThat(view.todayStats().onBreak()).isTrue();
        assertThat(view.todayStats().workedMinutes()).isEqualTo(180);
        assertThat(view.weeks().getFirst().workedMinutes()).isEqualTo(180);
        assertThat(view.warnings()).isEmpty();
    }

    @Test
    void unfinishedPastDayCountsZeroAndWarns() {
        var ts = sheet(from(MON1), DayEntry.work(MON1, NINE, null, null));
        var view = calculator.view(ts, MON1.plusDays(1).atTime(10, 0));

        var monday = view.weeks().getFirst().days().getFirst();
        assertThat(monday.unfinished()).isTrue();
        assertThat(monday.inProgress()).isFalse();
        assertThat(monday.workedMinutes()).isZero();
        assertThat(view.warnings()).singleElement().asString().contains("Mon 14 Sep").contains("never ended");
    }

    @Test
    void notesRideAlongOnTheDayAndWidenTheRange() {
        var ts = sheet(TimesheetConfig.defaults(), hours(MON2, 8))
                .withNote(MON2, "sprint planning")
                .withNote(MON1.plusDays(4), "before any entry")
                .withNote(MON3.plusDays(1), "booked demo");
        var view = calculator.view(ts, MON2.plusDays(1).atTime(12, 0));

        assertThat(view.weeks()).extracting(WeekStats::start).containsExactly(MON3, MON2, MON1);
        assertThat(view.weeks().get(1).days().getFirst().note()).isEqualTo("sprint planning");
        assertThat(view.weeks().get(1).days().get(1).note()).isNull();
        assertThat(view.weeks().get(2).days().get(4).note()).isEqualTo("before any entry");
        assertThat(view.weeks().get(0).days().get(1).note()).isEqualTo("booked demo");
        assertThat(view.weeks().get(0).upcoming()).isTrue();
        // A note-only week before the first entry still counts against the target, like any week.
        assertThat(view.weeks().get(2).workedMinutes()).isZero();
    }

    @Test
    void sevenHourDaysAcrossTwoWeeksRollUp() {
        var days = new java.util.ArrayList<DayEntry>();
        for (int i = 0; i < 5; i++) {
            days.add(sevenHours(MON1.plusDays(i)));
            days.add(sevenHours(MON2.plusDays(i)));
        }
        var view = calculator.view(new Timesheet(from(MON1), days), MON2.plusDays(6).atTime(12, 0));
        assertThat(view.totals()).isEqualTo(new Totals(1, 35 * 60, 40 * 60, 0, -5 * 60, 70 * 60));
        assertThat(view.weeks().getFirst().carryInMinutes()).isEqualTo(-5 * 60);

        var afterBothWeeks = calculator.view(new Timesheet(from(MON1), days), MON3.atTime(9, 0));
        assertThat(afterBothWeeks.totals()).isEqualTo(new Totals(2, 70 * 60, 80 * 60, 0, -10 * 60, 70 * 60));
    }
}
