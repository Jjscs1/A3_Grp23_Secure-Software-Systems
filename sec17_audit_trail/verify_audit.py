import argparse
import json
import sys

from cryptography.hazmat.primitives import serialization

from sec17.audit_log import verify_entries

def main() -> int:
    parser = argparse.ArgumentParser(description="Verify an exported audit log.")
    parser.add_argument("export")
    parser.add_argument("--public-key", help="trusted audit public key (PEM file)")
    args = parser.parse_args()

    with open(args.export, encoding="utf-8") as f:
        export = json.load(f)

    if args.public_key:
        with open(args.public_key, encoding="utf-8") as f:
            pem = f.read()
    else:
        print("note: using the public key inside the export (pass --public-key to pin it)")
        pem = export["public_key_pem"]

    report = verify_entries(
        export["entries"], serialization.load_pem_public_key(pem.encode()), export.get("head")
    )
    if report["ok"]:
        print(f"INTACT: all {report['checked']} entries verified (hash chain + signatures)")
        return 0
    print(f"TAMPERING DETECTED at entry {report['broken_at']}: {report['problem']}")
    return 1


if __name__ == "__main__":
    sys.exit(main())