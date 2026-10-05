package ai.totallywild.timesheets.domain;

import java.time.LocalDate;

/** Free-text notes for one calendar day: what happened, decisions, things to remember. */
public record DayNote(LocalDate date, String text) {

    public DayNote {
        if (date == null) {
            throw new TimesheetException("A note needs a date");
        }
        if (text == null || text.isBlank()) {
            throw new TimesheetException("A note needs some text; save an empty note to remove it");
        }
        text = text.strip();
    }
}
