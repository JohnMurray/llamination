package com.llamination.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class SessionIdentity {

    public static final String USERNAME_ATTRIBUTE = "username";

    private SessionIdentity() {
    }

    public static String requireUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String username = session == null ? null : (String) session.getAttribute(USERNAME_ATTRIBUTE);
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return username;
    }
}
