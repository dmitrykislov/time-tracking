import { useEffect, useState } from 'react';

export type Route = { page: 'timesheet' } | { page: 'notes'; date?: string };

export function parseRoute(hash: string): Route {
  const m = /^#\/notes(?:\/(\d{4}-\d{2}-\d{2}))?\/?$/.exec(hash);
  if (m) return { page: 'notes', date: m[1] };
  return { page: 'timesheet' };
}

export function notesHref(date?: string): string {
  return date ? `#/notes/${date}` : '#/notes';
}

/** The current hash route; navigation is a plain link to "#/..." so the browser back button works. */
export function useRoute(): Route {
  const [hash, setHash] = useState(() => window.location.hash);
  useEffect(() => {
    const onChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return parseRoute(hash);
}

/** The wall clock, ticking once a second, so running days and breaks count up live. */
export function useNow(intervalMs = 1000): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}
