import type { DayStats, View, WeekStats } from '../src/types';

export const emptyDay = (date: string): DayStats => ({
  date,
  entry: null,
  workedMinutes: 0,
  targetReductionMinutes: 0,
  beforeStart: false,
  inProgress: false,
  onBreak: false,
  unfinished: false,
});

export const week = (over: Partial<WeekStats> = {}): WeekStats => ({
  start: '2026-09-28',
  end: '2026-10-04',
  current: true,
  upcoming: false,
  days: ['28', '29', '30'].map((d) => emptyDay(`2026-09-${d}`)).concat(['01', '02', '03', '04'].map((d) => emptyDay(`2026-10-${d}`))),
  workedMinutes: 0,
  leaveMinutes: 0,
  targetMinutes: 2400,
  carryInMinutes: 0,
  effectiveTargetMinutes: 2400,
  remainingMinutes: 2400,
  balanceMinutes: -2400,
  runningBalanceMinutes: -2400,
  ...over,
});

export const view = (over: Partial<View> = {}): View => ({
  config: { weeklyTargetHours: 40, standardDayHours: 8, startDate: null, openingBalanceHours: 0 },
  today: '2026-09-30',
  now: '10:15',
  todayStats: emptyDay('2026-09-30'),
  weeks: [week()],
  totals: { completedWeeks: 0, workedMinutes: 0, targetMinutes: 0, leaveMinutes: 0, balanceMinutes: 0, workedToDateMinutes: 0 },
  warnings: [],
  ...over,
});
