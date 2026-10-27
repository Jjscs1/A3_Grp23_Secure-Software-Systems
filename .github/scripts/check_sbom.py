"""SEC-20: check that generated SBOMs are real, complete CycloneDX documents.

    python .github/scripts/check_sbom.py sbom-backend.cdx.json sbom-frontend.cdx.json

Fails the build if a file is not CycloneDX, lists no packages, or a package has no
version (an SBOM without exact versions is useless for spotting vulnerable packages).
"""

import json
import sys


def problems_in(sbom):
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


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    failed = False
    for name in sys.argv[1:]:
        with open(name, encoding="utf-8") as f:
            sbom = json.load(f)
        problems = problems_in(sbom)
        for problem in problems:
            print(f"{name}: SBOM INVALID: {problem}")
        failed = failed or bool(problems)
        if not problems:
            count = len(sbom["components"])
            print(f"{name}: OK, CycloneDX {sbom.get('specVersion')}, {count} components")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())