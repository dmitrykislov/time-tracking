package com.kislov.timesheets.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kislov.timesheets.domain.Break;
import com.kislov.timesheets.domain.DayEntry;
import com.kislov.timesheets.domain.DayType;
import com.kislov.timesheets.domain.Timesheet;
import com.kislov.timesheets.domain.TimesheetCalculator;
import com.kislov.timesheets.domain.TimesheetConfig;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class TimesheetPdfTest {

    static final LocalDate MON1 = LocalDate.of(2026, 9, 21);
    static final LocalDate MON2 = MON1.plusWeeks(1);
    static final LocalDateTime NOW = MON2.plusDays(2).atTime(19, 5); // Wed 30 Sep

    final TimesheetPdf pdf = new TimesheetPdf();
    final TimesheetCalculator calculator = new TimesheetCalculator();

    static Timesheet sheet() {
        return new Timesheet(new TimesheetConfig(40.0, 8.0, MON1, 0.0), List.of(
                DayEntry.work(MON1, LocalTime.of(8, 30), LocalTime.of(17, 15), List.of(new Break(LocalTime.of(12, 0), LocalTime.of(12, 45), "lunch with client"))),
                DayEntry.work(MON1.plusDays(1), LocalTime.of(9, 0), LocalTime.of(18, 30),
                        List.of(new Break(LocalTime.of(10, 30), LocalTime.of(10, 45)), new Break(LocalTime.of(12, 30), LocalTime.of(13, 15)))),
                DayEntry.leave(MON1.plusDays(2), DayType.SICK, 4.0),
                DayEntry.work(MON1.plusDays(3), LocalTime.of(8, 0), LocalTime.of(16, 0), List.of()),
                DayEntry.leave(MON1.plusDays(4), DayType.TIME_IN_LIEU, null),
                DayEntry.work(MON2, LocalTime.of(8, 30), LocalTime.of(17, 30), List.of(new Break(LocalTime.of(12, 0), LocalTime.of(12, 30)))),
                DayEntry.work(MON2.plusDays(1), LocalTime.of(8, 45), LocalTime.of(17, 15), List.of()),
                DayEntry.leave(MON2.plusWeeks(1), DayType.PUBLIC_HOLIDAY, null)));
    }

    static String text(byte[] bytes) throws IOException {
        var reader = new PdfReader(bytes);
        var extractor = new PdfTextExtractor(reader);
        var sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            sb.append(extractor.getTextFromPage(i)).append('\n');
        }
        reader.close();
        // The extractor does not put spaces between table cells, so compare with all whitespace removed.
        return squash(sb.toString());
    }

    static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    static org.assertj.core.api.AbstractStringAssert<?> assertText(String text) {
        return new org.assertj.core.api.StringAssert(text) {
            @Override
            public org.assertj.core.api.StringAssert contains(CharSequence... values) {
                return super.contains(java.util.Arrays.stream(values).map(v -> squash(v.toString())).toArray(CharSequence[]::new));
            }

            @Override
            public org.assertj.core.api.StringAssert doesNotContain(CharSequence... values) {
                return super.doesNotContain(java.util.Arrays.stream(values).map(v -> squash(v.toString())).toArray(CharSequence[]::new));
            }
        };
    }

    @Test
    void rendersEveryWeekInThePeriodWithDaysLedgerAndSummary() throws IOException {
        var view = calculator.view(sheet(), NOW);
        var bytes = pdf.render(view, MON1, NOW.toLocalDate(), NOW);
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");

        var text = text(bytes);
        assertText(text).contains("Timesheet").contains("21 September 2026 to 30 September 2026");
        assertText(text).contains("40h week, 8h per leave day").contains("Counting from 21 Sep 2026").contains("Generated 30 Sep 2026, 19:05");

        // Summary strip: the balance story for the whole period, carried in included.
        assertText(text).contains("HOURS WORKED 41h 30m 5 work days, 2 weeks");
        assertText(text).contains("BREAKS 2h 15m");
        assertText(text).contains("CARRIED IN 0m balance before 21 Sep 2026");
        assertText(text).contains("OVER / UNDER -34h 30m across the weeks shown");
        assertText(text).contains("BALANCE AFTER -34h 30m going into the week after 4 Oct 2026");

        // Week 1: a row per day, with breaks (and their notes), break totals and worked hours.
        assertText(text).contains("Week 21 - 27 September 2026").contains("Monday to Sunday");
        assertText(text).contains("Mon 21 Sep Work 08:30 17:15 12:00-12:45 lunch with client 45m 8h");
        assertText(text).contains("Tue 22 Sep Work 09:00 18:30 10:30-10:45").contains("12:30-13:15 1h 8h 30m");
        assertText(text).contains("Wed 23 Sep Sick leave (4h) -4h target");
        assertText(text).contains("Thu 24 Sep Work 08:00 16:00 8h");
        assertText(text).contains("Fri 25 Sep Time in lieu from balance");
        assertText(text).contains("Sat 26 Sep Sun 27 Sep");
        assertText(text).contains("Total for the 7 days listed 1h 45m 24h 30m");
        // Week ledger: worked, target (after leave), then carried in + over/under = balance after.
        assertText(text).contains("WORKED 24h 30m TARGET 36h after 4h leave CARRIED IN 0m + OVER / UNDER -11h 30m = BALANCE AFTER -11h 30m");

        // Week 2 is the current week, cut at today.
        assertText(text).contains("Week 28 September - 4 October 2026").contains("3 of 7 days fall inside the period");
        assertText(text).contains("Mon 28 Sep Work 08:30 17:30 12:00-12:30 30m 8h 30m");
        assertText(text).contains("Tue 29 Sep Work 08:45 17:15 8h 30m");
        assertText(text).contains("Wed 30 Sep Total for the 3 days listed");
        assertText(text).doesNotContain("Thu 1 Oct");
        assertText(text).contains("Total for the 3 days listed 30m 17h");
        assertText(text).contains("CARRIED IN -11h 30m + OVER / UNDER -23h = BALANCE AFTER -34h 30m");

        // The upcoming holiday week is outside the period.
        assertText(text).doesNotContain("5 - 11 October");
        assertText(text).contains("Page 1 of");
    }

    @Test
    void aPeriodCutMidWeekListsOnlyThoseDaysButKeepsTheFullWeekLedger() throws IOException {
        var view = calculator.view(sheet(), NOW);
        var text = text(pdf.render(view, MON1.plusDays(2), MON1.plusDays(3), NOW));

        assertText(text).contains("23 September 2026 to 24 September 2026");
        assertText(text).contains("Wed 23 Sep").contains("Thu 24 Sep");
        assertText(text).doesNotContain("Mon 21 Sep").doesNotContain("Fri 25 Sep").doesNotContain("Week 28 Sep");
        assertText(text).contains("Total for the 2 days listed 0m 8h");
        assertText(text).contains("2 of 7 days fall inside the period");
        assertText(text).contains("WORKED 24h 30m TARGET 36h after 4h leave");
        assertText(text).contains("HOURS WORKED 8h 1 work day, 1 week");
        assertText(text).contains("CARRIED IN 0m balance before 23 Sep 2026");
        assertText(text).contains("BALANCE AFTER -11h 30m going into the week after 27 Sep 2026");
    }

    @Test
    void anEmptyPeriodSaysSo() throws IOException {
        var view = calculator.view(sheet(), NOW);
        var text = text(pdf.render(view, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31), NOW));
        assertText(text).contains("Nothing recorded in this period.");
        assertText(text).doesNotContain("HOURS WORKED");
    }

    @Test
    void rendersRunningUnfinishedBeforeStartAndFractionalLeaveDays() throws IOException {
        var wednesday = MON1.plusDays(2);
        var ts = new Timesheet(new TimesheetConfig(38.0, 7.6, wednesday, 2.0), List.of(
                DayEntry.work(MON1, LocalTime.of(9, 0), LocalTime.of(17, 0), List.of()),            // before start: ignored
                DayEntry.work(wednesday, LocalTime.of(9, 0), null, List.of()),                         // never ended
                DayEntry.leave(wednesday.plusDays(1), DayType.SICK, 3.8),                              // fractional hours
                DayEntry.work(wednesday.plusDays(2), LocalTime.of(8, 0), null,
                        List.of(new Break(LocalTime.of(10, 0), LocalTime.of(10, 10), "coffee"), new Break(LocalTime.of(12, 0), null)))));
        var now = wednesday.plusDays(2).atTime(12, 20);                                                 // Friday, on a break
        var view = calculator.view(ts, now);
        var text = text(pdf.render(view, MON1, now.toLocalDate(), now));

        assertText(text).contains("Mon 21 Sep Before start");
        assertText(text).contains("Wed 23 Sep Work 09:00 never ended");
        assertText(text).contains("Thu 24 Sep Sick leave (3.8h) -3h 48m target");
        assertText(text).contains("Fri 25 Sep Work 08:00 running 10:00-10:10 coffee 12:00-now 30m 3h 50m");
        assertText(text).contains("Total for the 5 days listed 30m 3h 50m");
        // Prorated first week: Mon and Tue before the start take 7.6h each off the 38h target.
        assertText(text).contains("WORKED 3h 50m TARGET 19h after 19h leave CARRIED IN +2h");
        assertText(text).contains("CARRIED IN +2h balance before 21 Sep 2026");
        assertText(text).contains("Page 1 of 1");
    }

    @Test
    void weekendWorkIsListedLikeAnyOtherDay() throws IOException {
        var saturday = MON1.plusDays(5);
        var ts = sheet().withDay(DayEntry.work(saturday, LocalTime.of(10, 0), LocalTime.of(14, 0), List.of()));
        var text = text(pdf.render(calculator.view(ts, NOW), MON1, MON1.plusDays(6), NOW));
        assertText(text).contains("Sat 26 Sep Work 10:00 14:00 4h");
        assertText(text).contains("Total for the 7 days listed 1h 45m 28h 30m");
    }

    @Test
    void aSingleDayPeriodIsWordedInTheSingular() throws IOException {
        var view = calculator.view(sheet(), NOW);
        var text = text(pdf.render(view, MON1, MON1, NOW));
        assertText(text).contains("1 of 7 days fall inside the period");
        assertText(text).contains("Total for the 1 day listed 45m 8h");
        assertText(text).contains("HOURS WORKED 8h 1 work day, 1 week");
    }

    @Test
    void refusesAReversedPeriod() {
        var view = calculator.view(sheet(), NOW);
        assertThatThrownBy(() -> pdf.render(view, MON2, MON1, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    /** A period spanning an earlier balance shows that balance as carried in, so the reader sees where the week started. */
    @Test
    void carriedInFromBeforeThePeriodIsShownUpFront() throws IOException {
        var ts = sheet().withConfig(new TimesheetConfig(40.0, 8.0, MON1, 3.5));
        var view = calculator.view(ts, NOW);
        var text = text(pdf.render(view, MON2, NOW.toLocalDate(), NOW));

        // Week 1 ended at +3h30 - 11h30 = -8h, which is what the printed period starts with.
        assertText(text).contains("CARRIED IN -8h balance before 28 Sep 2026");
        assertText(text).contains("OVER / UNDER -23h across the weeks shown");
        assertText(text).contains("BALANCE AFTER -31h going into the week after 4 Oct 2026");
        assertText(text).contains("CARRIED IN -8h + OVER / UNDER -23h = BALANCE AFTER -31h");
        assertText(text).doesNotContain("Week 21 - 27 September");
    }

    @Test
    void weekRangesReadNaturally() {
        var sameMonth = calculator.view(sheet(), NOW).weeks().getLast();
        assertThat(TimesheetPdf.weekRange(sameMonth)).isEqualTo("21 - 27 September 2026");
        var crossMonth = calculator.view(sheet(), NOW).weeks().get(1);
        assertThat(TimesheetPdf.weekRange(crossMonth)).isEqualTo("28 September - 4 October 2026");
        var crossYear = calculator.view(Timesheet.empty(), LocalDate.of(2026, 12, 30).atTime(9, 0)).weeks().getFirst();
        assertThat(TimesheetPdf.weekRange(crossYear)).isEqualTo("28 December 2026 - 3 January 2027");
    }

    @Test
    void formatsHoursWithPlainAsciiSigns() {
        assertThat(TimesheetPdf.hours(0)).isEqualTo("0m");
        assertThat(TimesheetPdf.hours(45)).isEqualTo("45m");
        assertThat(TimesheetPdf.hours(480)).isEqualTo("8h");
        assertThat(TimesheetPdf.hours(-690)).isEqualTo("-11h 30m");
        assertThat(TimesheetPdf.signed(90)).isEqualTo("+1h 30m");
        assertThat(TimesheetPdf.signed(-90)).isEqualTo("-1h 30m");
        assertThat(TimesheetPdf.signed(0)).isEqualTo("0m");
    }
}
