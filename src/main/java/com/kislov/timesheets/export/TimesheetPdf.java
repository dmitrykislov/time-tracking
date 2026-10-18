package com.kislov.timesheets.export;

import com.kislov.timesheets.domain.DayStats;
import com.kislov.timesheets.domain.TimesheetView;
import com.kislov.timesheets.domain.WeekStats;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Renders the timesheet as a printable PDF.
 *
 * <p>Top: the period and a summary strip (hours worked, breaks, what was carried in from before
 * the period, the period's over/under, and the balance after it). Then one section per week: a
 * table with a row per day and a total row, followed by the week's ledger laid out as
 * <em>carried in + over/under = balance after</em>, exactly as on screen.
 *
 * <p>Only days inside the requested period are listed, so a month that starts mid-week prints the
 * right days; the ledger is always the full Monday-to-Sunday week.
 */
public final class TimesheetPdf {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    // Palette: near-black text, two greys, a blue accent, green for credit and amber for debt.
    private static final Color INK = new Color(0x1C, 0x1F, 0x24);
    private static final Color MUTED_INK = new Color(0x6B, 0x72, 0x80);
    private static final Color LABEL_INK = new Color(0x55, 0x5A, 0x64);
    private static final Color ACCENT = new Color(0x1D, 0x4E, 0xD8);
    private static final Color POSITIVE = new Color(0x15, 0x80, 0x3D);
    private static final Color NEGATIVE = new Color(0xB4, 0x53, 0x09);
    private static final Color RULE = new Color(0xE3, 0xE5, 0xE9);
    private static final Color HEAD_BG = new Color(0xF1, 0xF3, 0xF6);
    private static final Color ZEBRA_BG = new Color(0xFA, 0xFB, 0xFC);
    private static final Color STRIP_BG = new Color(0xF6, 0xF7, 0xF9);
    private static final Color ACCENT_BG = new Color(0xEE, 0xF3, 0xFD);

    private static final Font TITLE = font(FontFactory.HELVETICA_BOLD, 22, INK);
    private static final Font SUBTITLE = font(FontFactory.HELVETICA, 11, MUTED_INK);
    private static final Font H2 = font(FontFactory.HELVETICA_BOLD, 12.5f, INK);
    private static final Font H2_MUTED = font(FontFactory.HELVETICA, 9.5f, MUTED_INK);
    private static final Font BODY = font(FontFactory.HELVETICA, 9, INK);
    private static final Font BOLD = font(FontFactory.HELVETICA_BOLD, 9, INK);
    private static final Font MUTED = font(FontFactory.HELVETICA, 8.5f, MUTED_INK);
    private static final Font MUTED_SMALL = font(FontFactory.HELVETICA, 7.5f, MUTED_INK);
    private static final Font LABEL = font(FontFactory.HELVETICA_BOLD, 7, LABEL_INK);
    private static final Font KPI = font(FontFactory.HELVETICA_BOLD, 13, INK);
    private static final Font LEDGER = font(FontFactory.HELVETICA_BOLD, 10, INK);
    private static final Font OPERATOR = font(FontFactory.HELVETICA, 12, MUTED_INK);

    private static Font font(String name, float size, Color color) {
        var bold = name.endsWith("-Bold");
        return FontFactory.getFont(bold ? FontFactory.HELVETICA : name, size, bold ? Font.BOLD : Font.NORMAL, color);
    }

    /** Builds the document for every week that overlaps {@code from}..{@code to}. */
    public byte[] render(TimesheetView view, LocalDate from, LocalDate to, LocalDateTime generatedAt) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        var weeks = view.weeks().reversed().stream()
                .filter(w -> !w.end().isBefore(from) && !w.start().isAfter(to))
                .toList();

        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 42, 42, 46, 52);
        var writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new Footer("Timesheet  " + SHORT_DAY.format(from) + " - " + SHORT_DAY.format(to)));
        document.addTitle("Timesheet " + from + " to " + to);
        document.addCreator("Timesheets");
        document.open();

        document.add(header(view, from, to, generatedAt));

        if (weeks.isEmpty()) {
            var p = new Paragraph("Nothing recorded in this period.", BODY);
            p.setSpacingBefore(14);
            document.add(p);
            document.close();
            return out.toByteArray();
        }

        long periodWorked = 0;
        long periodBreaks = 0;
        int workDays = 0;
        for (var week : weeks) {
            var days = daysInRange(week, from, to);
            periodWorked += days.stream().mapToLong(DayStats::workedMinutes).sum();
            periodBreaks += days.stream().mapToLong(d -> breakMinutes(d, view.now())).sum();
            workDays += (int) days.stream().filter(d -> d.entry() != null && d.entry().isWork()).count();
        }
        document.add(summaryStrip(weeks, from, to, periodWorked, periodBreaks, workDays));

        for (var week : weeks) {
            var days = daysInRange(week, from, to);
            // One block per week, kept on a single page so a heading never strands at the bottom of one.
            var block = new PdfPTable(1);
            block.setWidthPercentage(100);
            block.setKeepTogether(true);
            block.setSpacingAfter(16);
            var cell = new PdfPCell();
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(0);
            cell.addElement(weekHeading(week, days.size()));
            cell.addElement(dayTable(days, view.now()));
            cell.addElement(ledger(week));
            block.addCell(cell);
            document.add(block);
        }
        document.close();
        return out.toByteArray();
    }

    private static List<DayStats> daysInRange(WeekStats week, LocalDate from, LocalDate to) {
        return week.days().stream().filter(d -> !d.date().isBefore(from) && !d.date().isAfter(to)).toList();
    }

    // ---------------------------------------------------------------- header and summary

    private static PdfPTable header(TimesheetView view, LocalDate from, LocalDate to, LocalDateTime generatedAt) {
        var table = new PdfPTable(new float[] {55, 45});
        table.setWidthPercentage(100);
        table.setSpacingAfter(14);

        var left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);
        left.addElement(new Paragraph("Timesheet", TITLE));
        var period = new Paragraph(LONG_DAY.format(from) + " to " + LONG_DAY.format(to), SUBTITLE);
        period.setSpacingBefore(2);
        left.addElement(period);
        table.addCell(left);

        var right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        right.setPaddingTop(6);
        var config = view.config();
        right.addElement(rightAligned("%s week, %s per leave day".formatted(hours(config.weeklyTargetMinutes()), hours(config.standardDayMinutes())), MUTED));
        right.addElement(rightAligned("Counting from " + SHORT_DAY.format(view.startDate()), MUTED));
        right.addElement(rightAligned("Generated " + STAMP.format(generatedAt), MUTED));
        table.addCell(right);
        return table;
    }

    private static Paragraph rightAligned(String text, Font font) {
        var p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        p.setLeading(11.5f);
        return p;
    }

    /** The five numbers that matter for the period, in a tinted strip. */
    private static PdfPTable summaryStrip(List<WeekStats> weeks, LocalDate from, LocalDate to, long worked, long breaks, int workDays) {
        var first = weeks.getFirst();
        var last = weeks.getLast();
        long carriedIn = first.carryInMinutes();
        long after = last.runningBalanceMinutes();
        long overUnder = after - carriedIn;

        var table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingAfter(18);
        table.addCell(kpi("Hours worked", hours(worked), INK, workDays + (workDays == 1 ? " work day" : " work days") + ", " + weeks.size() + (weeks.size() == 1 ? " week" : " weeks")));
        table.addCell(kpi("Breaks", hours(breaks), INK, "inside the days listed"));
        table.addCell(kpi("Carried in", signed(carriedIn), tone(carriedIn), "balance before " + SHORT_DAY.format(from)));
        table.addCell(kpi("Over / under", signed(overUnder), tone(overUnder), "across the weeks shown"));
        var afterCell = kpi("Balance after", signed(after), tone(after), "going into the week after " + SHORT_DAY.format(last.end()));
        afterCell.setBackgroundColor(ACCENT_BG);
        table.addCell(afterCell);
        return table;
    }

    private static PdfPCell kpi(String label, String value, Color valueColor, String hint) {
        var cell = new PdfPCell();
        cell.setBackgroundColor(STRIP_BG);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(10);
        cell.setPaddingBottom(9);
        cell.addElement(new Paragraph(label.toUpperCase(Locale.ENGLISH), LABEL));
        var v = new Paragraph(value, font(FontFactory.HELVETICA_BOLD, 15, valueColor));
        v.setSpacingBefore(3);
        v.setLeading(17);
        cell.addElement(v);
        var h = new Paragraph(hint, MUTED_SMALL);
        h.setLeading(9.5f);
        cell.addElement(h);
        return cell;
    }

    // ---------------------------------------------------------------- weeks

    private static PdfPTable weekHeading(WeekStats week, int daysListed) {
        var table = new PdfPTable(new float[] {55, 45});
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(5);
        table.setKeepTogether(true);

        var left = new PdfPCell(new Phrase("Week " + weekRange(week), H2));
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);
        left.setPaddingTop(6);
        left.setVerticalAlignment(Element.ALIGN_BOTTOM);
        table.addCell(left);

        var note = daysListed < 7 ? daysListed + " of 7 days fall inside the period" : "Monday to Sunday";
        var right = new PdfPCell(new Phrase(note, H2_MUTED));
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        right.setPaddingTop(6);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setVerticalAlignment(Element.ALIGN_BOTTOM);
        table.addCell(right);
        return table;
    }

    /** "21 - 27 September 2026" or "28 September - 4 October 2026". */
    static String weekRange(WeekStats week) {
        var start = week.start();
        var end = week.end();
        if (start.getMonth() == end.getMonth()) {
            return start.getDayOfMonth() + " - " + LONG_DAY.format(end);
        }
        var startText = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH).format(start);
        if (start.getYear() != end.getYear()) {
            startText += " " + start.getYear();
        }
        return startText + " - " + LONG_DAY.format(end);
    }

    private static PdfPTable dayTable(List<DayStats> days, LocalTime now) {
        var table = new PdfPTable(new float[] {15, 15, 9, 9, 29, 11, 12});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSplitLate(false);
        var titles = new String[] {"Day", "Type", "Start", "Finish", "Breaks", "Breaks total", "Worked"};
        for (var title : titles) {
            table.addCell(head(title));
        }
        long worked = 0;
        long breaks = 0;
        int row = 0;
        for (var day : days) {
            var e = day.entry();
            var weekend = day.date().getDayOfWeek() == DayOfWeek.SATURDAY || day.date().getDayOfWeek() == DayOfWeek.SUNDAY;
            var bg = row++ % 2 == 1 ? ZEBRA_BG : null;
            var dayBreaks = breakMinutes(day, now);
            worked += day.workedMinutes();
            breaks += dayBreaks;
            var dayFont = weekend && e == null ? MUTED : BODY;
            table.addCell(cell(DAY.format(day.date()), dayFont, Element.ALIGN_LEFT, bg));
            if (e == null) {
                table.addCell(cell(day.beforeStart() ? "Before start" : "", MUTED, Element.ALIGN_LEFT, bg));
                for (int i = 0; i < 5; i++) {
                    table.addCell(cell("", BODY, Element.ALIGN_LEFT, bg));
                }
                continue;
            }
            var type = e.type().label() + (e.hours() != null ? " (" + trim(e.hours()) + "h)" : "");
            table.addCell(cell(type, e.isWork() ? BODY : MUTED, Element.ALIGN_LEFT, bg));
            table.addCell(cell(e.start() == null ? "" : HHMM.format(e.start()), BODY, Element.ALIGN_LEFT, bg));
            var finish = e.finish() != null ? HHMM.format(e.finish()) : e.isWork() ? (day.unfinished() ? "never ended" : "running") : "";
            table.addCell(cell(finish, e.finish() == null && e.isWork() ? MUTED : BODY, Element.ALIGN_LEFT, bg));
            var breakText = e.breaks().stream()
                    .map(b -> HHMM.format(b.start()) + "-" + (b.finish() == null ? "now" : HHMM.format(b.finish())) + (b.note() == null ? "" : "  " + b.note()))
                    .reduce((a, b) -> a + "\n" + b).orElse("");
            table.addCell(cell(breakText, BODY, Element.ALIGN_LEFT, bg));
            table.addCell(cell(e.breaks().isEmpty() ? "" : hours(dayBreaks), BODY, Element.ALIGN_RIGHT, bg));
            if (e.isWork()) {
                table.addCell(cell(hours(day.workedMinutes()), BOLD, Element.ALIGN_RIGHT, bg));
            } else {
                var text = day.targetReductionMinutes() > 0 ? "-" + hours(day.targetReductionMinutes()) + " target" : "from balance";
                table.addCell(cell(text, MUTED, Element.ALIGN_RIGHT, bg));
            }
        }
        var label = cell("Total for the " + days.size() + (days.size() == 1 ? " day" : " days") + " listed", BOLD, Element.ALIGN_LEFT, HEAD_BG);
        label.setColspan(5);
        label.setBorderWidthTop(1);
        label.setBorderColorTop(MUTED_INK);
        table.addCell(label);
        var breaksTotal = cell(hours(breaks), BOLD, Element.ALIGN_RIGHT, HEAD_BG);
        breaksTotal.setBorderWidthTop(1);
        breaksTotal.setBorderColorTop(MUTED_INK);
        table.addCell(breaksTotal);
        var workedTotal = cell(hours(worked), BOLD, Element.ALIGN_RIGHT, HEAD_BG);
        workedTotal.setBorderWidthTop(1);
        workedTotal.setBorderColorTop(MUTED_INK);
        table.addCell(workedTotal);
        return table;
    }

    /** The week's ledger as on screen: worked and target on the left, carried in + over/under = balance after on the right. */
    private static PdfPTable ledger(WeekStats week) {
        var table = new PdfPTable(new float[] {14, 18, 10, 14, 4, 14, 4, 16});
        table.setWidthPercentage(100);
        table.setSpacingBefore(0);

        table.addCell(ledgerCell("Worked", hours(week.workedMinutes()), INK, null));
        var targetHint = week.leaveMinutes() > 0 ? "after " + hours(week.leaveMinutes()) + " leave" : null;
        table.addCell(ledgerCell("Target", hours(week.targetMinutes()), INK, targetHint));
        table.addCell(ledgerSpacer());
        table.addCell(ledgerCell("Carried in", signed(week.carryInMinutes()), tone(week.carryInMinutes()), null));
        table.addCell(operator("+"));
        table.addCell(ledgerCell("Over / under", signed(week.balanceMinutes()), tone(week.balanceMinutes()), null));
        table.addCell(operator("="));
        var after = ledgerCell("Balance after", signed(week.runningBalanceMinutes()), tone(week.runningBalanceMinutes()), null);
        after.setBackgroundColor(ACCENT_BG);
        table.addCell(after);
        return table;
    }

    private static PdfPCell ledgerCell(String label, String value, Color color, String hint) {
        var cell = new PdfPCell();
        cell.setBackgroundColor(STRIP_BG);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(7);
        cell.setPaddingLeft(9);
        cell.addElement(new Paragraph(label.toUpperCase(Locale.ENGLISH), LABEL));
        var v = new Paragraph(value, font(FontFactory.HELVETICA_BOLD, 10.5f, color));
        v.setSpacingBefore(2);
        v.setLeading(12.5f);
        cell.addElement(v);
        if (hint != null) {
            var h = new Paragraph(hint, MUTED_SMALL);
            h.setLeading(9);
            cell.addElement(h);
        }
        return cell;
    }

    private static PdfPCell ledgerSpacer() {
        var cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private static PdfPCell operator(String symbol) {
        var cell = new PdfPCell(new Phrase(symbol, OPERATOR));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(0);
        return cell;
    }

    // ---------------------------------------------------------------- cells and formatting

    private static long breakMinutes(DayStats day, LocalTime now) {
        var e = day.entry();
        if (e == null || !e.isWork() || day.unfinished()) {
            return 0;
        }
        var end = e.finish() != null ? e.finish() : now;
        return e.breaks().stream().mapToLong(b -> b.minutes(end)).sum();
    }

    private static PdfPCell head(String text) {
        var right = text.equals("Worked") || text.equals("Breaks total");
        var c = cell(text.toUpperCase(Locale.ENGLISH), LABEL, right ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, HEAD_BG);
        c.setPaddingTop(6);
        c.setPaddingBottom(6);
        c.setBorderWidthBottom(0);
        return c;
    }

    private static PdfPCell cell(String text, Font font, int align, Color background) {
        var c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBorderWidth(0);
        c.setBorderWidthBottom(0.5f);
        c.setBorderColor(RULE);
        c.setPaddingTop(5);
        c.setPaddingBottom(5);
        c.setPaddingLeft(6);
        c.setPaddingRight(6);
        if (background != null) {
            c.setBackgroundColor(background);
        }
        return c;
    }

    private static Color tone(long minutes) {
        return minutes > 0 ? POSITIVE : minutes < 0 ? NEGATIVE : INK;
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

    /** Footer on every page: the document name on the left, "Page n of N" on the right. */
    private static final class Footer extends PdfPageEventHelper {
        private final String title;
        private PdfTemplate total;
        private BaseFont base;

        Footer(String title) {
            this.title = title;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(40, 12);
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            var cb = writer.getDirectContent();
            var y = document.bottom() - 22;
            cb.setLineWidth(0.5f);
            cb.setColorStroke(RULE);
            cb.moveTo(document.left(), y + 12);
            cb.lineTo(document.right(), y + 12);
            cb.stroke();
            var small = new Font(base, 7.5f, Font.NORMAL, MUTED_INK);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(title, small), document.left(), y, 0);
            var page = new Phrase("Page " + writer.getPageNumber() + " of", small);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, page, document.right() - 14, y, 0);
            cb.addTemplate(total, document.right() - 11, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(base, 7.5f);
            total.setColorFill(MUTED_INK);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
