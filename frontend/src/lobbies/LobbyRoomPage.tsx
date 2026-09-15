import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider';
import { chooseTeam, getLobby, leaveLobby, lobbyKeys, startLobby } from './api';
import type { Lobby, TeamChoice } from './types';

export function LobbyRoomPage() {
  const { lobbyId = '' } = useParams();
  const auth = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const lobbyQuery = useQuery({ queryKey: lobbyKeys.detail(lobbyId), queryFn: () => getLobby(lobbyId), enabled: Boolean(lobbyId) });
  const lobby = lobbyQuery.data;

  useEffect(() => {
    if (lobby?.state === 'STARTED' && lobby.gameId) navigate(`/games/${lobby.gameId}`, { replace: true });
  }, [lobby, navigate]);

  const team = useMutation({
    mutationFn: (choice: TeamChoice) => chooseTeam(lobbyId, choice),
    onSuccess: (next) => queryClient.setQueryData(lobbyKeys.detail(lobbyId), next),
  });
  const start = useMutation({
    mutationFn: () => startLobby(lobbyId),
    onSuccess: (next) => queryClient.setQueryData(lobbyKeys.detail(lobbyId), next),
  });
  const leave = useMutation({
    mutationFn: () => leaveLobby(lobbyId),
    onSuccess() {
      queryClient.setQueryData(lobbyKeys.current, null);
      navigate('/lobbies', { replace: true });
    },
  });

  if (lobbyQuery.isLoading) return <main className="page"><p className="loading">Entering lobby…</p></main>;
  if (!lobby || lobbyQuery.isError) return <main className="page"><div className="empty-state"><h2>This lobby is unavailable.</h2><button className="secondary" onClick={() => navigate('/lobbies')}>Browse lobbies</button></div></main>;

  const currentMember = lobby.members.find((member) => member.username === auth.username);
  const locked = lobby.state !== 'WAITING';
  const error = team.error ?? start.error ?? leave.error;

  return (
    <main className="page lobby-room">
      <section className="room-heading">
        <div>
          <p className="eyebrow">{lobby.visibility === 'PRIVATE' ? 'Private lobby' : 'Public lobby'}</p>
          <h1>{lobby.creatorUsername}’s lobby</h1>
          <p className="subtitle">{lobby.description || 'Preparing for the next battle.'}</p>
        </div>
        <div className="room-status">
          <strong>{lobby.members.length}/{lobby.maxPlayers}</strong>
          <span>players</span>
        </div>
      </section>

      {lobby.state === 'COUNTDOWN' && lobby.countdownEndsAt && <Countdown endsAt={lobby.countdownEndsAt} />}
      {lobby.state === 'STARTING' && <aside className="countdown"><span>Preparing the battlefield…</span></aside>}

      <section className="team-picker panel">
        <div>
          <h2>Choose your team</h2>
          <p>Pick a side or let the server balance you when the countdown begins.</p>
        </div>
        <div className="segmented" role="group" aria-label="Team choice">
          {(['TEAM_ONE', 'RANDOM', 'TEAM_TWO'] as TeamChoice[]).map((choice) => (
            <button
              key={choice}
              type="button"
              className={currentMember?.teamChoice === choice ? 'selected' : ''}
              disabled={locked || team.isPending}
              onClick={() => team.mutate(choice)}
            >
              {teamLabel(choice)}
            </button>
          ))}
        </div>
      </section>

      <div className="teams-grid">
        <TeamPanel title="Team One" lobby={lobby} team="TEAM_ONE" />
        <TeamPanel title="Random assignment" lobby={lobby} team="RANDOM" />
        <TeamPanel title="Team Two" lobby={lobby} team="TEAM_TWO" />
      </div>

      {lobby.visibility === 'PRIVATE' && lobby.inviteToken && (
        <InviteLink token={lobby.inviteToken} />
      )}

      {error && <p className="banner-error" role="alert">{error.message}</p>}

      <footer className="room-actions">
        <button className="danger" type="button" disabled={leave.isPending} onClick={() => leave.mutate()}>
          {currentMember?.creator ? 'Close lobby' : 'Leave lobby'}
        </button>
        {currentMember?.creator && (
          <button className="primary" type="button" disabled={locked || lobby.members.length < lobby.minPlayers || start.isPending} onClick={() => start.mutate()}>
            {lobby.members.length < lobby.minPlayers ? `Need ${lobby.minPlayers} players` : 'Start 10-second countdown'}
          </button>
        )}
      </footer>
    </main>
  );
}

function TeamPanel({ title, lobby, team }: { title: string; lobby: Lobby; team: TeamChoice }) {
  const players = lobby.members.filter((member) => member.teamChoice === team);
  return (
    <section className={`team-panel ${team.toLowerCase()}`}>
      <header><h2>{title}</h2><span>{players.length}{team === 'RANDOM' ? '' : `/${lobby.maxPlayersPerTeam}`}</span></header>
      <ul>
        {players.map((member) => <li key={member.username}><span className="avatar">{member.username.charAt(0).toUpperCase()}</span><span>{member.username}</span>{member.creator && <small>Creator</small>}</li>)}
        {!players.length && <li className="empty-slot">No players yet</li>}
      </ul>
    </section>
  );
}

function Countdown({ endsAt }: { endsAt: string }) {
  const [seconds, setSeconds] = useState(() => secondsLeft(endsAt));
  useEffect(() => {
    setSeconds(secondsLeft(endsAt));
    const timer = window.setInterval(() => setSeconds(secondsLeft(endsAt)), 200);
    return () => window.clearInterval(timer);
  }, [endsAt]);
  return <aside className="countdown"><span>Teams locked. Game begins in</span><strong>{seconds}</strong></aside>;
}

function secondsLeft(endsAt: string) {
  return Math.max(0, Math.ceil((new Date(endsAt).getTime() - Date.now()) / 1_000));
}

function InviteLink({ token }: { token: string }) {
  const [copied, setCopied] = useState(false);
  const url = `${window.location.origin}/invite/${token}`;
  async function copy() {
    await navigator.clipboard.writeText(url);
    setCopied(true);
  }
  return <section className="invite-box"><div><h2>Invite players</h2><p>Copy this link now. It remains valid after a restart, but the server stores only its hash and may not display it again.</p></div><div className="copy-row"><input readOnly value={url} aria-label="Invite link" /><button className="secondary" type="button" onClick={() => void copy()}>{copied ? 'Copied!' : 'Copy link'}</button></div></section>;
}

function teamLabel(choice: TeamChoice) {
  if (choice === 'TEAM_ONE') return 'Team One';
  if (choice === 'TEAM_TWO') return 'Team Two';
  return 'Random';
}
