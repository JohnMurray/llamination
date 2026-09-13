package com.llamination.backend.lobby;

import org.springframework.stereotype.Component;

@Component
public class DefaultLobbyConstraintsProvider implements LobbyConstraintsProvider {

    private static final LobbyConstraints PLACEHOLDER_CONSTRAINTS =
            new LobbyConstraints("placeholder-map", 2, 6, 2, 3);

    @Override
    public LobbyConstraints currentConstraints() {
        // TODO: Read these constraints from the selected map once the map catalog exists.
        return PLACEHOLDER_CONSTRAINTS;
    }
}
