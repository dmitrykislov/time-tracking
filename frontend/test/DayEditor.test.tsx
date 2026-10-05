import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { DayEditor } from '../src/components/DayEditor';

describe('DayEditor', () => {
  it('saves a work day with a new break', async () => {
    const onSave = vi.fn().mockResolvedValue(true);
    render(
      <DayEditor
        date="2026-09-28"
        entry={{ date: '2026-09-28', type: 'WORK', start: '08:30', finish: '17:00', breaks: [{ start: '12:00', finish: '12:30' }] }}
        standardDayHours={8}
        busy={false}
        onSave={onSave}
        onCancel={vi.fn()}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: '+ Add break' }));
    await userEvent.type(screen.getByLabelText('Break 2 start'), '15:00');
    await userEvent.type(screen.getByLabelText('Break 2 finish'), '15:10');
    await userEvent.type(screen.getByLabelText('Break 2 note'), '  coffee with Sam ');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(onSave).toHaveBeenCalledWith({
      date: '2026-09-28',
      type: 'WORK',
      start: '08:30',
      finish: '17:00',
      breaks: [
        { start: '12:00', finish: '12:30', note: null },
        { start: '15:00', finish: '15:10', note: 'coffee with Sam' },
      ],
    });
  });

  it('removes a break', async () => {
    const onSave = vi.fn().mockResolvedValue(true);
    render(
      <DayEditor
        date="2026-09-28"
        entry={{ date: '2026-09-28', type: 'WORK', start: '08:30', finish: '17:00', breaks: [{ start: '12:00', finish: '12:30' }] }}
        standardDayHours={8}
        busy={false}
        onSave={onSave}
        onCancel={vi.fn()}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Remove break 1' }));
    expect(screen.getByText('No breaks.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ breaks: [], finish: '17:00' }));
  });

  it('switches to leave and sends hours only', async () => {
    const onSave = vi.fn().mockResolvedValue(true);
    render(<DayEditor date="2026-09-29" entry={null} standardDayHours={7.6} busy={false} onSave={onSave} onCancel={vi.fn()} />);

    await userEvent.selectOptions(screen.getByLabelText('Type'), 'SICK');
    expect(screen.queryByLabelText('Start')).toBeNull();
    expect(screen.getByText("Takes 7.6h off this week's target. Leave blank for a standard day.")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Hours'), '4');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(onSave).toHaveBeenCalledWith({ date: '2026-09-29', type: 'SICK', hours: 4 });
  });

  it('explains time in lieu differently', async () => {
    render(<DayEditor date="2026-09-29" entry={null} standardDayHours={8} busy={false} onSave={vi.fn()} onCancel={vi.fn()} />);
    await userEvent.selectOptions(screen.getByLabelText('Type'), 'TIME_IN_LIEU');
    expect(screen.getByText('Does not change the target: the day is spent from your balance.')).toBeInTheDocument();
  });

  it('refuses a work day without a start and never calls save', async () => {
    const onSave = vi.fn();
    render(<DayEditor date="2026-09-29" entry={null} standardDayHours={8} busy={false} onSave={onSave} onCancel={vi.fn()} />);
    await userEvent.clear(screen.getByLabelText('Start'));
    // Bypass native required validation by submitting the form directly.
    const form = screen.getByRole('form', { name: 'Edit 2026-09-29' });
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    expect(await screen.findByRole('alert')).toHaveTextContent('A work day needs a start time.');
    expect(onSave).not.toHaveBeenCalled();
  });

  it('offers delete only for an existing entry', async () => {
    const onDelete = vi.fn();
    const { rerender } = render(
      <DayEditor date="2026-09-29" entry={null} standardDayHours={8} busy={false} onSave={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(screen.queryByRole('button', { name: 'Delete day' })).toBeNull();

    rerender(
      <DayEditor
        date="2026-09-29"
        entry={{ date: '2026-09-29', type: 'SICK' }}
        standardDayHours={8}
        busy={false}
        onSave={vi.fn()}
        onDelete={onDelete}
        onCancel={vi.fn()}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Delete day' }));
    expect(onDelete).toHaveBeenCalled();
  });

  it('prefills a new work day from the defaults so am and pm are already right', async () => {
    const onSave = vi.fn().mockResolvedValue(true);
    render(
      <DayEditor date="2026-10-01" entry={null} standardDayHours={8} defaults={{ start: '08:15', finish: '16:45' }} busy={false} onSave={onSave} onCancel={vi.fn()} />,
    );
    expect(screen.getByLabelText('Start')).toHaveValue('08:15');
    expect(screen.getByLabelText('Finish')).toHaveValue('16:45');

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(onSave).toHaveBeenCalledWith({ date: '2026-10-01', type: 'WORK', start: '08:15', finish: '16:45', breaks: [] });
  });

  it('falls back to nine-to-five without defaults, and never overrides an existing entry', () => {
    const { unmount } = render(<DayEditor date="2026-10-01" entry={null} standardDayHours={8} busy={false} onSave={vi.fn()} onCancel={vi.fn()} />);
    expect(screen.getByLabelText('Start')).toHaveValue('09:00');
    expect(screen.getByLabelText('Finish')).toHaveValue('17:00');
    unmount();

    render(
      <DayEditor
        date="2026-10-01"
        entry={{ date: '2026-10-01', type: 'WORK', start: '07:00', breaks: [] }}
        standardDayHours={8}
        defaults={{ start: '08:15', finish: '16:45' }}
        busy={false}
        onSave={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    // A running day keeps its empty finish: prefilling it would end the day on save.
    expect(screen.getByLabelText('Start')).toHaveValue('07:00');
    expect(screen.getByLabelText('Finish')).toHaveValue('');
  });

  it('keeps an existing break note and shows it in the field', () => {
    render(
      <DayEditor
        date="2026-09-28"
        entry={{ date: '2026-09-28', type: 'WORK', start: '08:30', finish: '17:00', breaks: [{ start: '12:00', finish: '12:45', note: 'lunch with client' }] }}
        standardDayHours={8}
        busy={false}
        onSave={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByLabelText('Break 1 note')).toHaveValue('lunch with client');
  });
});
