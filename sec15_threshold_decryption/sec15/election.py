"""The election and its decryption lock (SEC-15).

Decryption is only possible once the poll is CLOSED. Before that, every
decryption attempt raises PollStillOpen, which the web API turns into
HTTP 423 Locked - even if the caller has all five trustee shares.
"""

from __future__ import annotations

from .ballot_box import decrypt_ballot, encrypt_ballot
from .election_keys import ElectionKeys, unlock_private_key

OPEN, CLOSED = "open", "closed"


class PollStillOpen(Exception):
    """Raised when someone tries to decrypt before the poll has closed (-> 423)."""


class Election:
    def __init__(self, keys: ElectionKeys):
        self.keys = keys
        self.status = OPEN
        self.ballot_box: list[dict] = []
        self.audit: list[str] = []  # simple record of who tried what

    def cast(self, ballot: dict) -> None:
        if self.status != OPEN:
            raise ValueError("Voting has closed.")
        self.ballot_box.append(encrypt_ballot(self.keys.public_key(), ballot))

    def close_poll(self) -> None:
        self.status = CLOSED
        self.audit.append("poll_closed")

    def require_closed(self) -> None:
        """The lock. Called before ANY use of the private key."""
        if self.status != CLOSED:
            self.audit.append("decryption_blocked: poll still open")
            raise PollStillOpen("Ballot decryption is locked until the poll has closed.")

    def decrypt_all(self, share_texts: list[str]) -> list[dict]:
        """Decrypt every ballot using trustee shares. Needs a closed poll and 3+ shares."""
        self.require_closed()  # 1. time lock (checked BEFORE the shares are even looked at)
        private_key = unlock_private_key(self.keys, share_texts)  # 2. 3-of-5 quorum
        self.audit.append(f"decrypted with {len([s for s in share_texts if s])} shares")
        return [decrypt_ballot(private_key, sealed) for sealed in self.ballot_box]