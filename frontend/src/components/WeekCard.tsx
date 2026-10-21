import { useState } from 'react';
import { notesHref } from '../hooks';
import { breakMinutes, fmtDay, fmtMinutes, fmtRange, fmtSigned, type DefaultTimes } from '../lib/time';
import { dayTypeLabel, type Config, type DayEntry, type DayStats, type WeekStats } from '../types';
import { DayEditor } from './DayEditor';

interface Props {
  week: WeekStats;
  today: string;
  /** Server clock at the last refresh, HH:mm; used to size a running break. */
  now: string;
  config: Config;
  defaults: DefaultTimes;
  expanded: boolean;
  busy: boolean;
  onToggle: () => void;
  onSaveDay: (entry: DayEntry) => Promise<boolean>;
  onDeleteDay: (date: string) => Promise<boolean>;
}

function timesText(day: DayStats): string {
  const e = day.entry;
  if (!e || e.type !== 'WORK') return '';
  if (day.unfinished) return `${e.start} – never ended`;
  if (day.inProgress) return `${e.start} – now`;
  return `${e.start} – ${e.finish}`;
}

function hoursCell(day: DayStats) {
  if (day.beforeStart) return <span className="muted">before start</span>;
  const e = day.entry;
  if (!e) return <span className="muted">—</span>;
  if (e.type === 'WORK') return <strong>{fmtMinutes(day.workedMinutes)}</strong>;
  if (day.targetReductionMinutes > 0) return <span className="muted">−{fmtMinutes(day.targetReductionMinutes)} target</span>;
  return <span className="muted">from balance</span>;
}

/** "11h 30m short", "2h over", "on target" for a finished week; "24h 12m to go" while it is running. */
export function verdict(week: WeekStats): { text: string; tone: 'pos' | 'neg' | 'even' } {
  const diff = week.workedMinutes - week.targetMinutes;
  if (week.current) {
    if (diff >= 0) return { text: diff === 0 ? 'target reached' : `${fmtMinutes(diff)} over`, tone: 'pos' };
    return { text: `${fmtMinutes(-diff)} to go`, tone: 'even' };
  }
  if (diff > 0) return { text: `${fmtMinutes(diff)} over`, tone: 'pos' };
  if (diff < 0) return { text: `${fmtMinutes(-diff)} short`, tone: 'neg' };
  return { text: 'on target', tone: 'even' };
}

export function WeekCard({ week, today, now, config, defaults, expanded, busy, onToggle, onSaveDay, onDeleteDay }: Props) {
  const [editing, setEditing] = useState<string | null>(null);
  const v = verdict(week);
  const progress = week.targetMinutes > 0 ? Math.min(100, Math.round((week.workedMinutes / week.targetMinutes) * 100)) : 100;
  const met = week.workedMinutes >= week.targetMinutes;

  return (
    <section
      className={`week card ${week.current ? 'week-current' : ''} ${week.upcoming ? 'week-upcoming' : ''}`}
      aria-label={`Week of ${week.start}`}
    >
      <header className="week-head" onClick={onToggle}>
        <div className="week-title">
          <h3>{fmtRange(week.start, week.end)}</h3>
          {week.current && <span className="badge">This week</span>}
          {week.upcoming && <span className="badge badge-muted">Upcoming</span>}
          <span className="muted small chevron">{expanded ? '▾' : '▸'}</span>
        </div>

        {week.upcoming ? (
          <dl className="week-summary">
            <div className="ws">
              <dt>Target</dt>
              <dd>
                {fmtMinutes(week.targetMinutes)}
                {week.leaveMinutes > 0 && <small className="muted"> after {fmtMinutes(week.leaveMinutes)} leave</small>}
              </dd>
            </div>
          </dl>
        ) : (
          <dl className="week-summary">
            <div className="ws" title="Balance brought into this week from earlier weeks">
              <dt>Carried in</dt>
              <dd className={week.carryInMinutes > 0 ? 'pos' : week.carryInMinutes < 0 ? 'neg' : ''}>{fmtSigned(week.carryInMinutes)}</dd>
            </div>
            <div className="ws ws-work" title="Hours worked this week against its target">
              <dt>{week.current ? 'So far' : 'Worked'}</dt>
              <dd>
                <strong>{fmtMinutes(week.workedMinutes)}</strong>
                <span className={`verdict ${v.tone}`}> · {v.text}</span>
              </dd>
              <div className="meter meter-sm" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
                <div className={`meter-fill ${met ? 'done' : ''}`} style={{ width: `${progress}%` }} />
              </div>
              {week.leaveMinutes > 0 && (
                <small className="muted">
                  target {fmtMinutes(week.targetMinutes)} after {fmtMinutes(week.leaveMinutes)} leave
                </small>
              )}
            </div>
            <div className="ws ws-forward" title={week.current ? 'Where the balance stands if the week ended now' : 'What this week passed on to the next'}>
              <dt>{week.current ? 'Balance so far' : 'Carried forward'}</dt>
              <dd className={week.runningBalanceMinutes > 0 ? 'pos' : week.runningBalanceMinutes < 0 ? 'neg' : ''}>
                {fmtSigned(week.runningBalanceMinutes)}
              </dd>
            </div>
          </dl>
        )}
      </header>

      {expanded && (
        <div className="table-wrap">
          <table className="days">
            <thead>
              <tr>
                <th>Day</th>
                <th>Type</th>
                <th>Times</th>
                <th>Breaks</th>
                <th>Hours</th>
                <th>Notes</th>
              </tr>
            </thead>
            <tbody>
              {week.days.map((day) => {
                const isEditing = editing === day.date;
                const e = day.entry;
                const rowClass = [
                  day.date === today ? 'is-today' : '',
                  day.beforeStart ? 'is-before' : '',
                  day.unfinished ? 'is-unfinished' : '',
                  e ? '' : 'is-empty',
                ].join(' ');
                return (
                  <FragmentRow key={day.date}>
                    <tr
                      className={rowClass + (day.beforeStart ? '' : ' is-clickable')}
                      onClick={() => !day.beforeStart && setEditing(isEditing ? null : day.date)}
                      aria-expanded={day.beforeStart ? undefined : isEditing}
                      tabIndex={day.beforeStart ? -1 : 0}
                      onKeyDown={(ev) => {
                        if ((ev.key === 'Enter' || ev.key === ' ') && !day.beforeStart) {
                          ev.preventDefault();
                          setEditing(isEditing ? null : day.date);
                        }
                      }}
                    >
                      <td className="day-name">{fmtDay(day.date)}</td>
                      <td>{e ? dayTypeLabel(e.type) : ''}</td>
                      <td className="mono">{timesText(day)}</td>
                      <td className="breaks-cell">
                        {e && e.type === 'WORK' && (e.breaks ?? []).length > 0 && (
                          <>
                            <strong className="break-total" aria-label="Total breaks">
                              {fmtMinutes(breakMinutes(e, now))}
                            </strong>
                            {(e.breaks ?? []).map((b, i) => (
                              <span className={`break-chip mono ${b.note ? 'has-note' : ''}`} key={i} title={b.note ?? undefined}>
                                {b.start}–{b.finish ?? 'now'}
                              </span>
                            ))}
                          </>
                        )}
                      </td>
                      <td>{hoursCell(day)}</td>
                      <td className="note">
                        {!day.beforeStart && (
                          <a
                            href={notesHref(day.date)}
                            className={`note-link ${day.note ? '' : 'note-link-empty'}`}
                            onClick={(ev) => ev.stopPropagation()}
                            title={day.note ? 'Open the notes for this day' : 'Add a note for this day'}
                          >
                            {day.note ? firstLine(day.note) : 'Add note'}
                          </a>
                        )}
                      </td>
                    </tr>
                    {isEditing && (
                      <tr className="editor-row">
                        <td colSpan={6}>
                          <DayEditor
                            date={day.date}
                            entry={e ?? null}
                            standardDayHours={config.standardDayHours}
                            defaults={defaults}
                            busy={busy}
                            onSave={async (entry) => {
                              const ok = await onSaveDay(entry);
                              if (ok) setEditing(null);
                              return ok;
                            }}
                            onDelete={
                              e
                                ? async () => {
                                    if (await onDeleteDay(day.date)) setEditing(null);
                                  }
                                : undefined
                            }
                            onCancel={() => setEditing(null)}
                          />
                        </td>
                      </tr>
                    )}
                  </FragmentRow>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function firstLine(text: string): string {
  const line = text.split('\n').find((l) => l.trim() !== '') ?? '';
  return line.length > 60 ? line.slice(0, 57).trimEnd() + '…' : line;
}

function FragmentRow({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
