import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { NotesPage, daySummary, weekAsText } from '../src/components/NotesPage';
import { fmtNoteDay } from '../src/lib/time';
import { emptyDay, view, week } from './fixtures';

function notedWeek() {
  const w = week();
  w.days[0] = {
    ...emptyDay('2026-09-28'),
    entry: { date: '2026-09-28', type: 'WORK', start: '08:30', finish: '17:30', breaks: [{ start: '12:00', finish: '12:30' }] },
    note: 'Sprint planning.\n\nAgreed the parser rewrite.\nSam owns the spike.',
    workedMinutes: 510,
  };
  w.days[1] = { ...emptyDay('2026-09-29'), entry: { date: '2026-09-29', type: 'SICK', hours: 4 }, note: 'Dentist in the morning.' };
  w.days[4] = { ...emptyDay('2026-10-02'), entry: { date: '2026-10-02', type: 'WORK', start: '09:00', finish: '17:00', breaks: [] }, workedMinutes: 480 };
  return w;
}

describe('fmtNoteDay', () => {
  it('formats like Fri, 04 Sept 2026', () => {
    expect(fmtNoteDay('2026-09-04')).toBe('Fri, 04 Sept 2026');
    expect(fmtNoteDay('2026-06-15')).toBe('Mon, 15 June 2026');
    expect(fmtNoteDay('2027-01-03')).toBe('Sun, 03 Jan 2027');
  });
});

describe('daySummary', () => {
  it('describes what the timesheet holds for the day', () => {
    expect(daySummary(notedWeek().days[0])).toBe('Work · 08:30 – 17:30 · 8h 30m');
    expect(daySummary(notedWeek().days[1])).toBe('Sick leave · 4h');
    expect(daySummary(emptyDay('2026-09-30'))).toBe('Nothing recorded');
    expect(daySummary({ ...emptyDay('2026-09-30'), entry: { date: '2026-09-30', type: 'WORK', start: '09:00', breaks: [] }, inProgress: true, workedMinutes: 60 })).toBe('Work · 09:00 – now · 1h');
    expect(daySummary({ ...emptyDay('2026-09-30'), entry: { date: '2026-09-30', type: 'WORK', start: '09:00', breaks: [] }, unfinished: true })).toBe('Work · 09:00 – never ended');
    expect(daySummary({ ...emptyDay('2026-09-30'), beforeStart: true })).toBe('Before start date');
  });
});

describe('weekAsText', () => {
  it('lists the week chronologically with the note under each day, skipping empty days', () => {
    expect(weekAsText(notedWeek())).toBe(
      [
        'Week 28 Sep – 4 Oct',
        '',
        'Mon, 28 Sept 2026 — Work · 08:30 – 17:30 · 8h 30m',
        'Sprint planning.\n\nAgreed the parser rewrite.\nSam owns the spike.',
        '',
        'Tue, 29 Sept 2026 — Sick leave · 4h',
        'Dentist in the morning.',
        '',
        'Fri, 02 Oct 2026 — Work · 09:00 – 17:00 · 8h',
        '',
      ].join('\n'),
    );
  });
});

describe('NotesPage', () => {
  const older = week({ start: '2026-09-21', end: '2026-09-27', current: false });
  older.days = ['21', '22', '23', '24', '25', '26', '27'].map((d) => emptyDay(`2026-09-${d}`));
  older.days[2] = { ...emptyDay('2026-09-23'), note: 'Kick-off.' };

  it('shows the current week with every day headed by its date and the timesheet summary', () => {
    const v = view({ weeks: [notedWeek(), older] });
    render(<NotesPage view={v} busy={false} onSaveNote={vi.fn()} />);

    const headings = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent);
    expect(headings).toEqual([
      'Mon, 28 Sept 2026',
      'Tue, 29 Sept 2026',
      'Wed, 30 Sept 2026Today',
      'Thu, 01 Oct 2026',
      'Fri, 02 Oct 2026',
      'Sat, 03 Oct 2026',
      'Sun, 04 Oct 2026',
    ]);
    const monday = screen.getByLabelText('Mon, 28 Sept 2026');
    expect(monday).toHaveTextContent('Work · 08:30 – 17:30 · 8h 30m');
    expect(within(monday).getAllByRole('paragraph')).toHaveLength(3);
    expect(monday).toHaveTextContent('Agreed the parser rewrite.');
    expect(screen.getByLabelText('Thu, 01 Oct 2026')).toHaveTextContent('No notes yet.');

    const weeks = screen.getByLabelText('Weeks');
    expect(weeks).toHaveTextContent('28 Sep – 4 OctThis week');
    expect(weeks).toHaveTextContent('21 Sep – 27 Sep1 note');
  });

  it('switches weeks from the side list and with the older and newer buttons', async () => {
    const v = view({ weeks: [notedWeek(), older] });
    render(<NotesPage view={v} busy={false} onSaveNote={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: /21 Sep – 27 Sep/ }));
    expect(screen.getByLabelText('Notes for week of 2026-09-21')).toBeInTheDocument();
    expect(screen.getByLabelText('Wed, 23 Sept 2026')).toHaveTextContent('Kick-off.');
    expect(screen.getByRole('button', { name: 'Older week' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Newer week' }));
    expect(screen.getByLabelText('Notes for week of 2026-09-28')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Newer week' })).toBeDisabled();
  });

  it('opens the editor for the focused day and saves on Cmd+Enter', async () => {
    const onSaveNote = vi.fn().mockResolvedValue(true);
    const v = view({ weeks: [notedWeek(), older] });
    render(<NotesPage view={v} focusDate="2026-09-23" busy={false} onSaveNote={onSaveNote} />);

    // The linked day's week is selected and its editor is already open with the existing text.
    expect(screen.getByLabelText('Notes for week of 2026-09-21')).toBeInTheDocument();
    const textarea = screen.getByLabelText('Note for Wed, 23 Sept 2026');
    expect(textarea).toHaveValue('Kick-off.');
    expect(textarea).toHaveFocus();

    await userEvent.type(textarea, ' Met the whole team.');
    await userEvent.keyboard('{Meta>}{Enter}{/Meta}');
    expect(onSaveNote).toHaveBeenCalledWith('2026-09-23', 'Kick-off. Met the whole team.');
  });

  it('saves when the textarea loses focus, and cancels on Escape without saving', async () => {
    const onSaveNote = vi.fn().mockResolvedValue(true);
    render(<NotesPage view={view({ weeks: [notedWeek()] })} busy={false} onSaveNote={onSaveNote} />);

    await userEvent.click(screen.getByRole('button', { name: 'Edit note for Tue, 29 Sept 2026' }));
    const textarea = screen.getByLabelText('Note for Tue, 29 Sept 2026');
    await userEvent.type(textarea, ' Back after lunch.');
    await userEvent.tab();
    expect(onSaveNote).toHaveBeenCalledWith('2026-09-29', 'Dentist in the morning. Back after lunch.');

    await userEvent.click(screen.getByRole('button', { name: 'Edit note for Thu, 01 Oct 2026' }));
    const fresh = screen.getByRole('textbox', { name: /Note for/ });
    await userEvent.type(fresh, 'oops');
    await userEvent.keyboard('{Escape}');
    expect(onSaveNote).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('textbox', { name: /Note for/ })).toBeNull();
  });

  it('does not call save when nothing changed', async () => {
    const onSaveNote = vi.fn();
    render(<NotesPage view={view({ weeks: [notedWeek()] })} busy={false} onSaveNote={onSaveNote} />);
    await userEvent.click(screen.getByRole('button', { name: 'Edit note for Mon, 28 Sept 2026' }));
    await userEvent.tab();
    expect(onSaveNote).not.toHaveBeenCalled();
    expect(screen.queryByRole('textbox')).toBeNull();
  });

  it('copies the week as text', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
    render(<NotesPage view={view({ weeks: [notedWeek()] })} busy={false} onSaveNote={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Copy week as text' }));
    expect(writeText).toHaveBeenCalledWith(weekAsText(notedWeek()));
    expect(await screen.findByText('Copied ✓')).toBeInTheDocument();
    vi.unstubAllGlobals();
  });
});
