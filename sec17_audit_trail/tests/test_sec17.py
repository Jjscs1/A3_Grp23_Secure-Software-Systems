import json
import sqlite3

import pytest

from sec17.audit_log import APP_ROLES, AuditLog, verify_entries

@pytest.fixture
def log(tmp_path):
    audit = AuditLog(str(tmp_path / "audit.db"))
    audit.record("admin", 1, "10.0.0.5", "login_success")
    audit.record("admin", 1, "10.0.0.5", "election_opened", {"election_id": 1})
    audit.record("aec_staff", 7, "10.0.2.14", "ballot_code_issued", {"booth": "MELB-3"})
    audit.record("delegate", 3, "10.0.1.9", "candidate_added", {"state": "VIC"})
    return audit

def rogue_connection(log):
    """A database administrator going around the application entirely."""
    return sqlite3.connect(log.db_path, isolation_level=None)

#Criterion 1: UPDATE or DELETE on the audit table fails for every application role
@pytest.mark.parametrize("role", APP_ROLES)
@pytest.mark.parametrize(
    "sql",
    [
        "UPDATE audit_log SET action = 'hidden'",
        "UPDATE audit_log SET details = '{}' WHERE seq = 2",
        "DELETE FROM audit_log",
        "DELETE FROM audit_log WHERE seq = (SELECT MAX(seq) FROM audit_log)",
    ],
)
def test_update_or_delete_fails_for_every_application_role(log, role, sql):
    with pytest.raises(sqlite3.DatabaseError):
        log.connect_as(role).execute(sql)
    assert len(log.entries()) == 4  # nothing changed

@pytest.mark.parametrize("role", APP_ROLES)
def test_no_role_can_drop_the_protecting_triggers(log, role):
    with pytest.raises(sqlite3.DatabaseError):
        log.connect_as(role).execute("DROP TRIGGER audit_no_update")

def test_triggers_block_update_and_delete_even_without_role_checks(log):
    rogue = rogue_connection(log)
    with pytest.raises(sqlite3.IntegrityError, match="append-only"):
        rogue.execute("UPDATE audit_log SET action = 'hidden'")
    with pytest.raises(sqlite3.IntegrityError, match="append-only"):
        rogue.execute("DELETE FROM audit_log")

def test_auditor_is_read_only(log):
    with pytest.raises(sqlite3.DatabaseError):
        log.record("auditor", 9, "10.0.9.9", "sneaky_entry")
    assert log.connect_as("auditor").execute("SELECT COUNT(*) FROM audit_log").fetchone()[0] == 4

def test_forged_insert_that_does_not_extend_the_chain_is_rejected(log):
    with pytest.raises(sqlite3.IntegrityError, match="hash chain"):
        rogue_connection(log).execute(
            "INSERT INTO audit_log VALUES"
            " (99, 't', 'admin', '1', '-', 'forged', '{}', ?, 'ab', 'x')",
            ("0" * 64,),
        )

#Criterion 2: editing one row breaks hash-chain verification
def test_clean_log_verifies(log):
    assert log.verify() == {"ok": True, "checked": 4, "broken_at": None, "problem": None}

def test_editing_one_row_breaks_verification(log):
    rogue = rogue_connection(log)
    rogue.execute("DROP TRIGGER audit_no_update")  # the attacker removes the protection first
    rogue.execute('UPDATE audit_log SET details = \'{"booth":"ELSEWHERE"}\' WHERE seq = 3')
    report = log.verify()
    assert not report["ok"]
    assert report["broken_at"] == 3
    assert report["problem"] == "content was changed after it was written"

def test_recalculating_the_hash_does_not_help_without_the_signing_key(log):
    rogue = rogue_connection(log)
    rogue.execute("DROP TRIGGER audit_no_update")
    row = dict(log.entries()[-1])
    row["action"] = "nothing_happened"
    from sec17.audit_log import entry_hash

    new_hash = entry_hash(row["prev_hash"], row)
    rogue.execute(
        "UPDATE audit_log SET action = ?, hash = ? WHERE seq = ?",
        (row["action"], new_hash, row["seq"]),
    )
    assert log.verify()["problem"] == "signature invalid"

def test_deleting_a_middle_row_is_detected(log):
    rogue = rogue_connection(log)
    rogue.execute("DROP TRIGGER audit_no_delete")
    rogue.execute("DELETE FROM audit_log WHERE seq = 2")
    assert "sequence gap" in log.verify()["problem"]

def test_cutting_off_the_end_is_detected_with_a_published_head(log):
    published_head = log.head()  # e.g. printed inside the signed election results
    rogue = rogue_connection(log)
    rogue.execute("DROP TRIGGER audit_no_delete")
    rogue.execute("DELETE FROM audit_log WHERE seq = 4")
    assert log.verify()["ok"]  # the shorter chain still links up...
    assert not log.verify(anchor=published_head)["ok"]  # ...but the published head exposes it

#Attribution and offline auditing
def test_every_entry_says_who_where_when_and_what(log):
    entry = log.entries()[2]
    assert entry["actor_role"] == "aec_staff" and entry["actor_id"] == "7"
    assert entry["source_ip"] == "10.0.2.14"
    assert entry["ts"].endswith("+00:00")
    assert json.loads(entry["details"]) == {"booth": "MELB-3"}

def test_offline_auditor_needs_only_the_export_and_public_key(log):
    export = json.loads(json.dumps(log.export()))  # exactly what is handed to the auditor
    from cryptography.hazmat.primitives import serialization

    key = serialization.load_pem_public_key(export["public_key_pem"].encode())
    assert verify_entries(export["entries"], key, export["head"])["ok"]

    export["entries"][0]["actor_id"] = "999"
    assert not verify_entries(export["entries"], key)["ok"]