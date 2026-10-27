CREATE TRIGGER IF NOT EXISTS audit_entry_no_update BEFORE UPDATE ON audit_entry
    FOR EACH ROW CALL 'com.evp.audit.H2AppendOnlyTrigger';;
CREATE TRIGGER IF NOT EXISTS audit_entry_no_delete BEFORE DELETE ON audit_entry
    FOR EACH ROW CALL 'com.evp.audit.H2AppendOnlyTrigger';;

CREATE USER IF NOT EXISTS evp_app PASSWORD 'test-only';;
CREATE USER IF NOT EXISTS evp_auditor PASSWORD 'test-only';;
CREATE USER IF NOT EXISTS evp_admin PASSWORD 'test-only';;
GRANT SELECT, INSERT ON audit_entry TO evp_app;;
GRANT SELECT ON audit_entry TO evp_auditor;;
GRANT SELECT ON audit_entry TO evp_admin;;