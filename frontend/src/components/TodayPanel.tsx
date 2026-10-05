import { notesHref, useNow } from '../hooks';
import { finishTimeFor, fmtLongDay, fmtMinutes, fmtSigned, hhmm, workedMinutes } from '../lib/time';
import { dayTypeLabel, type DayStats, type TodayAction, type WeekStats } from '../types';

interface Props {
  today: string;
  stats: DayStats | null | undefined;
  week: WeekStats | undefined;
  busy: boolean;
  onAction: (action: TodayAction) => void;
}

/** The four buttons, a live clock, and how the week is tracking. */
export function TodayPanel({ today, stats, week, busy, onAction }: Props) {
  const now = useNow();
  const nowText = hhmm(now);
  const entry = stats?.entry ?? null;
  const running = !!stats?.inProgress;
  const onBreak = !!stats?.onBreak;

  const todayWorked = running ? workedMinutes(entry, nowText) : (stats?.workedMinutes ?? 0);
  const weekWorked = week ? week.workedMinutes - (stats?.workedMinutes ?? 0) + todayWorked : todayWorked;
  const target = week?.effectiveTargetMinutes ?? 0;
  const carryIn = week?.carryInMinutes ?? 0;
  const remaining = target - weekWorked;
  const progress = target > 0 ? Math.min(100, Math.round((weekWorked / target) * 100)) : 100;
  const finishAt = finishTimeFor(nowText, remaining);
  const leaveToday = !!entry && entry.type !== 'WORK';
  const finished = !!entry && entry.type === 'WORK' && !running;

  let finishHint: string | null = null;
  if (!week) {
    finishHint = null;
  } else if (remaining <= 0) {
    finishHint = 'Weekly target reached. Anything more goes into your balance.';
  } else if (leaveToday || finished) {
    finishHint = null;
  } else if (!finishAt) {
    finishHint = 'More than a day of work left this week, so no finish time today.';
  } else if (onBreak) {
    finishHint = `End the break now and work until ${finishAt} to hit the weekly target.`;
  } else if (running) {
    finishHint = `Finish at ${finishAt} to hit the weekly target.`;
  } else {
    finishHint = `Start now and finish at ${finishAt} to hit the weekly target.`;
  }

  let status: string;
  if (!entry) {
    status = 'Not started';
  } else if (entry.type !== 'WORK') {
    status = dayTypeLabel(entry.type) + (entry.hours ? ` (${entry.hours}h)` : '');
  } else if (onBreak) {
    const open = entry.breaks?.find((b) => !b.finish);
    status = `On a break since ${open?.start ?? ''}`;
  } else if (running) {
    status = `Working since ${entry.start}`;
  } else {
    status = `Finished · ${entry.start} – ${entry.finish}`;
  }

  return (
    <section className="today card" aria-label="Today">
      <div className="today-head">
        <div>
          <p className="eyebrow">Today</p>
          <h2>{fmtLongDay(today)}</h2>
        </div>
        <div className="today-right">
          <p className="clock" aria-label="Current time">{nowText}</p>
          <a className="today-notes-link" href={notesHref(today)}>
            {stats?.note ? 'Today’s notes ›' : 'Add today’s notes ›'}
          </a>
        </div>
      </div>

      <div className="today-body">
        <div className="today-status">
          <p className={`status status-${onBreak ? 'break' : running ? 'running' : 'idle'}`}>{status}</p>
          <div className="big-numbers">
            <p className="big-number" aria-label="Worked today">
              {fmtMinutes(todayWorked)} <span className="muted">today</span>
            </p>
            {week && (
              <p className={`big-number ${remaining <= 0 ? 'pos' : ''}`} aria-label="Left this week">
                {fmtMinutes(Math.abs(remaining))} <span className="muted">{remaining > 0 ? 'left this week' : 'over this week'}</span>
              </p>
            )}
          </div>
        </div>

        <div className="today-actions">
          {!entry && (
            <button className="btn btn-primary btn-lg" disabled={busy} onClick={() => onAction('start-day')}>
              Start day
            </button>
          )}
          {running && !onBreak && (
            <>
              <button className="btn btn-lg" disabled={busy} onClick={() => onAction('start-break')}>
                Start break
              </button>
              <button className="btn btn-primary btn-lg" disabled={busy} onClick={() => onAction('end-day')}>
                End day
              </button>
            </>
          )}
          {onBreak && (
            <>
              <button className="btn btn-primary btn-lg" disabled={busy} onClick={() => onAction('end-break')}>
                End break
              </button>
              <button className="btn btn-lg" disabled={busy} onClick={() => onAction('end-day')}>
                End day
              </button>
            </>
          )}
          {entry && !running && <p className="muted small">Edit today's times in the week below.</p>}
        </div>
      </div>

      {week && (
        <div className="week-progress" aria-label="This week">
          <div className="progress-labels">
            <span>
              <strong>{fmtMinutes(weekWorked)}</strong> worked this week
            </span>
            <span>
              {remaining > 0 ? (
                <>
                  <strong>{fmtMinutes(remaining)}</strong> to go
                </>
              ) : (
                <>
                  <strong>{fmtMinutes(-remaining)}</strong> over
                </>
              )}
              <span className="muted">
                {' '}
                · {fmtMinutes(week.targetMinutes)} target{carryIn !== 0 && <>, {fmtSigned(carryIn)} carried in</>}
              </span>
            </span>
          </div>
          <div className="progress" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
            <div className={`progress-fill ${remaining <= 0 ? 'done' : ''}`} style={{ width: `${progress}%` }} />
          </div>
          {finishHint && (
            <p className="finish-hint" aria-label="Finish time">
              {finishHint}
            </p>
          )}
        </div>
      )}
    </section>
  );
}
