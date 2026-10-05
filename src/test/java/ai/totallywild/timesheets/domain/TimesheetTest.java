package ai.totallywild.timesheets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimesheetTest {

    static final LocalDate MON = LocalDate.of(2026, 9, 28);

    @Test
    void daysAreSortedByDate() {
        var ts = new Timesheet(null, List.of(
                DayEntry.leave(MON.plusDays(2), DayType.SICK, null),
                DayEntry.leave(MON, DayType.PUBLIC_HOLIDAY, null)));
        assertThat(ts.days()).extracting(DayEntry::date).containsExactly(MON, MON.plusDays(2));
        assertThat(ts.config()).isEqualTo(TimesheetConfig.defaults());
    }

    @Test
    void duplicateDatesAreRejected() {
        assertThatThrownBy(() -> new Timesheet(null, List.of(
                DayEntry.leave(MON, DayType.SICK, null),
                DayEntry.leave(MON, DayType.PUBLIC_HOLIDAY, null))))
                .isInstanceOf(TimesheetException.class).hasMessageContaining("Two entries");
    }

    @Test
    void withDayReplacesSameDate() {
        var ts = Timesheet.empty().withDay(DayEntry.leave(MON, DayType.SICK, null));
        var updated = ts.withDay(DayEntry.leave(MON, DayType.ANNUAL_LEAVE, null));
        assertThat(updated.days()).hasSize(1);
        assertThat(updated.day(MON)).get().extracting(DayEntry::type).isEqualTo(DayType.ANNUAL_LEAVE);
    }

    @Test
    void withoutDayRemoves() {
        var ts = Timesheet.empty().withDay(DayEntry.leave(MON, DayType.SICK, null)).withoutDay(MON);
        assertThat(ts.days()).isEmpty();
        assertThat(ts.day(MON)).isEmpty();
    }

    @Test
    void notesAreIndependentOfEntriesAndSortedAndUnique() {
        var ts = Timesheet.empty()
                .withNote(MON.plusDays(2), "Wednesday")
                .withNote(MON, "Monday")
                .withDay(DayEntry.leave(MON, DayType.SICK, null));
        assertThat(ts.notes()).extracting(DayNote::date).containsExactly(MON, MON.plusDays(2));
        assertThat(ts.note(MON)).get().extracting(DayNote::text).isEqualTo("Monday");
        assertThat(ts.note(MON.plusDays(1))).isEmpty();
        assertThat(ts.withoutDay(MON).notes()).hasSize(2);

        assertThat(ts.withNote(MON, "Monday, updated").note(MON)).get().extracting(DayNote::text).isEqualTo("Monday, updated");
        assertThat(ts.withNote(MON, "").notes()).extracting(DayNote::date).containsExactly(MON.plusDays(2));
        assertThat(ts.withNote(MON, null).notes()).hasSize(1);

        assertThatThrownBy(() -> new Timesheet(null, List.of(), List.of(new DayNote(MON, "a"), new DayNote(MON, "b"))))
                .hasMessageContaining("Two notes");
        assertThatThrownBy(() -> new DayNote(MON, " ")).hasMessageContaining("some text");
        assertThatThrownBy(() -> new DayNote(null, "x")).hasMessageContaining("date");
    }

    @Test
    void configValidation() {
        assertThatThrownBy(() -> new TimesheetConfig(0.0, 8.0, null, 0.0)).hasMessageContaining("Weekly target");
        assertThatThrownBy(() -> new TimesheetConfig(40.0, 0.0, null, 0.0)).hasMessageContaining("Standard day");
        assertThatThrownBy(() -> new TimesheetConfig(40.0, 25.0, null, 0.0)).hasMessageContaining("24");
        var config = new TimesheetConfig(38.0, 7.6, null, -1.5);
        assertThat(config.weeklyTargetMinutes()).isEqualTo(2280);
        assertThat(config.standardDayMinutes()).isEqualTo(456);
        assertThat(config.openingBalanceMinutes()).isEqualTo(-90);
        assertThat(new TimesheetConfig(null, null, null, null)).isEqualTo(new TimesheetConfig(40.0, 8.0, null, 0.0));
    }
}
