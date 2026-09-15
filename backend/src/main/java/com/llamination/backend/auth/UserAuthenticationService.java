package com.llamination.backend.auth;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Verifies supplied credentials while keeping account lookup and password hashing replaceable. */
@Service
public class UserAuthenticationService {

    // Matching a dummy hash makes an unknown username less distinguishable from a bad password.
    private static final String DUMMY_PASSWORD_HASH =
            "$2y$12$6773xKcTqQi.4MJ.yooLt.RQPgcP7QXCOetAtfHkAc9r3BLKNZLqK";

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    UserAuthenticationService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<SessionIdentity.Identity> authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return Optional.empty();
        }

        Optional<UserAccount> account = accounts.findByNormalizedUsername(normalize(username));
        String storedHash = account.map(UserAccount::passwordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);
        return account.filter(UserAccount::enabled)
                .filter(ignored -> passwordMatches)
                .map(found -> new SessionIdentity.Identity(found.id(), found.username()));
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
