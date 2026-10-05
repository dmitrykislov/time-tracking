package com.kislov.timesheets.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * The whole picture the UI renders, computed fresh from the file on every request.
 *
 * @param weeks newest week first
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimesheetView(
        TimesheetConfig config,
        LocalDate today,
        @JsonFormat(pattern = "HH:mm") LocalTime now,
        DayStats todayStats,
        List<WeekStats> weeks,
        Totals totals,
        List<String> warnings) {
}
