#!/usr/bin/env python3
"""Check a locally owned Army of Two: TDC XEX set against ReXGlue's AOT target.

Reads, never modifies/uploads/redistributes game files. Matching these checksums
is necessary for the known static recompilation, not sufficient for an Android port.
The values are published by BrewSparrow/ArmyOfTwoTDC-Recomp (retail 0.0.0.11).
"""
import argparse
import hashlib
import json
from pathlib import Path

TITLE_ID = "454109AB"
MEDIA_ID = "60F8EF51"
KNOWN_SHA1 = {
    "default.xex": "b7f987b19a6f392d6c74b65444bd4b3e4eb8d924",
    "AO4.Main.xex": "64da993ea47798252dd580c8625afac45b64df27",
    "Engine.BuildInfo.xex": "3ad0e5f5913d68c927a67439e833538e613b0558",
}


def digest_sha1(path):
    sha = hashlib.sha1()
    with path.open("rb") as data:
        for chunk in iter(lambda: data.read(1024 * 1024), b""):
            sha.update(chunk)
    return sha.hexdigest()


def verify(folder, expected=KNOWN_SHA1):
    folder = Path(folder)
    if not folder.is_dir():
        raise ValueError("Input must be a directory of game files extracted from your own disc")
    files = [p for p in folder.iterdir() if p.is_file() and not p.is_symlink()]
    entries = []
    for filename, sha in expected.items():
        matching = [p for p in files if p.name.lower() == filename.lower()]
        if len(matching) != 1:
            entries.append({"filename": filename, "status": "missing_or_ambiguous"})
            continue
        found = digest_sha1(matching[0])
        entries.append({"filename": filename, "status": "match" if found == sha else "mismatch",
                        "sha1": found, "expected_sha1": sha})
    return {
        "title_id": TITLE_ID,
        "media_id": MEDIA_ID,
        "target": "retail 0.0.0.11",
        "valid_for_known_aot_source": all(x["status"] == "match" for x in entries),
        "files": entries,
        "limitations": [
            "This verifies only the input version for a Windows D3D12 recompiler.",
            "An Android ARM64/Vulkan renderer is not included or verified.",
            "No game code or assets are copied or uploaded.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("extracted_game_folder", type=Path)
    args = parser.parse_args()
    try:
        report = verify(args.extracted_game_folder)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(json.dumps(report, indent=2))
    raise SystemExit(0 if report["valid_for_known_aot_source"] else 2)


if __name__ == "__main__":
    main()
