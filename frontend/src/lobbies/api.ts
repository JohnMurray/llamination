import { request } from '../api/httpClient';
import type { Lobby, LobbyVisibility, PublicLobby, TeamChoice } from './types';

export const lobbyKeys = {
  all: ['lobbies'] as const,
  current: ['lobbies', 'current'] as const,
  detail: (id: string) => ['lobbies', id] as const,
};

export function browseLobbies() {
  return request<PublicLobby[]>('/api/lobbies');
}

export function getCurrentLobby() {
  return request<Lobby | null>('/api/me/lobby');
}

export function getLobby(id: string) {
  return request<Lobby>(`/api/lobbies/${id}`);
}

export function createLobby(visibility: LobbyVisibility, description: string) {
  return request<Lobby>('/api/lobbies', {
    method: 'POST',
    body: JSON.stringify({ visibility, description }),
  });
}

export function joinLobby(id: string) {
  return request<Lobby>(`/api/lobbies/${id}/join`, { method: 'POST' });
}

export function joinInvite(token: string) {
  return request<Lobby>(`/api/lobby-invites/${encodeURIComponent(token)}/join`, { method: 'POST' });
}

export function autoJoinLobby() {
  return request<Lobby>('/api/lobbies/auto-join', { method: 'POST' });
}

export function chooseTeam(id: string, teamChoice: TeamChoice) {
  return request<Lobby>(`/api/lobbies/${id}/team`, {
    method: 'PUT',
    body: JSON.stringify({ teamChoice }),
  });
}

export function startLobby(id: string) {
  return request<Lobby>(`/api/lobbies/${id}/start`, { method: 'POST' });
}

export function leaveLobby(id: string) {
  return request<null>(`/api/lobbies/${id}/leave`, { method: 'POST' });
}
