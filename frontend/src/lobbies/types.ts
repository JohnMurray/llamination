export type LobbyVisibility = 'PUBLIC' | 'PRIVATE';
export type LobbyState = 'WAITING' | 'COUNTDOWN' | 'STARTING' | 'STARTED' | 'CLOSED';
export type TeamChoice = 'RANDOM' | 'TEAM_ONE' | 'TEAM_TWO';
export type Team = 'TEAM_ONE' | 'TEAM_TWO';

export interface LobbyMember {
  username: string;
  creator: boolean;
  teamChoice: TeamChoice;
  assignedTeam: Team | null;
}

export interface Lobby {
  id: string;
  creatorUsername: string;
  visibility: LobbyVisibility;
  description: string;
  mapId: string;
  minPlayers: number;
  maxPlayers: number;
  maxPlayersPerTeam: number;
  state: LobbyState;
  members: LobbyMember[];
  countdownEndsAt: string | null;
  inviteToken: string | null;
  gameId: string | null;
  createdAt: string;
  version: number;
}

export interface PublicLobby {
  id: string;
  creatorUsername: string;
  description: string;
  mapId: string;
  currentPlayers: number;
  minPlayers: number;
  maxPlayers: number;
  createdAt: string;
}
