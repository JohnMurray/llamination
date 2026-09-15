package com.llamination.backend.auth;

import java.util.UUID;

/** Database projection containing the credential material needed during authentication. */
record UserAccount(UUID id, String username, String passwordHash, boolean enabled) {
}

