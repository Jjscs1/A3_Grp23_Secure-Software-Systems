-- SEC-19: personal details are stored as AES-256-GCM text (v1.<kid>.<iv>.<ct>), never in the clear.
-- Example for the elector roll; adapt names to the group's Voter table.
ALTER TABLE voter
    ALTER COLUMN full_name TYPE TEXT,
    ALTER COLUMN address   TYPE TEXT,
    ALTER COLUMN dob       TYPE TEXT,
    ALTER COLUMN email     TYPE TEXT;
-- Equality lookup without plaintext: HMAC index (BlindIndex.of("email", value))
ALTER TABLE voter ADD COLUMN IF NOT EXISTS email_idx CHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS voter_email_idx ON voter (email_idx);
-- Connect over TLS:  jdbc:postgresql://host:5432/evp?sslmode=verify-full   (server: ssl = on, ssl_min_protocol_version = 'TLSv1.3')
