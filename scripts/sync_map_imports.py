#!/usr/bin/env python3
"""Rewrite dev-seed/map-imports.json from osm-data/*/latest/pois.append.json."""
from __future__ import annotations

import json
from pathlib import Path


def main() -> int:
    repo = Path(__file__).resolve().parents[1]
    osm_root = repo / "src/main/resources/osm-data"
    slugs: list[str] = []
    for scenic in sorted(osm_root.iterdir()):
        if not scenic.is_dir():
            continue
        if (scenic / "latest" / "pois.append.json").exists():
            slugs.append(scenic.name)

    def cp(target: str) -> str:
        return f"classpath:osm-data/{target}"

    cfg = {
        "scenicAreas": [cp(f"{s}/latest/scenic_areas.append.json") for s in slugs],
        "pois": [cp(f"{s}/latest/pois.append.json") for s in slugs],
        "buildings": [],
        "roads": [cp(f"{s}/latest/roads.append.json") for s in slugs],
        "facilities": [cp(f"{s}/latest/facilities.append.json") for s in slugs],
    }
    out = repo / "src/main/resources/dev-seed/map-imports.json"
    out.write_text(json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"synced {len(slugs)} map pack(s) -> {out}", flush=True)
    for s in slugs:
        print(f"  - {s}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
