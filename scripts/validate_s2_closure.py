#!/usr/bin/env python3
"""S2 R12: validate scale metrics from dev-seed + osm-data."""
from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SEED = REPO / "src/main/resources/dev-seed"
OSM = REPO / "src/main/resources/osm-data"


def classpath_to_path(cp: str) -> Path:
    return REPO / "src/main/resources" / cp.removeprefix("classpath:")


def main() -> int:
    cfg = json.loads((SEED / "map-imports.json").read_text(encoding="utf-8"))
    canonical = 0
    pois_per_area: list[int] = []
    for cp in cfg.get("scenicAreas", []):
        rows = json.loads(classpath_to_path(cp).read_text(encoding="utf-8"))
        if rows:
            canonical += 1
    for cp in cfg.get("pois", []):
        pois = json.loads(classpath_to_path(cp).read_text(encoding="utf-8"))
        pois_per_area.append(len(pois))

    aliases = json.loads((SEED / "scenic-area-aliases.json").read_text(encoding="utf-8"))
    scenic_total = canonical + len(aliases)

    fac_types: set[str] = set()
    fac_count = 0
    for p in OSM.glob("*/latest/facilities.append.json"):
        for f in json.loads(p.read_text(encoding="utf-8")):
            fac_types.add(str(f.get("type", "")))
            fac_count += 1
    for f in json.loads((SEED / "facilities.json").read_text(encoding="utf-8")):
        fac_types.add(str(f.get("type", "")))
        fac_count += 1

    roads = 0
    for p in OSM.glob("*/latest/roads.append.json"):
        roads += len(json.loads(p.read_text(encoding="utf-8")))

    users = len(json.loads((SEED / "users.json").read_text(encoding="utf-8")))
    foods = len(json.loads((SEED / "foods.json").read_text(encoding="utf-8")))
    diaries = len(json.loads((SEED / "diaries.json").read_text(encoding="utf-8")))

    checks = [
        ("scenic_areas>=200", scenic_total >= 200, scenic_total),
        ("facility_types>=10", len(fac_types) >= 10, len(fac_types)),
        ("facilities>=50", fac_count >= 50, fac_count),
        ("roads>=200", roads >= 200, roads),
        ("users>=10", users >= 10, users),
        ("poi_per_area>=20", all(n >= 20 for n in pois_per_area), min(pois_per_area) if pois_per_area else 0),
        ("foods>0", foods > 0, foods),
        ("diaries>0", diaries > 0, diaries),
    ]

    failed = 0
    for name, ok, val in checks:
        status = "OK" if ok else "FAIL"
        print(f"[{status}] {name}: {val}")
        if not ok:
            failed += 1
    print(f"facility types: {sorted(fac_types)}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
