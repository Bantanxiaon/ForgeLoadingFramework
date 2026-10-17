"""Fetch only the official SDL compile SDK, verified against recorded SHA256."""
from pathlib import Path
import argparse
import hashlib
import json
import urllib.request

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    folder = root / "adapters/modern"
    entries = json.loads((folder / "build-sdk.json").read_text(encoding="utf-8"))
    target = folder / "dependencies"
    for entry in entries:
        path = target / entry["name"]
        if path.exists():
            if hashlib.sha256(path.read_bytes()).hexdigest() != entry["sha256"]:
                raise SystemExit("Existing SDK hash mismatch; preserve and inspect: " + str(path))
            print("VERIFIED", entry["name"])
            continue
        if args.verify_only:
            raise SystemExit("Missing SDK: " + str(path))
        if not entry["url"].startswith("https://libraries.minecraft.net/"):
            raise SystemExit("Unexpected SDK source")
        with urllib.request.urlopen(entry["url"], timeout=60) as response:
            content = response.read(8 * 1024 * 1024 + 1)
        if len(content) > 8 * 1024 * 1024 or hashlib.sha256(content).hexdigest() != entry["sha256"]:
            raise SystemExit("Downloaded SDK failed verification: " + entry["name"])
        target.mkdir(parents=True, exist_ok=True)
        with path.open("xb") as output:
            output.write(content)
        print("FETCHED", entry["name"])

if __name__ == "__main__":
    main()
