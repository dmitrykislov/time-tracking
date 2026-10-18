import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ExportDialog, exportUrl } from '../src/components/ExportDialog';

describe('ExportDialog', () => {
  it('defaults the period to the start date and today, and opens the PDF in a new tab', async () => {
    const onClose = vi.fn();
    render(<ExportDialog startDate="2026-09-21" today="2026-10-19" onClose={onClose} />);

    expect(screen.getByLabelText('From')).toHaveValue('2026-09-21');
    expect(screen.getByLabelText('To')).toHaveValue('2026-10-19');
    const open = screen.getByRole('link', { name: 'Open PDF' });
    expect(open).toHaveAttribute('href', '/api/export.pdf?from=2026-09-21&to=2026-10-19');
    expect(open).toHaveAttribute('target', '_blank');
    expect(open).toHaveAttribute('rel', 'noopener');

    await userEvent.click(open);
    expect(onClose).toHaveBeenCalled();
  });

  it('uses the dates the user picks', async () => {
    render(<ExportDialog startDate="2026-09-21" today="2026-10-19" onClose={vi.fn()} />);
    const from = screen.getByLabelText('From');
    const to = screen.getByLabelText('To');
    await userEvent.clear(from);
    await userEvent.type(from, '2026-10-01');
    await userEvent.clear(to);
    await userEvent.type(to, '2026-10-15');
    expect(screen.getByRole('link', { name: 'Open PDF' })).toHaveAttribute('href', exportUrl('2026-10-01', '2026-10-15'));
  });

  it('refuses a period that starts after it ends', async () => {
    const onClose = vi.fn();
    render(<ExportDialog startDate="2026-09-21" today="2026-10-19" onClose={onClose} />);
    const from = screen.getByLabelText('From');
    await userEvent.clear(from);
    await userEvent.type(from, '2026-11-01');

    expect(screen.getByRole('alert')).toHaveTextContent('The period starts after it ends.');
    // Without an href the anchor is no longer a link, which is exactly the point.
    expect(screen.queryByRole('link', { name: 'Open PDF' })).toBeNull();
    const open = screen.getByText('Open PDF');
    expect(open).not.toHaveAttribute('href');
    expect(open).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(open);
    expect(onClose).not.toHaveBeenCalled();
  });

  it('closes on cancel', async () => {
    const onClose = vi.fn();
    render(<ExportDialog startDate="2026-09-21" today="2026-10-19" onClose={onClose} />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalled();
  });
});
