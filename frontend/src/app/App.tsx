import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider';
import { LoginPage } from '../auth/LoginPage';
import { GamePage } from '../game/GamePage';
import { GameSandboxPage } from '../game/GameSandboxPage';
import { CreateLobbyPage } from '../lobbies/CreateLobbyPage';
import { InvitePage } from '../lobbies/InvitePage';
import { LobbyBrowserPage } from '../lobbies/LobbyBrowserPage';
import { LobbyRoomPage } from '../lobbies/LobbyRoomPage';
import { useLobbyEvents } from '../realtime/useLobbyEvents';

export function App() {
  const auth = useAuth();
  if (auth.loading) {
    return <main className="centered-shell"><p className="loading">Gathering the herd…</p></main>;
  }
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/sandbox" element={<GameSandboxPage />} />
      <Route element={<ProtectedLayout />}>
        <Route path="/lobbies" element={<LobbyBrowserPage />} />
        <Route path="/lobbies/new" element={<CreateLobbyPage />} />
        <Route path="/lobbies/:lobbyId" element={<LobbyRoomPage />} />
        <Route path="/invite/:inviteToken" element={<InvitePage />} />
        <Route path="/games/:gameId" element={<GamePage />} />
      </Route>
      <Route path="*" element={<Navigate to={auth.authenticated ? '/lobbies' : '/login'} replace />} />
    </Routes>
  );
}

function ProtectedLayout() {
  const auth = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  useLobbyEvents();

  if (!auth.authenticated) {
    return <Navigate to={`/login?returnTo=${encodeURIComponent(location.pathname)}`} replace />;
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <button className="brand" type="button" onClick={() => navigate('/lobbies')}>Llamination</button>
        <div className="account">
          <span>Signed in as <strong>{auth.username}</strong></span>
          <button className="secondary compact" type="button" onClick={() => void auth.logout().then(() => navigate('/login'))}>
            Log out
          </button>
        </div>
      </header>
      <Outlet />
    </div>
  );
}
