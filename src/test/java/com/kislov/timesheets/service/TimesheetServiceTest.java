package com.kislov.timesheets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kislov.timesheets.domain.Break;
import com.kislov.timesheets.domain.DayEntry;
import com.kislov.timesheets.domain.DayType;
import com.kislov.timesheets.domain.TimesheetConfig;
import com.kislov.timesheets.domain.TimesheetException;
import com.kislov.timesheets.storage.TimesheetStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TimesheetServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 30); // a Wednesday
    static final ZoneId ZONE = ZoneId.of("Australia/Brisbane");

    @TempDir
    Path dir;

    TimesheetStore store;
    MutableClock clock;
    TimesheetService service;

    /** A clock the tests can move forward. */
    static class MutableClock extends Clock {
        LocalDateTime now;

        MutableClock(LocalDateTime now) {
            this.now = now;
        }

        void set(LocalTime time) {
            now = now.toLocalDate().atTime(time);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now.atZone(ZONE).toInstant();
        }
    }

    @BeforeEach
    void setUp() {
        store = new TimesheetStore(dir.resolve("timesheet.json"));
        clock = new MutableClock(TODAY.atTime(8, 30, 45));
        service = new TimesheetService(store, clock);
    }

    @Test
    void aWholeDayThroughTheButtons() {
        var view = service.startDay();
        assertThat(view.todayStats().inProgress()).isTrue();
        assertThat(view.todayStats().entry().start()).isEqualTo(LocalTime.of(8, 30));
        assertThat(store.load().day(TODAY)).isPresent();

        clock.set(LocalTime.of(12, 0));
        view = service.startBreak();
        assertThat(view.todayStats().onBreak()).isTrue();
        assertThat(view.todayStats().workedMinutes()).isEqualTo(210);

        clock.set(LocalTime.of(12, 45));
        view = service.endBreak();
        assertThat(view.todayStats().onBreak()).isFalse();
        assertThat(view.todayStats().entry().breaks()).containsExactly(new Break(LocalTime.of(12, 0), LocalTime.of(12, 45)));

        clock.set(LocalTime.of(17, 0));
        view = service.endDay();
        assertThat(view.todayStats().inProgress()).isFalse();
        assertThat(view.todayStats().workedMinutes()).isEqualTo(8 * 60 + 30 - 45);
        assertThat(view.weeks().getFirst().workedMinutes()).isEqualTo(465);
    }

    @Test
    void endDayClosesAnOpenBreak() {
        service.startDay();
        clock.set(LocalTime.of(12, 0));
        service.startBreak();
        clock.set(LocalTime.of(12, 20));
        var view = service.endDay();
        var entry = view.todayStats().entry();
        assertThat(entry.finish()).isEqualTo(LocalTime.of(12, 20));
        assertThat(entry.breaks().getFirst().finish()).isEqualTo(LocalTime.of(12, 20));
        assertThat(view.todayStats().workedMinutes()).isEqualTo(210);
    }

    @Test
    void buttonsRefuseImpossibleTransitions() {
        assertThatThrownBy(service::startBreak).isInstanceOf(TimesheetException.class).hasMessageContaining("not started");
        assertThatThrownBy(service::endBreak).hasMessageContaining("not started");
        assertThatThrownBy(service::endDay).hasMessageContaining("not started");

        service.startDay();
        assertThatThrownBy(service::startDay).hasMessageContaining("already started at 08:30");
        assertThatThrownBy(service::endBreak).hasMessageContaining("Not on a break");

        service.startBreak();
        assertThatThrownBy(service::startBreak).hasMessageContaining("Already on a break");

        clock.set(LocalTime.of(17, 0));
        service.endDay();
        assertThatThrownBy(service::startDay).hasMessageContaining("already finished at 17:00");
        assertThatThrownBy(service::startBreak).hasMessageContaining("already finished");
    }

    @Test
    void startDayRefusesALeaveDay() {
        service.saveDay(DayEntry.leave(TODAY, DayType.SICK, null));
        assertThatThrownBy(service::startDay).hasMessageContaining("Sick leave");
        assertThatThrownBy(service::startBreak).hasMessageContaining("Sick leave");
    }

    @Test
    void saveDeleteAndConfigPersist() {
        var monday = TODAY.minusDays(2);
        service.saveDay(DayEntry.work(monday, LocalTime.of(9, 0), LocalTime.of(17, 0), List.of()));
        service.saveDay(DayEntry.leave(TODAY.plusDays(1), DayType.PUBLIC_HOLIDAY, null));
        var view = service.updateConfig(new TimesheetConfig(38.0, 7.6, monday, 0.0));

        assertThat(view.config().weeklyTargetHours()).isEqualTo(38);
        var week = view.weeks().getFirst();
        assertThat(week.workedMinutes()).isEqualTo(480);
        assertThat(week.leaveMinutes()).isEqualTo(456);
        assertThat(week.targetMinutes()).isEqualTo(2280 - 456);

        view = service.deleteDay(monday);
        assertThat(view.weeks().getFirst().workedMinutes()).isZero();
        assertThat(store.load().days()).hasSize(1);

        assertThatThrownBy(() -> service.deleteDay(monday)).hasMessageContaining("Nothing recorded");
    }

    @Test
    void invalidEntriesNeverReachTheFile() {
        service.saveDay(DayEntry.leave(TODAY, DayType.SICK, null));
        assertThatThrownBy(() -> service.saveDay(new DayEntry(TODAY, DayType.WORK, LocalTime.of(9, 0), LocalTime.of(8, 0), null, null)))
                .isInstanceOf(TimesheetException.class);
        assertThat(store.load().day(TODAY)).get().extracting(DayEntry::type).isEqualTo(DayType.SICK);
    }

    @Test
    void notesAreSavedRemovedAndShownOnTheDay() {
        var view = service.saveNote(TODAY, "  Deployed v2.\nPaired with Sam.  ");
        assertThat(view.todayStats().note()).isEqualTo("Deployed v2.\nPaired with Sam.");
        assertThat(store.load().note(TODAY)).get().extracting(com.kislov.timesheets.domain.DayNote::text).isEqualTo("Deployed v2.\nPaired with Sam.");

        // A note on a day with no entry and outside the current range still shows up.
        var lastMonth = TODAY.minusWeeks(3);
        view = service.saveNote(lastMonth, "kick-off");
        assertThat(view.weeks()).hasSize(4);
        assertThat(view.weeks().getLast().days().stream().filter(d -> d.date().equals(lastMonth)).findFirst())
                .get().extracting(com.kislov.timesheets.domain.DayStats::note).isEqualTo("kick-off");

        view = service.saveNote(TODAY, "   ");
        assertThat(view.todayStats().note()).isNull();
        assertThat(store.load().notes()).hasSize(1);
    }

    @Test
    void breakNotesSurviveTheButtons() {
        service.startDay();
        clock.set(LocalTime.of(12, 0));
        service.startBreak();
        var withNote = store.load().day(TODAY).orElseThrow();
        store.save(store.load().withDay(withNote.withBreaks(List.of(new Break(LocalTime.of(12, 0), null, "lunch")))));
        clock.set(LocalTime.of(12, 30));
        var view = service.endBreak();
        assertThat(view.todayStats().entry().breaks()).containsExactly(new Break(LocalTime.of(12, 0), LocalTime.of(12, 30), "lunch"));
    }

    @Test
    void viewSeesHandEditsMadeBetweenCalls() {
        service.startDay();
        var edited = store.load().withDay(DayEntry.leave(TODAY, DayType.ANNUAL_LEAVE, null));
        store.save(edited);
        assertThat(service.view().todayStats().entry().type()).isEqualTo(DayType.ANNUAL_LEAVE);
    }
}
