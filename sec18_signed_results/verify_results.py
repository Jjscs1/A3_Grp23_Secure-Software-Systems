import argparse
import json
import sys

from sec18.verifier import Rejected, SampleVerifier


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify a signed results publication.")
    parser.add_argument("publication", help="JSON file received from the commission")
    parser.add_argument("--public-key", required=True, help="the commission's published key")
    parser.add_argument("--state", default="verifier_state.json")
    args = parser.parse_args()

    with open(args.public_key, encoding="utf-8") as f:
        verifier = SampleVerifier(f.read(), state_file=args.state)
    with open(args.publication, encoding="utf-8") as f:
        publication = json.load(f)

    try:
        payload = verifier.verify(publication)
    except Rejected as reason:
        print(f"REJECTED: {reason}")
        return 1
    print(
        f"ACCEPTED: election {payload['election_id']}, sequence {payload['sequence']}, "
        f"issued {payload['issued_at']}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())