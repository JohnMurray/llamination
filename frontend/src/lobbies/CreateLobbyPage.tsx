import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';

import { createLobby, lobbyKeys } from './api';
import type { LobbyVisibility } from './types';

const DESCRIPTION_LIMIT = 160;

export function CreateLobbyPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [description, setDescription] = useState('');
  const mutation = useMutation({
    mutationFn: ({ visibility }: { visibility: LobbyVisibility }) => createLobby(visibility, description),
    onSuccess(lobby) {
      queryClient.setQueryData(lobbyKeys.current, lobby);
      navigate(`/lobbies/${lobby.id}`);
    },
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    mutation.mutate({ visibility: data.get('visibility') as LobbyVisibility });
  }

  return (
    <main className="page narrow-page">
      <button className="back-link" type="button" onClick={() => navigate('/lobbies')}>← Public lobbies</button>
      <section className="panel">
        <p className="eyebrow">Gather your herd</p>
        <h1>Create a lobby</h1>
        <p className="subtitle">The current map supports 2–6 players across two teams.</p>
        <form onSubmit={submit}>
          <fieldset className="visibility-options">
            <legend>Who can join?</legend>
            <label className="radio-card">
              <input type="radio" name="visibility" value="PUBLIC" defaultChecked />
              <span><strong>Public</strong><small>Visible to everyone browsing lobbies.</small></span>
            </label>
            <label className="radio-card">
              <input type="radio" name="visibility" value="PRIVATE" />
              <span><strong>Private</strong><small>Only authenticated players with the invite link.</small></span>
            </label>
          </fieldset>
          <label htmlFor="description">Short description <span className="optional">Optional</span></label>
          <textarea
            id="description"
            maxLength={DESCRIPTION_LIMIT}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="Casual match, newcomers welcome…"
            rows={4}
          />
          <div className="field-meta"><span>Plain text only</span><span>{description.length}/{DESCRIPTION_LIMIT}</span></div>
          {mutation.error && <p className="form-error" role="alert">{mutation.error.message}</p>}
          <button className="primary full-width" type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Creating…' : 'Create lobby'}
          </button>
        </form>
      </section>
    </main>
  );
}
