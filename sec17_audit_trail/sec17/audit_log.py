from __future__ import annotations

import base64
import datetime as dt
import hashlib
import json
import sqlite3

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519

GENESIS_HASH = "0" * 64  # the "previous hash" of the very first row

# The roles the application uses. The auditor may only read.
APP_ROLES = ("voter", "aec_staff", "delegate", "admin", "auditor")
READ_ONLY_ROLES = ("auditor",)

SCHEMA = f"""
CREATE TABLE IF NOT EXISTS audit_log (
    seq        INTEGER PRIMARY KEY,
    ts         TEXT NOT NULL,
    actor_role TEXT NOT NULL,
    actor_id   TEXT NOT NULL,
    source_ip  TEXT NOT NULL,
    action     TEXT NOT NULL,
    details    TEXT NOT NULL,
    prev_hash  TEXT NOT NULL,
    hash       TEXT NOT NULL UNIQUE,
    signature  TEXT NOT NULL
);

-- No row can ever be changed or removed.
CREATE TRIGGER IF NOT EXISTS audit_no_update BEFORE UPDATE ON audit_log
BEGIN SELECT RAISE(ABORT, 'audit log is append-only'); END;

CREATE TRIGGER IF NOT EXISTS audit_no_delete BEFORE DELETE ON audit_log
BEGIN SELECT RAISE(ABORT, 'audit log is append-only'); END;

-- A new row must be the next number AND point at the current last hash.
CREATE TRIGGER IF NOT EXISTS audit_must_extend_chain BEFORE INSERT ON audit_log
WHEN NEW.seq != IFNULL((SELECT MAX(seq) FROM audit_log), 0) + 1
  OR NEW.prev_hash != IFNULL((SELECT hash FROM audit_log ORDER BY seq DESC LIMIT 1),
                             '{GENESIS_HASH}')
BEGIN SELECT RAISE(ABORT, 'audit entry does not extend the hash chain'); END;
"""

# Helpers

def canonical_json(obj) -> bytes:
    """Same input -> exactly the same bytes, so hashes are reproducible."""
    return json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()


def entry_hash(prev_hash: str, entry: dict) -> str:
    """SHA-256 of the previous hash followed by this entry's content."""
    content = canonical_json(
        {
            "seq": entry["seq"],
            "ts": entry["ts"],
            "actor_role": entry["actor_role"],
            "actor_id": entry["actor_id"],
            "source_ip": entry["source_ip"],
            "action": entry["action"],
            "details": entry["details"],
        }
    )
    return hashlib.sha256(bytes.fromhex(prev_hash) + content).hexdigest()


def _role_authorizer(role: str):
    """SQLite calls this for every operation on a role's connection.

    Application roles may INSERT and SELECT on the audit log, but never
    UPDATE, DELETE, DROP or ALTER it. The auditor role may only SELECT.
    """
    forbidden_always = {
        sqlite3.SQLITE_UPDATE,
        sqlite3.SQLITE_DELETE,
        sqlite3.SQLITE_DROP_TABLE,
        sqlite3.SQLITE_DROP_TRIGGER,
        sqlite3.SQLITE_ALTER_TABLE,
    }

    def authorizer(action, arg1, arg2, db_name, trigger_name):
        if trigger_name:  # actions run *inside* our own triggers are allowed
            return sqlite3.SQLITE_OK
        # For DROP TRIGGER, SQLite passes the table name in arg2.
        touches_audit = arg1 == "audit_log" or arg2 == "audit_log"
        if touches_audit and action in forbidden_always:
            return sqlite3.SQLITE_DENY
        if touches_audit and role in READ_ONLY_ROLES and action == sqlite3.SQLITE_INSERT:
            return sqlite3.SQLITE_DENY
        return sqlite3.SQLITE_OK

    return authorizer

# The audit log

class AuditLog:
    def __init__(self, db_path: str, signing_key: ed25519.Ed25519PrivateKey | None = None):
        self.db_path = db_path
        self.signing_key = signing_key or ed25519.Ed25519PrivateKey.generate()
        self.public_key = self.signing_key.public_key()

        # Set up the table and triggers once (this is the "owner" / installer step).
        setup = sqlite3.connect(db_path)
        setup.executescript(SCHEMA)
        setup.close()

        self._connections: dict[str, sqlite3.Connection] = {}

    #connections
    def connect_as(self, role: str) -> sqlite3.Connection:
        """A database connection for one application role, with its permissions applied."""
        if role not in APP_ROLES:
            raise ValueError(f"unknown role {role!r}")
        if role not in self._connections:
            conn = sqlite3.connect(self.db_path, isolation_level=None)
            conn.row_factory = sqlite3.Row
            conn.set_authorizer(_role_authorizer(role))
            self._connections[role] = conn
        return self._connections[role]

    def public_key_pem(self) -> str:
        return self.public_key.public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
        ).decode()

    #writing
    def record(self, role: str, actor_id, source_ip: str, action: str, details=None) -> int:
        """Append one attributable, signed entry. Returns its sequence number."""
        conn = self.connect_as(role)
        details_json = json.dumps(details or {}, sort_keys=True, separators=(",", ":"))

        conn.execute("BEGIN IMMEDIATE")  # one writer at a time, so the chain never forks
        try:
            last = conn.execute(
                "SELECT seq, hash FROM audit_log ORDER BY seq DESC LIMIT 1"
            ).fetchone()
            entry = {
                "seq": (last["seq"] + 1) if last else 1,
                "ts": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
                "actor_role": role,
                "actor_id": str(actor_id),
                "source_ip": source_ip,
                "action": action,
                "details": details_json,
            }
            prev_hash = last["hash"] if last else GENESIS_HASH
            digest = entry_hash(prev_hash, entry)
            signature = base64.b64encode(self.signing_key.sign(bytes.fromhex(digest))).decode()

            conn.execute(
                "INSERT INTO audit_log (seq, ts, actor_role, actor_id, source_ip, action,"
                " details, prev_hash, hash, signature) VALUES (?,?,?,?,?,?,?,?,?,?)",
                (
                    entry["seq"],
                    entry["ts"],
                    role,
                    entry["actor_id"],
                    source_ip,
                    action,
                    details_json,
                    prev_hash,
                    digest,
                    signature,
                ),
            )
            conn.execute("COMMIT")
        except Exception:
            conn.execute("ROLLBACK")
            raise
        return entry["seq"]

    #reading
    def entries(self) -> list[dict]:
        rows = self.connect_as("auditor").execute("SELECT * FROM audit_log ORDER BY seq")
        return [dict(row) for row in rows]

    def head(self) -> dict:
        """The latest entry's number and hash - publish this (e.g. in signed results)
        so that later truncation of the log can be detected."""
        last = (
            self.connect_as("auditor")
            .execute("SELECT seq, hash FROM audit_log ORDER BY seq DESC LIMIT 1")
            .fetchone()
        )
        return (
            {"seq": last["seq"], "hash": last["hash"]} if last else {"seq": 0, "hash": GENESIS_HASH}
        )

    def verify(self, anchor: dict | None = None) -> dict:
        return verify_entries(self.entries(), self.public_key, anchor)

    def export(self) -> dict:
        """Evidence package for an external auditor."""
        return {
            "schema": "sec17.audit.v1",
            "public_key_pem": self.public_key_pem(),
            "head": self.head(),
            "entries": self.entries(),
        }

# Verification (also used by the offline auditor tool)

def verify_entries(entries: list[dict], public_key, anchor: dict | None = None) -> dict:
    """Check every link, hash and signature. Returns a small report dictionary."""
    prev_hash = GENESIS_HASH
    seen = {}

    for expected_seq, entry in enumerate(entries, start=1):
        problem = None
        if entry["seq"] != expected_seq:
            problem = f"sequence gap: expected {expected_seq}, found {entry['seq']}"
        elif entry["prev_hash"] != prev_hash:
            problem = "chain link broken (prev_hash does not match)"
        elif entry_hash(prev_hash, entry) != entry["hash"]:
            problem = "content was changed after it was written"
        else:
            try:
                public_key.verify(
                    base64.b64decode(entry["signature"]), bytes.fromhex(entry["hash"])
                )
            except Exception:  # noqa: BLE001 - any failure means the signature is not valid
                problem = "signature invalid"

        if problem:
            return {
                "ok": False,
                "checked": expected_seq - 1,
                "broken_at": entry["seq"],
                "problem": problem,
            }
        prev_hash = entry["hash"]
        seen[entry["seq"]] = entry["hash"]

    if anchor and anchor["seq"] > 0 and seen.get(anchor["seq"]) != anchor["hash"]:
        return {
            "ok": False,
            "checked": len(entries),
            "broken_at": anchor["seq"],
            "problem": "log was truncated or rewritten (does not contain the published head)",
        }

    return {"ok": True, "checked": len(entries), "broken_at": None, "problem": None}