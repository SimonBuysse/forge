import os

DECK_EXTS = {".dck"}

def should_scan_file(filename: str) -> bool:
    return os.path.splitext(filename)[1].lower() in DECK_EXTS

def parse_main_total(text: str) -> int | None:
    lines = text.splitlines()
    in_main = False
    saw_main = False
    total = 0

    for raw in lines:
        line = raw.strip()
        if not line:
            continue

        # section header like [Main]
        if line.startswith("[") and line.endswith("]"):
            if line.lower() == "[main]":
                in_main = True
                saw_main = True
            else:
                in_main = False
            continue

        if not in_main:
            continue

        # "<qty> <card name>"
        parts = line.split(None, 1)
        if not parts:
            continue
        try:
            qty = int(parts[0])
        except ValueError:
            continue

        if qty > 0:
            total += qty

    return total if saw_main else None

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

            total = parse_main_total(text)
            if total is None:
                continue

            checked += 1

            # ✅ delete if below 60, keep if 60 or above
            if total < 60:
                try:
                    os.remove(path)
                    deleted += 1
                    print(f"DELETED (BELOW 60): {path}  (Main = {total})")
                except Exception as e:
                    print(f"[FAIL] Could not delete: {path} ({e})")
            else:
                kept += 1
                print(f"OK (>=60): {path}  (Main = {total})")

    print("\n--- SUMMARY ---")
    print(f"Root: {root}")
    print(f".dck files scanned: {scanned}")
    print(f"Checked deck files: {checked}")
    print(f"Kept: {kept}")
    print(f"Deleted: {deleted}")
    input("\nDone. Press Enter to close...")

if __name__ == "__main__":
    main()
