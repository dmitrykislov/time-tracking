import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '../src/lib/api';

describe('api', () => {
  const fetchMock = vi.fn();
  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it('sends JSON and returns the parsed view', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ today: '2026-09-30' }), { status: 200 }));
    const result = await api.saveNote('2026-09-30', 'hello');
    expect(result).toEqual({ today: '2026-09-30' });
    expect(fetchMock).toHaveBeenCalledWith('/api/notes/2026-09-30', {
      method: 'PUT',
      body: JSON.stringify({ text: 'hello' }),
      headers: { 'Content-Type': 'application/json' },
    });
  });

  it('turns a problem response into an ApiError carrying the detail', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ status: 400, detail: 'Already on a break' }), { status: 400, statusText: 'Bad Request' }));
    await expect(api.today('start-break')).rejects.toThrow(new ApiError('Already on a break'));
  });

  it('falls back to the status line when the error body is not a problem document', async () => {
    fetchMock.mockResolvedValue(new Response('<html>boom</html>', { status: 500, statusText: 'Internal Server Error' }));
    await expect(api.view()).rejects.toThrow(new ApiError('500 Internal Server Error'));

    fetchMock.mockResolvedValue(new Response(JSON.stringify({ message: 'no detail field' }), { status: 404, statusText: 'Not Found' }));
    await expect(api.deleteDay('2026-09-30')).rejects.toThrow(new ApiError('404 Not Found'));
  });

  it('lets a network failure through untouched', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(api.view()).rejects.toThrow(TypeError);
  });
});
