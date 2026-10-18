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
        assertText(text).contains("40h week, 8h per leave day. Generated 30 Sep 2026, 19:05.");

        // Week 1: a row per day, with breaks (and their notes), break totals and worked hours.
        assertText(text).contains("Week 21 Sep - 27 Sep 2026");
        assertText(text).contains("Mon 21 Sep Work 08:30 17:15 12:00-12:45 lunch with client 45m 8h");
        assertText(text).contains("Tue 22 Sep Work 09:00 18:30 10:30-10:45").contains("12:30-13:15 1h 8h 30m");
        assertText(text).contains("Wed 23 Sep Sick leave (4h) -4h target");
        assertText(text).contains("Thu 24 Sep Work 08:00 16:00 8h");
        assertText(text).contains("Fri 25 Sep Time in lieu from balance");
        assertText(text).contains("Sat 26 Sep -");
        assertText(text).contains("Total for the 7 days listed 1h 45m 24h 30m");
        assertText(text).contains("Full week: worked 24h 30m, target 36h after 4h leave. Carried in 0m, over/under -11h 30m, balance after -11h 30m.");

        // Week 2 is the current week, cut at today.
        assertText(text).contains("Week 28 Sep - 4 Oct 2026");
        assertText(text).contains("Mon 28 Sep Work 08:30 17:30 12:00-12:30 30m 8h 30m");
        assertText(text).contains("Tue 29 Sep Work 08:45 17:15 8h 30m");
        assertText(text).contains("Wed 30 Sep -");
        assertText(text).doesNotContain("Thu 1 Oct");
        assertText(text).contains("Total for the 3 days listed 30m 17h");
        assertText(text).contains("Carried in -11h 30m, over/under -23h, balance after -34h 30m.");

        // The upcoming holiday week is outside the period.
        assertText(text).doesNotContain("5 Oct - 11 Oct");

        assertText(text).contains("Period summary");
        assertText(text).contains("Hours worked 41h 30m");
        assertText(text).contains("Breaks 2h 15m");
        assertText(text).contains("Work days 5");
        assertText(text).contains("Weeks 2");
        assertText(text).contains("Carried into first week 0m");
        assertText(text).contains("Balance after last week -34h 30m");
    }

    @Test
    void aPeriodCutMidWeekListsOnlyThoseDaysButKeepsTheFullWeekLedger() throws IOException {
        var view = calculator.view(sheet(), NOW);
        var text = text(pdf.render(view, MON1.plusDays(2), MON1.plusDays(3), NOW));

        assertText(text).contains("23 September 2026 to 24 September 2026");
        assertText(text).contains("Wed 23 Sep").contains("Thu 24 Sep");
        assertText(text).doesNotContain("Mon 21 Sep").doesNotContain("Fri 25 Sep").doesNotContain("Week 28 Sep");
        assertText(text).contains("Total for the 2 days listed 0m 8h");
        assertText(text).contains("Full week: worked 24h 30m, target 36h after 4h leave.");
        assertText(text).contains("Hours worked 8h").contains("Work days 1").contains("Weeks 1");
    }

    @Test
    void anEmptyPeriodSaysSo() throws IOException {
        var view = calculator.view(sheet(), NOW);
        var text = text(pdf.render(view, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31), NOW));
        assertText(text).contains("Nothing recorded in this period.");
        assertText(text).doesNotContain("Period summary");
    }

    @Test
    void refusesAReversedPeriod() {
        var view = calculator.view(sheet(), NOW);
        assertThatThrownBy(() -> pdf.render(view, MON2, MON1, NOW)).isInstanceOf(IllegalArgumentException.class);
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
