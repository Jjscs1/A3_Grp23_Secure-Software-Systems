-- SEC-14: append-only Merkle log tables (PostgreSQL). Run once as the schema owner.
CREATE TABLE IF NOT EXISTS ballotbox_leaf (
    idx  BIGSERIAL PRIMARY KEY,
    leaf CHAR(64) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS ballotbox_checkpoint (
    id   BIGSERIAL PRIMARY KEY,
    alg  VARCHAR(32)  NOT NULL,
    kid  VARCHAR(64)  NOT NULL,
    size BIGINT       NOT NULL,
    root CHAR(64)     NOT NULL,
    ts   VARCHAR(40)  NOT NULL,
    sig  VARCHAR(200) NOT NULL
);
CREATE TABLE IF NOT EXISTS ballotbox_pending (
    id   BIGSERIAL PRIMARY KEY,
    leaf CHAR(64) NOT NULL
);

-- The log and checkpoints can only grow: UPDATE, DELETE and TRUNCATE are rejected, even for the owner.
CREATE OR REPLACE FUNCTION ballotbox_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ballot box log is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS leaf_no_change ON ballotbox_leaf;
CREATE TRIGGER leaf_no_change BEFORE UPDATE OR DELETE ON ballotbox_leaf
    FOR EACH ROW EXECUTE FUNCTION ballotbox_append_only();
DROP TRIGGER IF EXISTS leaf_no_truncate ON ballotbox_leaf;
CREATE TRIGGER leaf_no_truncate BEFORE TRUNCATE ON ballotbox_leaf
    FOR EACH STATEMENT EXECUTE FUNCTION ballotbox_append_only();
DROP TRIGGER IF EXISTS cp_no_change ON ballotbox_checkpoint;
CREATE TRIGGER cp_no_change BEFORE UPDATE OR DELETE ON ballotbox_checkpoint
    FOR EACH ROW EXECUTE FUNCTION ballotbox_append_only();
DROP TRIGGER IF EXISTS cp_no_truncate ON ballotbox_checkpoint;
CREATE TRIGGER cp_no_truncate BEFORE TRUNCATE ON ballotbox_checkpoint
    FOR EACH STATEMENT EXECUTE FUNCTION ballotbox_append_only();

-- Public checkpoints may be mirrored by scrutineers: GET /api/ballotbox/checkpoint
-- Key generation (keep the .key.pem OFF git):
--   openssl genpkey -algorithm ED25519 -out bb-1.key.pem
--   openssl pkey -in bb-1.key.pem -pubout -out bb-1.pub.pem
