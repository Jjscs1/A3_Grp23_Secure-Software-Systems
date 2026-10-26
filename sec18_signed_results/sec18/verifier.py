from __future__ import annotations

import datetime as dt
import hashlib
import json
from pathlib import Path

from .signing import canonical_json, key_id, signature_is_valid

ALLOWED_CLOCK_SKEW = dt.timedelta(minutes=5)


class Rejected(Exception):
    """The publication must not be trusted. The message says why."""


class SampleVerifier:
    def __init__(self, trusted_public_key_pem: str, state_file: str | None = None):
        self.public_key_pem = trusted_public_key_pem
        self.key_id = key_id(trusted_public_key_pem)
        self.state_file = Path(state_file) if state_file else None
        self.accepted: dict[str, dict] = {}  # election_id -> newest accepted publication info
        if self.state_file and self.state_file.exists():
            self.accepted = json.loads(self.state_file.read_text(encoding="utf-8"))

    def verify(self, publication: dict, now: dt.datetime | None = None) -> dict:
        """Return the payload if it is genuine and fresh; otherwise raise Rejected."""
        payload = publication.get("payload")
        signature = publication.get("signature")
        if not isinstance(payload, dict) or not isinstance(signature, str):
            raise Rejected("not a results publication (needs 'payload' and 'signature')")

        # 1 + 2: genuine?
        if payload.get("key_id") != self.key_id:
            raise Rejected("signed with a different key than the commission's published key")
        if not signature_is_valid(self.public_key_pem, payload, signature):
            raise Rejected("SIGNATURE INVALID - the payload was forged or modified")

        # 3 + 4 + 5: fresh?
        election = str(payload["election_id"])
        sequence = payload["sequence"]
        issued_at = dt.datetime.fromisoformat(payload["issued_at"])
        now = now or dt.datetime.now(dt.timezone.utc)
        digest = hashlib.sha256(canonical_json(payload)).hexdigest()

        if issued_at - now > ALLOWED_CLOCK_SKEW:
            raise Rejected("issued_at is in the future")

        newest = self.accepted.get(election)
        if newest:
            if sequence < newest["sequence"]:
                raise Rejected(
                    f"REPLAY: sequence {sequence} is lower than the already-accepted "
                    f"sequence {newest['sequence']}"
                )
            if sequence == newest["sequence"] and digest != newest["digest"]:
                raise Rejected(f"CONFLICT: a different payload with sequence {sequence} exists")
            if issued_at < dt.datetime.fromisoformat(newest["issued_at"]):
                raise Rejected("issued_at is earlier than the last accepted publication")

        # Accepted: remember it.
        self.accepted[election] = {
            "sequence": sequence,
            "issued_at": payload["issued_at"],
            "digest": digest,
        }
        if self.state_file:
            self.state_file.write_text(json.dumps(self.accepted, indent=2), encoding="utf-8")
        return payload