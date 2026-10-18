package com.kislov.timesheets.export;

import com.kislov.timesheets.domain.DayStats;
import com.kislov.timesheets.domain.TimesheetView;
import com.kislov.timesheets.domain.WeekStats;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Renders the timesheet as a printable PDF: one table per week with a row per day, the week's
 * ledger underneath (carried in, over/under, balance after), and a total for the period.
 *
 * <p>Only days inside the requested period are listed, so a month that starts mid-week prints the
 * right days; the ledger line is always the full Monday-to-Sunday week, as on screen.
 */
public final class TimesheetPdf {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font MUTED = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(0x66, 0x6B, 0x75));
    private static final Font HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(0x55, 0x5A, 0x64));
    private static final Color RULE = new Color(0xDD, 0xDF, 0xE3);
    private static final Color HEAD_BG = new Color(0xF2, 0xF3, 0xF5);
    private static final Color TOTAL_BG = new Color(0xF7, 0xF8, 0xFA);

    /** Builds the document for every week that overlaps {@code from}..{@code to}. */
    public byte[] render(TimesheetView view, LocalDate from, LocalDate to, LocalDateTime generatedAt) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        var weeks = view.weeks().reversed().stream()
                .filter(w -> !w.end().isBefore(from) && !w.start().isAfter(to))
                .toList();

        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 40, 40, 44, 44);
        PdfWriter.getInstance(document, out);
        document.addTitle("Timesheet " + from + " to " + to);
        document.addCreator("Timesheets");
        document.open();

        document.add(new Paragraph("Timesheet", TITLE));
        document.add(new Paragraph(LONG_DAY.format(from) + " to " + LONG_DAY.format(to), H2));
        var config = view.config();
        document.add(muted("%s week, %s per leave day. Generated %s."
                .formatted(hours(config.weeklyTargetMinutes()), hours(config.standardDayMinutes()), STAMP.format(generatedAt))));
        document.add(spacer(10));

        long periodWorked = 0;
        int periodDays = 0;
        long periodBreaks = 0;
        for (var week : weeks) {
            var days = week.days().stream().filter(d -> !d.date().isBefore(from) && !d.date().isAfter(to)).toList();
            document.add(weekHeading(week));
            document.add(dayTable(days, view.now()));
            document.add(ledger(week));
            document.add(spacer(12));
            periodWorked += days.stream().mapToLong(DayStats::workedMinutes).sum();
            periodBreaks += days.stream().mapToLong(d -> breakMinutes(d, view.now())).sum();
            periodDays += (int) days.stream().filter(d -> d.entry() != null && d.entry().isWork()).count();
        }

        if (weeks.isEmpty()) {
            document.add(new Paragraph("Nothing recorded in this period.", BODY));
        } else {
            document.add(summary(weeks, periodWorked, periodBreaks, periodDays));
        }
        document.close();
        return out.toByteArray();
    }

    private static Paragraph weekHeading(WeekStats week) {
        var year = week.end().getYear();
        var p = new Paragraph("Week " + DAY.format(week.start()).substring(4) + " - " + DAY.format(week.end()).substring(4) + " " + year, H2);
        p.setSpacingBefore(4);
        p.setSpacingAfter(5);
        return p;
    }

    private static PdfPTable dayTable(List<DayStats> days, LocalTime now) {
        var table = new PdfPTable(new float[] {17, 15, 9, 9, 28, 10, 12});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (var title : new String[] {"Day", "Type", "Start", "Finish", "Breaks", "Breaks total", "Worked"}) {
            table.addCell(head(title));
        }
        long worked = 0;
        long breaks = 0;
        for (var day : days) {
            var e = day.entry();
            var dayBreaks = breakMinutes(day, now);
            worked += day.workedMinutes();
            breaks += dayBreaks;
            table.addCell(cell(DAY.format(day.date()), BODY, Element.ALIGN_LEFT));
            if (e == null) {
                table.addCell(cell(day.beforeStart() ? "Before start" : "-", MUTED, Element.ALIGN_LEFT));
                table.addCell(cell("", BODY, Element.ALIGN_LEFT));
                table.addCell(cell("", BODY, Element.ALIGN_LEFT));
                table.addCell(cell("", BODY, Element.ALIGN_LEFT));
                table.addCell(cell("", BODY, Element.ALIGN_RIGHT));
                table.addCell(cell("", BODY, Element.ALIGN_RIGHT));
                continue;
            }
            var type = e.type().label() + (e.hours() != null ? " (" + trim(e.hours()) + "h)" : "");
            table.addCell(cell(type, BODY, Element.ALIGN_LEFT));
            table.addCell(cell(e.start() == null ? "" : HHMM.format(e.start()), BODY, Element.ALIGN_LEFT));
            table.addCell(cell(e.finish() == null ? (e.isWork() ? (day.unfinished() ? "never ended" : "running") : "") : HHMM.format(e.finish()), BODY, Element.ALIGN_LEFT));
            var breakText = e.breaks().stream()
                    .map(b -> HHMM.format(b.start()) + "-" + (b.finish() == null ? "now" : HHMM.format(b.finish())) + (b.note() == null ? "" : " " + b.note()))
                    .reduce((a, b) -> a + "\n" + b).orElse("");
            table.addCell(cell(breakText, BODY, Element.ALIGN_LEFT));
            table.addCell(cell(e.breaks().isEmpty() ? "" : hours(dayBreaks), BODY, Element.ALIGN_RIGHT));
            var workedText = e.isWork() ? hours(day.workedMinutes()) : (day.targetReductionMinutes() > 0 ? "-" + hours(day.targetReductionMinutes()) + " target" : "from balance");
            table.addCell(cell(workedText, e.isWork() ? BOLD : MUTED, Element.ALIGN_RIGHT));
        }
        var label = cell("Total for the " + days.size() + " days listed", BOLD, Element.ALIGN_LEFT);
        label.setColspan(5);
        label.setBackgroundColor(TOTAL_BG);
        table.addCell(label);
        var breaksTotal = cell(hours(breaks), BOLD, Element.ALIGN_RIGHT);
        breaksTotal.setBackgroundColor(TOTAL_BG);
        table.addCell(breaksTotal);
        var workedTotal = cell(hours(worked), BOLD, Element.ALIGN_RIGHT);
        workedTotal.setBackgroundColor(TOTAL_BG);
        table.addCell(workedTotal);
        return table;
    }

    private static Paragraph ledger(WeekStats week) {
        var target = hours(week.targetMinutes()) + (week.leaveMinutes() > 0 ? " after " + hours(week.leaveMinutes()) + " leave" : "");
        var text = "Full week: worked %s, target %s. Carried in %s, over/under %s, balance after %s."
                .formatted(hours(week.workedMinutes()), target, signed(week.carryInMinutes()), signed(week.balanceMinutes()), signed(week.runningBalanceMinutes()));
        var p = new Paragraph(text, MUTED);
        p.setSpacingBefore(4);
        return p;
    }

    private static PdfPTable summary(List<WeekStats> weeks, long worked, long breaks, long workDays) {
        var last = weeks.getLast();
        var table = new PdfPTable(new float[] {40, 60});
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingBefore(6);
        var title = cell("Period summary", H2, Element.ALIGN_LEFT);
        title.setColspan(2);
        title.setBorder(0);
        title.setPaddingBottom(6);
        table.addCell(title);
        row(table, "Hours worked", hours(worked));
        row(table, "Breaks", hours(breaks));
        row(table, "Work days", String.valueOf(workDays));
        row(table, "Weeks", String.valueOf(weeks.size()));
        row(table, "Carried into first week", signed(weeks.getFirst().carryInMinutes()));
        row(table, "Balance after last week", signed(last.runningBalanceMinutes()));
        return table;
    }

    private static void row(PdfPTable table, String label, String value) {
        table.addCell(cell(label, MUTED, Element.ALIGN_LEFT));
        table.addCell(cell(value, BOLD, Element.ALIGN_LEFT));
    }

    private static long breakMinutes(DayStats day, LocalTime now) {
        var e = day.entry();
        if (e == null || !e.isWork() || day.unfinished()) {
            return 0;
        }
        var end = e.finish() != null ? e.finish() : now;
        return e.breaks().stream().mapToLong(b -> b.minutes(end)).sum();
    }

    private static PdfPCell head(String text) {
        var c = cell(text, HEAD, text.equals("Worked") || text.equals("Breaks total") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.setBackgroundColor(HEAD_BG);
        c.setPaddingTop(5);
        c.setPaddingBottom(5);
        return c;
    }

    private static PdfPCell cell(String text, Font font, int align) {
        var c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBorderWidth(0);
        c.setBorderWidthBottom(0.5f);
        c.setBorderColor(RULE);
        c.setPaddingTop(4.5f);
        c.setPaddingBottom(4.5f);
        c.setPaddingLeft(5);
        c.setPaddingRight(5);
        return c;
    }

    private static Paragraph muted(String text) {
        return new Paragraph(text, MUTED);
    }

    private static Paragraph spacer(float height) {
        var p = new Paragraph(" ", BODY);
        p.setSpacingBefore(height / 2);
        p.setSpacingAfter(height / 2);
        return p;
    }

    /** 450 -> "7h 30m"; uses a plain hyphen for negatives because the built-in fonts lack U+2212. */
    static String hours(long minutes) {
        var abs = Math.abs(minutes);
        var h = abs / 60;
        var m = abs % 60;
        var text = h == 0 ? m + "m" : m == 0 ? h + "h" : h + "h " + m + "m";
        return minutes < 0 ? "-" + text : text;
    }

    static String signed(long minutes) {
        return minutes > 0 ? "+" + hours(minutes) : hours(minutes);
    }

    private static String trim(double hours) {
        return hours == Math.rint(hours) ? String.valueOf((long) hours) : String.valueOf(hours);
    }
}
