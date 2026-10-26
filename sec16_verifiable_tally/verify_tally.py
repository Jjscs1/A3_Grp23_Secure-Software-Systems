import json
import sys

from sec16.tally import recompute


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: python verify_tally.py published_results.json")
        return 2

    with open(sys.argv[1], encoding="utf-8") as f:
        results = json.load(f)

    all_ok = True
    for result in results:
        problems = recompute(result)
        name = f"{result['contest']:6} {result['region']}"
        if problems:
            all_ok = False
            for problem in problems:
                print(f"MISMATCH  {problem}")
        else:
            print(f"VERIFIED  {name}: recount gives the same result")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())