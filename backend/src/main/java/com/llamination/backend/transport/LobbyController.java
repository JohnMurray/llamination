package com.llamination.backend.transport;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.llamination.backend.auth.SessionIdentity;
import com.llamination.backend.lobby.LobbyService;
import com.llamination.backend.lobby.LobbyPlayer;
import com.llamination.backend.lobby.LobbySnapshot;
import com.llamination.backend.lobby.LobbyVisibility;
import com.llamination.backend.lobby.PublicLobbySummary;
import com.llamination.backend.lobby.TeamChoice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LobbyController {

    private final LobbyService lobbyService;

    public LobbyController(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    @GetMapping("/lobbies")
    public List<PublicLobbySummary> browse(HttpServletRequest request) {
        SessionIdentity.requireUsername(request);
        return lobbyService.browsePublic();
    }

    @PostMapping("/lobbies")
    public LobbySnapshot create(
            @RequestBody CreateLobbyRequest body, HttpServletRequest request) {
        return lobbyService.create(
                player(request), body.visibility(), body.description());
    }

    @GetMapping("/lobbies/{lobbyId}")
    public LobbySnapshot get(
            @PathVariable UUID lobbyId, HttpServletRequest request) {
        return lobbyService.getForMember(lobbyId, SessionIdentity.require(request).userId());
    }

    @PostMapping("/lobbies/{lobbyId}/join")
    public LobbySnapshot join(
            @PathVariable UUID lobbyId, HttpServletRequest request) {
        return lobbyService.joinPublic(lobbyId, player(request));
    }

    @PostMapping("/lobby-invites/{inviteToken}/join")
    public LobbySnapshot joinInvite(
            @PathVariable String inviteToken, HttpServletRequest request) {
        return lobbyService.joinPrivate(inviteToken, player(request));
    }

    @PostMapping("/lobbies/auto-join")
    public LobbySnapshot autoJoin(HttpServletRequest request) {
        return lobbyService.autoJoin(player(request));
    }

    @PutMapping("/lobbies/{lobbyId}/team")
    public LobbySnapshot chooseTeam(
            @PathVariable UUID lobbyId,
            @RequestBody TeamChoiceRequest body,
            HttpServletRequest request) {
        return lobbyService.chooseTeam(
                lobbyId, player(request), body.teamChoice());
    }

    @PostMapping("/lobbies/{lobbyId}/start")
    public LobbySnapshot start(
            @PathVariable UUID lobbyId, HttpServletRequest request) {
        return lobbyService.start(lobbyId, player(request));
    }

    @PostMapping("/lobbies/{lobbyId}/leave")
    public ResponseEntity<Void> leave(
            @PathVariable UUID lobbyId, HttpServletRequest request) {
        lobbyService.leave(lobbyId, player(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/lobby")
    public ResponseEntity<LobbySnapshot> current(HttpServletRequest request) {
        return lobbyService.currentLobby(SessionIdentity.require(request).userId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static LobbyPlayer player(HttpServletRequest request) {
        SessionIdentity.Identity identity = SessionIdentity.require(request);
        return new LobbyPlayer(identity.userId(), identity.username());
    }

    public record CreateLobbyRequest(LobbyVisibility visibility, String description) {
        public CreateLobbyRequest {
            if (visibility == null) {
                visibility = LobbyVisibility.PUBLIC;
            }
        }
    }

    public record TeamChoiceRequest(TeamChoice teamChoice) {
        public TeamChoiceRequest {
            if (teamChoice == null) {
                throw new IllegalArgumentException("teamChoice is required");
            }
        }
    }
}
