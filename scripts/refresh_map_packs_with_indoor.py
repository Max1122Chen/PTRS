#!/usr/bin/env python3
"""Re-collect map packs listed in dev-seed/map-imports.json (outdoor + indoor).

Reads each osm-data/*/_context.json under repo resources, or explicit --only slugs.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path


def load_context(scenic_root: Path) -> dict | None:
    ctx_path = scenic_root / "_context.json"
    if not ctx_path.exists():
        return None
    return json.loads(ctx_path.read_text(encoding="utf-8"))


def collect_one(repo: Path, ctx: dict, *, apply_seed: bool, radius: int, sleep: float) -> int:
    name = str(ctx.get("name") or "").strip()
    display = str(ctx.get("display_name") or name).strip()
    place_id = str(ctx.get("place_id") or "").strip()
    osm_type = str(ctx.get("osm_type") or "").strip()
    osm_id = str(ctx.get("osm_id") or "").strip()
    if not name or not display or not osm_type or not osm_id:
        print(f"[skip] incomplete context: {ctx}", flush=True)
        return 2

    cmd = [
        sys.executable,
        "scripts/osm_seed.py",
        "--skip-config",
        "--target-name",
        name,
        "--query",
        display,
        "--osm-type",
        osm_type,
        "--osm-id",
        osm_id,
        "--radius",
        str(radius),
        "--sleep",
        str(sleep),
        "--collect-indoor",
    ]
    if place_id:
        cmd.extend(["--place-id", place_id])
    if apply_seed:
        cmd.append("--apply-seed")

    print("[run]", " ".join(cmd), flush=True)
    proc = subprocess.run(cmd, cwd=repo, text=True, encoding="utf-8", errors="replace")
    if proc.stdout:
        print(proc.stdout[-6000:], flush=True)
    if proc.stderr:
        print(proc.stderr[-3000:], file=sys.stderr, flush=True)
    return proc.returncode


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", action="append", default=[], help="Substring match on scenic folder name")
    parser.add_argument("--no-apply-seed", action="store_true")
    parser.add_argument("--radius", type=int, default=700)
    parser.add_argument("--sleep", type=float, default=1.5)
    args = parser.parse_args()

    repo = Path(__file__).resolve().parents[1]
    osm_root = repo / "src/main/resources/osm-data"
    if not osm_root.is_dir():
        print("osm-data root missing", file=sys.stderr)
        return 1

    roots = sorted(p for p in osm_root.iterdir() if p.is_dir())
    if args.only:
        roots = [p for p in roots if any(k in p.name for k in args.only)]

    if not roots:
        print("no scenic roots matched", file=sys.stderr)
        return 1

    failed = 0
    for scenic_root in roots:
        ctx = load_context(scenic_root)
        if ctx is None:
            print(f"[skip] no _context.json: {scenic_root.name}", flush=True)
            failed += 1
            continue
        print(f"\n=== {scenic_root.name} ===", flush=True)
        code = collect_one(
            repo,
            ctx,
            apply_seed=not args.no_apply_seed,
            radius=args.radius,
            sleep=args.sleep,
        )
        if code != 0:
            print(f"[fail] exit {code}: {scenic_root.name}", flush=True)
            failed += 1

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
