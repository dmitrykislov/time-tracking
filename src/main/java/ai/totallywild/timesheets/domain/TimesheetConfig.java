package ai.totallywild.timesheets.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

/**
 * The rules of the engagement.
 *
 * @param weeklyTargetHours   hours a full week should contain, e.g. 40
 * @param standardDayHours    hours one leave day removes from the target, e.g. 8 (or 7.6)
 * @param startDate           first day that counts; earlier entries and weeks are ignored. Null means
 *                            "from the first entry".
 * @param openingBalanceHours balance carried in on the start date; positive means hours already in credit
 *
 * <p>Any of the numbers may be omitted in the file and fall back to 40, 8 and 0.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimesheetConfig(
        Double weeklyTargetHours,
        Double standardDayHours,
        LocalDate startDate,
        Double openingBalanceHours) {

    private static final double DEFAULT_WEEK = 40;
    private static final double DEFAULT_DAY = 8;

    public TimesheetConfig {
        weeklyTargetHours = weeklyTargetHours == null ? DEFAULT_WEEK : weeklyTargetHours;
        standardDayHours = standardDayHours == null ? DEFAULT_DAY : standardDayHours;
        openingBalanceHours = openingBalanceHours == null ? 0.0 : openingBalanceHours;
        if (weeklyTargetHours <= 0) {
            throw new TimesheetException("Weekly target must be greater than zero");
        }
        if (standardDayHours <= 0) {
            throw new TimesheetException("Standard day must be greater than zero");
        }
        if (standardDayHours > 24) {
            throw new TimesheetException("A standard day cannot exceed 24 hours");
        }
    }

    /** 40 hours a week, 8 hour days, counting from the first entry. Missing fields in the file fall back to these. */
    public static TimesheetConfig defaults() {
        return new TimesheetConfig(null, null, null, null);
    }

    public long weeklyTargetMinutes() {
        return Minutes.fromHours(weeklyTargetHours);
    }

    public long standardDayMinutes() {
        return Minutes.fromHours(standardDayHours);
    }

    public long openingBalanceMinutes() {
        return Minutes.fromHours(openingBalanceHours);
    }
}
