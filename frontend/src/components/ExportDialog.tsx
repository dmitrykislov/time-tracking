import { useState } from 'react';

interface Props {
  /** Default start of the period: the timesheet's start date. */
  startDate: string;
  today: string;
  onClose: () => void;
}

/** Builds the URL the browser opens in a new tab. */
export function exportUrl(from: string, to: string): string {
  return `/api/export.pdf?from=${from}&to=${to}`;
}

export function ExportDialog({ startDate, today, onClose }: Props) {
  const [from, setFrom] = useState(startDate);
  const [to, setTo] = useState(today);
  const valid = !!from && !!to && from <= to;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal card" onClick={(e) => e.stopPropagation()} onSubmit={(e) => e.preventDefault()} aria-label="Export PDF">
        <h2>Export PDF</h2>
        <p className="muted small">
          A printable timesheet, week by week: times, breaks, hours worked, and each week's carried-in balance. Opens in a new tab.
        </p>
        <div className="field">
          <label htmlFor="export-from">From</label>
          <input id="export-from" type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} required />
          <small className="muted">Defaults to your start date.</small>
        </div>
        <div className="field">
          <label htmlFor="export-to">To</label>
          <input id="export-to" type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} required />
          <small className="muted">Defaults to today.</small>
        </div>
        {!valid && from && to && (
          <p className="problem" role="alert">
            The period starts after it ends.
          </p>
        )}
        <div className="editor-actions">
          <a
            className={`btn btn-primary ${valid ? '' : 'btn-disabled'}`}
            href={valid ? exportUrl(from, to) : undefined}
            target="_blank"
            rel="noopener"
            aria-disabled={!valid}
            onClick={(e) => {
              if (!valid) e.preventDefault();
              else onClose();
            }}
          >
            Open PDF
          </a>
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
