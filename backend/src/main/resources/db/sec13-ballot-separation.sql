-- SEC-13 (PostgreSQL). Run once as the database owner, e.g.
--   psql -v writer_pw="$EVP_BALLOT_DB_WRITER_PASSWORD" -v counter_pw="$EVP_BALLOT_DB_COUNTER_PASSWORD" -f sec13-ballot-separation.sql
-- "evp_app" is the role the Spring app already uses for identity data (voters, users, change requests...).

CREATE SCHEMA IF NOT EXISTS ballots;

-- No voter id, no user id, no IP, no foreign key. Random receipt only; cast time to the hour.
CREATE TABLE ballots.ballot (
    receipt   VARCHAR(32)   PRIMARY KEY,
    division  VARCHAR(60)   NOT NULL,
    payload   VARCHAR(8192) NOT NULL,
    cast_hour VARCHAR(17)   NOT NULL
);

CREATE ROLE ballot_writer  LOGIN PASSWORD :'writer_pw';   -- may only INSERT
CREATE ROLE ballot_counter LOGIN PASSWORD :'counter_pw';  -- may only SELECT (counting)

REVOKE ALL ON SCHEMA ballots FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA ballots FROM PUBLIC;
REVOKE ALL ON SCHEMA ballots FROM evp_app;                 -- the identity role has NO access to ballots

GRANT USAGE  ON SCHEMA ballots TO ballot_writer, ballot_counter;
GRANT INSERT ON ballots.ballot TO ballot_writer;
GRANT SELECT ON ballots.ballot TO ballot_counter;

-- No single role holds privileges on both schemas, so no one credential can join identity data to ballots.
-- Heap order can reveal insertion order: the app shuffles on read; also run CLUSTER ballots.ballot USING ballot_pkey
-- (receipt is random) after polls close and before counting.
