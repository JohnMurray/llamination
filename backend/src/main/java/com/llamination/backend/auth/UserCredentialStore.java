package com.llamination.backend.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UserCredentialStore {

    private final Path usersFile;

    public UserCredentialStore(@Value("${llamination.auth.users-file}") String usersFile) {
        this.usersFile = Path.of(usersFile);
    }

    public boolean authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return false;
        }

        String expectedPassword = loadUsers().get(username);
        return expectedPassword != null && constantTimeEquals(expectedPassword, password);
    }

    private Map<String, String> loadUsers() {
        Map<String, String> users = new HashMap<>();
        try {
            for (String rawLine : Files.readAllLines(usersFile, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separator = line.indexOf(':');
                if (separator <= 0) {
                    throw new IllegalStateException("Invalid users file line: expected username:password");
                }

                String username = line.substring(0, separator).trim();
                String password = line.substring(separator + 1);
                if (username.isEmpty() || users.putIfAbsent(username, password) != null) {
                    throw new IllegalStateException("Invalid or duplicate username in users file: " + username);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read users file: " + usersFile.toAbsolutePath(), exception);
        }
        return users;
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] suppliedBytes = supplied.getBytes(StandardCharsets.UTF_8);
        int difference = expectedBytes.length ^ suppliedBytes.length;
        int length = Math.max(expectedBytes.length, suppliedBytes.length);
        for (int index = 0; index < length; index++) {
            byte expectedByte = index < expectedBytes.length ? expectedBytes[index] : 0;
            byte suppliedByte = index < suppliedBytes.length ? suppliedBytes[index] : 0;
            difference |= expectedByte ^ suppliedByte;
        }
        return difference == 0;
    }
}
