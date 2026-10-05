package com.kislov.timesheets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DayEntryTest {

    static final LocalDate DATE = LocalDate.of(2026, 9, 28);
    static final LocalTime T0830 = LocalTime.of(8, 30);
    static final LocalTime T1200 = LocalTime.of(12, 0);
    static final LocalTime T1245 = LocalTime.of(12, 45);
    static final LocalTime T1700 = LocalTime.of(17, 0);

    @Nested
    class WorkedMinutes {

        @Test
        void fullDayMinusBreaks() {
            var day = DayEntry.work(DATE, T0830, T1700, List.of(new Break(T1200, T1245)));
            assertThat(day.workedMinutes(LocalTime.MIDNIGHT)).isEqualTo(8 * 60 + 30 - 45);
        }

        @Test
        void multipleBreaks() {
            var day = DayEntry.work(DATE, T0830, T1700, List.of(
                    new Break(LocalTime.of(10, 0), LocalTime.of(10, 15)),
                    new Break(T1200, T1245),
                    new Break(LocalTime.of(15, 0), LocalTime.of(15, 10))));
            assertThat(day.workedMinutes(LocalTime.MIDNIGHT)).isEqualTo(510 - 15 - 45 - 10);
        }

        @Test
        void inProgressDayCountsUpToNow() {
            var day = DayEntry.work(DATE, T0830, null, List.of());
            assertThat(day.inProgress()).isTrue();
            assertThat(day.workedMinutes(LocalTime.of(11, 0))).isEqualTo(150);
        }

        @Test
        void inProgressBreakCountsUpToNow() {
            var day = DayEntry.work(DATE, T0830, null, List.of(new Break(T1200, null)));
            assertThat(day.onBreak()).isTrue();
            assertThat(day.workedMinutes(LocalTime.of(12, 30))).isEqualTo(210);
        }

        @Test
        void nowBeforeStartIsZeroNotNegative() {
            var day = DayEntry.work(DATE, T0830, null, List.of());
            assertThat(day.workedMinutes(LocalTime.of(7, 0))).isZero();
        }

        @Test
        void leaveIsZero() {
            assertThat(DayEntry.leave(DATE, DayType.SICK, null).workedMinutes(T1700)).isZero();
        }
    }

    @Nested
    class TargetReduction {

        @Test
        void leaveTakesAStandardDayOff() {
            assertThat(DayEntry.leave(DATE, DayType.PUBLIC_HOLIDAY, null).targetReductionMinutes(480)).isEqualTo(480);
        }

        @Test
        void partialLeaveUsesItsOwnHours() {
            assertThat(DayEntry.leave(DATE, DayType.SICK, 4.0).targetReductionMinutes(480)).isEqualTo(240);
        }

        @Test
        void timeInLieuAndWorkLeaveTargetAlone() {
            assertThat(DayEntry.leave(DATE, DayType.TIME_IN_LIEU, null).targetReductionMinutes(480)).isZero();
            assertThat(DayEntry.work(DATE, T0830, T1700, null).targetReductionMinutes(480)).isZero();
        }
    }

    @Nested
    class Validation {

        @Test
        void workNeedsStart() {
            assertThatThrownBy(() -> DayEntry.work(DATE, null, T1700, null))
                    .isInstanceOf(TimesheetException.class).hasMessageContaining("start");
        }

        @Test
        void finishNotBeforeStart() {
            assertThat(DayEntry.work(DATE, T0830, T0830, null).workedMinutes(T1700)).isZero();
            assertThatThrownBy(() -> DayEntry.work(DATE, T1700, T0830, null))
                    .isInstanceOf(TimesheetException.class).hasMessageContaining("before start");
        }

        @Test
        void breakInsideDay() {
            assertThatThrownBy(() -> DayEntry.work(DATE, T0830, T1700, List.of(new Break(LocalTime.of(7, 0), LocalTime.of(7, 30)))))
                    .hasMessageContaining("before the day starts");
            assertThatThrownBy(() -> DayEntry.work(DATE, T0830, T1700, List.of(new Break(LocalTime.of(16, 0), LocalTime.of(18, 0)))))
                    .hasMessageContaining("past the end of the day");
        }

        @Test
        void breaksMustNotOverlap() {
            assertThatThrownBy(() -> DayEntry.work(DATE, T0830, T1700, List.of(
                    new Break(T1200, LocalTime.of(13, 0)), new Break(T1245, LocalTime.of(13, 30)))))
                    .hasMessageContaining("overlap");
        }

        @Test
        void openBreakOnFinishedDayIsRejected() {
            assertThatThrownBy(() -> DayEntry.work(DATE, T0830, T1700, List.of(new Break(T1200, null))))
                    .hasMessageContaining("still open");
        }

        @Test
        void breakFinishAfterBreakStart() {
            assertThatThrownBy(() -> new Break(T1245, T1200)).hasMessageContaining("before its start");
            assertThat(new Break(T1200, T1200).minutes(T1700)).isZero();
        }

        @Test
        void leaveHasNoTimes() {
            assertThatThrownBy(() -> new DayEntry(DATE, DayType.SICK, T0830, null, null, null))
                    .hasMessageContaining("no start");
        }

        @Test
        void leaveHoursPositive() {
            assertThatThrownBy(() -> DayEntry.leave(DATE, DayType.SICK, 0.0)).hasMessageContaining("greater than zero");
        }

        @Test
        void workDayCannotCarryHours() {
            assertThatThrownBy(() -> new DayEntry(DATE, DayType.WORK, T0830, T1700, null, 8.0))
                    .hasMessageContaining("only for leave");
        }

        @Test
        void breakNoteIsTrimmedAndBlankBecomesNull() {
            assertThat(new Break(T1200, T1245, "  lunch  ").note()).isEqualTo("lunch");
            assertThat(new Break(T1200, T1245, "   ").note()).isNull();
            assertThat(new Break(T1200, T1245).note()).isNull();
            assertThat(new Break(T1200, null, "coffee").endedAt(T1245)).isEqualTo(new Break(T1200, T1245, "coffee"));
        }
    }

    @Test
    void finishedAtClosesOpenBreak() {
        var day = DayEntry.work(DATE, T0830, null, List.of(new Break(T1200, null)));
        var closed = day.finishedAt(LocalTime.of(12, 30));
        assertThat(closed.finish()).isEqualTo(LocalTime.of(12, 30));
        assertThat(closed.breaks()).containsExactly(new Break(T1200, LocalTime.of(12, 30)));
        assertThat(closed.workedMinutes(LocalTime.MIDNIGHT)).isEqualTo(210);
    }
}
