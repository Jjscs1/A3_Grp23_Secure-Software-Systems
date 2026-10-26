from __future__ import annotations

import base64
import hashlib
import json

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519


def canonical_json(obj) -> bytes:
    return json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()


def new_election_key() -> ed25519.Ed25519PrivateKey:
    return ed25519.Ed25519PrivateKey.generate()


def public_key_pem(private_key: ed25519.Ed25519PrivateKey) -> str:
    return (
        private_key.public_key()
        .public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
        .decode()
    )


def key_id(public_pem: str) -> str:
    """A short fingerprint of the public key, so verifiers know which key signed a payload."""
    return hashlib.sha256(public_pem.strip().encode()).hexdigest()[:16]


def sign(private_key: ed25519.Ed25519PrivateKey, payload: dict) -> str:
    return base64.b64encode(private_key.sign(canonical_json(payload))).decode()


def signature_is_valid(public_pem: str, payload: dict, signature: str) -> bool:
    try:
        public_key = serialization.load_pem_public_key(public_pem.encode())
        public_key.verify(base64.b64decode(signature), canonical_json(payload))
        return True
    except Exception:  # noqa: BLE001 - any failure (bad key, bad base64, wrong bytes) = invalid
        return False