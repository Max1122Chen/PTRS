#!/usr/bin/env python3
"""One-off: analyze indoor room mis-attribution for Shahe campus."""
import json
import math
from collections import Counter
from pathlib import Path

from osm_building_geo import (
    assign_indoor_to_nearest_building,
    build_building_registry,
    element_center,
    filter_indoor_pool,
    min_distance_to_building_m,
    poi_osm_ref,
    subset_indoor_for_building,
)

ROOT = Path(__file__).resolve().parents[1] / (
    "src/main/resources/osm-data/北京邮电大学-沙河校区-鸿雁路-沙河镇-昌平区-北京市-102206-中国/latest"
)

TARGETS = {
    685054783: "图书馆",
    919010294: "学术报告厅",
    1458664382: "公共教学楼",
}


def dist_m(c1, c2):
    lon1, lat1 = c1[0], c1[1]
    lon2, lat2 = c2[0], c2[1]
    dx = (lon2 - lon1) * 111320 * math.cos(math.radians((lat1 + lat2) / 2))
    dy = (lat2 - lat1) * 110540
    return math.hypot(dx, dy)


def main() -> None:
    elements = json.loads((ROOT / "raw/overpass.json").read_text(encoding="utf-8"))["elements"]
    pois = json.loads((ROOT / "pois.append.json").read_text(encoding="utf-8"))
    registry = build_building_registry(elements)
    pool = filter_indoor_pool(elements)
    by_id = {int(r["osmId"]): r for r in registry}

    print("=== Building registry (targets) ===")
    for oid, label in TARGETS.items():
        r = by_id.get(oid)
        if not r:
            print(f"  {label} way/{oid}: NOT in registry")
            continue
        print(f"  {label} way/{oid}: centroid={r.get('centroid')} building={r.get('buildingTag')}")

    print("\n=== Centroid distances (m) ===")
    oids = list(TARGETS.keys())
    for i, a in enumerate(oids):
        for b in oids[i + 1 :]:
            if a in by_id and b in by_id:
                d = dist_m(by_id[a]["centroid"], by_id[b]["centroid"])
                print(f"  {TARGETS[a]} <-> {TARGETS[b]}: {d:.0f}m")

    print("\n=== indoor=room ways: nearest building (150m) ===")
    assign = assign_indoor_to_nearest_building(pool, registry, buffer_m=150.0)
    room_ways = [e for e in pool if (e.get("tags") or {}).get("indoor") == "room" and e.get("type") == "way"]
    print(f"  total room ways in raw: {len(room_ways)}")
    for oid, label in TARGETS.items():
        subset = assign.get(("way", oid), [])
        rooms = [e for e in subset if (e.get("tags") or {}).get("indoor") == "room"]
        print(f"  nearest -> {label}: {len(rooms)} rooms")

    print("\n=== Current script: priority P0 claim (library first, cap 42) ===")
    pri_order = [685054783, 1458664382, 919010294]
    remaining = list(pool)
    for oid in pri_order:
        reg = by_id[oid]
        sub = subset_indoor_for_building(remaining, reg, buffer_m=150.0)
        if len(sub) > 42:
            sub.sort(key=lambda el: min_distance_to_building_m(el, reg))
            sub = sub[:42]
        rooms = [e for e in sub if (e.get("tags") or {}).get("indoor") == "room"]
        claimed = {(e["type"], e["id"]) for e in sub}
        remaining = [e for e in remaining if (e["type"], e["id"]) not in claimed]
        print(f"  claimed by {TARGETS[oid]}: {len(rooms)} rooms, left in pool {len(remaining)}")

    print("\n=== Room name patterns per assigned building (nearest) ===")
    for oid, label in TARGETS.items():
        subset = assign.get(("way", oid), [])
        names = []
        for e in subset:
            tags = e.get("tags") or {}
            if tags.get("indoor") != "room":
                continue
            names.append(str(tags.get("name") or tags.get("ref") or "?")[:20])
        c = Counter(names)
        print(f"  {label} sample names: {list(c.keys())[:8]}")

    print("\n=== S4-related in raw ===")
    s4_building = [e for e in elements if (e.get("tags") or {}).get("name") == "S4区" and e.get("type") == "way"]
    for e in s4_building:
        tags = e["tags"]
        print(f"  S4区 way/{e['id']}: building={tags.get('building')} amenity={tags.get('amenity')}")
    s4_rooms = [
        e
        for e in room_ways
        if str((e.get("tags") or {}).get("name", "")).startswith("S4")
    ]
    print(f"  room ways with S4-* name: {len(s4_rooms)}")
    if s4_rooms:
        dists = {TARGETS[oid]: [] for oid in TARGETS}
        for e in s4_rooms[:5]:
            c = element_center(e)
            nm = (e.get("tags") or {}).get("name")
            row = [nm]
            for oid in TARGETS:
                d = min_distance_to_building_m(e, by_id[oid])
                row.append(f"{TARGETS[oid]}={d:.0f}m")
            print("   ", " | ".join(row))

    print("\n=== POI mapping ===")
    for p in pois:
        ref = poi_osm_ref(p)
        if not ref:
            continue
        if ref[1] in TARGETS or "S4" in str(p.get("name", "")):
            print(f"  POI {p['id']} {p['name']} type={p.get('type')} osm={ref[1]}")


if __name__ == "__main__":
    main()
