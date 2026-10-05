package ai.totallywild.timesheets.domain;

/**
 * The rolling picture. Everything except {@code workedToDateMinutes} covers <em>completed</em> weeks
 * only, so {@code balanceMinutes} is exactly what is carried into the current week (opening balance
 * included) and is not dragged around by the days of this week that have not happened yet.
 *
 * @param completedWeeks      weeks that ended before today
 * @param workedMinutes       minutes worked in completed weeks
 * @param targetMinutes       sum of the completed weeks' targets, after leave
 * @param leaveMinutes        minutes of leave taken off those targets
 * @param balanceMinutes      running balance carried into the current week
 * @param workedToDateMinutes minutes worked in completed weeks plus the current week so far
 */
public record Totals(
        int completedWeeks,
        long workedMinutes,
        long targetMinutes,
        long leaveMinutes,
        long balanceMinutes,
        long workedToDateMinutes) {
}
