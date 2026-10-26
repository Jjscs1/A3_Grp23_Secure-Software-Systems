from __future__ import annotations

import json
import sys
from pathlib import Path


def problems_in(sbom: dict) -> list[str]:
    problems = []
    if sbom.get("bomFormat") != "CycloneDX":
        problems.append("bomFormat is not CycloneDX")
    components = sbom.get("components") or []
    if not components:
        problems.append("the SBOM lists no components")
    for component in components:
        if not component.get("version"):
            problems.append(f"{component.get('name', '?')} has no version")
    return problems


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: python check_sbom.py <sbom.cdx.json>")
        return 1
    sbom = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    problems = problems_in(sbom)
    if problems:
        for problem in problems:
            print(f"SBOM INVALID: {problem}")
        return 1
    names = sorted(f"{c['name']}=={c['version']}" for c in sbom["components"])
    print(f"SBOM OK: CycloneDX {sbom.get('specVersion')}, {len(names)} components")
    for name in names:
        print(f"  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())