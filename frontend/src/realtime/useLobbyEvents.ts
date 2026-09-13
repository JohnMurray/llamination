import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider';
import { lobbyKeys } from '../lobbies/api';
import type { Lobby } from '../lobbies/types';

interface EventEnvelope {
  type: string;
  payload: unknown;
}

export function useLobbyEvents() {
  const auth = useAuth();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  useEffect(() => {
    if (!auth.authenticated || !auth.username) return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    let socket: WebSocket | undefined;
    let reconnectTimer: number | undefined;
    let stopped = false;

    function connect() {
      socket = new WebSocket(`${protocol}//${window.location.host}/ws/events`);
      socket.addEventListener('message', (event) => {
        const message = JSON.parse(String(event.data)) as EventEnvelope;
        if (message.type === 'lobby_directory_changed') {
          void queryClient.invalidateQueries({ queryKey: lobbyKeys.all });
        } else if (message.type === 'lobby_updated') {
          const lobby = message.payload as Lobby;
          queryClient.setQueryData(lobbyKeys.detail(lobby.id), lobby);
          if (lobby.members.some((member) => member.username === auth.username)) {
            queryClient.setQueryData(lobbyKeys.current, lobby);
          }
        } else if (message.type === 'lobby_closed') {
          queryClient.setQueryData(lobbyKeys.current, null);
          void queryClient.invalidateQueries({ queryKey: lobbyKeys.all });
          navigate('/lobbies', { replace: true });
        } else if (message.type === 'game_started') {
          const payload = message.payload as { gameId: string };
          navigate(`/games/${payload.gameId}`, { replace: true });
        }
      });
      socket.addEventListener('close', () => {
        if (!stopped) reconnectTimer = window.setTimeout(connect, 1_000);
      });
    }

    connect();

    return () => {
      stopped = true;
      if (reconnectTimer) window.clearTimeout(reconnectTimer);
      socket?.close();
    };
  }, [auth.authenticated, auth.username, navigate, queryClient]);
}
