package com.llamination.backend.auth;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Loads durable accounts without exposing persistence details to the authentication workflow. */
@Repository
class UserAccountRepository {

    private final JdbcClient jdbcClient;

    UserAccountRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Optional<UserAccount> findByNormalizedUsername(String normalizedUsername) {
        return jdbcClient.sql("""
                        SELECT user_id, username, password_hash, enabled
                        FROM users
                        WHERE normalized_username = :normalizedUsername
                        """)
                .param("normalizedUsername", normalizedUsername)
                .query((resultSet, rowNumber) -> new UserAccount(
                        resultSet.getObject("user_id", java.util.UUID.class),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        resultSet.getBoolean("enabled")))
                .optional();
    }
}

