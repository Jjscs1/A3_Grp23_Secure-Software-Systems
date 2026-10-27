
CREATE OR REPLACE FUNCTION audit_entry_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_entry is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;;

DROP TRIGGER IF EXISTS audit_entry_no_change ON audit_entry;;
CREATE TRIGGER audit_entry_no_change
    BEFORE UPDATE OR DELETE ON audit_entry
    FOR EACH ROW EXECUTE FUNCTION audit_entry_append_only();;

DROP TRIGGER IF EXISTS audit_entry_no_truncate ON audit_entry;;
CREATE TRIGGER audit_entry_no_truncate
    BEFORE TRUNCATE ON audit_entry
    FOR EACH STATEMENT EXECUTE FUNCTION audit_entry_append_only();;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'evp_app') THEN CREATE ROLE evp_app NOLOGIN; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'evp_auditor') THEN CREATE ROLE evp_auditor NOLOGIN; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'evp_admin') THEN CREATE ROLE evp_admin NOLOGIN; END IF;
END
$$;;

REVOKE ALL ON audit_entry FROM PUBLIC;;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_entry FROM evp_app, evp_auditor, evp_admin;;
GRANT SELECT, INSERT ON audit_entry TO evp_app;;
GRANT USAGE ON SEQUENCE audit_entry_id_seq TO evp_app;;
GRANT SELECT ON audit_entry TO evp_auditor, evp_admin;;