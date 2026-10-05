export type DayType = 'WORK' | 'PUBLIC_HOLIDAY' | 'SICK' | 'PERSONAL_LEAVE' | 'ANNUAL_LEAVE' | 'TIME_IN_LIEU';

export const DAY_TYPES: { value: DayType; label: string; leave: boolean; reducesTarget: boolean }[] = [
  { value: 'WORK', label: 'Work', leave: false, reducesTarget: false },
  { value: 'PUBLIC_HOLIDAY', label: 'Public holiday', leave: true, reducesTarget: true },
  { value: 'SICK', label: 'Sick leave', leave: true, reducesTarget: true },
  { value: 'PERSONAL_LEAVE', label: 'Personal leave', leave: true, reducesTarget: true },
  { value: 'ANNUAL_LEAVE', label: 'Annual leave', leave: true, reducesTarget: true },
  { value: 'TIME_IN_LIEU', label: 'Time in lieu', leave: true, reducesTarget: false },
];

export function dayTypeLabel(type: DayType): string {
  return DAY_TYPES.find((t) => t.value === type)?.label ?? type;
}

export interface Break {
  start: string;
  finish?: string | null;
  note?: string | null;
}

export interface DayEntry {
  date: string;
  type: DayType;
  start?: string | null;
  finish?: string | null;
  breaks?: Break[];
  hours?: number | null;
}

export interface DayStats {
  date: string;
  entry?: DayEntry | null;
  /** The day's free-text note, independent of the entry. */
  note?: string | null;
  workedMinutes: number;
  targetReductionMinutes: number;
  beforeStart: boolean;
  inProgress: boolean;
  onBreak: boolean;
  unfinished: boolean;
}

export interface WeekStats {
  start: string;
  end: string;
  current: boolean;
  upcoming: boolean;
  days: DayStats[];
  workedMinutes: number;
  leaveMinutes: number;
  targetMinutes: number;
  carryInMinutes: number;
  effectiveTargetMinutes: number;
  remainingMinutes: number;
  balanceMinutes: number;
  runningBalanceMinutes: number;
}

/** Completed weeks only, except workedToDateMinutes which adds the current week so far. */
export interface Totals {
  completedWeeks: number;
  workedMinutes: number;
  targetMinutes: number;
  leaveMinutes: number;
  balanceMinutes: number;
  workedToDateMinutes: number;
}

export interface Config {
  weeklyTargetHours: number;
  standardDayHours: number;
  startDate?: string | null;
  openingBalanceHours: number;
}

export interface View {
  config: Config;
  today: string;
  now: string;
  todayStats?: DayStats | null;
  weeks: WeekStats[];
  totals: Totals;
  warnings: string[];
}

export type TodayAction = 'start-day' | 'start-break' | 'end-break' | 'end-day';
