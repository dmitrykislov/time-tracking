import { describe, expect, it } from 'vitest';
import { notesHref, parseRoute } from '../src/hooks';

describe('routes', () => {
  it('parses the timesheet and notes routes', () => {
    expect(parseRoute('')).toEqual({ page: 'timesheet' });
    expect(parseRoute('#/')).toEqual({ page: 'timesheet' });
    expect(parseRoute('#/notes')).toEqual({ page: 'notes', date: undefined });
    expect(parseRoute('#/notes/')).toEqual({ page: 'notes', date: undefined });
    expect(parseRoute('#/notes/2026-09-28')).toEqual({ page: 'notes', date: '2026-09-28' });
  });

  it('falls back to the timesheet for anything it does not recognise', () => {
    expect(parseRoute('#/notes/28-09-2026')).toEqual({ page: 'timesheet' });
    expect(parseRoute('#/settings')).toEqual({ page: 'timesheet' });
    expect(parseRoute('#garbage')).toEqual({ page: 'timesheet' });
  });

  it('builds notes links', () => {
    expect(notesHref()).toBe('#/notes');
    expect(notesHref('2026-09-28')).toBe('#/notes/2026-09-28');
  });
});
