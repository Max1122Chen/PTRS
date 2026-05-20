#!/usr/bin/env python3
"""Smoke test: outdoor osm_seed for 北京邮电大学沙河校区 (admin-style args).

Usage (repo root):
  python scripts/verify_osm_outdoor.py
  python scripts/verify_osm_outdoor.py --apply-seed

Exit 0 = pass; non-zero = fail (prints reason).
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

# Canonical anchor from Nominatim (沙河校区 way); matches dev _context.json samples.
BUPT_SHAHE = {
    "target_name": "北京邮电大学沙河校区",
    "query": "北京邮电大学（沙河校区）, 国脉路, 沙河镇, 昌平区, 北京市, 102206, 中国",
    "place_id": "219438262",
    "osm_type": "way",
    "osm_id": "685054417",
}

MIN_BUSINESS_POIS = 40
MIN_ROADS = 50


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply-seed", action="store_true")
    parser.add_argument("--radius", type=int, default=700)
    args = parser.parse_args()

    repo = Path(__file__).resolve().parents[1]
    py = sys.executable
    cmd = [
        py,
        "scripts/osm_seed.py",
        "--skip-config",
        "--target-name",
        BUPT_SHAHE["target_name"],
        "--query",
        BUPT_SHAHE["query"],
        "--place-id",
        BUPT_SHAHE["place_id"],
        "--osm-type",
        BUPT_SHAHE["osm_type"],
        "--osm-id",
        BUPT_SHAHE["osm_id"],
        "--no-collect-indoor",
        "--radius",
        str(args.radius),
        "--sleep",
        "1.5",
    ]
    if args.apply_seed:
        cmd.append("--apply-seed")

    print("[verify] running:", " ".join(cmd), flush=True)
    proc = subprocess.run(cmd, cwd=repo, capture_output=True, text=True, encoding="utf-8", errors="replace")
    stdout = proc.stdout or ""
    stderr = proc.stderr or ""
    if proc.returncode != 0:
        print("[verify] osm_seed exit", proc.returncode, file=sys.stderr)
        print(stdout[-8000:], file=sys.stderr)
        print(stderr[-4000:], file=sys.stderr)
        return proc.returncode or 1

    scenic_root = None
    for line in stdout.splitlines():
        if line.strip().startswith("SCENIC_ROOT="):
            scenic_root = Path(line.strip().split("=", 1)[1])
            break
    if scenic_root is None:
        for line in stdout.splitlines():
            if "SCENIC_ROOT=" in line:
                scenic_root = Path(line.split("SCENIC_ROOT=", 1)[1].strip())
                break
    if scenic_root is None or not scenic_root.is_dir():
        marker_globs = list((repo / "src/main/resources/osm-data").glob("*/latest/.scenic_root.txt"))
        if marker_globs:
            scenic_root = Path(marker_globs[-1].read_text(encoding="utf-8").strip())
    if scenic_root is None or not scenic_root.is_dir():
        print("[verify] FAIL: SCENIC_ROOT not found in script output", file=sys.stderr)
        return 2

    latest = scenic_root / "latest"
    pois_path = latest / "pois.append.json"
    roads_path = latest / "roads.append.json"
    scenic_path = latest / "scenic_areas.append.json"
    for p in (pois_path, roads_path, scenic_path):
        if not p.exists():
            print(f"[verify] FAIL: missing {p}", file=sys.stderr)
            return 3

    pois = json.loads(pois_path.read_text(encoding="utf-8"))
    roads = json.loads(roads_path.read_text(encoding="utf-8"))
    business = [p for p in pois if str(p.get("type") or "").lower() != "virtual_node"]
    osm_fallback = [
        p for p in pois if str(p.get("name") or "").lower().startswith("osm-node-")
    ]

    print(f"[verify] scenic_root={scenic_root}")
    print(f"[verify] pois={len(pois)} business={len(business)} roads={len(roads)} osm_fallback={len(osm_fallback)}")

    failures: list[str] = []
    if len(business) < MIN_BUSINESS_POIS:
        failures.append(f"business POI count {len(business)} < {MIN_BUSINESS_POIS}")
    if len(roads) < MIN_ROADS:
        failures.append(f"roads {len(roads)} < {MIN_ROADS}")
    if osm_fallback:
        failures.append(f"osm-node fallback names still present: {len(osm_fallback)}")

    ctx_path = scenic_root / "_context.json"
    if ctx_path.exists():
        ctx = json.loads(ctx_path.read_text(encoding="utf-8"))
        if str(ctx.get("osm_id")) != BUPT_SHAHE["osm_id"]:
            failures.append(f"context osm_id {ctx.get('osm_id')} != expected {BUPT_SHAHE['osm_id']}")

    if failures:
        for f in failures:
            print("[verify] FAIL:", f, file=sys.stderr)
        return 4

    print("[verify] PASS: 北京邮电大学沙河校区 outdoor seed OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
