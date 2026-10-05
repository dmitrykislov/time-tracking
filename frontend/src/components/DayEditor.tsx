import { useState, type FormEvent } from 'react';
import { FALLBACK_TIMES, type DefaultTimes } from '../lib/time';
import { DAY_TYPES, type Break, type DayEntry, type DayType } from '../types';

interface Props {
  date: string;
  entry: DayEntry | null;
  standardDayHours: number;
  /** Prefill for a brand-new work day; an existing entry keeps its own times. */
  defaults?: DefaultTimes;
  busy: boolean;
  onSave: (entry: DayEntry) => Promise<boolean> | boolean;
  onDelete?: () => void;
  onCancel: () => void;
}

interface BreakDraft {
  start: string;
  finish: string;
  note: string;
}

/** Inline form for one day. Times are plain HH:mm inputs, leave days take optional hours. */
export function DayEditor({ date, entry, standardDayHours, defaults = FALLBACK_TIMES, busy, onSave, onDelete, onCancel }: Props) {
  const [type, setType] = useState<DayType>(entry?.type ?? 'WORK');
  const [start, setStart] = useState(entry ? (entry.start ?? '') : defaults.start);
  const [finish, setFinish] = useState(entry ? (entry.finish ?? '') : defaults.finish);
  const [breaks, setBreaks] = useState<BreakDraft[]>(
    (entry?.breaks ?? []).map((b) => ({ start: b.start, finish: b.finish ?? '', note: b.note ?? '' })),
  );
  const [hours, setHours] = useState(entry?.hours != null ? String(entry.hours) : '');
  const [problem, setProblem] = useState<string | null>(null);

  const meta = DAY_TYPES.find((t) => t.value === type)!;
  const id = `day-${date}`;

  function updateBreak(index: number, patch: Partial<BreakDraft>) {
    setBreaks((list) => list.map((b, i) => (i === index ? { ...b, ...patch } : b)));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setProblem(null);
    let draft: DayEntry;
    if (type === 'WORK') {
      if (!start) {
        setProblem('A work day needs a start time.');
        return;
      }
      if (breaks.some((b) => !b.start)) {
        setProblem('Every break needs a start time.');
        return;
      }
      const cleaned: Break[] = breaks.map((b) => ({ start: b.start, finish: b.finish || null, note: b.note.trim() || null }));
      draft = { date, type, start, finish: finish || null, breaks: cleaned };
    } else {
      const parsed = hours.trim() === '' ? null : Number(hours);
      if (parsed !== null && (!Number.isFinite(parsed) || parsed <= 0)) {
        setProblem('Hours must be a positive number, or blank for a standard day.');
        return;
      }
      draft = { date, type, hours: parsed };
    }
    await onSave(draft);
  }

  return (
    <form className="day-editor" onSubmit={submit} aria-label={`Edit ${date}`}>
      <div className="field-row">
        <div className="field">
          <label htmlFor={`${id}-type`}>Type</label>
          <select id={`${id}-type`} value={type} onChange={(e) => setType(e.target.value as DayType)}>
            {DAY_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </div>

        {type === 'WORK' ? (
          <>
            <div className="field">
              <label htmlFor={`${id}-start`}>Start</label>
              <input id={`${id}-start`} type="time" value={start} onChange={(e) => setStart(e.target.value)} required />
            </div>
            <div className="field">
              <label htmlFor={`${id}-finish`}>Finish</label>
              <input id={`${id}-finish`} type="time" value={finish} onChange={(e) => setFinish(e.target.value)} />
            </div>
          </>
        ) : (
          <div className="field">
            <label htmlFor={`${id}-hours`}>Hours</label>
            <input
              id={`${id}-hours`}
              type="number"
              step="0.25"
              min="0"
              placeholder={String(standardDayHours)}
              value={hours}
              onChange={(e) => setHours(e.target.value)}
              aria-describedby={`${id}-hours-hint`}
            />
          </div>
        )}
      </div>

      {type !== 'WORK' && (
        <p id={`${id}-hours-hint`} className="muted small">
          {meta.reducesTarget
            ? `Takes ${hours || standardDayHours}h off this week's target. Leave blank for a standard day.`
            : 'Does not change the target: the day is spent from your balance.'}
        </p>
      )}

      {type === 'WORK' && (
        <div className="breaks">
          <div className="breaks-head">
            <span>Breaks</span>
            <button type="button" className="btn btn-sm" onClick={() => setBreaks((b) => [...b, { start: '', finish: '', note: '' }])}>
              + Add break
            </button>
          </div>
          {breaks.length === 0 && <p className="muted small">No breaks.</p>}
          {breaks.map((b, i) => (
            <div className="break-row" key={i}>
              <input
                type="time"
                aria-label={`Break ${i + 1} start`}
                value={b.start}
                onChange={(e) => updateBreak(i, { start: e.target.value })}
                required
              />
              <span className="muted">to</span>
              <input
                type="time"
                aria-label={`Break ${i + 1} finish`}
                value={b.finish}
                onChange={(e) => updateBreak(i, { finish: e.target.value })}
              />
              <input
                type="text"
                className="break-note"
                aria-label={`Break ${i + 1} note`}
                placeholder="note (optional)"
                value={b.note}
                onChange={(e) => updateBreak(i, { note: e.target.value })}
              />
              <button
                type="button"
                className="btn btn-sm btn-ghost"
                aria-label={`Remove break ${i + 1}`}
                onClick={() => setBreaks((list) => list.filter((_, j) => j !== i))}
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}

      {problem && (
        <p className="problem" role="alert">
          {problem}
        </p>
      )}

      <div className="editor-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          Save
        </button>
        <button type="button" className="btn" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        {onDelete && (
          <button type="button" className="btn btn-danger btn-ghost" onClick={onDelete} disabled={busy}>
            Delete day
          </button>
        )}
      </div>
    </form>
  );
}
