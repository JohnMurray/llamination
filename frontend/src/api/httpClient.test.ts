import { request } from './httpClient';

describe('request', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('returns null for a successful no-content response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(request<null>('/api/me/lobby')).resolves.toBeNull();
  });

  it('preserves structured API errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ code: 'LOBBY_FULL', message: 'Lobby is full' }),
      { status: 409, headers: { 'Content-Type': 'application/json' } },
    )));

    await expect(request('/api/lobbies/one/join')).rejects.toEqual(
      expect.objectContaining({ status: 409, code: 'LOBBY_FULL', message: 'Lobby is full' }),
    );
  });
});
