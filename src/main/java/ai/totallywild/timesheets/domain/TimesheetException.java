package ai.totallywild.timesheets.domain;

/** A rule of the timesheet was broken. Surfaces to the UI as a 400 with the message. */
public class TimesheetException extends RuntimeException {
    public TimesheetException(String message) {
        super(message);
    }
}
