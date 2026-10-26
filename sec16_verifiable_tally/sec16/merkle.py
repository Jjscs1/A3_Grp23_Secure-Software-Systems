from __future__ import annotations

import hashlib


def merkle_root(receipts: list[str]) -> str:
    if not receipts:
        return hashlib.sha256(b"\x02empty").hexdigest()

    level = [hashlib.sha256(b"\x00" + r.encode()).digest() for r in sorted(receipts)]
    while len(level) > 1:
        if len(level) % 2 == 1:
            level.append(level[-1])  # odd number of nodes: repeat the last one
        level = [
            hashlib.sha256(b"\x01" + level[i] + level[i + 1]).digest()
            for i in range(0, len(level), 2)
        ]
    return level[0].hex()