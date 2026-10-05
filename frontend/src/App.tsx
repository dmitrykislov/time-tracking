import { useCallback, useEffect, useState } from 'react';
import { notesHref, useRoute } from './hooks';
import { api, ApiError } from './lib/api';
import { defaultTimes, fmtMinutes } from './lib/time';
import type { Config, DayEntry, TodayAction, View } from './types';
import { BalanceChip } from './components/BalanceChip';
import { NotesPage } from './components/NotesPage';
import { Settings } from './components/Settings';
import { TodayPanel } from './components/TodayPanel';
import { WeekCard } from './components/WeekCard';

const REFRESH_MS = 30_000;

function message(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return `Could not reach the server: ${e.message}`;
  return String(e);
}

export function App() {
  const [view, setView] = useState<View | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [showAll, setShowAll] = useState(false);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const route = useRoute();

  const refresh = useCallback(async () => {
    try {
      setView(await api.view());
    } catch (e) {
      setError(message(e));
    }
  }, []);

  useEffect(() => {
    void refresh();
    const id = setInterval(() => void refresh(), REFRESH_MS);
    return () => clearInterval(id);
  }, [refresh]);

  /** Runs one mutation; on success the returned view replaces everything on screen. */
  async function run(op: () => Promise<View>): Promise<boolean> {
    setBusy(true);
    try {
      setView(await op());
      setError(null);
      return true;
    } catch (e) {
      setError(message(e));
      return false;
    } finally {
      setBusy(false);
    }
  }

  const onAction = (action: TodayAction) => void run(() => api.today(action));
  const onSaveDay = (entry: DayEntry) => run(() => api.saveDay(entry));
  const onDeleteDay = (date: string) => run(() => api.deleteDay(date));
  const onSaveConfig = (config: Config) => run(() => api.saveConfig(config));
  const onSaveNote = (date: string, text: string) => run(() => api.saveNote(date, text));

  if (!view) {
    return (
      <div className="page">
        <p className="muted">{error ?? 'Loading…'}</p>
      </div>
    );
  }

  const currentWeek = view.weeks.find((w) => w.current);
  const pastAndCurrent = view.weeks.filter((w) => !w.upcoming);
  const upcoming = view.weeks.filter((w) => w.upcoming).reverse();
  const visibleWeeks = showAll ? pastAndCurrent : pastAndCurrent.slice(0, 6);
  const isExpanded = (start: string, index: number) => expanded[start] ?? index < 2;
  const totals = view.totals;
  const defaults = defaultTimes(view.weeks.flatMap((w) => w.days.map((d) => d.entry)));

  return (
    <div className="page">
      <header className="topbar">
        <div className="topbar-left">
          <h1>Timesheets</h1>
          <nav className="tabs" aria-label="Pages">
            <a href="#/" className={`tab ${route.page === 'timesheet' ? 'active' : ''}`} aria-current={route.page === 'timesheet' ? 'page' : undefined}>
              Timesheet
            </a>
            <a href={notesHref()} className={`tab ${route.page === 'notes' ? 'active' : ''}`} aria-current={route.page === 'notes' ? 'page' : undefined}>
              Notes
            </a>
          </nav>
        </div>
        <div className="topbar-right">
          <BalanceChip minutes={totals.balanceMinutes} title="Balance carried into this week" />
          <button className="btn btn-ghost" onClick={() => setSettingsOpen(true)} aria-label="Settings">
            ⚙ Settings
          </button>
        </div>
      </header>

      {error && (
        <div className="banner banner-error" role="alert">
          <span>{error}</span>
          <button className="btn btn-sm btn-ghost" onClick={() => setError(null)} aria-label="Dismiss">
            ✕
          </button>
        </div>
      )}
      {view.warnings.map((w) => (
        <div className="banner banner-warn" key={w}>
          {w}
        </div>
      ))}

      {route.page === 'notes' ? (
        <NotesPage view={view} focusDate={route.date} busy={busy} onSaveNote={onSaveNote} />
      ) : (
        <>
      <TodayPanel today={view.today} stats={view.todayStats} week={currentWeek} busy={busy} onAction={onAction} />

      <section className="totals card" aria-label="Rolling totals">
        <div>
          <dt>Carried in</dt>
          <dd>
            <BalanceChip minutes={totals.balanceMinutes} />
          </dd>
        </div>
        <div>
          <dt>Worked to date</dt>
          <dd>{fmtMinutes(totals.workedToDateMinutes)}</dd>
        </div>
        <div>
          <dt>Completed weeks</dt>
          <dd>{totals.completedWeeks}</dd>
        </div>
        <div>
          <dt>Worked, completed</dt>
          <dd>{fmtMinutes(totals.workedMinutes)}</dd>
        </div>
        <div>
          <dt>Target, completed</dt>
          <dd>{fmtMinutes(totals.targetMinutes)}</dd>
        </div>
        <div>
          <dt>Leave, completed</dt>
          <dd>{fmtMinutes(totals.leaveMinutes)}</dd>
        </div>
      </section>

      <div className="weeks">
        {visibleWeeks.map((week, index) => (
          <WeekCard
            key={week.start}
            week={week}
            today={view.today}
            now={view.now}
            config={view.config}
            defaults={defaults}
            expanded={isExpanded(week.start, index)}
            busy={busy}
            onToggle={() => setExpanded((e) => ({ ...e, [week.start]: !isExpanded(week.start, index) }))}
            onSaveDay={onSaveDay}
            onDeleteDay={onDeleteDay}
          />
        ))}
        {pastAndCurrent.length > visibleWeeks.length && (
          <button className="btn btn-ghost" onClick={() => setShowAll(true)}>
            Show all {pastAndCurrent.length} weeks
          </button>
        )}
      </div>

      {upcoming.length > 0 && (
        <div className="weeks" aria-label="Upcoming">
          <p className="eyebrow">Upcoming</p>
          {upcoming.map((week) => (
            <WeekCard
              key={week.start}
              week={week}
              today={view.today}
              now={view.now}
              config={view.config}
              defaults={defaults}
              expanded={expanded[week.start] ?? false}
              busy={busy}
              onToggle={() => setExpanded((e) => ({ ...e, [week.start]: !(e[week.start] ?? false) }))}
              onSaveDay={onSaveDay}
              onDeleteDay={onDeleteDay}
            />
          ))}
        </div>
      )}
        </>
      )}

      {settingsOpen && <Settings config={view.config} busy={busy} onSave={onSaveConfig} onClose={() => setSettingsOpen(false)} />}
    </div>
  );
}
