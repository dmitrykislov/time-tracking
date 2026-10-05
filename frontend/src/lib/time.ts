import type { DayEntry } from '../types';

/** "08:30" -> 510 */
export function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
}

/** A Date -> "HH:mm" in local time. */
export function hhmm(date: Date): string {
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

/** Local calendar date -> "YYYY-MM-DD". */
export function isoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** 450 -> "7h 30m"; 480 -> "8h"; 45 -> "45m"; 0 -> "0m" */
export function fmtMinutes(minutes: number): string {
  const abs = Math.abs(Math.round(minutes));
  const h = Math.floor(abs / 60);
  const m = abs % 60;
  if (h === 0) return `${m}m`;
  if (m === 0) return `${h}h`;
  return `${h}h ${m}m`;
}

/** Signed with a proper minus sign: 75 -> "+1h 15m", -75 -> "−1h 15m", 0 -> "0m" */
export function fmtSigned(minutes: number): string {
  if (minutes === 0) return '0m';
  return (minutes > 0 ? '+' : '−') + fmtMinutes(minutes);
}

/** Decimal hours for inputs and the config: 456 -> 7.6 */
export function toHours(minutes: number): number {
  return Math.round((minutes / 60) * 100) / 100;
}

const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

function parseIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** "2026-09-28" -> "Mon 28 Sep" (fixed English, independent of the browser locale) */
export function fmtDay(iso: string): string {
  const d = parseIso(iso);
  return `${DAYS[d.getDay()].slice(0, 3)} ${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)}`;
}

const NOTE_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'June', 'July', 'Aug', 'Sept', 'Oct', 'Nov', 'Dec'];

/** "2026-09-04" -> "Fri, 04 Sept 2026", the heading style used on the notes page. */
export function fmtNoteDay(iso: string): string {
  const d = parseIso(iso);
  return `${DAYS[d.getDay()].slice(0, 3)}, ${String(d.getDate()).padStart(2, '0')} ${NOTE_MONTHS[d.getMonth()]} ${d.getFullYear()}`;
}

/** "2026-09-28" -> "Monday 28 September 2026" */
export function fmtLongDay(iso: string): string {
  const d = parseIso(iso);
  return `${DAYS[d.getDay()]} ${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
}

/** Week range, e.g. "28 Sep – 4 Oct" or "28 Dec – 3 Jan 2027" */
export function fmtRange(startIso: string, endIso: string): string {
  const start = parseIso(startIso);
  const end = parseIso(endIso);
  const short = (d: Date) => `${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)}`;
  const year = start.getFullYear() !== end.getFullYear() ? ` ${end.getFullYear()}` : '';
  return `${short(start)} – ${short(end)}${year}`;
}

/** 510 -> "08:30" */
export function fromMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/**
 * The clock time at which the week's remaining minutes would be used up if work ran without a
 * pause from {@code now}. Null when that is not today (past midnight) or when nothing is left.
 */
export function finishTimeFor(now: string, remainingMinutes: number): string | null {
  if (remainingMinutes <= 0) return null;
  const end = toMinutes(now) + remainingMinutes;
  if (end >= 24 * 60) return null;
  return fromMinutes(end);
}

/** Total minutes of breaks on a day; a running break counts up to {@code now}. */
export function breakMinutes(entry: DayEntry | null | undefined, now: string): number {
  if (!entry || entry.type !== 'WORK') return 0;
  const end = toMinutes(entry.finish ?? now);
  return (entry.breaks ?? []).reduce((sum, b) => {
    const breakEnd = b.finish ? toMinutes(b.finish) : end;
    return sum + Math.max(0, breakEnd - toMinutes(b.start));
  }, 0);
}

export interface DefaultTimes {
  start: string;
  finish: string;
}

export const FALLBACK_TIMES: DefaultTimes = { start: '09:00', finish: '17:00' };

/**
 * What a new work day should be prefilled with: the times of the most recent finished work day,
 * so the am/pm halves are already right, else 09:00 to 17:00.
 */
export function defaultTimes(entries: (DayEntry | null | undefined)[]): DefaultTimes {
  const latest = entries
    .filter((e): e is DayEntry => !!e && e.type === 'WORK' && !!e.start && !!e.finish)
    .sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))[0];
  return latest ? { start: latest.start!, finish: latest.finish! } : FALLBACK_TIMES;
}

/** Mirrors DayEntry.workedMinutes on the server so the Today panel can tick without a round trip. */
export function workedMinutes(entry: DayEntry | null | undefined, now: string): number {
  if (!entry || entry.type !== 'WORK' || !entry.start) return 0;
  const end = toMinutes(entry.finish ?? now);
  const gross = Math.max(0, end - toMinutes(entry.start));
  const paused = (entry.breaks ?? []).reduce((sum, b) => {
    const breakEnd = b.finish ? toMinutes(b.finish) : end;
    return sum + Math.max(0, breakEnd - toMinutes(b.start));
  }, 0);
  return Math.max(0, gross - paused);
}
