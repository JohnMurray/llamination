package com.llamination.backend.transport;

import java.time.Instant;

import com.llamination.backend.lobby.LobbyError;
import com.llamination.backend.lobby.LobbyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LobbyErrorHandler {

    @ExceptionHandler(LobbyException.class)
    ResponseEntity<ApiError> handleLobbyError(LobbyException exception) {
        HttpStatus status = switch (exception.error()) {
            case LOBBY_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_DESCRIPTION -> HttpStatus.BAD_REQUEST;
            case NOT_A_MEMBER, NOT_CREATOR -> HttpStatus.FORBIDDEN;
            case ALREADY_IN_LOBBY,
                    LOBBY_FULL,
                    LOBBY_NOT_JOINABLE,
                    MINIMUM_NOT_REACHED,
                    INVALID_TEAM_ASSIGNMENT,
                    TEAM_FULL -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(new ApiError(exception.error(), exception.getMessage(), Instant.now()));
    }

    public record ApiError(LobbyError code, String message, Instant timestamp) {
    }
}
