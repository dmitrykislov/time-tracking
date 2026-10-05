import type { Config, DayEntry, TodayAction, View } from '../types';

export class ApiError extends Error {}

async function call<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const problem = await response.json();
      if (problem?.detail) detail = problem.detail;
    } catch {
      // not a problem body, keep the status text
    }
    throw new ApiError(detail);
  }
  return (await response.json()) as T;
}

export const api = {
  view: () => call<View>('/api/view'),
  saveConfig: (config: Config) => call<View>('/api/config', { method: 'PUT', body: JSON.stringify(config) }),
  saveDay: (entry: DayEntry) => call<View>(`/api/days/${entry.date}`, { method: 'PUT', body: JSON.stringify(entry) }),
  deleteDay: (date: string) => call<View>(`/api/days/${date}`, { method: 'DELETE' }),
  saveNote: (date: string, text: string) => call<View>(`/api/notes/${date}`, { method: 'PUT', body: JSON.stringify({ text }) }),
  today: (action: TodayAction) => call<View>(`/api/today/${action}`, { method: 'POST' }),
};
