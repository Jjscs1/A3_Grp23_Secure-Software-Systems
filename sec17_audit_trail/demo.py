import json
import os
import sqlite3

from sec17.audit_log import APP_ROLES, AuditLog

DB = "demo_audit.db"
if os.path.exists(DB):
    os.remove(DB)

log = AuditLog(DB)

print("1. Recording privileged and security events:")
log.record("admin", 1, "10.0.0.5", "login_success")
log.record("admin", 1, "10.0.0.5", "election_opened", {"election_id": 1})
log.record("aec_staff", 7, "10.0.2.14", "ballot_code_issued", {"booth": "Melbourne 3"})
log.record("delegate", 3, "10.0.1.9", "candidate_added", {"state": "VIC"})
log.record("voter", 1234, "203.0.113.20", "login_failed", {"reason": "bad password"})
for e in log.entries():
    print(
        f"   #{e['seq']} {e['actor_role']}:{e['actor_id']} from {e['source_ip']} -> {e['action']}"
    )

print(f"\n2. Verify: {log.verify()}")

print("\n3. Every application role tries to UPDATE and DELETE the log:")
for role in APP_ROLES:
    conn = log.connect_as(role)
    for sql in ("UPDATE audit_log SET action = 'nothing'", "DELETE FROM audit_log"):
        try:
            conn.execute(sql)
            print(f"   {role:10} {sql.split()[0]:6} -> ALLOWED (this should never happen)")
        except sqlite3.DatabaseError as error:
            print(f"   {role:10} {sql.split()[0]:6} -> BLOCKED ({error})")

with open("audit_export.json", "w", encoding="utf-8") as f:
    json.dump(log.export(), f, indent=2)
print("\n   Exported a clean copy for the auditor: audit_export.json")

print("\n4. A rogue DBA bypasses the app, drops the trigger and edits entry #3:")
rogue = sqlite3.connect(DB)
rogue.execute("DROP TRIGGER audit_no_update")
rogue.execute('UPDATE audit_log SET details = \'{"booth":"somewhere else"}\' WHERE seq = 3')
rogue.commit()
result = log.verify()
print(f"   Verify: ok={result['ok']}, broken at entry #{result['broken_at']}: {result['problem']}")

with open("audit_export_tampered.json", "w", encoding="utf-8") as f:
    json.dump(log.export(), f, indent=2)
print("\n5. Exported the tampered log too: audit_export_tampered.json")
print("   Check both with:  python verify_audit.py <file>")