import os
import re

DECK_EXTS = {".dck"}

# Allow ~9 swapped cards in a 60-card main deck before it's "not a duplicate"
# For 60 cards: sim = (60 - S)/(60 + S). S=9 -> 51/69 = 0.739130...
SIM_THRESHOLD = 51 / 69  # ~= 0.7391304348

BASIC_LANDS = {
    "plains", "island", "swamp", "mountain", "forest", "wastes",
    "snow-covered plains", "snow-covered island", "snow-covered swamp",
    "snow-covered mountain", "snow-covered forest",
}

# also ignore any card whose name contains these as a whole word (catches most land names like "Watery Grave")
BASIC_WORDS = {"plains", "island", "swamp", "mountain", "forest", "wastes"}

def should_scan_file(filename: str) -> bool:
    return os.path.splitext(filename)[1].lower() in DECK_EXTS

def normalize_name(name: str) -> str:
    name = name.strip()
    name = re.sub(r"\s*\(.*?\)\s*$", "", name)
    name = re.sub(r"\s*\[.*?\]\s*$", "", name)
    name = re.sub(r"\s+", " ", name)
    return name.strip().lower()

def is_land_name(normalized_name: str) -> bool:
    # exact basics / snow basics
    if normalized_name in BASIC_LANDS:
        return True
    # word-based catch (e.g., "tropical island", "watery grave" won't match here,
    # but "tropical island" contains "island" as a word -> treated as land)
    words = set(re.findall(r"[a-z]+", normalized_name))
    return any(w in BASIC_WORDS for w in words)

def parse_main_deck_to_counts(text: str) -> dict[str, int] | None:
    lines = text.splitlines()
    in_main = False
    saw_main = False
    counts: dict[str, int] = {}

    for raw in lines:
        line = raw.strip()
        if not line:
            continue

        if line.startswith("[") and line.endswith("]"):
            if line.lower() == "[main]":
                in_main = True
                saw_main = True
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

        if qty <= 0:
            continue

        name = normalize_name(parts[1])
        if not name:
            continue

        counts[name] = counts.get(name, 0) + qty

    return counts if saw_main and counts else None

def strip_lands(counts: dict[str, int]) -> dict[str, int]:
    return {name: qty for name, qty in counts.items() if not is_land_name(name)}

def similarity_multiset(a: dict[str, int], b: dict[str, int]) -> float:
    keys = set(a) | set(b)
    min_sum = 0
    max_sum = 0
    for k in keys:
        qa = a.get(k, 0)
        qb = b.get(k, 0)
        min_sum += min(qa, qb)
        max_sum += max(qa, qb)
    return (min_sum / max_sum) if max_sum else 0.0

def main():
    root = os.path.dirname(os.path.abspath(__file__))

    scanned = 0
    parsed = 0
    deleted = 0
    kept = 0

    for dirpath, _, filenames in os.walk(root):
        deck_files = [fn for fn in filenames if should_scan_file(fn)]
        if not deck_files:
            continue

        deck_files.sort(key=lambda s: s.lower())  # keep the first one we see
        kept_decks: list[tuple[str, dict[str, int]]] = []  # (path, counts_without_lands)

        for fn in deck_files:
            path = os.path.join(dirpath, fn)
            scanned += 1

            try:
                with open(path, "r", encoding="utf-8", errors="replace") as f:
                    text = f.read()
            except Exception as e:
                print(f"[SKIP] Can't read: {path} ({e})")
                continue

            counts = parse_main_deck_to_counts(text)
            if counts is None:
                continue

            parsed += 1
            counts_nolands = strip_lands(counts)

            best_sim = 0.0
            best_path = None

            for kept_path, kept_counts in kept_decks:
                sim = similarity_multiset(counts_nolands, kept_counts)
                if sim > best_sim:
                    best_sim = sim
                    best_path = kept_path
                if sim >= SIM_THRESHOLD:
                    break

            if best_sim >= SIM_THRESHOLD and best_path:
                try:
                    os.remove(path)
                    deleted += 1
                    print(f"DELETED (SIM {best_sim*100:.1f}% >= {SIM_THRESHOLD*100:.1f}%): {path}")
                    print(f"  ↳ too similar to: {best_path}")
                except Exception as e:
                    print(f"[FAIL] Could not delete: {path} ({e})")
            else:
                kept += 1
                kept_decks.append((path, counts_nolands))

    print("\n--- SUMMARY ---")
    print(f"Root: {root}")
    print(f"Scanned .dck files: {scanned}")
    print(f"Parsed decks (had [Main]): {parsed}")
    print(f"Kept: {kept}")
    print(f"Deleted (duplicates): {deleted}")
    print(f"Threshold: {SIM_THRESHOLD*100:.2f}% (≈9 swapped cards in 60-card decks, lands ignored)")
    input("\nDone. Press Enter to close...")

if __name__ == "__main__":
    main()
