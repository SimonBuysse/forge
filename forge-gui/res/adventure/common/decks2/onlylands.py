import os
import re

DECK_EXTS = {".dck"}

# names to treat as "basic lands" (and optionally snow basics)
BASIC_LANDS = {
    "plains", "island", "swamp", "mountain", "forest", "wastes",
    "snow-covered plains", "snow-covered island", "snow-covered swamp",
    "snow-covered mountain", "snow-covered forest",
}

def should_scan_file(filename: str) -> bool:
    return os.path.splitext(filename)[1].lower() in DECK_EXTS

def normalize_name(name: str) -> str:
    # strip common suffix junk (rare in .dck, but safe)
    name = name.strip()
    name = re.sub(r"\s*\(.*?\)\s*$", "", name)   # trailing "(...)" set info
    name = re.sub(r"\s*\[.*?\]\s*$", "", name)   # trailing "[...]" tags
    name = re.sub(r"\s+", " ", name)
    return name.strip().lower()

def extract_main_cards(text: str) -> list[tuple[int, str]]:
    """
    Returns list of (qty, cardname) from [Main] only.
    Stops at next [Section] (e.g. [Sideboard]).
    """
    lines = text.splitlines()
    in_main = False
    cards: list[tuple[int, str]] = []

    for raw in lines:
        line = raw.strip()
        if not line:
            continue

        if line.startswith("[") and line.endswith("]"):
            if line.lower() == "[main]":
                in_main = True
            else:
                in_main = False
            continue

        if not in_main:
            continue

        parts = line.split(None, 1)
        if len(parts) < 2:
            continue

        try:
            qty = int(parts[0])
        except ValueError:
            continue

        name = parts[1].strip()
        if qty > 0 and name:
            cards.append((qty, name))

    return cards

def is_only_basic_lands(cards: list[tuple[int, str]]) -> bool:
    # If no cards parsed, treat as not a valid "only basics" deck.
    if not cards:
        return False

    for qty, name in cards:
        n = normalize_name(name)
        if n not in BASIC_LANDS:
            return False
    return True

def main():
    root = os.path.dirname(os.path.abspath(__file__))

    deleted = 0
    kept = 0
    scanned = 0
    checked = 0

    for dirpath, _, filenames in os.walk(root):
        for fn in filenames:
            if not should_scan_file(fn):
                continue

            path = os.path.join(dirpath, fn)
            scanned += 1

            try:
                with open(path, "r", encoding="utf-8", errors="replace") as f:
                    text = f.read()
            except Exception as e:
                print(f"[SKIP] Can't read: {path} ({e})")
                continue

            cards = extract_main_cards(text)
            if not cards:
                continue  # no [Main] or nothing parsable

            checked += 1

            if is_only_basic_lands(cards):
                try:
                    os.remove(path)
                    deleted += 1
                    unique = sorted({normalize_name(n) for _, n in cards})
                    print(f"DELETED (ONLY BASIC LANDS): {path}  (cards: {', '.join(unique)})")
                except Exception as e:
                    print(f"[FAIL] Could not delete: {path} ({e})")
            else:
                kept += 1
                # print(f"OK: {path}")  # uncomment if you want spam

    print("\n--- SUMMARY ---")
    print(f"Root: {root}")
    print(f".dck files scanned: {scanned}")
    print(f"Checked deck files: {checked}")
    print(f"Kept: {kept}")
    print(f"Deleted: {deleted}")
    input("\nDone. Press Enter to close...")

if __name__ == "__main__":
    main()
