"""Shamir secret sharing (SEC-15).

Splits a 32-byte secret into `count` shares so that any `threshold` of them
rebuild it, and fewer than `threshold` reveal nothing at all about it.

How it works:
    * Pick a random polynomial  f(x) = secret + a1*x + a2*x^2   (degree = threshold - 1)
    * Share i is the point (i, f(i)).
    * Any `threshold` points fix the polynomial, so f(0) = secret can be recovered
      with Lagrange interpolation. With fewer points, every secret is equally likely.

All arithmetic is done modulo a large prime (2^521 - 1), and the random
coefficients come from Python's `secrets` module (a cryptographic RNG).
"""

from __future__ import annotations

import hashlib
import secrets
from dataclasses import dataclass

PRIME = 2**521 - 1  # a Mersenne prime, much larger than any 256-bit secret
SECRET_BYTES = 32
PREFIX = "evs1"  # version tag at the start of every share


class ShareError(ValueError):
    """Raised when shares are missing, mistyped, or do not belong together."""


@dataclass(frozen=True)
class Share:
    index: int  # the x value (1..count)
    value: int  # f(index)

    def to_text(self) -> str:
        """Text form a trustee can store, e.g. evs1-3-<hex>-<checksum>."""
        body = f"{PREFIX}-{self.index}-{self.value:0132x}"
        checksum = hashlib.sha256(body.encode()).hexdigest()[:8]
        return f"{body}-{checksum}"

    @classmethod
    def from_text(cls, text: str) -> Share:
        parts = (text or "").strip().split("-")
        if len(parts) != 4 or parts[0] != PREFIX:
            raise ShareError("Not a trustee share (expected evs1-<n>-<hex>-<checksum>).")

        body = "-".join(parts[:3])
        if hashlib.sha256(body.encode()).hexdigest()[:8] != parts[3].lower():
            raise ShareError("Share checksum mismatch - check it was copied correctly.")

        try:
            index, value = int(parts[1]), int(parts[2], 16)
        except ValueError:
            raise ShareError("Share is malformed.") from None
        if not 1 <= index < 256 or not 0 <= value < PRIME:
            raise ShareError("Share is out of range.")
        return cls(index, value)


def split(secret: bytes, threshold: int, count: int) -> list[Share]:
    """Split `secret` into `count` shares; any `threshold` of them rebuild it."""
    if len(secret) != SECRET_BYTES:
        raise ValueError("secret must be exactly 32 bytes")
    if not 2 <= threshold <= count < 256:
        raise ValueError("need 2 <= threshold <= count < 256")

    # f(x) = secret + a1*x + ... + a(t-1)*x^(t-1), with random a1..a(t-1)
    coefficients = [int.from_bytes(secret, "big")]
    coefficients += [secrets.randbelow(PRIME) for _ in range(threshold - 1)]

    shares = []
    for x in range(1, count + 1):
        y = 0
        for c in reversed(coefficients):  # Horner's rule: evaluate f(x)
            y = (y * x + c) % PRIME
        shares.append(Share(x, y))
    return shares


def combine(shares: list[Share], threshold: int) -> bytes:
    """Rebuild the secret from at least `threshold` *different* shares."""
    unique = {share.index: share for share in shares}  # the same share twice only counts once
    if len(unique) < threshold:
        raise ShareError(
            f"At least {threshold} different trustee shares are required ({len(unique)} supplied)."
        )

    points = list(unique.values())[:threshold]
    secret = 0
    for i, share_i in enumerate(points):
        # Lagrange basis polynomial evaluated at x = 0
        numerator, denominator = 1, 1
        for j, share_j in enumerate(points):
            if i != j:
                numerator = (numerator * -share_j.index) % PRIME
                denominator = (denominator * (share_i.index - share_j.index)) % PRIME
        secret = (secret + share_i.value * numerator * pow(denominator, -1, PRIME)) % PRIME

    if secret >= 1 << (8 * SECRET_BYTES):
        raise ShareError("These shares do not belong to the same key.")
    return secret.to_bytes(SECRET_BYTES, "big")