#!/usr/bin/env python3
"""Generate review-only seed data from OpenStreetMap (Nominatim + Overpass).

This script does NOT modify existing seed files. It writes review files for manual approval.
"""

from __future__ import annotations

import argparse
import datetime as dt
import heapq
import json
import math
import os
import shutil
import time
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Set, Tuple
from urllib.parse import quote_plus, urlencode
from urllib.request import Request, urlopen


NOMINATIM_BASE = "https://nominatim.openstreetmap.org/search"
OVERPASS_BASE = "https://overpass-api.de/api/interpreter"
SEED_DIR = Path("src/main/resources/dev-seed")
DEFAULT_OUTPUT_DIR = Path("src/main/resources/osm-data")
DEFAULT_MAP_IMPORTS = Path("src/main/resources/dev-seed/map-imports.json")
DEFAULT_ID_REGISTRY = Path("src/main/resources/dev-seed/id-registry.json")
DEFAULT_POI_TYPES_CONFIG = Path("src/main/resources/config/poi-types.json")


def load_known_poi_type_codes(repo_root: Path) -> Set[str]:
    default_codes = {
        "scenic_spot",
        "gate",
        "library",
        "teaching",
        "restaurant",
        "service",
        "toilet",
        "dormitory",
        "lab",
        "virtual_node",
        "shop",
        "medical",
        "parking",
        "sports",
    }

    path = repo_root / DEFAULT_POI_TYPES_CONFIG
    if not path.exists():
        return default_codes

    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return default_codes

    types = data.get("types", []) if isinstance(data, dict) else []
    if not isinstance(types, list):
        return default_codes

    codes: Set[str] = set()
    for row in types:
        if not isinstance(row, dict):
            continue
        code = str(row.get("code", "")).strip().lower()
        if code:
            codes.add(code)

    return codes or default_codes


def now_iso() -> str:
    return dt.datetime.now().replace(microsecond=0).isoformat()


def slugify(name: str) -> str:
    out = []
    for ch in (name or "").strip().lower():
        if ch.isalnum():
            out.append(ch)
        elif ch in {" ", "-", "_", "（", "）", "(", ")", "/", "\\"}:
            out.append("-")
    s = "".join(out).strip("-")
    while "--" in s:
        s = s.replace("--", "-")
    return s or "scenic"


def resolve_matched_dir_key(nominatim_top: Dict[str, object], fallback: str) -> str:
    """Use OSM matched address/name as directory key source.

    Prefer display_name for stable place identity, then name.
    """
    raw = str(nominatim_top.get("display_name") or nominatim_top.get("name") or fallback)
    # Keep path length predictable.
    return raw[:120]


def resolve_matched_scenic_name(nominatim_top: Dict[str, object], fallback: str) -> str:
    """Use OSM matched place name for scenic area name."""
    raw = str(nominatim_top.get("name") or nominatim_top.get("display_name") or fallback).strip()
    return raw[:120] if raw else fallback


def read_max_id(name: str) -> int:
    path = SEED_DIR / f"{name}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    return max(int(x.get("id", 0)) for x in data)


def resolve_resource_path(path_str: str, repo_root: Path) -> Path:
    s = (path_str or "").strip()
    if s.startswith("classpath:"):
        rel = s[len("classpath:") :].lstrip("/")
        return repo_root / "src" / "main" / "resources" / rel
    if s.startswith("file:"):
        return Path(s[len("file:") :])
    p = Path(s)
    if p.is_absolute():
        return p
    return repo_root / p


def read_json_array(path: Path) -> List[Dict[str, object]]:
    if not path.exists():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return []
    if isinstance(data, list):
        return [x for x in data if isinstance(x, dict)]
    return []


def discover_existing_max_ids(repo_root: Path, map_imports_path: Path) -> Dict[str, int]:
    max_ids = {
        "scenic_areas": 0,
        "buildings": 0,
        "facilities": 0,
        "roads": 0,
    }

    def touch(entity: str, file_path: Path) -> None:
        for row in read_json_array(file_path):
            try:
                rid = int(row.get("id", 0) or 0)
            except Exception:
                rid = 0
            if rid > max_ids[entity]:
                max_ids[entity] = rid

    # 1) base seeds
    touch("scenic_areas", repo_root / "src" / "main" / "resources" / "dev-seed" / "scenic_areas.json")
    touch("buildings", repo_root / "src" / "main" / "resources" / "dev-seed" / "buildings.json")
    touch("facilities", repo_root / "src" / "main" / "resources" / "dev-seed" / "facilities.json")
    touch("roads", repo_root / "src" / "main" / "resources" / "dev-seed" / "roads.json")

    # 2) map-imports configured files
    if map_imports_path.exists():
        try:
            cfg = json.loads(map_imports_path.read_text(encoding="utf-8"))
            if isinstance(cfg, dict):
                for p in cfg.get("scenicAreas", []) or []:
                    if isinstance(p, str):
                        touch("scenic_areas", resolve_resource_path(p, repo_root))
                for p in cfg.get("pois", []) or []:
                    if isinstance(p, str):
                        touch("buildings", resolve_resource_path(p, repo_root))
                for p in cfg.get("buildings", []) or []:
                    if isinstance(p, str):
                        touch("buildings", resolve_resource_path(p, repo_root))
                for p in cfg.get("facilities", []) or []:
                    if isinstance(p, str):
                        touch("facilities", resolve_resource_path(p, repo_root))
                for p in cfg.get("roads", []) or []:
                    if isinstance(p, str):
                        touch("roads", resolve_resource_path(p, repo_root))
        except Exception:
            pass

    # 3) fallback scan all generated append files under osm-data
    osm_data_root = repo_root / "src" / "main" / "resources" / "osm-data"
    if osm_data_root.exists():
        for fp in osm_data_root.rglob("*.append.json"):
            name = fp.name
            if name == "scenic_areas.append.json":
                touch("scenic_areas", fp)
            elif name == "pois.append.json":
                touch("buildings", fp)
            elif name == "facilities.append.json":
                touch("facilities", fp)
            elif name == "roads.append.json":
                touch("roads", fp)

    return max_ids


def acquire_lock(lock_path: Path, timeout_sec: float = 20.0) -> None:
    start = time.monotonic()
    while True:
        try:
            fd = os.open(str(lock_path), os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.close(fd)
            return
        except FileExistsError:
            if time.monotonic() - start > timeout_sec:
                raise RuntimeError(f"acquire lock timeout: {lock_path}")
            time.sleep(0.1)


def release_lock(lock_path: Path) -> None:
    try:
        lock_path.unlink(missing_ok=True)
    except Exception:
        pass


def allocate_id_ranges(
    repo_root: Path,
    map_imports_path: Path,
    id_registry_path: Path,
    counts: Dict[str, int],
) -> Dict[str, int]:
    existing_max = discover_existing_max_ids(repo_root, map_imports_path)
    lock_path = id_registry_path.with_suffix(id_registry_path.suffix + ".lock")
    acquire_lock(lock_path)
    try:
        registry: Dict[str, int] = {
            "scenic_areas": 0,
            "buildings": 0,
            "facilities": 0,
            "roads": 0,
        }
        if id_registry_path.exists():
            try:
                raw = json.loads(id_registry_path.read_text(encoding="utf-8"))
                if isinstance(raw, dict):
                    for k in registry:
                        try:
                            registry[k] = int(raw.get(k, 0) or 0)
                        except Exception:
                            registry[k] = 0
            except Exception:
                pass

        starts: Dict[str, int] = {}
        for key in ("scenic_areas", "buildings", "facilities", "roads"):
            base = max(existing_max.get(key, 0), registry.get(key, 0))
            cnt = max(0, int(counts.get(key, 0) or 0))
            starts[key] = base + 1
            registry[key] = base + cnt

        id_registry_path.parent.mkdir(parents=True, exist_ok=True)
        id_registry_path.write_text(json.dumps(registry, ensure_ascii=False, indent=2), encoding="utf-8")
        return starts
    finally:
        release_lock(lock_path)


def distance_m(lng1: float, lat1: float, lng2: float, lat2: float) -> float:
    r = 6371000.0
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def request_json(url: str, user_agent: str, timeout: int = 30) -> Dict[str, object] | List[object]:
    req = Request(url, headers={"User-Agent": user_agent, "Accept": "application/json"})
    with urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8", errors="replace"))


def nominatim_search(query: str, user_agent: str) -> Dict[str, object]:
    params = {
        "format": "jsonv2",
        "q": query,
        "limit": 5,
        "addressdetails": 1,
    }
    url = f"{NOMINATIM_BASE}?{urlencode(params)}"
    data = request_json(url, user_agent)
    if not isinstance(data, list) or not data:
        raise RuntimeError("Nominatim search returned empty result.")
    top = data[0]
    if not isinstance(top, dict):
        raise RuntimeError("Nominatim search invalid result structure.")
    return top


def overpass_query(query: str, user_agent: str) -> Dict[str, object]:
    url = f"{OVERPASS_BASE}?data={quote_plus(query)}"
    data = request_json(url, user_agent)
    if not isinstance(data, dict):
        raise RuntimeError("Overpass returned invalid result.")
    return data


def build_overpass_query(
    osm_type: str,
    osm_id: int,
    center_lat: float,
    center_lng: float,
    radius: int,
) -> str:
    osm_type = (osm_type or "").strip().lower()
    if osm_type == "way":
        area_head = f"way({osm_id});\nmap_to_area->.searchArea;"
        selector = "area.searchArea"
    elif osm_type == "relation":
        area_head = f"relation({osm_id});\nmap_to_area->.searchArea;"
        selector = "area.searchArea"
    else:
        area_head = ""
        selector = f"around:{radius},{center_lat},{center_lng}"

    if selector == "area.searchArea":
        body = f"""
    [out:json][timeout:60];
    {area_head}
    (
      node({selector})[name];
      way({selector})[name];
      node({selector})[amenity];
      way({selector})[amenity];
      way({selector})[highway~\"footway|path|pedestrian|service|residential\"];
    );
    out geom tags;
    """
    else:
        body = f"""
    [out:json][timeout:60];
    (
      node({selector})[name];
      way({selector})[name];
      node({selector})[amenity];
      way({selector})[amenity];
      way({selector})[highway~\"footway|path|pedestrian|service|residential\"];
    );
        out geom tags;
    """
    return body


def element_center(el: Dict[str, object]) -> Optional[Tuple[float, float]]:
    if "lat" in el and "lon" in el:
        return float(el["lon"]), float(el["lat"])
    center = el.get("center")
    if isinstance(center, dict) and "lat" in center and "lon" in center:
        return float(center["lon"]), float(center["lat"])
    geometry = el.get("geometry")
    if isinstance(geometry, list) and geometry:
        xs = []
        ys = []
        for p in geometry:
            if not isinstance(p, dict):
                continue
            if "lon" not in p or "lat" not in p:
                continue
            xs.append(float(p["lon"]))
            ys.append(float(p["lat"]))
        if xs:
            return (sum(xs) / len(xs), sum(ys) / len(ys))
    return None


def dijkstra_distance(adjacency: Dict[int, List[Tuple[int, float]]], source: int, target: int) -> Optional[float]:
    if source == target:
        return 0.0
    heap: List[Tuple[float, int]] = [(0.0, source)]
    best: Dict[int, float] = {source: 0.0}
    while heap:
        dist, node = heapq.heappop(heap)
        if node == target:
            return dist
        if dist > best.get(node, float("inf")):
            continue
        for nxt, w in adjacency.get(node, []):
            nd = dist + w
            if nd + 1e-9 < best.get(nxt, float("inf")):
                best[nxt] = nd
                heapq.heappush(heap, (nd, nxt))
    return None


def pick_name(tags: Dict[str, object], fallback: str) -> str:
    for key in ("name:zh", "name", "official_name", "alt_name"):
        val = tags.get(key)
        if isinstance(val, str) and val.strip():
            return val.strip()
    return fallback


def classify_poi(tags: Dict[str, object]) -> Optional[str]:
    amenity = str(tags.get("amenity", "")).lower()
    building = str(tags.get("building", "")).lower()
    leisure = str(tags.get("leisure", "")).lower()
    tourism = str(tags.get("tourism", "")).lower()
    highway = str(tags.get("highway", "")).lower()
    landuse = str(tags.get("landuse", "")).lower()
    name = str(tags.get("name:zh", "") or tags.get("name", "")).lower()

    if amenity in {"school", "college", "university", "classroom"}:
        return "teaching"
    if amenity == "library":
        return "library"
    if amenity in {"canteen", "food_court", "restaurant", "fast_food", "cafe", "pub", "bar"}:
        return "restaurant"
    if leisure in {"sports_centre", "stadium", "pitch"}:
        return "service"
    if tourism in {"attraction", "viewpoint"}:
        return "scenic_spot"
    if amenity in {"entrance", "gate"}:
        return "gate"
    if building in {"apartments", "dormitory", "residential"}:
        return "dormitory"
    if building in {"stadium", "sports_hall", "gym"}:
        return "service"
    if leisure in {"park", "garden"}:
        return "scenic_spot"
    if landuse in {"cemetery", "recreation_ground"}:
        return "scenic_spot"
    if highway == "pedestrian" and ("广场" in name or "plaza" in name or "square" in name):
        return "scenic_spot"
    if building in {"school", "university", "college", "yes"}:
        return "teaching"
    if highway == "gate":
        return "gate"
    
    # 名称-based fallback
    name_lower = name.lower()
    if "店" in name_lower or "shop" in name_lower or "store" in name_lower:
        return "shop"
    if "餐厅" in name_lower or "饭" in name_lower or "食堂" in name_lower or "餐馆" in name_lower or "饭店" in name_lower:
        return "restaurant"
    if "医院" in name_lower or "诊所" in name_lower or "医疗" in name_lower:
        return "medical"
    if "停车场" in name_lower or "停车" in name_lower:
        return "parking"
    if "体育" in name_lower or "运动" in name_lower or " gym" in name_lower:
        return "sports"
    return None


def is_poi_candidate(tags: Dict[str, object]) -> bool:
    """Whether an OSM element should be persisted as a POI candidate."""
    has_name = bool(str(tags.get("name:zh", "") or tags.get("name", "")).strip())
    amenity = str(tags.get("amenity", "")).strip().lower()
    building = str(tags.get("building", "")).strip().lower()
    leisure = str(tags.get("leisure", "")).strip().lower()
    tourism = str(tags.get("tourism", "")).strip().lower()
    highway = str(tags.get("highway", "")).strip().lower()

    # Skip pure road geometry that does not represent a POI.
    if highway and not (amenity or building or leisure or tourism or has_name):
        return False

    return bool(has_name or amenity or building or leisure or tourism)


def build_unmatched_poi_item(el: Dict[str, object], tags: Dict[str, object], name: str, loc_text: str) -> Dict[str, object]:
    return {
        "osmType": str(el.get("type", "")),
        "osmId": el.get("id"),
        "name": name,
        "location": loc_text,
        "amenity": tags.get("amenity"),
        "building": tags.get("building"),
        "leisure": tags.get("leisure"),
        "tourism": tags.get("tourism"),
        "highway": tags.get("highway"),
        "landuse": tags.get("landuse"),
    }


def classify_facility(tags: Dict[str, object]) -> Optional[str]:
    amenity = str(tags.get("amenity", "")).lower()
    if amenity in {"toilets", "toilet"}:
        return "toilet"
    if amenity in {"clinic", "hospital", "doctors"}:
        return "hospital"
    if amenity in {"bicycle_parking", "bicycle_rental"}:
        return "bike"
    if amenity in {"information", "reception"}:
        return "service"
    if amenity in {"printer", "copyshop"}:
        return "printer"
    return None


def dedup_rows(rows: Iterable[Dict[str, object]]) -> List[Dict[str, object]]:
    seen = set()
    out = []
    for row in rows:
        key = (row.get("name"), round(float(row.get("longitude", 0.0)), 6), round(float(row.get("latitude", 0.0)), 6), row.get("type"))
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
    return out


def append_rows_to_seed(seed_name: str, rows: List[Dict[str, object]]) -> int:
    if not rows:
        return 0
    path = SEED_DIR / f"{seed_name}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise RuntimeError(f"Seed file is not a JSON array: {path}")
    data.extend(rows)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return len(rows)


def update_map_imports(map_imports_path: Path, scenic_slug: str, classpath_base: str) -> None:
    if map_imports_path.exists():
        cfg = json.loads(map_imports_path.read_text(encoding="utf-8"))
    else:
        cfg = {}
    if not isinstance(cfg, dict):
        cfg = {}

    for key in ("scenicAreas", "pois", "buildings", "roads", "facilities"):
        if key not in cfg or not isinstance(cfg[key], list):
            cfg[key] = []

    scenic_prefix = f"classpath:osm-data/{scenic_slug}/"

    def replace_for_key(key: str, target: str) -> None:
        items = [str(x) for x in cfg.get(key, []) if isinstance(x, str)]
        kept = [x for x in items if scenic_prefix not in x]
        kept.append(f"classpath:{classpath_base}/{target}")
        cfg[key] = kept

    replace_for_key("scenicAreas", "scenic_areas.append.json")
    replace_for_key("pois", "pois.append.json")
    replace_for_key("roads", "roads.append.json")
    replace_for_key("facilities", "facilities.append.json")

    map_imports_path.parent.mkdir(parents=True, exist_ok=True)
    map_imports_path.write_text(json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate review-only OSM data")
    parser.add_argument("--target-name", default="广州市执信中学（执信南路校区）")
    parser.add_argument("--query", default="广州市执信中学 执信路校区")
    parser.add_argument("--radius", type=int, default=700)
    parser.add_argument("--user-agent", default="BUPT-TravelSeedBot/1.0 (student-project)")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--sleep", type=float, default=1.0)
    parser.add_argument("--config", default="scripts/config/osm_seed_config.json")
    parser.add_argument("--skip-config", action="store_true", help="Ignore config file and use CLI arguments directly")
    parser.add_argument("--apply-seed", action="store_true")
    parser.add_argument("--run-name", default="latest")
    parser.add_argument("--map-imports", default=str(DEFAULT_MAP_IMPORTS))
    parser.add_argument("--id-registry", default=str(DEFAULT_ID_REGISTRY))
    parser.add_argument("--update-map-imports", action="store_true", default=True)
    parser.add_argument("--virtual-node-base", type=int, default=900000000)
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    os.chdir(repo_root)
    known_poi_type_codes = load_known_poi_type_codes(repo_root)

    cfg_path = Path(args.config)
    if not cfg_path.is_absolute():
        cfg_path = repo_root / cfg_path
    if cfg_path.exists() and not args.skip_config:
        cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
        args.target_name = str(cfg.get("target_name", args.target_name))
        args.query = str(cfg.get("query", args.query))
        args.radius = int(cfg.get("radius", args.radius))
        args.user_agent = str(cfg.get("user_agent", args.user_agent))
        args.output_dir = str(cfg.get("output_dir", args.output_dir))
        args.sleep = float(cfg.get("sleep", args.sleep))
        args.run_name = str(cfg.get("run_name", args.run_name))
        args.map_imports = str(cfg.get("map_imports", args.map_imports))
        args.id_registry = str(cfg.get("id_registry", args.id_registry))
        if "update_map_imports" in cfg:
            args.update_map_imports = bool(cfg.get("update_map_imports"))
        if "virtual_node_base" in cfg:
            args.virtual_node_base = int(cfg.get("virtual_node_base"))

    ts = now_iso()

    top: Dict[str, object] = nominatim_search(args.query, args.user_agent)
    context_from_cache = False

    out_root = Path(args.output_dir)
    matched_dir_key = resolve_matched_dir_key(top, args.target_name)
    scenic_slug = slugify(matched_dir_key)
    scenic_root = out_root / scenic_slug
    scenic_root.mkdir(parents=True, exist_ok=True)
    context_file = scenic_root / "_context.json"
    context_file.write_text(json.dumps(top, ensure_ascii=False, indent=2), encoding="utf-8")

    center_lng = float(top["lon"])
    center_lat = float(top["lat"])

    osm_type = str(top.get("osm_type", ""))
    osm_id = int(top.get("osm_id", 0) or 0)
    overpass_q = build_overpass_query(osm_type, osm_id, center_lat, center_lng, args.radius)

    time.sleep(max(0.0, args.sleep))
    overpass_raw = overpass_query(overpass_q, args.user_agent)
    elements = overpass_raw.get("elements", []) if isinstance(overpass_raw, dict) else []
    if not isinstance(elements, list):
        elements = []

    # Temporary IDs are used during graph construction and are remapped to global IDs later.
    scenic_id = 1
    poi_id = 1
    fac_id = 1
    road_id = 1

    matched_scenic_name = resolve_matched_scenic_name(top, args.target_name)

    scenic_area = {
        "id": scenic_id,
        "name": matched_scenic_name,
        "description": "Generated from OpenStreetMap (review required).",
        "location": str(top.get("display_name", ""))[:255],
        "longitude": round(center_lng, 6),
        "latitude": round(center_lat, 6),
        "type": "campus",
        "rating": 0.0,
        "heat": 0,
        "openTime": "待核实",
        "ticketPrice": "待核实",
        "createTime": ts,
        "updateTime": ts,
    }

    poi_rows: List[Dict[str, object]] = []
    fac_rows: List[Dict[str, object]] = []
    road_rows: List[Dict[str, object]] = []
    unmatched_poi_items: List[Dict[str, object]] = []

    for el in elements:
        if not isinstance(el, dict):
            continue
        tags = el.get("tags", {})
        if not isinstance(tags, dict):
            continue
        center = element_center(el)
        if not center:
            continue
        lng, lat = center

        # Limit to radius buffer.
        if distance_m(center_lng, center_lat, lng, lat) > args.radius * 1.2:
            continue

        name = pick_name(tags, f"osm-{el.get('type', 'obj')}-{el.get('id', 'x')}")
        loc_text = str(tags.get("addr:full", "") or tags.get("addr:street", "") or name)[:255]

        poi_type = classify_poi(tags)
        if poi_type and poi_type in known_poi_type_codes:
            poi_rows.append(
                {
                    "id": poi_id,
                    "name": name,
                    "type": poi_type,
                    "description": f"OSM source={el.get('type')}:{el.get('id')}, review required",
                    "location": loc_text,
                    "longitude": round(lng, 6),
                    "latitude": round(lat, 6),
                    "parentId": None,
                    "areaId": scenic_id,
                    "createTime": ts,
                    "updateTime": ts,
                }
            )
            poi_id += 1
        elif is_poi_candidate(tags):
            # Keep candidate POI with explicit null type for later taxonomy expansion.
            poi_rows.append(
                {
                    "id": poi_id,
                    "name": name,
                    "type": None,
                    "description": f"OSM source={el.get('type')}:{el.get('id')}, poi_type_unmatched",
                    "location": loc_text,
                    "longitude": round(lng, 6),
                    "latitude": round(lat, 6),
                    "parentId": None,
                    "areaId": scenic_id,
                    "createTime": ts,
                    "updateTime": ts,
                }
            )
            unmatched_poi_items.append(build_unmatched_poi_item(el, tags, name, loc_text))
            poi_id += 1

        fac_type = classify_facility(tags)
        if fac_type:
            fac_rows.append(
                {
                    "id": fac_id,
                    "name": name,
                    "type": fac_type,
                    "description": f"OSM source={el.get('type')}:{el.get('id')}, review required",
                    "location": loc_text,
                    "longitude": round(lng, 6),
                    "latitude": round(lat, 6),
                    "areaId": scenic_id,
                    "createTime": ts,
                    "updateTime": ts,
                }
            )
            fac_id += 1

    poi_rows = dedup_rows(poi_rows)[:20]
    fac_rows = dedup_rows(fac_rows)[:15]
    business_poi_ids = [int(row["id"]) for row in poi_rows]

    # Build true-road-network graph from OSM highway way geometry.
    adjacency: Dict[int, List[Tuple[int, float]]] = {}
    road_node_ids = set()
    graph_node_coord: Dict[int, Tuple[float, float]] = {}
    graph_node_index: Dict[Tuple[int, int], int] = {}
    next_graph_node_id = 1

    def get_graph_node_id(lng: float, lat: float) -> int:
        nonlocal next_graph_node_id
        key = (round(lng * 1_000_000), round(lat * 1_000_000))
        gid = graph_node_index.get(key)
        if gid is not None:
            return gid
        gid = next_graph_node_id
        next_graph_node_id += 1
        graph_node_index[key] = gid
        graph_node_coord[gid] = (lng, lat)
        return gid

    for el in elements:
        if not isinstance(el, dict) or str(el.get("type", "")) != "way":
            continue
        tags = el.get("tags", {})
        if not isinstance(tags, dict):
            continue
        if not str(tags.get("highway", "")).strip():
            continue
        geometry = el.get("geometry", [])
        if not isinstance(geometry, list) or len(geometry) < 2:
            continue
        for idx in range(len(geometry) - 1):
            pa = geometry[idx]
            pb = geometry[idx + 1]
            if not isinstance(pa, dict) or not isinstance(pb, dict):
                continue
            if "lon" not in pa or "lat" not in pa or "lon" not in pb or "lat" not in pb:
                continue
            try:
                alng = float(pa["lon"])
                alat = float(pa["lat"])
                blng = float(pb["lon"])
                blat = float(pb["lat"])
            except Exception:
                continue
            a = get_graph_node_id(alng, alat)
            b = get_graph_node_id(blng, blat)
            d = distance_m(alng, alat, blng, blat)
            if d <= 0.5 or d > 800:
                continue
            adjacency.setdefault(a, []).append((b, d))
            adjacency.setdefault(b, []).append((a, d))
            road_node_ids.add(a)
            road_node_ids.add(b)

    # Snap POIs to nearest road node.
    poi_snap: Dict[int, int] = {}
    for row in poi_rows:
        pid = int(row["id"])
        plng = float(row["longitude"])
        plat = float(row["latitude"])
        best_node = None
        best_dist = float("inf")
        for nid in road_node_ids:
            c = graph_node_coord.get(nid)
            if not c:
                continue
            d = distance_m(plng, plat, c[0], c[1])
            if d < best_dist:
                best_dist = d
                best_node = nid
        if best_node is not None and best_dist <= 220:
            poi_snap[pid] = best_node

    # Use virtual non-POI nodes as real road endpoints.
    virtual_node_id: Dict[int, int] = {}
    for nid in road_node_ids:
        virtual_node_id[nid] = args.virtual_node_base + nid

    # 1) Highway graph edges between virtual nodes.
    for start_node, targets in adjacency.items():
        for end_node, dist in targets:
            if start_node >= end_node:
                continue
            if dist < 2 or dist > 800:
                continue
            vs = virtual_node_id.get(start_node)
            ve = virtual_node_id.get(end_node)
            if vs is None or ve is None:
                continue
            road_rows.append(
                {
                    "id": road_id,
                    "startId": vs,
                    "endId": ve,
                    "distance": round(dist, 1),
                    "speed": 2.8,
                    "congestion": 0.8,
                    "vehicleType": "walk",
                    "areaId": scenic_id,
                    "createTime": ts,
                    "updateTime": ts,
                }
            )
            road_id += 1

    # 2) Connector edges between POI and snapped virtual node.
    for row in poi_rows:
        pid = int(row["id"])
        n = poi_snap.get(pid)
        if n is None:
            continue
        c = graph_node_coord.get(n)
        if not c:
            continue
        dist = distance_m(float(row["longitude"]), float(row["latitude"]), c[0], c[1])
        if dist > 260:
            continue
        ve = virtual_node_id.get(n)
        if ve is None:
            continue
        road_rows.append(
            {
                "id": road_id,
                "startId": pid,
                "endId": ve,
                "distance": round(max(dist, 2.0), 1),
                "speed": 2.8,
                "congestion": 0.8,
                "vehicleType": "walk",
                "areaId": scenic_id,
                "createTime": ts,
                "updateTime": ts,
            }
        )
        road_id += 1

    # 3) Persist virtual road-network nodes as non-POI readable nodes.
    for road_nid in sorted(road_node_ids):
        gid = virtual_node_id.get(road_nid)
        c = graph_node_coord.get(road_nid)
        if gid is None or c is None:
            continue
        poi_rows.append(
            {
                "id": gid,
                "name": f"道路节点-{gid}",
                "type": "virtual_node",
                "location": "road_network",
                "longitude": round(c[0], 6),
                "latitude": round(c[1], 6),
                "parentId": None,
                "areaId": scenic_id,
            }
        )

    poi_rows = dedup_rows(poi_rows)

    map_imports_path = Path(args.map_imports)
    if not map_imports_path.is_absolute():
        map_imports_path = repo_root / map_imports_path
    id_registry_path = Path(args.id_registry)
    if not id_registry_path.is_absolute():
        id_registry_path = repo_root / id_registry_path

    degree_map: Dict[int, int] = {}
    for row in road_rows:
        s = int(row["startId"])
        e = int(row["endId"])
        degree_map[s] = degree_map.get(s, 0) + 1
        degree_map[e] = degree_map.get(e, 0) + 1

    virtual_ids = set(virtual_node_id.values())
    isolated_virtual_count = sum(1 for vid in virtual_ids if degree_map.get(vid, 0) == 0)
    degree_one_virtual_count = sum(1 for vid in virtual_ids if degree_map.get(vid, 0) == 1)

    poi_attach_count = sum(1 for pid in business_poi_ids if pid in poi_snap)
    poi_attach_ratio = (poi_attach_count / len(business_poi_ids)) if business_poi_ids else 0.0

    degree_hist = {"deg1": 0, "deg2": 0, "deg3_plus": 0}
    for d in degree_map.values():
        if d <= 1:
            degree_hist["deg1"] += 1
        elif d == 2:
            degree_hist["deg2"] += 1
        else:
            degree_hist["deg3_plus"] += 1

    counts = {
        "scenic_areas": 1,
        "buildings": len(poi_rows),
        "facilities": len(fac_rows),
        "roads": len(road_rows),
    }
    starts = allocate_id_ranges(repo_root, map_imports_path, id_registry_path, counts)

    scenic_new_id = starts["scenic_areas"]
    scenic_area["id"] = scenic_new_id

    poi_id_map: Dict[int, int] = {}
    next_poi_id = starts["buildings"]
    for row in poi_rows:
        old_id = int(row["id"])
        poi_id_map[old_id] = next_poi_id
        row["id"] = next_poi_id
        row["areaId"] = scenic_new_id
        next_poi_id += 1

    next_fac_id = starts["facilities"]
    for row in fac_rows:
        row["id"] = next_fac_id
        row["areaId"] = scenic_new_id
        next_fac_id += 1

    next_road_id = starts["roads"]
    for row in road_rows:
        old_start = int(row["startId"])
        old_end = int(row["endId"])
        if old_start not in poi_id_map or old_end not in poi_id_map:
            raise RuntimeError(f"road endpoint id not found in POI id map: {old_start}->{old_end}")
        row["id"] = next_road_id
        row["startId"] = poi_id_map[old_start]
        row["endId"] = poi_id_map[old_end]
        row["areaId"] = scenic_new_id
        next_road_id += 1

    run_name = (args.run_name or "latest").strip()
    out_dir = scenic_root / run_name
    if out_dir.exists():
        shutil.rmtree(out_dir)
    raw_dir = out_dir / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)

    (out_dir / "scenic_areas.append.json").write_text(json.dumps([scenic_area], ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "pois.append.json").write_text(json.dumps(poi_rows, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "facilities.append.json").write_text(json.dumps(fac_rows, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "roads.append.json").write_text(json.dumps(road_rows, ensure_ascii=False, indent=2), encoding="utf-8")

    (raw_dir / "nominatim_top.json").write_text(json.dumps(top, ensure_ascii=False, indent=2), encoding="utf-8")
    (raw_dir / "overpass.json").write_text(json.dumps(overpass_raw, ensure_ascii=False, indent=2), encoding="utf-8")
    (raw_dir / "unmatched_poi_types.json").write_text(json.dumps(unmatched_poi_items, ensure_ascii=False, indent=2), encoding="utf-8")

    file_size_stats = {
        "scenic_areas.append.json": (out_dir / "scenic_areas.append.json").stat().st_size,
        "pois.append.json": (out_dir / "pois.append.json").stat().st_size,
        "facilities.append.json": (out_dir / "facilities.append.json").stat().st_size,
        "roads.append.json": (out_dir / "roads.append.json").stat().st_size,
    }
    total_size = sum(file_size_stats.values())

    report = [
        "# OSM Data Report",
        "",
        f"- generatedAt: {ts}",
        f"- targetNameInput: {args.target_name}",
        f"- scenicNameMatched: {matched_scenic_name}",
        f"- query: {args.query}",
        f"- matchedAddressName: {matched_dir_key}",
        f"- outputDirSlug: {scenic_slug}",
        f"- center: {center_lng:.6f},{center_lat:.6f}",
        f"- scenicCount: 1",
        f"- poiCount: {len(poi_rows)}",
        f"- facilityCount: {len(fac_rows)}",
        f"- roadCount: {len(road_rows)}",
        f"- unmatchedPoiTypeCount: {len(unmatched_poi_items)}",
        "",
        "## Notes",
        "- Review-only output. Existing seed files remain unchanged.",
        "- OSM coverage may include nearby non-campus entities within radius; manual approval required.",
        f"- roadNetworkNodes: {len(road_node_ids)}",
        f"- roadGraphEdgesApprox: {sum(len(v) for v in adjacency.values()) // 2}",
        f"- virtualNodeCount: {len(virtual_node_id)}",
        f"- snappedPoiCount: {len(poi_snap)}",
        f"- businessPoiAttachCount: {poi_attach_count}/{len(business_poi_ids)} ({poi_attach_ratio:.2%})",
        f"- isolatedVirtualNodeCount: {isolated_virtual_count}",
        f"- degreeOneVirtualNodeCount: {degree_one_virtual_count}",
        f"- nodeDegreeHistogram: deg1={degree_hist['deg1']}, deg2={degree_hist['deg2']}, deg3plus={degree_hist['deg3_plus']}",
        "- Roads are generated from OSM highway network with virtual non-POI nodes as endpoints.",
        f"- unmatchedPoiTypeLog: raw/unmatched_poi_types.json",
        f"- contextSource: {'cache' if context_from_cache else 'nominatim'}",
        "",
        "## Payload Size",
        f"- scenicAreasBytes: {file_size_stats['scenic_areas.append.json']}",
        f"- poisBytes: {file_size_stats['pois.append.json']}",
        f"- facilitiesBytes: {file_size_stats['facilities.append.json']}",
        f"- roadsBytes: {file_size_stats['roads.append.json']}",
        f"- totalBytes: {total_size}",
    ]

    if args.apply_seed:
        scenic_written = append_rows_to_seed("scenic_areas", [scenic_area])
        # POI 语义层仍写入 buildings.json，以保持当前后端加载兼容。
        poi_written = append_rows_to_seed("buildings", poi_rows)
        fac_written = append_rows_to_seed("facilities", fac_rows)
        road_written = append_rows_to_seed("roads", road_rows)
        report.extend(
            [
                "",
                "## Applied To Seed",
                f"- scenic_areas appended: {scenic_written}",
                f"- buildings(POI) appended: {poi_written}",
                f"- facilities appended: {fac_written}",
                f"- roads appended: {road_written}",
            ]
        )

    if args.update_map_imports:

        resources_root = (repo_root / "src" / "main" / "resources").resolve()
        out_dir_abs = out_dir.resolve()
        classpath_base = ""
        if str(out_dir_abs).startswith(str(resources_root)):
            classpath_base = out_dir_abs.relative_to(resources_root).as_posix()
            update_map_imports(map_imports_path, scenic_slug, classpath_base)
            report.extend([
                "",
                "## Map Imports",
                f"- updated: true",
                f"- mapImportsFile: {map_imports_path.as_posix()}",
                f"- classpathBase: {classpath_base}",
                f"- idRegistryFile: {id_registry_path.as_posix()}",
            ])
        else:
            report.extend([
                "",
                "## Map Imports",
                "- updated: false",
                "- reason: output directory is outside src/main/resources",
            ])

    (out_dir / "report.md").write_text("\n".join(report), encoding="utf-8")

    print(f"Output written to: {out_dir}")
    print(f"POI={len(poi_rows)}, Facilities={len(fac_rows)}, Roads={len(road_rows)}")
    if unmatched_poi_items:
        print(f"Unmatched POI types: {len(unmatched_poi_items)} (see {raw_dir / 'unmatched_poi_types.json'})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
