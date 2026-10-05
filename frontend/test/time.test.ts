import { describe, expect, it } from 'vitest';
import { breakMinutes, defaultTimes, finishTimeFor, fmtMinutes, fmtRange, fmtSigned, fromMinutes, hhmm, isoDate, toHours, toMinutes, workedMinutes } from '../src/lib/time';

describe('formatting', () => {
  it('formats minutes as hours and minutes', () => {
    expect(fmtMinutes(0)).toBe('0m');
    expect(fmtMinutes(45)).toBe('45m');
    expect(fmtMinutes(480)).toBe('8h');
    expect(fmtMinutes(450)).toBe('7h 30m');
    expect(fmtMinutes(-90)).toBe('1h 30m');
  });

  it('formats signed balances with a real minus sign', () => {
    expect(fmtSigned(0)).toBe('0m');
    expect(fmtSigned(75)).toBe('+1h 15m');
    expect(fmtSigned(-75)).toBe('−1h 15m');
  });

  it('converts between clock text and minutes', () => {
    expect(toMinutes('08:30')).toBe(510);
    expect(hhmm(new Date(2026, 8, 30, 9, 5))).toBe('09:05');
    expect(isoDate(new Date(2026, 8, 3))).toBe('2026-09-03');
    expect(toHours(456)).toBe(7.6);
  });

  it('formats a week range, adding the year only when it changes', () => {
    expect(fmtRange('2026-09-28', '2026-10-04')).toBe('28 Sep – 4 Oct');
    expect(fmtRange('2026-12-28', '2027-01-03')).toBe('28 Dec – 3 Jan 2027');
  });
});

describe('finishTimeFor', () => {
  it('adds the remaining minutes to the clock', () => {
    expect(finishTimeFor('09:00', 8 * 60 + 30)).toBe('17:30');
    expect(finishTimeFor('14:05', 55)).toBe('15:00');
    expect(fromMinutes(510)).toBe('08:30');
    expect(fromMinutes(0)).toBe('00:00');
  });

  it('is null when nothing is left or the finish would be past midnight', () => {
    expect(finishTimeFor('09:00', 0)).toBeNull();
    expect(finishTimeFor('09:00', -30)).toBeNull();
    expect(finishTimeFor('20:00', 4 * 60)).toBeNull();
    expect(finishTimeFor('20:00', 3 * 60 + 59)).toBe('23:59');
  });
});

describe('breakMinutes', () => {
  it('adds up finished breaks', () => {
    const entry = { date: 'd', type: 'WORK' as const, start: '08:00', finish: '17:00', breaks: [{ start: '10:00', finish: '10:36' }, { start: '12:00', finish: '12:36' }] };
    expect(breakMinutes(entry, '23:00')).toBe(72);
  });

  it('counts a running break up to now, and nothing for leave or no breaks', () => {
    expect(breakMinutes({ date: 'd', type: 'WORK', start: '08:00', breaks: [{ start: '12:00' }] }, '12:20')).toBe(20);
    expect(breakMinutes({ date: 'd', type: 'WORK', start: '08:00', finish: '16:00', breaks: [] }, '12:20')).toBe(0);
    expect(breakMinutes({ date: 'd', type: 'SICK' }, '12:20')).toBe(0);
    expect(breakMinutes(null, '12:20')).toBe(0);
  });
});

describe('defaultTimes', () => {
  it('uses the most recent finished work day', () => {
    expect(
      defaultTimes([
        { date: '2026-09-21', type: 'WORK', start: '07:30', finish: '15:30' },
        { date: '2026-09-29', type: 'WORK', start: '08:45', finish: '17:15' },
        { date: '2026-09-30', type: 'WORK', start: '09:00' }, // running, no finish yet
        { date: '2026-10-05', type: 'PUBLIC_HOLIDAY' },
        null,
      ]),
    ).toEqual({ start: '08:45', finish: '17:15' });
  });

  it('falls back to a nine-to-five day', () => {
    expect(defaultTimes([])).toEqual({ start: '09:00', finish: '17:00' });
    expect(defaultTimes([{ date: '2026-09-30', type: 'SICK' }])).toEqual({ start: '09:00', finish: '17:00' });
  });
});

describe('workedMinutes', () => {
  it('matches the server for a finished day with breaks', () => {
    expect(
      workedMinutes(
        { date: 'd', type: 'WORK', start: '08:30', finish: '17:00', breaks: [{ start: '12:00', finish: '12:45' }] },
        '23:00',
      ),
    ).toBe(465);
  });

  it('counts a running day and a running break up to now', () => {
    expect(workedMinutes({ date: 'd', type: 'WORK', start: '09:00', breaks: [] }, '11:00')).toBe(120);
    expect(workedMinutes({ date: 'd', type: 'WORK', start: '09:00', breaks: [{ start: '12:00' }] }, '12:30')).toBe(180);
  });

  it('is zero for leave, nothing, or a clock before the start', () => {
    expect(workedMinutes(null, '10:00')).toBe(0);
    expect(workedMinutes({ date: 'd', type: 'SICK' }, '10:00')).toBe(0);
    expect(workedMinutes({ date: 'd', type: 'WORK', start: '09:00' }, '08:00')).toBe(0);
  });
});
