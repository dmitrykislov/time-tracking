package com.kislov.timesheets.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * A Monday-to-Sunday week with its numbers. Balances are signed: positive means hours in credit.
 *
 * @param current                the week that contains today
 * @param upcoming               the week starts after today; it is shown for booked leave only and its
 *                               balance figures are speculative
 * @param targetMinutes          the weekly target after leave has been taken off
 * @param carryInMinutes         running balance at the start of the week
 * @param effectiveTargetMinutes what actually has to be worked this week: target minus carry-in
 * @param remainingMinutes       effective target minus worked; negative once the week is over target
 * @param balanceMinutes         worked minus target, this week alone
 * @param runningBalanceMinutes  carry-in plus this week's balance
 */
public record WeekStats(
        LocalDate start,
        LocalDate end,
        boolean current,
        boolean upcoming,
        List<DayStats> days,
        long workedMinutes,
        long leaveMinutes,
        long targetMinutes,
        long carryInMinutes,
        long effectiveTargetMinutes,
        long remainingMinutes,
        long balanceMinutes,
        long runningBalanceMinutes) {
}
