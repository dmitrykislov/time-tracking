package com.kislov.timesheets.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalTime;

/**
 * A pause inside a work day. {@code finish} is null while the break is still running; {@code note}
 * is an optional label such as "lunch with the client".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Break(
        @JsonFormat(pattern = "HH:mm") LocalTime start,
        @JsonFormat(pattern = "HH:mm") LocalTime finish,
        String note) {

    public Break(LocalTime start, LocalTime finish) {
        this(start, finish, null);
    }

    public Break {
        if (start == null) {
            throw new TimesheetException("A break needs a start time");
        }
        note = note == null || note.isBlank() ? null : note.strip();
        if (finish != null && finish.isBefore(start)) {
            throw new TimesheetException("Break finish (%s) is before its start (%s)".formatted(finish, start));
        }
    }

    public boolean inProgress() {
        return finish == null;
    }

    /** Minutes this break removes from the day, using {@code dayEnd} when the break is still open. */
    public long minutes(LocalTime dayEnd) {
        return Minutes.between(start, finish != null ? finish : dayEnd);
    }

    public Break endedAt(LocalTime time) {
        return new Break(start, time, note);
    }
}
