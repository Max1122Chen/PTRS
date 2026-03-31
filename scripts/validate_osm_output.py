#!/usr/bin/env python3
"""Validate OSM generated output quality and schema constraints.

Usage:
  python scripts/validate_osm_output.py --dir src/main/resources/osm-data/<slug>/latest
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Dict, List, Tuple


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def check_virtual_node_fields(pois: List[dict]) -> List[str]:
    errors: List[str] = []
    forbidden = {"description", "createTime", "updateTime"}
    for row in pois:
        if str(row.get("type", "")).strip().lower() != "virtual_node":
            continue
        hit = [k for k in forbidden if k in row]
        if hit:
            errors.append(f"virtual_node id={row.get('id')} contains forbidden fields: {','.join(sorted(hit))}")
    return errors


def check_roads(roads: List[dict]) -> List[str]:
    errors: List[str] = []
    seen = set()
    for row in roads:
        sid = row.get("startId")
        eid = row.get("endId")
        rid = row.get("id")
        if sid == eid:
            errors.append(f"road id={rid} is self-loop: {sid}->{eid}")
            continue
        a, b = (sid, eid) if sid <= eid else (eid, sid)
        key = (a, b)
        if key in seen:
            errors.append(f"duplicate undirected road: {a}<->{b}")
        seen.add(key)
    return errors


def degree_stats(roads: List[dict]) -> Dict[int, int]:
    deg: Dict[int, int] = {}
    for row in roads:
        sid = int(row["startId"])
        eid = int(row["endId"])
        deg[sid] = deg.get(sid, 0) + 1
        deg[eid] = deg.get(eid, 0) + 1
    return deg


def check_virtual_connectivity(pois: List[dict], roads: List[dict]) -> List[str]:
    errors: List[str] = []
    virtual_ids = {
        int(row["id"])
        for row in pois
        if str(row.get("type", "")).strip().lower() == "virtual_node"
    }
    if not virtual_ids:
        return errors
    deg = degree_stats(roads)
    isolated = [vid for vid in sorted(virtual_ids) if deg.get(vid, 0) == 0]
    if isolated:
        errors.append(f"isolated virtual_node count={len(isolated)} sample={isolated[:8]}")
    return errors


def check_poi_coverage(pois: List[dict], roads: List[dict]) -> List[str]:
    errors: List[str] = []
    virtual_ids = {
        int(row["id"])
        for row in pois
        if str(row.get("type", "")).strip().lower() == "virtual_node"
    }
    business_pois = {
        int(row["id"])
        for row in pois
        if str(row.get("type", "")).strip().lower() != "virtual_node"
    }
    if not business_pois:
        errors.append("no business POIs found")
        return errors

    attached = set()
    for row in roads:
        sid = int(row["startId"])
        eid = int(row["endId"])
        if sid in business_pois and eid in virtual_ids:
            attached.add(sid)
        if eid in business_pois and sid in virtual_ids:
            attached.add(eid)

    missing = sorted(business_pois - attached)
    if missing:
        errors.append(f"business POIs not attached to road network: count={len(missing)} sample={missing[:8]}")
    return errors


def file_size_summary(base_dir: Path) -> List[Tuple[str, int]]:
    files = [
        "scenic_areas.append.json",
        "pois.append.json",
        "facilities.append.json",
        "roads.append.json",
    ]
    out = []
    for name in files:
        path = base_dir / name
        out.append((name, path.stat().st_size if path.exists() else -1))
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate OSM generated output")
    parser.add_argument("--dir", required=True, help="Output directory, e.g. src/main/resources/osm-data/<slug>/latest")
    args = parser.parse_args()

    base_dir = Path(args.dir)
    pois = load_json(base_dir / "pois.append.json")
    roads = load_json(base_dir / "roads.append.json")

    errors: List[str] = []
    errors.extend(check_virtual_node_fields(pois))
    errors.extend(check_roads(roads))
    errors.extend(check_virtual_connectivity(pois, roads))
    errors.extend(check_poi_coverage(pois, roads))

    print("Validation target:", base_dir.as_posix())
    print("File sizes (bytes):")
    for name, size in file_size_summary(base_dir):
        print(f"  - {name}: {size}")

    if errors:
        print("\nValidation FAILED")
        for e in errors:
            print("  -", e)
        return 2

    print("\nValidation PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
