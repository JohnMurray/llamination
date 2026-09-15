package com.llamination.backend.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class SessionIdentity {

    public static final String USERNAME_ATTRIBUTE = "username";
    public static final String IDENTITY_ATTRIBUTE = "identity";

    private SessionIdentity() {
    }

    public static String requireUsername(HttpServletRequest request) {
        return require(request).username();
    }

    public static Identity require(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Identity identity = session == null ? null : (Identity) session.getAttribute(IDENTITY_ATTRIBUTE);
        if (identity == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return identity;
    }

    /** Stable user ID plus display username, serialized with the Redis-backed HTTP session. */
    public record Identity(UUID userId, String username) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
