package com.kislov.timesheets.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kislov.timesheets.domain.Break;
import com.kislov.timesheets.domain.DayEntry;
import com.kislov.timesheets.domain.DayNote;
import com.kislov.timesheets.domain.DayType;
import com.kislov.timesheets.domain.Timesheet;
import com.kislov.timesheets.domain.TimesheetConfig;
import com.kislov.timesheets.domain.TimesheetException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TimesheetStoreTest {

    @TempDir
    Path dir;

    @Test
    void missingFileLoadsAsEmptyWithDefaults() {
        var store = new TimesheetStore(dir.resolve("nested/timesheet.json"));
        assertThat(store.exists()).isFalse();
        assertThat(store.load()).isEqualTo(Timesheet.empty());
    }

    @Test
    void roundTripsEverything() {
        var store = new TimesheetStore(dir.resolve("nested/timesheet.json"));
        var config = new TimesheetConfig(38.0, 7.6, LocalDate.of(2026, 9, 1), 2.5);
        var sheet = new Timesheet(config, List.of(
                DayEntry.work(LocalDate.of(2026, 9, 28), LocalTime.of(8, 30), LocalTime.of(17, 15), List.of(new Break(LocalTime.of(12, 0), LocalTime.of(12, 45), "lunch with client"))),
                DayEntry.work(LocalDate.of(2026, 9, 29), LocalTime.of(9, 0), null, List.of(new Break(LocalTime.of(12, 0), null))),
                DayEntry.leave(LocalDate.of(2026, 9, 30), DayType.SICK, 4.0),
                DayEntry.leave(LocalDate.of(2026, 10, 5), DayType.PUBLIC_HOLIDAY, null)),
                List.of(new DayNote(LocalDate.of(2026, 9, 28), "Deployed v2.\n\nPaired with Sam on the parser."),
                        new DayNote(LocalDate.of(2026, 10, 1), "no entry that day, just a note")));

        store.save(sheet);

        assertThat(store.exists()).isTrue();
        assertThat(store.load()).isEqualTo(sheet);
    }

    @Test
    void fileIsHumanFriendly() throws IOException {
        var file = dir.resolve("timesheet.json");
        var store = new TimesheetStore(file);
        store.save(Timesheet.empty().withDay(DayEntry.work(LocalDate.of(2026, 9, 28), LocalTime.of(8, 30), LocalTime.of(17, 0), List.of(new Break(LocalTime.of(12, 0), LocalTime.of(12, 30))))));

        var text = Files.readString(file);
        assertThat(text).contains("\"weeklyTargetHours\" : 40.0");
        assertThat(text).contains("\"date\" : \"2026-09-28\"");
        assertThat(text).contains("\"start\" : \"08:30\"");
        assertThat(text).contains("\"finish\" : \"17:00\"");
        assertThat(text).doesNotContain("\"note\"");
        assertThat(text).doesNotContain("notes");
        assertThat(text).doesNotContain("\"hours\"");
        assertThat(text).doesNotContain("startDate");
        assertThat(text).doesNotContain("\"work\"");
        assertThat(text).doesNotContain("inProgress");
        assertThat(text.lines().count()).isGreaterThan(10);
    }

    @Test
    void readsAHandWrittenFile() throws IOException {
        var file = dir.resolve("timesheet.json");
        Files.writeString(file, """
                {
                  "config": { "weeklyTargetHours": 40, "standardDayHours": 8 },
                  "days": [
                    { "date": "2026-09-29", "type": "PUBLIC_HOLIDAY" },
                    { "date": "2026-09-28", "type": "WORK", "start": "09:00", "finish": "17:00",
                      "breaks": [ { "start": "12:00", "finish": "12:30", "note": "lunch" } ], "somethingNew": 1 }
                  ],
                  "notes": [ { "date": "2026-09-28", "text": "hand written" } ]
                }
                """);
        var sheet = new TimesheetStore(file).load();
        assertThat(sheet.note(LocalDate.of(2026, 9, 28))).get().extracting(DayNote::text).isEqualTo("hand written");
        assertThat(sheet.days().getFirst().breaks().getFirst().note()).isEqualTo("lunch");
        assertThat(sheet.config().openingBalanceHours()).isZero();
        assertThat(sheet.days()).extracting(DayEntry::date).containsExactly(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29));
        assertThat(sheet.days().getFirst().workedMinutes(LocalTime.MIDNIGHT)).isEqualTo(450);
    }

    @Test
    void blankFileIsEmpty() throws IOException {
        var file = dir.resolve("timesheet.json");
        Files.writeString(file, "\n");
        assertThat(new TimesheetStore(file).load()).isEqualTo(Timesheet.empty());
    }

    @Test
    void invalidContentIsReportedWithTheRuleThatFailed() throws IOException {
        var file = dir.resolve("timesheet.json");
        Files.writeString(file, """
                { "days": [ { "date": "2026-09-28", "type": "WORK", "start": "17:00", "finish": "09:00" } ] }
                """);
        assertThatThrownBy(() -> new TimesheetStore(file).load())
                .isInstanceOf(TimesheetException.class)
                .hasMessageContaining("could not be parsed")
                .hasMessageContaining("before start");
    }

    @Test
    void savingReplacesTheFileAndLeavesNoTempBehind() throws IOException {
        var file = dir.resolve("timesheet.json");
        var store = new TimesheetStore(file);
        store.save(Timesheet.empty());
        store.save(Timesheet.empty().withDay(DayEntry.leave(LocalDate.of(2026, 9, 28), DayType.SICK, null)));

        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("timesheet.json");
        }
        assertThat(store.load().days()).hasSize(1);
    }
}
