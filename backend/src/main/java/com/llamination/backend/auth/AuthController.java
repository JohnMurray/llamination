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

    private static final String USERNAME_ATTRIBUTE = "username";

    private final UserCredentialStore credentialStore;

    public AuthController(UserCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    @PostMapping("/login")
    public SessionResponse login(@RequestBody LoginRequest credentials, HttpServletRequest request) {
        if (!credentialStore.authenticate(credentials.username(), credentials.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(USERNAME_ATTRIBUTE, credentials.username());
        return new SessionResponse(true, credentials.username());
    }

    @GetMapping("/session")
    public SessionResponse session(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String username = session == null ? null : (String) session.getAttribute(USERNAME_ATTRIBUTE);
        return new SessionResponse(username != null, username);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public record LoginRequest(String username, String password) {
    }

    public record SessionResponse(boolean authenticated, String username) {
    }
}
