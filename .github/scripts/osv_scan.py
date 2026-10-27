"""SEC-20: dependency scan of the SBOMs against the OSV.dev vulnerability database.

    python .github/scripts/osv_scan.py --allowlist .github/dependency-allowlist.txt \
        sbom-backend.cdx.json sbom-frontend.cdx.json

Reads every package (with its exact version) from CycloneDX SBOMs, asks OSV.dev
(Google's open vulnerability database, which includes the GitHub Advisory Database)
whether that version is affected, and prints what it finds.

Fails the build (exit 1) on any HIGH or CRITICAL vulnerability, unless it is listed in
the allowlist with a reason and a review-by date that has not passed yet. Lower
severities are printed as warnings: visible, but they do not block a merge.

Standard library only, so it runs on any CI runner with Python 3.
"""

import argparse
import datetime as dt
import json
import sys
import urllib.request

OSV = "https://api.osv.dev/v1"
BLOCKING = {"HIGH", "CRITICAL"}


def post(path, body):
    request = urllib.request.Request(
        f"{OSV}/{path}",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def get(path):
    with urllib.request.urlopen(f"{OSV}/{path}", timeout=60) as response:
        return json.load(response)


def packages(sbom_files):
    purls = set()
    for name in sbom_files:
        with open(name, encoding="utf-8") as f:
            sbom = json.load(f)
        if sbom.get("bomFormat") != "CycloneDX":
            raise SystemExit(f"{name} is not a CycloneDX SBOM")
        for component in sbom.get("components", []):
            purl = component.get("purl")
            if purl and "@" in purl:
                purls.add(purl.split("?")[0])  # drop qualifiers such as ?type=jar
    return sorted(purls)


def read_allowlist(path):
    """Lines of:  ADVISORY-ID  review-by=YYYY-MM-DD  reason ..."""
    accepted = {}
    if not path:
        return accepted
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(maxsplit=2)
            if len(parts) < 3 or not parts[1].startswith("review-by="):
                raise SystemExit(f"Bad allowlist line (needs ID, review-by=DATE, reason): {line}")
            accepted[parts[0]] = (dt.date.fromisoformat(parts[1].split("=", 1)[1]), parts[2])
    return accepted


def severity(vuln):
    level = (vuln.get("database_specific") or {}).get("severity")
    return (level or "UNKNOWN").upper().replace("MODERATE", "MEDIUM")


def main():
    parser = argparse.ArgumentParser(description="Scan CycloneDX SBOMs against OSV.dev")
    parser.add_argument("sboms", nargs="+")
    parser.add_argument("--allowlist")
    args = parser.parse_args()

    accepted = read_allowlist(args.allowlist)
    purls = packages(args.sboms)
    print(f"Checking {len(purls)} packages against OSV.dev ...")

    findings = []
    for start in range(0, len(purls), 500):  # OSV accepts up to 1000 queries per batch
        batch = purls[start:start + 500]
        queries = [{"package": {"purl": p}} for p in batch]
        results = post("querybatch", {"queries": queries})["results"]
        for purl, result in zip(batch, results, strict=True):
            for vuln in result.get("vulns", []):
                details = get(f"vulns/{vuln['id']}")
                findings.append((severity(details), purl, vuln["id"], details.get("summary", "")))

    today = dt.date.today()
    blocking = 0
    for level, purl, vuln_id, summary in sorted(findings):
        if level not in BLOCKING:
            status = "warning "
        elif vuln_id in accepted and accepted[vuln_id][0] >= today:
            status = f"accepted until {accepted[vuln_id][0]}"
        else:
            status = "BLOCKING" + (" (allowlist entry expired)" if vuln_id in accepted else "")
            blocking += 1
        print(f"  {status} {level:8} {purl}  {vuln_id}  {summary[:70]}")
    print(f"{len(findings)} known vulnerabilities, {blocking} blocking.")
    return 1 if blocking else 0


if __name__ == "__main__":
    sys.exit(main())