package com.llamination.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserCredentialStore credentialStore;

    private final com.llamination.backend.lobby.LobbyService lobbyService;

    public AuthController(
            UserCredentialStore credentialStore,
            com.llamination.backend.lobby.LobbyService lobbyService) {
        this.credentialStore = credentialStore;
        this.lobbyService = lobbyService;
    }

    @PostMapping("/login")
    public SessionResponse login(@RequestBody LoginRequest credentials, HttpServletRequest request) {
        if (!credentialStore.authenticate(credentials.username(), credentials.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(SessionIdentity.USERNAME_ATTRIBUTE, credentials.username());
        return new SessionResponse(true, credentials.username());
    }

    @GetMapping("/session")
    public SessionResponse session(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String username = session == null
                ? null
                : (String) session.getAttribute(SessionIdentity.USERNAME_ATTRIBUTE);
        return new SessionResponse(username != null, username);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String username = (String) session.getAttribute(SessionIdentity.USERNAME_ATTRIBUTE);
            if (username != null) {
                lobbyService.leaveIfPresent(username);
            }
            session.invalidate();
        }
    }

    public record LoginRequest(String username, String password) {
    }

    public record SessionResponse(boolean authenticated, String username) {
    }
}
