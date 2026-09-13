import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { CreateLobbyPage } from './CreateLobbyPage';

describe('CreateLobbyPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('creates a private lobby with the entered description', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'lobby-1' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();

    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/lobbies/new']}>
          <Routes>
            <Route path="/lobbies/new" element={<CreateLobbyPage />} />
            <Route path="/lobbies/:id" element={<p>Lobby created</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await user.click(screen.getByRole('radio', { name: /private/i }));
    await user.type(screen.getByLabelText(/short description/i), 'New players welcome');
    await user.click(screen.getByRole('button', { name: 'Create lobby' }));

    expect(await screen.findByText('Lobby created')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/lobbies', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ visibility: 'PRIVATE', description: 'New players welcome' }),
    }));
  });
});
