#!/usr/bin/env python3
"""One-off audit: raw overpass vs facilities / indoor outputs."""
import json
import math
import sys
from collections import Counter
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
OSM_ROOT = REPO / "src/main/resources/osm-data"


def haversine_m(lat1, lon1, lat2, lon2):
    r = 6371000
    p = math.pi / 180
    a = math.sin((lat2 - lat1) * p / 2) ** 2 + math.cos(lat1 * p) * math.cos(lat2 * p) * math.sin(
        (lon2 - lon1) * p / 2
    ) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def load_json(p: Path):
    with p.open(encoding="utf-8") as f:
        return json.load(f)


def facility_relevant_raw(elements):
    hits = []
    for el in elements:
        tags = el.get("tags") or {}
        if not tags:
            continue
        amenity = str(tags.get("amenity", "")).lower()
        shop = str(tags.get("shop", "")).lower()
        indoor = str(tags.get("indoor", "")).lower()
        if indoor in {"room", "door", "corridor", "area", "wall"}:
            continue
        reasons = []
        if amenity in {
            "toilets", "toilet", "restaurant", "fast_food", "canteen", "food_court",
            "cafe", "pub", "bar", "library", "pharmacy", "bank", "atm", "vending_machine",
        }:
            reasons.append(f"amenity={amenity}")
        if shop:
            reasons.append(f"shop={shop}")
        if reasons:
            name = tags.get("name:zh") or tags.get("name") or ""
            hits.append({"name": name, "reasons": reasons, "tags": tags})
    return hits


def audit_scenic(latest: Path, label: str):
    raw_p = latest / "raw/overpass.json"
    if not raw_p.exists():
        return
    raw = load_json(raw_p)
    elements = raw if isinstance(raw, list) else raw.get("elements", [])
    facs = load_json(latest / "facilities.append.json") if (latest / "facilities.append.json").exists() else []
    pois = load_json(latest / "pois.append.json") if (latest / "pois.append.json").exists() else []

    raw_fac = facility_relevant_raw(elements)
    poi_like = [
        p for p in pois
        if p.get("type") in {"restaurant", "library", "shop", "toilet", "service", "medical", "canteen", "parking"}
        and p.get("type") != "virtual_node"
    ]

    print("=" * 72)
    print(label)
    print(f"  raw elements: {len(elements)}")
    print(f"  raw facility-relevant tags: {len(raw_fac)}")
    print(f"  facilities.append: {len(facs)} -> {Counter(f.get('type') for f in facs)}")
    print(f"  facility-like in POIs (not facilities table): {len(poi_like)}")
    for p in poi_like[:12]:
        print(f"    POI {p.get('type')}: {p.get('name')}")
    if len(raw_fac) > len(facs):
        print(f"  >>> GAP: {len(raw_fac)} raw facility tags vs {len(facs)} facilities rows")

    # indoor
    indoor_tags = Counter(str((el.get("tags") or {}).get("indoor", "")).lower() for el in elements if (el.get("tags") or {}).get("indoor"))
    room_ways = sum(
        1 for el in elements
        if el.get("type") == "way" and str((el.get("tags") or {}).get("indoor", "")).lower() == "room"
    )
    ok_indoor = [p for p in (latest / "indoor").glob("*.json") if p.name != "manifest.json"]
    rej_indoor = list((latest / "indoor/rejected").glob("*.json")) if (latest / "indoor/rejected").exists() else []
    collect = load_json(latest / "indoor_collect.json") if (latest / "indoor_collect.json").exists() else {}

    print(f"  raw indoor=room ways: {room_ways} | indoor tag dist: {dict(indoor_tags.most_common(8))}")
    print(f"  indoor ok: {len(ok_indoor)} rejected: {len(rej_indoor)}")
    reg_p = latest / "raw/building_registry.json"
    if reg_p.exists():
        reg = load_json(reg_p)
        print(f"  building_registry: {len(reg) if isinstance(reg, list) else 0} footprints")
    if collect:
        fc = Counter()
        for r in collect.get("results") or []:
            for f in r.get("failures") or []:
                fc[f] += 1
        print(f"  indoor collect: ok={collect.get('ok')} reject={collect.get('reject')} error={collect.get('error')}")
        print(f"  failure codes: {dict(fc)}")
        for r in collect.get("results") or []:
            if r.get("status") == "ok":
                print(f"    OK: {r.get('name')} strategy={r.get('strategy')}")
            elif r.get("status") == "reject" and room_ways > 0:
                print(f"    REJECT: {r.get('name')} failures={r.get('failures')} strategy={r.get('strategy')}")
    for bundle_path in ok_indoor:
        try:
            bundle = load_json(bundle_path)
            nodes = bundle.get("nodes") or []
            bid = int(bundle.get("buildingPoiId", 0))
            bad_parent = [
                n for n in nodes
                if n.get("parentId") is not None and int(n.get("parentId")) != bid
            ]
            missing_parent = sum(1 for n in nodes if n.get("parentId") is None)
            print(
                f"  parentId check {bundle_path.name}: nodes={len(nodes)} "
                f"missing_parent={missing_parent} bad_parent={len(bad_parent)}"
            )
        except Exception as ex:
            print(f"  parentId check {bundle_path.name}: ERROR {ex}")


SHAHE_CANONICAL = "北京邮电大学-沙河校区-鸿雁路-沙河镇-昌平区-北京市-102206-中国"


def audit_shahe_library_radius():
    latest = OSM_ROOT / SHAHE_CANONICAL / "latest"
    raw = load_json(latest / "raw/overpass.json")
    elements = raw if isinstance(raw, list) else raw.get("elements", [])
    pois = load_json(latest / "pois.append.json")

    rooms = []
    for el in elements:
        tags = el.get("tags") or {}
        if str(tags.get("indoor", "")).lower() != "room":
            continue
        if el.get("type") == "way" and el.get("geometry"):
            lats = [g["lat"] for g in el["geometry"]]
            lons = [g["lon"] for g in el["geometry"]]
            clat, clon = sum(lats) / len(lats), sum(lons) / len(lons)
        elif el.get("type") == "node":
            clat, clon = el.get("lat"), el.get("lon")
        else:
            continue
        rooms.append({
            "name": tags.get("name:zh") or tags.get("name"),
            "lat": clat,
            "lon": clon,
            "level": tags.get("level"),
        })

    libs = [p for p in pois if p.get("id") in (900020591, 900020582) or p.get("type") == "library"]
    print("=" * 72)
    print("SHAHE library radius vs indoor=room in campus raw")
    print(f"  campus raw indoor rooms: {len(rooms)}")
    for p in libs:
        lat, lon = p.get("latitude"), p.get("longitude")
        if lat is None:
            continue
        print(f"  POI {p['id']} {p['name']} @ ({lat}, {lon})")
        for r in (80, 120, 200, 500):
            n = sum(1 for x in rooms if haversine_m(lat, lon, x["lat"], x["lon"]) <= r)
            print(f"    rooms within {r}m: {n}")


def main():
    for scenic_dir in sorted(OSM_ROOT.iterdir()):
        latest = scenic_dir / "latest"
        if latest.is_dir():
            audit_scenic(latest, scenic_dir.name[:50])
    audit_shahe_library_radius()


if __name__ == "__main__":
    main()
