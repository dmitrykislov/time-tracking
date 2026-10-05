import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { WeekCard } from '../src/components/WeekCard';
import { emptyDay, week } from './fixtures';

const config = { weeklyTargetHours: 40, standardDayHours: 8, openingBalanceHours: 0 };
const defaults = { start: '08:30', finish: '17:30' };

describe('WeekCard', () => {
  it('shows the week numbers and a leave note', () => {
    const w = week({
      workedMinutes: 1980,
      leaveMinutes: 480,
      targetMinutes: 1920,
      carryInMinutes: 90,
      effectiveTargetMinutes: 1830,
      remainingMinutes: -150,
      balanceMinutes: 60,
      runningBalanceMinutes: 150,
    });
    render(<WeekCard week={w} today="2026-09-30" now="12:20" config={config} defaults={defaults} expanded={false} busy={false} onToggle={vi.fn()} onSaveDay={vi.fn()} onDeleteDay={vi.fn()} />);

    const head = screen.getByRole('heading', { name: '28 Sep – 4 Oct' }).closest('header')!;
    expect(head).toHaveTextContent('Worked33h');
    expect(head).toHaveTextContent('Target32h after 8h leave');
    // Ledger: carried in + this week = balance after.
    expect(head).toHaveTextContent('Carried in+1h 30m');
    expect(head).toHaveTextContent('Over/under+1h');
    expect(head).toHaveTextContent('Balance after+2h 30m');
    expect(head).not.toHaveTextContent('Left');
    expect(screen.getByText('This week')).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('lists the seven days with their details when expanded', () => {
    const w = week();
    w.days[0] = {
      ...emptyDay('2026-09-28'),
      entry: { date: '2026-09-28', type: 'WORK', start: '08:30', finish: '17:00', breaks: [{ start: '12:00', finish: '12:30' }] },
      note: 'on site\nsecond line',
      workedMinutes: 480,
    };
    w.days[1] = { ...emptyDay('2026-09-29'), entry: { date: '2026-09-29', type: 'PUBLIC_HOLIDAY' }, targetReductionMinutes: 480 };
    w.days[2] = { ...emptyDay('2026-09-30'), entry: { date: '2026-09-30', type: 'WORK', start: '09:00', breaks: [] }, inProgress: true, workedMinutes: 75 };
    w.days[3] = { ...emptyDay('2026-10-01'), entry: { date: '2026-10-01', type: 'TIME_IN_LIEU' } };
    render(<WeekCard week={w} today="2026-09-30" now="12:20" config={config} defaults={defaults} expanded busy={false} onToggle={vi.fn()} onSaveDay={vi.fn()} onDeleteDay={vi.fn()} />);

    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(7);
    expect(rows[0]).toHaveTextContent('Mon 28 Sep');
    expect(rows[0]).toHaveTextContent('08:30 – 17:00');
    expect(rows[0]).toHaveTextContent('12:00–12:30');
    expect(within(rows[0]).getByLabelText('Total breaks')).toHaveTextContent('30m');
    expect(rows[0]).toHaveTextContent('8h');
    expect(within(rows[0]).getByRole('link', { name: 'on site' })).toHaveAttribute('href', '#/notes/2026-09-28');
    expect(within(rows[2]).getByRole('link', { name: 'Add note' })).toHaveAttribute('href', '#/notes/2026-09-30');
    expect(rows[1]).toHaveTextContent('Public holiday');
    expect(rows[1]).toHaveTextContent('−8h target');
    expect(rows[2]).toHaveClass('is-today');
    expect(rows[2]).toHaveTextContent('09:00 – now');
    expect(rows[3]).toHaveTextContent('from balance');
    expect(rows[6]).toHaveTextContent('Sun 4 Oct');
  });

  it('opens the editor on a row and saves through it', async () => {
    const onSaveDay = vi.fn().mockResolvedValue(true);
    render(<WeekCard week={week()} today="2026-09-30" now="12:20" config={config} defaults={defaults} expanded busy={false} onToggle={vi.fn()} onSaveDay={onSaveDay} onDeleteDay={vi.fn()} />);

    await userEvent.click(screen.getByText('Tue 29 Sep'));
    const form = screen.getByRole('form', { name: 'Edit 2026-09-29' });
    expect(within(form).getByLabelText('Start')).toHaveValue('08:30');
    await userEvent.clear(within(form).getByLabelText('Start'));
    await userEvent.type(within(form).getByLabelText('Start'), '09:00');
    await userEvent.clear(within(form).getByLabelText('Finish'));
    await userEvent.type(within(form).getByLabelText('Finish'), '17:30');
    await userEvent.click(within(form).getByRole('button', { name: 'Save' }));

    expect(onSaveDay).toHaveBeenCalledWith({ date: '2026-09-29', type: 'WORK', start: '09:00', finish: '17:30', breaks: [] });
    expect(screen.queryByRole('form')).toBeNull();
  });

  it('keeps the editor open when saving fails', async () => {
    const onSaveDay = vi.fn().mockResolvedValue(false);
    render(<WeekCard week={week()} today="2026-09-30" now="12:20" config={config} defaults={defaults} expanded busy={false} onToggle={vi.fn()} onSaveDay={onSaveDay} onDeleteDay={vi.fn()} />);
    await userEvent.click(screen.getByText('Tue 29 Sep'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(onSaveDay).toHaveBeenCalled();
    expect(screen.getByRole('form')).toBeInTheDocument();
  });

  it('does not open an editor for days before the start date', async () => {
    const w = week();
    w.days[0] = { ...emptyDay('2026-09-28'), beforeStart: true, targetReductionMinutes: 480 };
    render(<WeekCard week={w} today="2026-09-30" now="12:20" config={config} defaults={defaults} expanded busy={false} onToggle={vi.fn()} onSaveDay={vi.fn()} onDeleteDay={vi.fn()} />);
    expect(screen.getByText('before start')).toBeInTheDocument();
    await userEvent.click(screen.getByText('Mon 28 Sep'));
    expect(screen.queryByRole('form')).toBeNull();
  });

  it('shows an upcoming week with its target only', () => {
    const w = week({ start: '2026-10-05', end: '2026-10-11', current: false, upcoming: true, leaveMinutes: 480, targetMinutes: 1920, carryInMinutes: -600 });
    render(<WeekCard week={w} today="2026-09-30" now="12:20" config={config} defaults={defaults} expanded={false} busy={false} onToggle={vi.fn()} onSaveDay={vi.fn()} onDeleteDay={vi.fn()} />);
    const head = screen.getByRole('heading', { name: '5 Oct – 11 Oct' }).closest('header')!;
    expect(screen.getByText('Upcoming')).toBeInTheDocument();
    expect(head).toHaveTextContent('Target32h after 8h leave');
    expect(head).not.toHaveTextContent('Carried in');
    expect(head).not.toHaveTextContent('Balance after');
  });

  it('totals several breaks and sizes a running break by the clock', () => {
    const w = week();
    w.days[0] = {
      ...emptyDay('2026-09-28'),
      entry: { date: '2026-09-28', type: 'WORK', start: '08:00', finish: '17:00', breaks: [{ start: '10:00', finish: '10:36' }, { start: '12:00', finish: '12:36' }] },
      workedMinutes: 468,
    };
    w.days[2] = { ...emptyDay('2026-09-30'), entry: { date: '2026-09-30', type: 'WORK', start: '09:00', breaks: [{ start: '12:00' }] }, inProgress: true, onBreak: true, workedMinutes: 180 };
    render(<WeekCard week={w} today="2026-09-30" now="12:20" config={config} defaults={defaults} expanded busy={false} onToggle={vi.fn()} onSaveDay={vi.fn()} onDeleteDay={vi.fn()} />);
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
    expect(within(rows[0]).getByLabelText('Total breaks')).toHaveTextContent('1h 12m');
    expect(within(rows[2]).getByLabelText('Total breaks')).toHaveTextContent('20m');
    expect(rows[2]).toHaveTextContent('12:00–now');
    expect(within(rows[1]).queryByLabelText('Total breaks')).toBeNull();
  });

  it('shows a break note as a tooltip and does not open the editor when the notes link is clicked', async () => {
    const w = week();
    w.days[0] = {
      ...emptyDay('2026-09-28'),
      entry: { date: '2026-09-28', type: 'WORK', start: '08:00', finish: '17:00', breaks: [{ start: '12:00', finish: '12:45', note: 'lunch with client' }] },
      note: 'A long first line that goes on and on and on and on and on and on and on and on\nsecond',
      workedMinutes: 495,
    };
    render(<WeekCard week={w} today="2026-09-30" now="12:20" config={config} defaults={defaults} expanded busy={false} onToggle={vi.fn()} onSaveDay={vi.fn()} onDeleteDay={vi.fn()} />);
    expect(screen.getByText('12:00–12:45')).toHaveAttribute('title', 'lunch with client');
    const link = screen.getByRole('link', { name: /A long first line/ });
    expect(link.textContent!.length).toBeLessThanOrEqual(60);
    expect(link.textContent!.endsWith('…')).toBe(true);
    await userEvent.click(link);
    expect(screen.queryByRole('form')).toBeNull();
  });
});
