package com.kislov.timesheets.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Everything in the data file: the config, one entry per calendar day, and one note per calendar day.
 * Entries and notes are independent: a day can have either, both or neither. Both lists are kept
 * sorted by date.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Timesheet(TimesheetConfig config, List<DayEntry> days, List<DayNote> notes) {

    public Timesheet {
        config = config == null ? TimesheetConfig.defaults() : config;
        days = days == null ? List.of() : days.stream().sorted(Comparator.comparing(DayEntry::date)).toList();
        notes = notes == null ? List.of() : notes.stream().sorted(Comparator.comparing(DayNote::date)).toList();
        var seen = new HashSet<LocalDate>();
        for (var day : days) {
            if (!seen.add(day.date())) {
                throw new TimesheetException("Two entries for " + day.date());
            }
        }
        seen.clear();
        for (var note : notes) {
            if (!seen.add(note.date())) {
                throw new TimesheetException("Two notes for " + note.date());
            }
        }
    }

    public Timesheet(TimesheetConfig config, List<DayEntry> days) {
        this(config, days, List.of());
    }

    public static Timesheet empty() {
        return new Timesheet(TimesheetConfig.defaults(), List.of(), List.of());
    }

    public Optional<DayEntry> day(LocalDate date) {
        return days.stream().filter(d -> d.date().equals(date)).findFirst();
    }

    public Optional<DayNote> note(LocalDate date) {
        return notes.stream().filter(n -> n.date().equals(date)).findFirst();
    }

    public Timesheet withDay(DayEntry entry) {
        var others = days.stream().filter(d -> !d.date().equals(entry.date()));
        return new Timesheet(config, Stream.concat(others, Stream.of(entry)).toList(), notes);
    }

    public Timesheet withoutDay(LocalDate date) {
        return new Timesheet(config, days.stream().filter(d -> !d.date().equals(date)).toList(), notes);
    }

    /** Sets the note for a day; blank text removes it. */
    public Timesheet withNote(LocalDate date, String text) {
        var others = notes.stream().filter(n -> !n.date().equals(date));
        if (text == null || text.isBlank()) {
            return new Timesheet(config, days, others.toList());
        }
        return new Timesheet(config, days, Stream.concat(others, Stream.of(new DayNote(date, text))).toList());
    }

    public Timesheet withConfig(TimesheetConfig newConfig) {
        return new Timesheet(newConfig, days, notes);
    }
}
