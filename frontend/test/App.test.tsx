import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../src/App';
import { emptyDay, view, week } from './fixtures';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('App', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it('loads the view and shows the rolling balance, today and weeks', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(view({ totals: { completedWeeks: 3, workedMinutes: 7300, targetMinutes: 7200, leaveMinutes: 0, balanceMinutes: 100, workedToDateMinutes: 7420 } })),
    );
    render(<App />);

    expect(await screen.findByText('Not started')).toBeInTheDocument();
    expect(screen.getAllByText('+1h 40m')[0]).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Start day' })).toBeInTheDocument();
    expect(screen.getByLabelText('Rolling totals')).toHaveTextContent('Worked to date123h 40m');
    expect(screen.getByLabelText('Rolling totals')).toHaveTextContent('Completed weeks3');
    expect(screen.getByLabelText('Week of 2026-09-28')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/view', expect.anything());
  });

  it('posts the button action and replaces the view with the response', async () => {
    const started = view({
      todayStats: { ...emptyDay('2026-09-30'), entry: { date: '2026-09-30', type: 'WORK', start: '10:15', breaks: [] }, inProgress: true },
      weeks: [week({ workedMinutes: 0 })],
    });
    fetchMock.mockImplementation((_url: string, init?: RequestInit) =>
      Promise.resolve(init?.method === 'POST' ? jsonResponse(started) : jsonResponse(view())),
    );
    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: 'Start day' }));

    expect(await screen.findByText('Working since 10:15')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/today/start-day', expect.objectContaining({ method: 'POST' }));
  });

  it('shows the server rule when an action is refused', async () => {
    fetchMock.mockImplementation((_url: string, init?: RequestInit) =>
      Promise.resolve(
        init?.method === 'POST'
          ? jsonResponse({ status: 400, detail: 'Today already started at 08:00' }, 400)
          : jsonResponse(view()),
      ),
    );
    render(<App />);
    await userEvent.click(await screen.findByRole('button', { name: 'Start day' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Today already started at 08:00');

    await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }));
    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  });

  it('shows warnings from the server', async () => {
    fetchMock.mockResolvedValue(jsonResponse(view({ warnings: ['Mon 28 Sep was started at 09:00 but never ended'] })));
    render(<App />);
    expect(await screen.findByText('Mon 28 Sep was started at 09:00 but never ended', { exact: false })).toBeInTheDocument();
  });

  it('saves settings through the modal', async () => {
    fetchMock.mockImplementation((_url: string, init?: RequestInit) =>
      Promise.resolve(init?.method === 'PUT' ? jsonResponse(view({ config: { weeklyTargetHours: 38, standardDayHours: 7.6, openingBalanceHours: 0 } })) : jsonResponse(view())),
    );
    render(<App />);
    await userEvent.click(await screen.findByRole('button', { name: 'Settings' }));
    const weekly = screen.getByLabelText('Hours per week');
    await userEvent.clear(weekly);
    await userEvent.type(weekly, '38');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(screen.queryByRole('form', { name: 'Settings' })).toBeNull());
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/config',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ weeklyTargetHours: 38, standardDayHours: 8, startDate: null, openingBalanceHours: 0 }) }),
    );
  });

  it('lists upcoming weeks separately after the current ones', async () => {
    const future = week({ start: '2026-10-05', end: '2026-10-11', current: false, upcoming: true });
    fetchMock.mockResolvedValue(jsonResponse(view({ weeks: [future, week()] })));
    render(<App />);
    await screen.findByText('Not started');
    const upcoming = screen.getByLabelText('Upcoming');
    expect(upcoming).toHaveTextContent('5 Oct – 11 Oct');
    expect(screen.getByLabelText('Week of 2026-09-28').compareDocumentPosition(upcoming) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('prefills a new day from the latest finished work day', async () => {
    const w = week();
    w.days[0] = { ...emptyDay('2026-09-28'), entry: { date: '2026-09-28', type: 'WORK', start: '07:45', finish: '16:10', breaks: [] }, workedMinutes: 505 };
    fetchMock.mockResolvedValue(jsonResponse(view({ weeks: [w] })));
    render(<App />);
    await userEvent.click(await screen.findByText('Thu 1 Oct'));
    expect(screen.getByLabelText('Start')).toHaveValue('07:45');
    expect(screen.getByLabelText('Finish')).toHaveValue('16:10');
  });

  it('switches between the timesheet and the notes page by hash, and links a day to its notes', async () => {
    const w = week();
    w.days[0] = { ...emptyDay('2026-09-28'), note: 'Sprint planning.\nMore detail.' };
    fetchMock.mockResolvedValue(jsonResponse(view({ weeks: [w] })));
    window.location.hash = '';
    render(<App />);
    await screen.findByText('Not started');

    expect(screen.getByRole('link', { name: 'Timesheet' })).toHaveAttribute('aria-current', 'page');
    const noteLink = screen.getByRole('link', { name: 'Sprint planning.' });
    expect(noteLink).toHaveAttribute('href', '#/notes/2026-09-28');
    expect(screen.getByRole('link', { name: 'Add today’s notes ›' })).toHaveAttribute('href', '#/notes/2026-09-30');

    window.location.hash = '#/notes/2026-09-28';
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    expect(await screen.findByLabelText('Note for Mon, 28 Sept 2026')).toHaveValue('Sprint planning.\nMore detail.');
    expect(screen.getByRole('link', { name: 'Notes' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByText('Not started')).toBeNull();

    window.location.hash = '#/';
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    expect(await screen.findByText('Not started')).toBeInTheDocument();
  });

  it('saves a note from the notes page and shows the returned view', async () => {
    const saved = week();
    saved.days[2] = { ...emptyDay('2026-09-30'), note: 'Wrote the release notes.' };
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(url === '/api/notes/2026-09-30' && init?.method === 'PUT' ? jsonResponse(view({ weeks: [saved] })) : jsonResponse(view())),
    );
    window.location.hash = '#/notes/2026-09-30';
    render(<App />);
    const textarea = await screen.findByLabelText('Note for Wed, 30 Sept 2026');
    await userEvent.type(textarea, 'Wrote the release notes.');
    await userEvent.keyboard('{Control>}{Enter}{/Control}');

    expect(fetchMock).toHaveBeenCalledWith('/api/notes/2026-09-30', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ text: 'Wrote the release notes.' }) }));
    expect(await screen.findByText('Wrote the release notes.')).toBeInTheDocument();
    window.location.hash = '';
  });
});
