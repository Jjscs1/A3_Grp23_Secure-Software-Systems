from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def find_modules(root: Path = REPO_ROOT) -> list[Path]:
    return sorted(
        folder
        for folder in root.iterdir()
        if folder.is_dir()
        and (folder / "requirements.txt").is_file()
        and (folder / "tests").is_dir()
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the tests of every module.")
    parser.add_argument("--skip-install", action="store_true")
    args = parser.parse_args()

    modules = find_modules()
    if not modules:
        print("No modules with tests found.")
        return 1

    results = {}
    for module in modules:
        print(f"\n=== {module.name} ===", flush=True)
        if not args.skip_install:
            subprocess.run(
                [sys.executable, "-m", "pip", "install", "-q", "-r", "requirements.txt"],
                cwd=module,
                check=True,
            )
        tests = subprocess.run([sys.executable, "-m", "pytest", "-q"], cwd=module)
        results[module.name] = tests.returncode == 0

    print("\n=== Summary ===")
    for name, passed in results.items():
        print(f"  {'PASS' if passed else 'FAIL'}  {name}")
    return 0 if all(results.values()) else 1


if __name__ == "__main__":
    sys.exit(main())