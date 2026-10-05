import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TodayPanel } from '../src/components/TodayPanel';
import { emptyDay, week } from './fixtures';

describe('TodayPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date(2026, 8, 30, 12, 30));
  });
  afterEach(() => vi.useRealTimers());

  it('offers Start day when nothing is recorded', async () => {
    const onAction = vi.fn();
    render(<TodayPanel today="2026-09-30" stats={emptyDay('2026-09-30')} week={week()} busy={false} onAction={onAction} />);

    expect(screen.getByText('Not started')).toBeInTheDocument();
    expect(screen.getByText('Wednesday 30 September 2026')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Start day' }));
    expect(onAction).toHaveBeenCalledWith('start-day');
    expect(screen.queryByRole('button', { name: 'End day' })).toBeNull();
  });

  it('counts a running day live and offers break and end', async () => {
    const onAction = vi.fn();
    const stats = {
      ...emptyDay('2026-09-30'),
      entry: { date: '2026-09-30', type: 'WORK' as const, start: '09:00', breaks: [] },
      inProgress: true,
      workedMinutes: 200,
    };
    render(<TodayPanel today="2026-09-30" stats={stats} week={week({ workedMinutes: 680 })} busy={false} onAction={onAction} />);

    expect(screen.getByText('Working since 09:00')).toBeInTheDocument();
    expect(screen.getByLabelText('Worked today')).toHaveTextContent('3h 30m today');
    // 680 on the server included 200 for today; live it is 210 now.
    expect(screen.getByLabelText('This week')).toHaveTextContent('11h 30m worked this week');
    expect(screen.getByLabelText('This week')).toHaveTextContent('28h 30m to go · 40h target');

    await userEvent.click(screen.getByRole('button', { name: 'Start break' }));
    expect(onAction).toHaveBeenCalledWith('start-break');
    await userEvent.click(screen.getByRole('button', { name: 'End day' }));
    expect(onAction).toHaveBeenCalledWith('end-day');
  });

  it('offers End break while paused', async () => {
    const onAction = vi.fn();
    const stats = {
      ...emptyDay('2026-09-30'),
      entry: { date: '2026-09-30', type: 'WORK' as const, start: '09:00', breaks: [{ start: '12:00' }] },
      inProgress: true,
      onBreak: true,
      workedMinutes: 180,
    };
    render(<TodayPanel today="2026-09-30" stats={stats} week={week()} busy={false} onAction={onAction} />);

    expect(screen.getByText('On a break since 12:00')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'End break' }));
    expect(onAction).toHaveBeenCalledWith('end-break');
    expect(screen.queryByRole('button', { name: 'Start break' })).toBeNull();
  });

  it('shows the finished day and a leave day without buttons', () => {
    const finished = {
      ...emptyDay('2026-09-30'),
      entry: { date: '2026-09-30', type: 'WORK' as const, start: '09:00', finish: '17:00', breaks: [] },
      workedMinutes: 480,
    };
    const { rerender } = render(<TodayPanel today="2026-09-30" stats={finished} week={week()} busy={false} onAction={vi.fn()} />);
    expect(screen.getByText('Finished · 09:00 – 17:00')).toBeInTheDocument();
    expect(screen.queryAllByRole('button')).toHaveLength(0);

    const sick = { ...emptyDay('2026-09-30'), entry: { date: '2026-09-30', type: 'SICK' as const, hours: 4 } };
    rerender(<TodayPanel today="2026-09-30" stats={sick} week={week()} busy={false} onAction={vi.fn()} />);
    expect(screen.getByText('Sick leave (4h)')).toBeInTheDocument();
  });

  it('shows over instead of to go once the effective target is met', () => {
    render(
      <TodayPanel
        today="2026-09-30"
        stats={emptyDay('2026-09-30')}
        week={week({ workedMinutes: 2500, carryInMinutes: 90, effectiveTargetMinutes: 2310, remainingMinutes: -190 })}
        busy={false}
        onAction={vi.fn()}
      />,
    );
    expect(screen.getByLabelText('This week')).toHaveTextContent('3h 10m over · 40h target, +1h 30m carried in');
  });

  it('shows hours left this week and the finish time while working', () => {
    // Clock is 12:30. Server says 200 worked today; live that is 210 (09:00 -> 12:30).
    const stats = {
      ...emptyDay('2026-09-30'),
      entry: { date: '2026-09-30', type: 'WORK' as const, start: '09:00', breaks: [] },
      inProgress: true,
      workedMinutes: 200,
    };
    render(
      <TodayPanel
        today="2026-09-30"
        stats={stats}
        week={week({ workedMinutes: 2000, effectiveTargetMinutes: 2400, remainingMinutes: 400 })}
        busy={false}
        onAction={vi.fn()}
      />,
    );
    // 2000 - 200 + 210 = 2010 worked; 390 left; 12:30 + 6h30 = 19:00.
    expect(screen.getByLabelText('Left this week')).toHaveTextContent('6h 30m left this week');
    expect(screen.getByLabelText('Finish time')).toHaveTextContent('Finish at 19:00 to hit the weekly target.');
  });

  it('phrases the finish time for a break, for an unstarted day, and for a met target', () => {
    const base = week({ workedMinutes: 2300, effectiveTargetMinutes: 2400, remainingMinutes: 100 });
    const onBreak = {
      ...emptyDay('2026-09-30'),
      entry: { date: '2026-09-30', type: 'WORK' as const, start: '09:00', breaks: [{ start: '12:00' }] },
      inProgress: true,
      onBreak: true,
      workedMinutes: 180,
    };
    const { rerender } = render(<TodayPanel today="2026-09-30" stats={onBreak} week={base} busy={false} onAction={vi.fn()} />);
    expect(screen.getByLabelText('Finish time')).toHaveTextContent('End the break now and work until 14:10 to hit the weekly target.');

    rerender(<TodayPanel today="2026-09-30" stats={emptyDay('2026-09-30')} week={base} busy={false} onAction={vi.fn()} />);
    expect(screen.getByLabelText('Finish time')).toHaveTextContent('Start now and finish at 14:10 to hit the weekly target.');

    rerender(
      <TodayPanel
        today="2026-09-30"
        stats={emptyDay('2026-09-30')}
        week={week({ workedMinutes: 2450, effectiveTargetMinutes: 2400, remainingMinutes: -50 })}
        busy={false}
        onAction={vi.fn()}
      />,
    );
    expect(screen.getByLabelText('Left this week')).toHaveTextContent('50m over this week');
    expect(screen.getByLabelText('Finish time')).toHaveTextContent('Weekly target reached.');
  });

  it('says so when the remaining work does not fit into today', () => {
    render(<TodayPanel today="2026-09-30" stats={emptyDay('2026-09-30')} week={week()} busy={false} onAction={vi.fn()} />);
    expect(screen.getByLabelText('Left this week')).toHaveTextContent('40h left this week');
    expect(screen.getByLabelText('Finish time')).toHaveTextContent('More than a day of work left this week');
  });

  it('gives no finish time once today is finished or is a leave day', () => {
    const finished = {
      ...emptyDay('2026-09-30'),
      entry: { date: '2026-09-30', type: 'WORK' as const, start: '09:00', finish: '17:00', breaks: [] },
      workedMinutes: 480,
    };
    const base = week({ workedMinutes: 2300, effectiveTargetMinutes: 2400, remainingMinutes: 100 });
    const { rerender } = render(<TodayPanel today="2026-09-30" stats={finished} week={base} busy={false} onAction={vi.fn()} />);
    expect(screen.queryByLabelText('Finish time')).toBeNull();
    rerender(<TodayPanel today="2026-09-30" stats={{ ...emptyDay('2026-09-30'), entry: { date: '2026-09-30', type: 'SICK' as const } }} week={base} busy={false} onAction={vi.fn()} />);
    expect(screen.queryByLabelText('Finish time')).toBeNull();
  });
});
