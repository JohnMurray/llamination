-- Stable development identities support repeatable multi-browser lobby testing.
INSERT INTO users (user_id, username, normalized_username, password_hash, enabled, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'commander', 'commander', '$2y$12$6Np4x.KoWszSdAHQWu4u0e.xdhnXE/ub3emokJKR82qt5g9cqEEi2', TRUE, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000002', 'scout', 'scout', '$2y$12$SOQKenpdfs8ZMbqKjz4tueAw.HZQpeBxTOwo4nMyXLZGAkzhnqNmu', TRUE, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000003', 'builder', 'builder', '$2y$12$MuMDqXanfCkzf1V5aXLRIuWyO4ZsJbgetBefHyIupnUKVTxYWuEs6', TRUE, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000004', 'rider', 'rider', '$2y$12$dsj5O7ePDkMB5lPtcxnHj.ka/4e3RpjRPO14HahSmEqVPuRTzLawe', TRUE, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000005', 'shepherd', 'shepherd', '$2y$12$y48pJ3QxwsTbsa3a3duVO.vbQ7z5TW.tlCY13TjQuxXf/2GpT1uyO', TRUE, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000006', 'herder', 'herder', '$2y$12$u.3LBT3PQ771w5inBGl/nujX0sHn7mnhYRYeVmcfHYbh80otXSuvy', TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (normalized_username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    enabled = TRUE;

