package ai.totallywild.timesheets.web;

import ai.totallywild.timesheets.domain.DayEntry;
import ai.totallywild.timesheets.domain.Timesheet;
import ai.totallywild.timesheets.domain.TimesheetConfig;
import ai.totallywild.timesheets.domain.TimesheetException;
import ai.totallywild.timesheets.domain.TimesheetView;
import ai.totallywild.timesheets.service.TimesheetService;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TimesheetController {

    private final TimesheetService service;

    public TimesheetController(TimesheetService service) {
        this.service = service;
    }

    /** Everything the UI needs, computed from the file right now. */
    @GetMapping("/view")
    public TimesheetView view() {
        return service.view();
    }

    /** The raw contents of the data file. */
    @GetMapping("/timesheet")
    public Timesheet timesheet() {
        return service.timesheet();
    }

    @PutMapping("/config")
    public TimesheetView updateConfig(@RequestBody TimesheetConfig config) {
        return service.updateConfig(config);
    }

    @PutMapping("/days/{date}")
    public TimesheetView saveDay(@PathVariable LocalDate date, @RequestBody DayEntry entry) {
        if (!date.equals(entry.date())) {
            throw new TimesheetException("The entry is for %s but was sent to %s".formatted(entry.date(), date));
        }
        return service.saveDay(entry);
    }

    @DeleteMapping("/days/{date}")
    public TimesheetView deleteDay(@PathVariable LocalDate date) {
        return service.deleteDay(date);
    }

    /** Body for {@link #saveNote}. */
    public record NoteBody(String text) {
    }

    /** Sets the day's free-text note; blank text removes it. */
    @PutMapping("/notes/{date}")
    public TimesheetView saveNote(@PathVariable LocalDate date, @RequestBody NoteBody body) {
        return service.saveNote(date, body.text());
    }

    /** The four buttons. {@code action} is one of start-day, start-break, end-break, end-day. */
    @PostMapping("/today/{action}")
    public TimesheetView today(@PathVariable String action) {
        return switch (action) {
            case "start-day" -> service.startDay();
            case "start-break" -> service.startBreak();
            case "end-break" -> service.endBreak();
            case "end-day" -> service.endDay();
            default -> throw new TimesheetException("Unknown action '" + action + "'");
        };
    }
}
