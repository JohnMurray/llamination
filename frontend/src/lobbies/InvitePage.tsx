import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { ApiError } from '../api/httpClient';
import { getCurrentLobby, joinInvite, lobbyKeys } from './api';

export function InvitePage() {
  const { inviteToken = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const attempted = useRef(false);
  const mutation = useMutation({
    mutationFn: () => joinInvite(inviteToken),
    async onError(error) {
      if (error instanceof ApiError && error.code === 'ALREADY_IN_LOBBY') {
        const lobby = await getCurrentLobby();
        if (lobby) navigate(`/lobbies/${lobby.id}`, { replace: true });
      }
    },
    onSuccess(lobby) {
      queryClient.setQueryData(lobbyKeys.current, lobby);
      navigate(`/lobbies/${lobby.id}`, { replace: true });
    },
  });

  useEffect(() => {
    if (!attempted.current && inviteToken) {
      attempted.current = true;
      mutation.mutate();
    }
  }, [inviteToken, mutation]);

  return (
    <main className="centered-shell">
      <section className="auth-card centered-copy">
        <p className="eyebrow">Private invitation</p>
        {mutation.isError ? <><h1>Invite unavailable</h1><p className="subtitle">{mutation.error.message}</p><button className="secondary" onClick={() => navigate('/lobbies')}>Browse public lobbies</button></> : <><h1>Joining the herd…</h1><p className="subtitle">Checking your invitation.</p></>}
      </section>
    </main>
  );
}
