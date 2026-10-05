import { useEffect, useMemo, useRef, useState } from 'react';
import { fmtMinutes, fmtNoteDay, fmtRange } from '../lib/time';
import { dayTypeLabel, type DayStats, type View, type WeekStats } from '../types';

interface Props {
  view: View;
  /** Day to open for editing straight away, from the #/notes/DATE route. */
  focusDate?: string;
  busy: boolean;
  onSaveNote: (date: string, text: string) => Promise<boolean>;
}

/** One line describing what the timesheet says about the day, shown under the note heading. */
export function daySummary(day: DayStats): string {
  const e = day.entry;
  if (!e) return day.beforeStart ? 'Before start date' : 'Nothing recorded';
  if (e.type !== 'WORK') return dayTypeLabel(e.type) + (e.hours ? ` · ${e.hours}h` : '');
  if (day.unfinished) return `Work · ${e.start} – never ended`;
  const end = day.inProgress ? 'now' : e.finish;
  return `Work · ${e.start} – ${end} · ${fmtMinutes(day.workedMinutes)}`;
}

/** Plain text for the whole week, for pasting into a status report or a message. */
export function weekAsText(week: WeekStats): string {
  const lines = [`Week ${fmtRange(week.start, week.end)}`, ''];
  for (const day of week.days) {
    if (!day.note && !day.entry) continue;
    lines.push(`${fmtNoteDay(day.date)} — ${daySummary(day)}`);
    if (day.note) lines.push(day.note.trim());
    lines.push('');
  }
  return lines.join('\n').trimEnd() + '\n';
}

export function NotesPage({ view, focusDate, busy, onSaveNote }: Props) {
  const weeks = view.weeks;
  const weekFor = (date: string | undefined) => weeks.find((w) => date && w.start <= date && date <= w.end);
  const [selectedStart, setSelectedStart] = useState<string>(() => (weekFor(focusDate) ?? weeks.find((w) => w.current) ?? weeks[0]).start);
  const [editing, setEditing] = useState<string | null>(focusDate ?? null);
  const [copied, setCopied] = useState(false);

  // Following a link to a specific day (from the timesheet) selects its week and opens its editor.
  useEffect(() => {
    if (!focusDate) return;
    const w = weekFor(focusDate);
    if (w) setSelectedStart(w.start);
    setEditing(focusDate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusDate]);

  const week = weeks.find((w) => w.start === selectedStart) ?? weeks[0];
  const index = weeks.indexOf(week);
  const newer = index > 0 ? weeks[index - 1] : undefined;
  const older = index < weeks.length - 1 ? weeks[index + 1] : undefined;
  const noteCount = (w: WeekStats) => w.days.filter((d) => d.note).length;

  async function copyWeek() {
    try {
      await navigator.clipboard.writeText(weekAsText(week));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="notes-layout">
      <aside className="notes-weeks" aria-label="Weeks">
        {weeks.map((w) => (
          <button
            key={w.start}
            className={`notes-week-btn ${w.start === week.start ? 'active' : ''}`}
            onClick={() => {
              setSelectedStart(w.start);
              setEditing(null);
            }}
            aria-current={w.start === week.start ? 'true' : undefined}
          >
            <span className="notes-week-range">{fmtRange(w.start, w.end)}</span>
            <span className="muted small">
              {w.current ? 'This week' : w.upcoming ? 'Upcoming' : noteCount(w) === 0 ? 'No notes' : `${noteCount(w)} ${noteCount(w) === 1 ? 'note' : 'notes'}`}
            </span>
          </button>
        ))}
      </aside>

      <section className="notes-week card" aria-label={`Notes for week of ${week.start}`}>
        <header className="notes-head">
          <div>
            <p className="eyebrow">Week</p>
            <h2>
              {fmtRange(week.start, week.end)} {week.end.slice(0, 4)}
            </h2>
          </div>
          <div className="notes-head-actions">
            <button className="btn btn-sm" onClick={() => older && setSelectedStart(older.start)} disabled={!older} aria-label="Older week">
              ‹ Older
            </button>
            <button className="btn btn-sm" onClick={() => newer && setSelectedStart(newer.start)} disabled={!newer} aria-label="Newer week">
              Newer ›
            </button>
            <button className="btn btn-sm" onClick={copyWeek} aria-label="Copy week as text">
              {copied ? 'Copied ✓' : 'Copy week'}
            </button>
            <a className="btn btn-sm btn-ghost" href="#/">
              Timesheet ›
            </a>
          </div>
        </header>

        {week.days.map((day) => (
          <DayNoteBlock
            key={day.date}
            day={day}
            today={view.today}
            editing={editing === day.date}
            busy={busy}
            onEdit={() => setEditing(day.date)}
            onClose={() => setEditing(null)}
            onSave={onSaveNote}
          />
        ))}
      </section>
    </div>
  );
}

interface BlockProps {
  day: DayStats;
  today: string;
  editing: boolean;
  busy: boolean;
  onEdit: () => void;
  onClose: () => void;
  onSave: (date: string, text: string) => Promise<boolean>;
}

function DayNoteBlock({ day, today, editing, busy, onEdit, onClose, onSave }: BlockProps) {
  const [draft, setDraft] = useState(day.note ?? '');
  const [state, setState] = useState<'idle' | 'saving' | 'saved' | 'failed'>('idle');
  const ref = useRef<HTMLTextAreaElement>(null);
  const savedText = day.note ?? '';

  // Pick up notes that changed elsewhere (another tab, a hand edit) unless we are mid-edit.
  useEffect(() => {
    if (!editing) setDraft(savedText);
  }, [savedText, editing]);

  useEffect(() => {
    if (editing && ref.current) {
      ref.current.focus();
      ref.current.setSelectionRange(ref.current.value.length, ref.current.value.length);
      autoGrow(ref.current);
    }
  }, [editing]);

  async function save() {
    if (draft.trim() === savedText.trim()) {
      onClose();
      return;
    }
    setState('saving');
    const ok = await onSave(day.date, draft);
    setState(ok ? 'saved' : 'failed');
    if (ok) onClose();
  }

  function cancel() {
    setDraft(savedText);
    setState('idle');
    onClose();
  }

  const isToday = day.date === today;
  const paragraphs = useMemo(() => (day.note ?? '').split(/\n{2,}/).map((p) => p.trim()).filter(Boolean), [day.note]);

  return (
    <article className={`note-day ${isToday ? 'is-today' : ''} ${day.beforeStart ? 'is-before' : ''}`} id={`note-${day.date}`} aria-label={fmtNoteDay(day.date)}>
      <header className="note-day-head">
        <div>
          <h3>
            {fmtNoteDay(day.date)}
            {isToday && <span className="badge">Today</span>}
          </h3>
          <p className="muted small">
            {daySummary(day)}
            {' · '}
            <a href="#/" className="muted-link">
              timesheet
            </a>
          </p>
        </div>
        {!editing && (
          <button className="btn btn-sm btn-ghost" onClick={onEdit} aria-label={`Edit note for ${fmtNoteDay(day.date)}`}>
            {day.note ? 'Edit' : '+ Add note'}
          </button>
        )}
      </header>

      {editing ? (
        <div className="note-editor">
          <textarea
            ref={ref}
            className="note-textarea"
            aria-label={`Note for ${fmtNoteDay(day.date)}`}
            value={draft}
            placeholder="What happened today? Decisions, blockers, things to remember…"
            onChange={(e) => {
              setDraft(e.target.value);
              autoGrow(e.target);
            }}
            onBlur={() => void save()}
            onKeyDown={(e) => {
              if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
                e.preventDefault();
                void save();
              } else if (e.key === 'Escape') {
                e.preventDefault();
                cancel();
              }
            }}
            disabled={busy && state === 'saving'}
          />
          <div className="note-editor-bar">
            <span className="muted small">
              {state === 'saving' ? 'Saving…' : state === 'failed' ? 'Not saved, see the message above' : 'Saves when you click away · ⌘↵ to save · Esc to cancel'}
            </span>
            <span className="note-editor-actions">
              <button className="btn btn-sm btn-primary" onMouseDown={(e) => e.preventDefault()} onClick={() => void save()} disabled={busy}>
                Save
              </button>
              <button className="btn btn-sm" onMouseDown={(e) => e.preventDefault()} onClick={cancel} disabled={busy}>
                Cancel
              </button>
            </span>
          </div>
        </div>
      ) : day.note ? (
        <div className="note-text" onClick={onEdit} role="button" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && onEdit()}>
          {paragraphs.map((p, i) => (
            <p key={i}>{renderLines(p)}</p>
          ))}
        </div>
      ) : (
        <p className="note-empty muted" onClick={onEdit}>
          No notes yet.
        </p>
      )}
    </article>
  );
}

function renderLines(paragraph: string) {
  const lines = paragraph.split('\n');
  return lines.map((line, i) => (
    <span key={i}>
      {line}
      {i < lines.length - 1 && <br />}
    </span>
  ));
}

function autoGrow(el: HTMLTextAreaElement) {
  el.style.height = 'auto';
  el.style.height = `${Math.max(140, el.scrollHeight + 2)}px`;
}
