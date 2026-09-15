package com.llamination.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.llamination.backend.lobby.LobbyPlayer;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    private final com.llamination.backend.lobby.LobbyService lobbyService;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            com.llamination.backend.lobby.LobbyService lobbyService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.lobbyService = lobbyService;
    }

    @PostMapping("/login")
    public SessionResponse login(
            @RequestBody LoginRequest credentials,
            HttpServletRequest request,
            HttpServletResponse response) {
        org.springframework.security.core.Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            credentials.username(), credentials.password()));
        } catch (org.springframework.security.core.AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        SessionIdentity.Identity identity = authenticationIdentity(authentication);
        session.setAttribute(SessionIdentity.IDENTITY_ATTRIBUTE, identity);
        session.setAttribute(SessionIdentity.USERNAME_ATTRIBUTE, identity.username());

        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);
        return new SessionResponse(true, identity.username());
    }

    @GetMapping("/session")
    public SessionResponse session(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        SessionIdentity.Identity identity = session == null
                ? null
                : (SessionIdentity.Identity) session.getAttribute(SessionIdentity.IDENTITY_ATTRIBUTE);
        String username = identity == null ? null : identity.username();
        return new SessionResponse(username != null, username);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            SessionIdentity.Identity identity =
                    (SessionIdentity.Identity) session.getAttribute(SessionIdentity.IDENTITY_ATTRIBUTE);
            if (identity != null) {
                lobbyService.leaveIfPresent(new LobbyPlayer(identity.userId(), identity.username()));
            }
            session.invalidate();
            SecurityContextHolder.clearContext();
        }
    }

    private SessionIdentity.Identity authenticationIdentity(
            org.springframework.security.core.Authentication authentication) {
        return authentication.getPrincipal() instanceof SessionIdentity.Identity identity
                ? identity
                : throwUnexpectedPrincipal();
    }

    private static SessionIdentity.Identity throwUnexpectedPrincipal() {
        throw new IllegalStateException("Authentication did not provide a session identity");
    }

    public record LoginRequest(String username, String password) {
    }

    public record SessionResponse(boolean authenticated, String username) {
    }
}
