# Timesheets

A single-user time tracker for contract work. Java 25 does the arithmetic, a small React page is the
face, and everything you record lives in one readable JSON file inside this folder.

```
./run.sh                           # builds once, starts on http://localhost:8020, opens your browser
./run.sh --port=9000               # listen on another port (or set TIMESHEETS_PORT)
./run.sh --data=~/work/2026.json   # use a different file
```

## What it does

- **Four buttons for the day.** Start day, Start break, End break, End day. Each press stamps the
  current minute. A running day and a running break count up live on screen.
- **Any number of breaks**, and everything stays editable afterwards. Click a day row to fix a time,
  add a forgotten break, add a note, or backfill a past day by hand.
- **Leave and holidays.** Mark a day as Public holiday, Sick, Personal, Annual or Time in lieu. A half
  day is a leave entry with its own hours.
- **Weeks run Monday to Sunday.** Each week card is a small ledger: hours worked, the target, then
  *carried in + over/under = balance after*. "Over/under" is worked minus target for that week alone;
  "balance after" is what goes into the next week. Hours still to go this week live in the Today panel.
- **Notes.** Every break can carry a short label. Every day has its own notes page: what happened,
  decisions, things to remember. The Notes tab shows a whole week at a time, chronologically, each
  day headed like *Fri, 04 Sept 2026* with the day's hours underneath, and "Copy week" gives you
  the week as plain text. Notes and timesheet link to each other per day.
- **A rolling balance.** Work 43 hours one week and next week's target is 37. Fall short and the
  shortfall follows you the same way. The chip in the top bar is the balance carried into this week,
  and the Today panel shows how far this week has to go.
- **Booked leave ahead.** A public holiday next month shows as an upcoming week with its reduced
  target and is left out of the balance until it arrives.

## The rules

- Worked time for a day is finish minus start minus breaks, to the minute.
- A public holiday, sick, personal or annual day takes a standard day (default 8h, or the entry's
  own hours) **off that week's target**. Hours worked are never inflated with credited hours.
- Time in lieu leaves the target alone. The day simply spends your positive balance, which is the
  point of having overworked.
- Balance for a week = worked − target. Running balance = previous running balance + week balance.
  The effective target shown for a week = target − carry-in.
- A week with nothing recorded still counts its full target. Mark leave if you were away.
- Rolling totals cover completed weeks only, so the carried balance is a settled number. "Worked to
  date" adds the current week so far. Weeks that start after today are upcoming and not counted.
- Every save recomputes everything from the file, so editing any past day updates that week, every
  later week's carry-in and the totals at once.
- If the start date falls mid-week, the weekdays before it are taken off that first week's target.
  With no start date set, counting begins from your first entry (or today, if there are none yet).
- A past day that was started but never ended counts zero and shows a warning until you add a finish.

Settings (gear icon): hours per week, hours per standard day, start date, opening balance.

## The data file

`data/timesheet.json` is created on your first save. It is pretty-printed and sorted by date, so it
diffs cleanly in git and is easy to edit in any editor. The app re-reads it on every request, so
hand edits show up on the next refresh. Writes go through a temp file and an atomic rename.

See `data/example.timesheet.json` for a filled-in week. The shape:

```json
{
  "config": { "weeklyTargetHours": 40, "standardDayHours": 8, "startDate": "2026-09-21", "openingBalanceHours": 0 },
  "days": [
    { "date": "2026-09-21", "type": "WORK", "start": "08:30", "finish": "17:15",
      "breaks": [ { "start": "12:00", "finish": "12:45", "note": "lunch with the client" } ] },
    { "date": "2026-09-23", "type": "SICK", "hours": 4 },
    { "date": "2026-10-05", "type": "PUBLIC_HOLIDAY" }
  ],
  "notes": [
    { "date": "2026-09-21", "text": "Sprint planning.\n\nAgreed the parser rewrite." }
  ]
}
```

Types: `WORK`, `PUBLIC_HOLIDAY`, `SICK`, `PERSONAL_LEAVE`, `ANNUAL_LEAVE`, `TIME_IN_LIEU`.
A running day has no `finish`; a running break has no `finish`. Config numbers may be omitted and
default to 40, 8 and 0. Notes are independent of entries: a day can have either, both or neither.

## API

The UI is a single page with two hash routes, `#/` for the timesheet and `#/notes` (or
`#/notes/2026-09-28` to open a day) for notes. It is a thin client over these endpoints, all returning the full computed view:

| Method | Path | |
|---|---|---|
| GET | `/api/view` | Weeks, totals, today, warnings |
| GET | `/api/timesheet` | The raw file contents |
| PUT | `/api/config` | Save settings |
| PUT | `/api/days/{date}` | Create or replace one day |
| DELETE | `/api/days/{date}` | Remove one day |
| PUT | `/api/notes/{date}` | Set the day's notes, `{ "text": "..." }`; blank text removes them |
| POST | `/api/today/{start-day,start-break,end-break,end-day}` | The buttons |

Rule violations come back as HTTP 400 with a `detail` message the UI shows verbatim.

## Development

```
mvn package                 # builds the React UI into the jar and runs every test (Java + Vitest)
mvn test -Dskip.npm -Dskip.installnodenpm   # Java tests only
cd frontend && npm run dev  # Vite dev server on :5173, proxying /api to :8020
cd frontend && npm test     # Vitest
```

Layout:

- `src/main/java/.../domain` — records and the `TimesheetCalculator`, no framework code
- `src/main/java/.../storage` — `TimesheetStore`, the JSON file
- `src/main/java/.../service` — `TimesheetService`, the four button actions and edits
- `src/main/java/.../web` — REST controller and error mapping
- `frontend/` — Vite + React + TypeScript; `npm run build` writes into `target/classes/static`

Stack: Java 25, Spring Boot 4.1, Jackson 3, React 19, Vite 8, Vitest 5.
