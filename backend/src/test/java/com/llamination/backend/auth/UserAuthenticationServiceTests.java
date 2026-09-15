package com.llamination.backend.auth;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers credential normalization and canonical identity without requiring infrastructure. */
class UserAuthenticationServiceTests {

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserAuthenticationService service = new UserAuthenticationService(accounts, encoder);

    @Test
    void authenticatesCaseInsensitiveUsernameAsCanonicalIdentity() {
        UUID id = UUID.randomUUID();
        when(accounts.findByNormalizedUsername("commander"))
                .thenReturn(Optional.of(new UserAccount(id, "Commander", encoder.encode("llama"), true)));

        assertThat(service.authenticate("  COMMANDER ", "llama"))
                .contains(new SessionIdentity.Identity(id, "Commander"));
    }

    @Test
    void rejectsBadPasswordAndDisabledAccount() {
        UUID id = UUID.randomUUID();
        when(accounts.findByNormalizedUsername("commander"))
                .thenReturn(Optional.of(new UserAccount(id, "commander", encoder.encode("llama"), false)));

        assertThat(service.authenticate("commander", "llama")).isEmpty();
        assertThat(service.authenticate("commander", "wrong")).isEmpty();
    }
}
