from __future__ import annotations

import json
import os

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

OAEP = padding.OAEP(
    mgf=padding.MGF1(algorithm=hashes.SHA256()), algorithm=hashes.SHA256(), label=None
)


def encrypt_ballot(public_key, ballot: dict) -> dict:
    aes_key = AESGCM.generate_key(bit_length=256)
    nonce = os.urandom(12)
    return {
        "wrapped_key": public_key.encrypt(aes_key, OAEP),
        "nonce": nonce,
        "ciphertext": AESGCM(aes_key).encrypt(nonce, json.dumps(ballot).encode(), None),
    }


def decrypt_ballot(private_key, sealed: dict) -> dict:
    aes_key = private_key.decrypt(sealed["wrapped_key"], OAEP)
    plaintext = AESGCM(aes_key).decrypt(sealed["nonce"], sealed["ciphertext"], None)
    return json.loads(plaintext)