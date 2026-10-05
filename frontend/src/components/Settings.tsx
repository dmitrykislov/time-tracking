import { useState, type FormEvent } from 'react';
import type { Config } from '../types';

interface Props {
  config: Config;
  busy: boolean;
  onSave: (config: Config) => Promise<boolean>;
  onClose: () => void;
}

export function Settings({ config, busy, onSave, onClose }: Props) {
  const [weekly, setWeekly] = useState(String(config.weeklyTargetHours));
  const [day, setDay] = useState(String(config.standardDayHours));
  const [startDate, setStartDate] = useState(config.startDate ?? '');
  const [opening, setOpening] = useState(String(config.openingBalanceHours ?? 0));

  async function submit(event: FormEvent) {
    event.preventDefault();
    const ok = await onSave({
      weeklyTargetHours: Number(weekly),
      standardDayHours: Number(day),
      startDate: startDate || null,
      openingBalanceHours: Number(opening || 0),
    });
    if (ok) onClose();
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal card" onClick={(e) => e.stopPropagation()} onSubmit={submit} aria-label="Settings">
        <h2>Settings</h2>
        <div className="field">
          <label htmlFor="cfg-week">Hours per week</label>
          <input id="cfg-week" type="number" step="0.1" min="0.1" value={weekly} onChange={(e) => setWeekly(e.target.value)} required />
          <small className="muted">Your contracted week, e.g. 40 or 38.</small>
        </div>
        <div className="field">
          <label htmlFor="cfg-day">Hours per day</label>
          <input id="cfg-day" type="number" step="0.1" min="0.1" max="24" value={day} onChange={(e) => setDay(e.target.value)} required />
          <small className="muted">What one leave day or public holiday takes off the week, e.g. 8 or 7.6.</small>
        </div>
        <div className="field">
          <label htmlFor="cfg-start">Start date</label>
          <input id="cfg-start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          <small className="muted">Nothing before this date counts. Leave blank to start from your first entry.</small>
        </div>
        <div className="field">
          <label htmlFor="cfg-opening">Opening balance (hours)</label>
          <input id="cfg-opening" type="number" step="0.25" value={opening} onChange={(e) => setOpening(e.target.value)} />
          <small className="muted">Hours already in credit (or negative, in debt) on the start date.</small>
        </div>
        <div className="editor-actions">
          <button type="submit" className="btn btn-primary" disabled={busy}>
            Save
          </button>
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
