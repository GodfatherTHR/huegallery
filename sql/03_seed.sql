-- Optional demo user. Username: demo / Password: DemoPass123!
-- The hash below was produced with jBCrypt (BCrypt.hashpw, cost 12).
-- Change or delete this account before exposing the application publicly.

MERGE INTO users u
USING (SELECT 'demo' AS username,
              '$2a$12$D2EAU.lZc8WIiSAa854HJueDJpOHwifTbjgidW2KJVZ6GO8bP4qui' AS password_hash
       FROM dual) s
ON (u.username = s.username)
WHEN NOT MATCHED THEN
    INSERT (username, password_hash) VALUES (s.username, s.password_hash);

COMMIT;
