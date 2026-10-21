import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Settings } from '../src/components/Settings';

const config = { weeklyTargetHours: 40, standardDayHours: 8, startDate: '2026-09-21', openingBalanceHours: 1.5 };

describe('Settings', () => {
  it('shows the current values and saves what was typed, blanks becoming null and zero', async () => {
    const onSave = vi.fn().mockResolvedValue(true);
    const onClose = vi.fn();
    render(<Settings config={config} busy={false} onSave={onSave} onClose={onClose} />);

    expect(screen.getByLabelText('Hours per week')).toHaveValue(40);
    expect(screen.getByLabelText('Start date')).toHaveValue('2026-09-21');
    expect(screen.getByLabelText('Opening balance (hours)')).toHaveValue(1.5);

    await userEvent.clear(screen.getByLabelText('Hours per day'));
    await userEvent.type(screen.getByLabelText('Hours per day'), '7.6');
    await userEvent.clear(screen.getByLabelText('Start date'));
    await userEvent.clear(screen.getByLabelText('Opening balance (hours)'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(onSave).toHaveBeenCalledWith({ weeklyTargetHours: 40, standardDayHours: 7.6, startDate: null, openingBalanceHours: 0 });
    expect(onClose).toHaveBeenCalled();
  });

  it('stays open when saving fails, and closes on cancel or a click on the backdrop', async () => {
    const onSave = vi.fn().mockResolvedValue(false);
    const onClose = vi.fn();
    const { container } = render(<Settings config={config} busy={false} onSave={onSave} onClose={onClose} />);
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(onSave).toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalledTimes(1);
    await userEvent.click(container.querySelector('.modal-backdrop')!);
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
