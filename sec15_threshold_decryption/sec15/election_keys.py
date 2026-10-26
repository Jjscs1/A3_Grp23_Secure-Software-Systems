"""Election key ceremony and threshold unlock (SEC-15).

At setup:
    1. Generate the election key pair (RSA-3072). Ballots are encrypted with the
       PUBLIC key, so anyone can cast a vote but nobody can read one.
    2. Generate a random 256-bit key-encryption key (KEK) and use it to encrypt
       the PRIVATE key.
    3. Split the KEK 3-of-5 between trustees with Shamir secret sharing.
    4. Throw the KEK away. It is never stored anywhere.

At the count:
    Any 3 trustees hand over their shares -> the KEK is rebuilt -> the private
    key is decrypted -> ballots can be decrypted. 2 shares are not enough.
"""

from __future__ import annotations

import hashlib
import os
from dataclasses import dataclass

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

from . import shamir

TRUSTEES = 5
THRESHOLD = 3


@dataclass
class ElectionKeys:
    public_key_pem: bytes  # published - used to encrypt ballots
    locked_private_key_pem: bytes  # private key, encrypted under the KEK
    kek_fingerprint: str  # lets us tell "wrong shares" from "corrupt key file"
    threshold: int = THRESHOLD

    def public_key(self):
        return serialization.load_pem_public_key(self.public_key_pem)


def _fingerprint(kek: bytes) -> str:
    return hashlib.sha256(b"sec15-kek" + kek).hexdigest()


def key_ceremony(trustees: int = TRUSTEES, threshold: int = THRESHOLD):
    """Create the election keys. Returns (ElectionKeys, list of share texts)."""
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    kek = os.urandom(32)

    keys = ElectionKeys(
        public_key_pem=private_key.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
        ),
        locked_private_key_pem=private_key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.BestAvailableEncryption(kek.hex().encode()),
        ),
        kek_fingerprint=_fingerprint(kek),
        threshold=threshold,
    )
    shares = [share.to_text() for share in shamir.split(kek, threshold, trustees)]

    del kek, private_key  # the KEK only ever exists as shares from here on
    return keys, shares


def unlock_private_key(keys: ElectionKeys, share_texts: list[str]):
    """Rebuild the KEK from trustee shares and decrypt the election private key.

    Raises shamir.ShareError if there are too few shares or they are wrong.
    """
    shares = [shamir.Share.from_text(text) for text in share_texts if text and text.strip()]
    kek = shamir.combine(shares, keys.threshold)

    if _fingerprint(kek) != keys.kek_fingerprint:
        raise shamir.ShareError("These shares do not unlock this election's key.")

    return serialization.load_pem_private_key(
        keys.locked_private_key_pem, password=kek.hex().encode()
    )