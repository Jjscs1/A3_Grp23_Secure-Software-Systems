from __future__ import annotations

import datetime as dt
import json
from pathlib import Path

from .signing import key_id, new_election_key, public_key_pem, sign

SCHEMA = "sec18.results.v1"


class ResultsPublisher:
    def __init__(self, private_key=None):
        self._private_key = private_key or new_election_key()
        self.public_key_pem = public_key_pem(self._private_key)
        self.key_id = key_id(self.public_key_pem)
        self.history: dict[int, list[dict]] = {}  # election_id -> every publication, in order

    def publish(self, election_id: int, results: list[dict]) -> dict:
        """Sign and release the next version of the results for an election."""
        previous = self.history.setdefault(election_id, [])
        payload = {
            "schema": SCHEMA,
            "election_id": election_id,
            "sequence": len(previous) + 1,
            "issued_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
            "key_id": self.key_id,
            "results": results,
        }
        publication = {"payload": payload, "signature": sign(self._private_key, payload)}
        previous.append(publication)
        return publication

    def latest(self, election_id: int) -> dict:
        return self.history[election_id][-1]

    def save_public_key(self, path: str) -> None:
        """Publish the public key (e.g. on the commission's website) for verifiers."""
        Path(path).write_text(self.public_key_pem, encoding="utf-8")

    @staticmethod
    def save(publication: dict, path: str) -> None:
        Path(path).write_text(json.dumps(publication, indent=2), encoding="utf-8")