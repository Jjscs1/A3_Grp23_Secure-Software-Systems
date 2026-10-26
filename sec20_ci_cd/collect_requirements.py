from __future__ import annotations

import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def collect(root: Path = REPO_ROOT) -> list[str]:
    lines = set()
    for req_file in sorted(root.glob("*/requirements.txt")):
        for line in req_file.read_text(encoding="utf-8").splitlines():
            line = line.split("#")[0].strip()  # drop comments and blank lines
            if line:
                lines.add(line)
    return sorted(lines, key=str.lower)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: python collect_requirements.py <output file>")
        return 1
    requirements = collect()
    Path(sys.argv[1]).write_text("\n".join(requirements) + "\n", encoding="utf-8")
    print(f"Wrote {len(requirements)} requirements to {sys.argv[1]}:")
    for line in requirements:
        print(f"  {line}")
    return 0


if __name__ == "__main__":
    sys.exit(main())