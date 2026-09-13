import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';

import { ApiError } from '../api/httpClient';
import { autoJoinLobby, browseLobbies, getCurrentLobby, joinLobby, lobbyKeys } from './api';

export function LobbyBrowserPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const lobbies = useQuery({ queryKey: lobbyKeys.all, queryFn: browseLobbies });
  const current = useQuery({ queryKey: lobbyKeys.current, queryFn: getCurrentLobby });

  const enter = useMutation({
    mutationFn: joinLobby,
    onSuccess(lobby) {
      queryClient.setQueryData(lobbyKeys.current, lobby);
      navigate(`/lobbies/${lobby.id}`);
    },
  });
  const autoJoin = useMutation({
    mutationFn: autoJoinLobby,
    onSuccess(lobby) {
      queryClient.setQueryData(lobbyKeys.current, lobby);
      navigate(`/lobbies/${lobby.id}`);
    },
  });
  const mutationError = enter.error ?? autoJoin.error;

  return (
    <main className="page">
      <section className="page-heading">
        <div>
          <p className="eyebrow">Ready rooms</p>
          <h1>Public lobbies</h1>
          <p className="subtitle">Find a herd, or gather one of your own.</p>
        </div>
        <div className="heading-actions">
          <button className="secondary" type="button" disabled={Boolean(current.data)} onClick={() => autoJoin.mutate()}>
            {autoJoin.isPending ? 'Searching…' : 'Join random'}
          </button>
          <button className="primary" type="button" disabled={Boolean(current.data)} onClick={() => navigate('/lobbies/new')}>
            Create lobby
          </button>
        </div>
      </section>

      {current.data && (
        <aside className="notice">
          <span>You are already in {current.data.creatorUsername}’s lobby.</span>
          <button className="text-button" type="button" onClick={() => navigate(`/lobbies/${current.data!.id}`)}>Return to lobby</button>
        </aside>
      )}

      {mutationError && <p className="banner-error" role="alert">{errorMessage(mutationError)}</p>}

      {lobbies.isLoading ? (
        <p className="loading">Looking for open lobbies…</p>
      ) : lobbies.isError ? (
        <div className="empty-state"><h2>We lost the trail.</h2><button className="secondary" onClick={() => void lobbies.refetch()}>Try again</button></div>
      ) : lobbies.data?.length ? (
        <div className="lobby-grid">
          {lobbies.data.map((lobby) => (
            <article className="lobby-card" key={lobby.id}>
              <div className="lobby-card-header">
                <div>
                  <p className="eyebrow">Hosted by</p>
                  <h2>{lobby.creatorUsername}</h2>
                </div>
                <span className="player-count">{lobby.currentPlayers}/{lobby.maxPlayers}</span>
              </div>
              <p className={lobby.description ? 'description' : 'description muted'}>
                {lobby.description || 'No description provided.'}
              </p>
              <div className="card-footer">
                <span>{lobby.mapId === 'placeholder-map' ? 'First pasture' : lobby.mapId}</span>
                <button className="primary compact" type="button" disabled={Boolean(current.data) || enter.isPending} onClick={() => enter.mutate(lobby.id)}>
                  Join
                </button>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <p className="eyebrow">Quiet pasture</p>
          <h2>No public lobbies are open.</h2>
          <p>Create one and be the first to welcome players.</p>
        </div>
      )}
    </main>
  );
}

function errorMessage(error: Error) {
  if (error instanceof ApiError && error.code === 'LOBBY_NOT_FOUND') return 'No public lobby is available yet.';
  return error.message;
}
