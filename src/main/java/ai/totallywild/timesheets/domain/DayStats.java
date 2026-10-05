package ai.totallywild.timesheets.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

/**
 * One calendar day as the UI sees it.
 *
 * @param entry                  the stored entry, or null when nothing was recorded
 * @param note                   the day's free-text note, or null
 * @param workedMinutes          minutes worked; zero for leave and for unfinished past days
 * @param targetReductionMinutes minutes this day takes off the week's target
 * @param beforeStart            the day is in the first week but precedes the configured start date
 * @param inProgress             a work day that is running right now
 * @param onBreak                a running work day that is currently paused
 * @param unfinished             a work day in the past that was started but never ended
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DayStats(
        LocalDate date,
        DayEntry entry,
        String note,
        long workedMinutes,
        long targetReductionMinutes,
        boolean beforeStart,
        boolean inProgress,
        boolean onBreak,
        boolean unfinished) {
}
