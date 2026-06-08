#!/usr/bin/env python3
"""Generate dev-seed/indoor/*.json from OSM (FR-004-5).

Design (aligned with common campus OSM tagging, e.g. BUPT Shahe raw):
  - ``indoor=room`` is often a **way** (polygon / room footprint), not a node → use way centroid as a room node.
  - ``highway=corridor`` may be **absent**; use **MST on room centroids** (same ``level``) to add synthetic
    ``corridor`` edges for routing + completeness (documented heuristic, not OSM truth).
  - Still ingest ``highway=corridor|elevator|steps``, ``node[indoor=door]``, ``node[indoor=room]``.
  - Optional: ``highway=footway`` + ``indoor=yes`` as corridor polylines (sparse campuses).

Completeness (aligned with ``IndoorSeedCompleteness.java``):
  - >= 1 level, >= 2 rooms, >= 3 **corridor** edges (synthetic allowed).
  - Room connectivity uses **corridor + elevator + steps** edges (Java matches this).
"""
from __future__ import annotations

import argparse
import json
import math
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

OVERPASS_URL = "https://overpass-api.de/api/interpreter"

DEFAULT_INDOOR_POI_TYPES = frozenset(
    {
        "library",
        "teaching",
        "lab",
        "dormitory",
        "scenic_spot",
        "gate",
        "medical",
        "sports",
    }
)

# Heuristic caps (avoid importing an entire city as one building graph)
MAX_ROOMS_PER_BUILDING = 36
# Campus OSM indoor footprints are often misaligned; P0 buffer for osmId-matched buildings.
P0_BUILDING_BUFFER_M = 150.0
P0_MAX_CLAIM_ELEMENTS = 42


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(max(1e-12, 1 - a)))
    return r * c


def flat_distance_m(lon1: float, lat1: float, lon2: float, lat2: float) -> float:
    """快速平面近似（米），用于半径裁剪。"""
    dx = (lon2 - lon1) * 111320.0 * math.cos(math.radians((lat1 + lat2) / 2))
    dy = (lat2 - lat1) * 110540.0
    return math.hypot(dx, dy)


def element_center(el: Dict[str, Any]) -> Optional[Tuple[float, float]]:
    if "lat" in el and "lon" in el:
        return float(el["lon"]), float(el["lat"])
    center = el.get("center")
    if isinstance(center, dict) and "lat" in center and "lon" in center:
        return float(center["lon"]), float(center["lat"])
    geometry = el.get("geometry")
    if isinstance(geometry, list) and geometry:
        xs: List[float] = []
        ys: List[float] = []
        for p in geometry:
            if not isinstance(p, dict) or "lon" not in p or "lat" not in p:
                continue
            xs.append(float(p["lon"]))
            ys.append(float(p["lat"]))
        if xs:
            return sum(xs) / len(xs), sum(ys) / len(ys)
    return None


def overpass(query: str, user_agent: str) -> Dict[str, Any]:
    url = f"{OVERPASS_URL}?data={urllib.parse.quote_plus(query)}"
    req = urllib.request.Request(url, headers={"User-Agent": user_agent})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def way_length_m(geometry: List[Dict[str, Any]]) -> float:
    total = 0.0
    for i in range(1, len(geometry)):
        lon1, lat1 = float(geometry[i - 1]["lon"]), float(geometry[i - 1]["lat"])
        lon2, lat2 = float(geometry[i]["lon"]), float(geometry[i]["lat"])
        dx = (lon2 - lon1) * 111320.0 * math.cos(math.radians((lat1 + lat2) / 2))
        dy = (lat2 - lat1) * 110540.0
        total += math.hypot(dx, dy)
    return max(total, 1.0)


def node_pair_distance_m(na: Dict[str, Any], nb: Dict[str, Any]) -> float:
    la, lo = float(na["latitude"]), float(na["longitude"])
    lb, ob = float(nb["latitude"]), float(nb["longitude"])
    return max(haversine_m(la, lo, lb, ob), 0.5)


WALKABLE_EDGE_KINDS = frozenset({"corridor", "elevator", "steps"})


def level_display_label(osm_level: str) -> str:
    """OSM level 展示：0 -> 1层（与 Java IndoorLevelLabel 一致）。"""
    s = (osm_level or "").strip()
    if not s:
        return "1层"
    try:
        n = int(float(s))
        return f"{n + 1}层"
    except ValueError:
        return f"{s}层"


def build_walkable_adjacency(edges: List[Dict[str, Any]]) -> Dict[int, Set[int]]:
    adj: Dict[int, Set[int]] = {}
    for e in edges:
        if str(e.get("edgeKind", "")).lower() not in WALKABLE_EDGE_KINDS:
            continue
        u, v = int(e["startNodeId"]), int(e["endNodeId"])
        adj.setdefault(u, set()).add(v)
        adj.setdefault(v, set()).add(u)
    return adj


def connected_components(node_ids: List[int], adj: Dict[int, Set[int]]) -> List[Set[int]]:
    seen: Set[int] = set()
    comps: List[Set[int]] = []
    for nid in node_ids:
        if nid in seen:
            continue
        stack = [nid]
        comp: Set[int] = set()
        while stack:
            u = stack.pop()
            if u in comp:
                continue
            comp.add(u)
            for v in adj.get(u, ()):
                if v not in comp:
                    stack.append(v)
        for u in comp:
            seen.add(u)
        comps.append(comp)
    return comps


def has_corridor_edge(edges: List[Dict[str, Any]], u: int, v: int) -> bool:
    for e in edges:
        if str(e.get("edgeKind", "")).lower() != "corridor":
            continue
        if {int(e["startNodeId"]), int(e["endNodeId"])} == {u, v}:
            return True
    return False


def bridge_walkable_components(nodes: List[Dict[str, Any]], edges: List[Dict[str, Any]]) -> int:
    """多连通分量时，在相距最近的两节点间补 corridor 边（采集兜底）。"""
    node_ids = [int(n["id"]) for n in nodes]
    if len(node_ids) < 2:
        return 0
    id_to_node = {int(n["id"]): n for n in nodes}
    adj = build_walkable_adjacency(edges)
    for nid in node_ids:
        adj.setdefault(nid, set())

    added = 0
    while True:
        comps = connected_components(node_ids, adj)
        if len(comps) <= 1:
            break
        best: Optional[Tuple[float, int, int]] = None
        for i in range(len(comps)):
            for j in range(i + 1, len(comps)):
                for u in comps[i]:
                    for v in comps[j]:
                        d = node_pair_distance_m(id_to_node[u], id_to_node[v])
                        if best is None or d < best[0]:
                            best = (d, u, v)
        if best is None:
            break
        d, u, v = best
        if not has_corridor_edge(edges, u, v):
            edges.append(
                {
                    "id": len(edges) + 1,
                    "startNodeId": u,
                    "endNodeId": v,
                    "edgeKind": "corridor",
                    "distance": round(d, 2),
                    "directed": False,
                }
            )
            added += 1
        adj[u].add(v)
        adj[v].add(u)
    return added


def rooms_connected_via_walkable(nodes: List[Dict[str, Any]], edges: List[Dict[str, Any]]) -> bool:
    """Same as Java post-fix: corridor + elevator + steps adjacency for rooms."""
    adj: Dict[int, Set[int]] = {}
    room_ids: Set[int] = set()
    for e in edges:
        if str(e.get("edgeKind", "")).lower() not in WALKABLE_EDGE_KINDS:
            continue
        u, v = int(e["startNodeId"]), int(e["endNodeId"])
        adj.setdefault(u, set()).add(v)
        adj.setdefault(v, set()).add(u)
    for n in nodes:
        if str(n.get("nodeKind", "")).lower() == "room":
            rid = int(n["id"])
            room_ids.add(rid)
            if rid not in adj:
                return False
    if len(room_ids) < 2:
        return False
    start = next(iter(room_ids))
    stack = [start]
    visited: Set[int] = set()
    while stack:
        u = stack.pop()
        if u in visited:
            continue
        visited.add(u)
        for v in adj.get(u, ()):
            if v not in visited:
                stack.append(v)
    return all(rid in visited for rid in room_ids)


def evaluate_completeness(nodes: List[Dict[str, Any]], edges: List[Dict[str, Any]]) -> Tuple[bool, List[str]]:
    failures: List[str] = []
    rooms = [n for n in nodes if str(n.get("nodeKind", "")).lower() == "room"]
    corridors = [e for e in edges if str(e.get("edgeKind", "")).lower() == "corridor"]
    levels = {str(n.get("level", "")).strip() for n in nodes if str(n.get("level", "")).strip()}
    if not levels:
        failures.append("LEVELS")
    if len(rooms) < 2:
        failures.append("ROOMS")
    if len(corridors) < 3:
        failures.append("CORRIDORS")
    if failures:
        return False, failures
    if not rooms_connected_via_walkable(nodes, edges):
        failures.append("DISCONNECTED")
    return len(failures) == 0, failures


def filter_elements_near(
    elements: List[Dict[str, Any]],
    origin_lat: float,
    origin_lng: float,
    radius_m: float,
) -> List[Dict[str, Any]]:
    out: List[Dict[str, Any]] = []
    for el in elements:
        if not isinstance(el, dict):
            continue
        c = element_center(el)
        if not c:
            continue
        lng, lat = c
        if flat_distance_m(origin_lng, origin_lat, lng, lat) <= radius_m * 1.15:
            out.append(el)
    return out


def build_graph_from_osm_elements(
    elements: List[Dict[str, Any]],
    origin_lat: float,
    origin_lng: float,
    node_id_start: int = 9000,
) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    """Parse OSM elements into indoor nodes/edges (WGS84 longitude/latitude)."""
    _ = (origin_lat, origin_lng)  # graph nodes use WGS84; origin only used by caller filters
    node_id_seq = node_id_start
    nodes: List[Dict[str, Any]] = []
    edges: List[Dict[str, Any]] = []

    def add_node(lat: float, lon: float, name: str, kind: str, level: str) -> int:
        nonlocal node_id_seq
        node_id_seq += 1
        nodes.append(
            {
                "id": node_id_seq,
                "level": level or "0",
                "name": name or kind,
                "nodeKind": kind,
                "longitude": round(lon, 7),
                "latitude": round(lat, 7),
                "x": 0.0,
                "y": 0.0,
            }
        )
        return node_id_seq

    for el in elements:
        tags = el.get("tags") or {}
        if not isinstance(tags, dict):
            tags = {}
        level = str(tags.get("level") or tags.get("level:ref") or "0")

        if el.get("type") == "node" and "lat" in el and "lon" in el:
            lat, lon = float(el["lat"]), float(el["lon"])
            indoor = tags.get("indoor")
            if indoor == "room":
                add_node(lat, lon, str(tags.get("name") or "room"), "room", level)
            elif indoor == "door":
                add_node(lat, lon, str(tags.get("name") or "door"), "door", level)
            continue

        if el.get("type") != "way" or not el.get("geometry"):
            continue
        geom = el["geometry"]
        if not isinstance(geom, list) or len(geom) < 1:
            continue

        indoor = tags.get("indoor")
        hw = str(tags.get("highway", "") or "").lower()

        # Room as area/way (BUPT pattern). elevator/steps room-ways are vertical polylines only.
        if indoor == "room" and hw in ("elevator", "steps"):
            pass  # handled in polyline block below
        elif indoor == "room":
            xs = [float(p["lon"]) for p in geom if isinstance(p, dict) and "lon" in p and "lat" in p]
            ys = [float(p["lat"]) for p in geom if isinstance(p, dict) and "lon" in p and "lat" in p]
            if not xs:
                continue
            clat = sum(ys) / len(ys)
            clon = sum(xs) / len(xs)
            add_node(clat, clon, str(tags.get("name") or "room"), "room", level)
            continue

        # Corridor / vertical as polylines (incl. indoor=room + highway=elevator mis-tagged ways)
        poly_kinds: List[str] = []
        if hw == "corridor" or indoor == "corridor":
            poly_kinds.append("corridor")
        elif hw in ("elevator", "steps"):
            poly_kinds.append(hw)
        elif hw == "footway" and tags.get("indoor") == "yes":
            poly_kinds.append("corridor")

        if not poly_kinds:
            continue

        edge_kind = poly_kinds[0]
        prev: Optional[int] = None
        prev_lat: float = 0.0
        prev_lon: float = 0.0
        for pt in geom:
            if not isinstance(pt, dict) or "lon" not in pt or "lat" not in pt:
                continue
            lat, lon = float(pt["lat"]), float(pt["lon"])
            jk = "corridor_junction" if edge_kind == "corridor" else edge_kind
            cur = add_node(lat, lon, str(tags.get("name") or edge_kind), jk, level)
            if prev is not None:
                seg_len = haversine_m(prev_lat, prev_lon, lat, lon)
                edges.append(
                    {
                        "id": len(edges) + 1,
                        "startNodeId": prev,
                        "endNodeId": cur,
                        "edgeKind": edge_kind,
                        "distance": round(max(seg_len, 0.5), 2),
                        "directed": False,
                    }
                )
            prev = cur
            prev_lat, prev_lon = lat, lon

    return nodes, edges


def prune_room_nodes_and_edges(
    nodes: List[Dict[str, Any]], edges: List[Dict[str, Any]], keep_room_ids: Set[int]
) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    drop = {
        int(n["id"])
        for n in nodes
        if str(n.get("nodeKind", "")).lower() == "room" and int(n["id"]) not in keep_room_ids
    }
    kept_nodes = [n for n in nodes if int(n["id"]) not in drop]
    kept_edges = [
        e
        for e in edges
        if int(e["startNodeId"]) not in drop and int(e["endNodeId"]) not in drop
    ]
    # renumber edge ids
    for i, e in enumerate(kept_edges, start=1):
        e["id"] = i
    return kept_nodes, kept_edges


def cap_rooms_by_distance(
    nodes: List[Dict[str, Any]],
    edges: List[Dict[str, Any]],
    origin_lat: float,
    origin_lng: float,
    max_rooms: int,
) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    rooms = [n for n in nodes if str(n.get("nodeKind", "")).lower() == "room"]
    if len(rooms) <= max_rooms:
        return nodes, edges

    def score(n: Dict[str, Any]) -> float:
        return haversine_m(
            float(n["latitude"]),
            float(n["longitude"]),
            origin_lat,
            origin_lng,
        )

    rooms.sort(key=score)
    keep = {int(n["id"]) for n in rooms[:max_rooms]}
    return prune_room_nodes_and_edges(nodes, edges, keep)


def cap_rooms_by_count(
    nodes: List[Dict[str, Any]],
    edges: List[Dict[str, Any]],
    max_rooms: int,
) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    rooms = [n for n in nodes if str(n.get("nodeKind", "")).lower() == "room"]
    if len(rooms) <= max_rooms:
        return nodes, edges
    rooms.sort(key=lambda n: int(n["id"]))
    keep = {int(n["id"]) for n in rooms[:max_rooms]}
    return prune_room_nodes_and_edges(nodes, edges, keep)


def add_synthetic_corridor_edges(nodes: List[Dict[str, Any]], edges: List[Dict[str, Any]]) -> None:
    """Per-level MST on room centroids + extra corridor edges until count >= 3."""
    rooms = [n for n in nodes if str(n.get("nodeKind", "")).lower() == "room"]
    if len(rooms) < 2:
        return

    def corridor_edges() -> List[Dict[str, Any]]:
        return [e for e in edges if str(e.get("edgeKind", "")).lower() == "corridor"]

    def has_corridor(u: int, v: int) -> bool:
        for e in edges:
            if str(e.get("edgeKind", "")).lower() != "corridor":
                continue
            a, b = int(e["startNodeId"]), int(e["endNodeId"])
            if {a, b} == {u, v}:
                return True
        return False

    def add_edge(u: int, v: int, d: float) -> None:
        if u == v or has_corridor(u, v):
            return
        edges.append(
            {
                "id": len(edges) + 1,
                "startNodeId": u,
                "endNodeId": v,
                "edgeKind": "corridor",
                "distance": round(d, 2),
                "directed": False,
            }
        )

    by_level: Dict[str, List[Dict[str, Any]]] = {}
    for r in rooms:
        by_level.setdefault(str(r.get("level") or "0").strip(), []).append(r)

    for lst in by_level.values():
        if len(lst) < 2:
            continue
        parent = {int(r["id"]): int(r["id"]) for r in lst}

        def find(x: int) -> int:
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x

        def union(a: int, b: int) -> bool:
            ra, rb = find(a), find(b)
            if ra == rb:
                return False
            parent[rb] = ra
            return True

        pairs: List[Tuple[float, int, int]] = []
        for i, a in enumerate(lst):
            for b in lst[i + 1 :]:
                pairs.append((node_pair_distance_m(a, b), int(a["id"]), int(b["id"])))
        pairs.sort(key=lambda t: t[0])
        for d, u, v in pairs:
            if union(u, v):
                add_edge(u, v, d)

    pairs_global: List[Tuple[float, int, int]] = []
    for i, a in enumerate(rooms):
        for b in rooms[i + 1 :]:
            pairs_global.append((node_pair_distance_m(a, b), int(a["id"]), int(b["id"])))
    pairs_global.sort(key=lambda t: t[0])
    for d, u, v in pairs_global:
        if len(corridor_edges()) >= 3:
            break
        add_edge(u, v, d)


def add_cross_level_vertical_edges(
    nodes: List[Dict[str, Any]], edges: List[Dict[str, Any]], vertical_meters: float = 10.0
) -> None:
    """When OSM has no elevator/stairs between floors, link closest room pair per adjacent level."""
    if rooms_connected_via_walkable(nodes, edges):
        return
    rooms = [n for n in nodes if str(n.get("nodeKind", "")).lower() == "room"]
    by_level: Dict[str, List[Dict[str, Any]]] = {}
    for r in rooms:
        by_level.setdefault(str(r.get("level") or "0").strip(), []).append(r)

    def level_sort_key(lv: str) -> Tuple[int, str]:
        try:
            return (0, str(float(lv)))
        except ValueError:
            return (1, lv)

    levels = sorted(by_level.keys(), key=level_sort_key)
    for i in range(len(levels) - 1):
        la, lb = levels[i], levels[i + 1]
        ra, rb = by_level[la], by_level[lb]
        if not ra or not rb:
            continue
        best: Optional[Tuple[float, int, int]] = None
        for a in ra:
            for b in rb:
                d = node_pair_distance_m(a, b)
                if best is None or d < best[0]:
                    best = (d, int(a["id"]), int(b["id"]))
        if best is None:
            continue
        _, u, v = best
        edges.append(
            {
                "id": len(edges) + 1,
                "startNodeId": u,
                "endNodeId": v,
                "edgeKind": "elevator",
                "distance": vertical_meters,
                "directed": False,
            }
        )
        if rooms_connected_via_walkable(nodes, edges):
            return


def pick_entrance_node_id(nodes: List[Dict[str, Any]], origin_lat: float, origin_lng: float) -> Optional[int]:
    doors = [n for n in nodes if str(n.get("nodeKind", "")).lower() == "door"]
    if doors:

        def ddoor(n: Dict[str, Any]) -> float:
            return haversine_m(float(n["latitude"]), float(n["longitude"]), origin_lat, origin_lng)

        doors.sort(key=ddoor)
        return int(doors[0]["id"])
    rooms = [n for n in nodes if str(n.get("nodeKind", "")).lower() == "room"]
    if rooms:

        def drm(n: Dict[str, Any]) -> float:
            return haversine_m(float(n["latitude"]), float(n["longitude"]), origin_lat, origin_lng)

        rooms.sort(key=drm)
        return int(rooms[0]["id"])
    return int(nodes[0]["id"]) if nodes else None


def finalize_indoor_bundle(
    nodes: List[Dict[str, Any]],
    edges: List[Dict[str, Any]],
    *,
    origin_lat: float,
    origin_lng: float,
    building_poi_id: int,
    area_id: int,
    source: str,
) -> Tuple[Optional[Dict[str, Any]], List[str]]:
    add_synthetic_corridor_edges(nodes, edges)
    add_cross_level_vertical_edges(nodes, edges)
    bridge_walkable_components(nodes, edges)
    ok, failures = evaluate_completeness(nodes, edges)
    if not ok:
        return None, failures
    for node in nodes:
        node["parentId"] = building_poi_id
    levels = sorted({str(n["level"]) for n in nodes})
    bundle = {
        "buildingPoiId": building_poi_id,
        "areaId": area_id,
        "source": source,
        "completenessScore": 1.0,
        "levels": [{"level": lv, "label": level_display_label(lv), "order": i} for i, lv in enumerate(levels)],
        "entranceNodeId": pick_entrance_node_id(nodes, origin_lat, origin_lng),
        "nodes": nodes,
        "edges": edges,
    }
    return bundle, []


def try_build_bundle_from_elements(
    elements: List[Dict[str, Any]],
    *,
    origin_lat: float,
    origin_lng: float,
    radius_m: float,
    building_poi_id: int,
    area_id: int,
) -> Tuple[Optional[Dict[str, Any]], List[str]]:
    subset = filter_elements_near(elements, origin_lat, origin_lng, radius_m)
    nodes, edges = build_graph_from_osm_elements(subset, origin_lat, origin_lng)
    nodes, edges = cap_rooms_by_distance(nodes, edges, origin_lat, origin_lng, MAX_ROOMS_PER_BUILDING)
    return finalize_indoor_bundle(
        nodes,
        edges,
        origin_lat=origin_lat,
        origin_lng=origin_lng,
        building_poi_id=building_poi_id,
        area_id=area_id,
        source="osm-raw+heuristic-corridors+component-bridges",
    )


def try_build_bundle_from_subset(
    elements: List[Dict[str, Any]],
    *,
    origin_lat: float,
    origin_lng: float,
    building_poi_id: int,
    area_id: int,
) -> Tuple[Optional[Dict[str, Any]], List[str]]:
    nodes, edges = build_graph_from_osm_elements(elements, origin_lat, origin_lng)
    nodes, edges = cap_rooms_by_count(nodes, edges, MAX_ROOMS_PER_BUILDING)
    return finalize_indoor_bundle(
        nodes,
        edges,
        origin_lat=origin_lat,
        origin_lng=origin_lng,
        building_poi_id=building_poi_id,
        area_id=area_id,
        source="osm-building-footprint+heuristic-corridors+component-bridges",
    )


def collect_indoor_for_poi(
    building_poi_id: int,
    area_id: int,
    lat: float,
    lng: float,
    *,
    radius: int = 80,
    out_dir: Path,
    user_agent: str,
    poi_name: str = "",
    poi_type: str = "",
    overpass_elements: Optional[List[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    result: Dict[str, Any] = {
        "buildingPoiId": building_poi_id,
        "areaId": area_id,
        "name": poi_name,
        "type": poi_type,
        "status": "error",
        "failures": [],
        "outputPath": None,
        "strategy": None,
    }

    if overpass_elements:
        bundle, failures = try_build_bundle_from_elements(
            overpass_elements,
            origin_lat=lat,
            origin_lng=lng,
            radius_m=float(radius),
            building_poi_id=building_poi_id,
            area_id=area_id,
        )
        if bundle is not None:
            result["strategy"] = "raw_elements"
            return _write_bundle(bundle, out_dir, result)

        result["failures"] = failures or ["RAW_REJECT"]

    # Fallback: legacy network Overpass (sparse indoor areas)
    q = f"""
    [out:json][timeout:60];
    (
      way(around:{radius},{lat},{lng})[highway=corridor];
      way(around:{radius},{lat},{lng})[highway=elevator];
      way(around:{radius},{lat},{lng})[highway=steps];
      way(around:{radius},{lat},{lng})[indoor=room];
      node(around:{radius},{lat},{lng})[indoor=room];
      node(around:{radius},{lat},{lng})[indoor=door];
    );
    out geom tags;
    """
    try:
        raw = overpass(q, user_agent)
    except Exception as ex:
        result["status"] = "error"
        result["failures"] = [f"OVERPASS:{ex}"]
        return result

    elements = raw.get("elements", [])
    bundle2, failures2 = try_build_bundle_from_elements(
        elements,
        origin_lat=lat,
        origin_lng=lng,
        radius_m=float(radius),
        building_poi_id=building_poi_id,
        area_id=area_id,
    )
    if bundle2 is not None:
        result["strategy"] = "network_overpass"
        return _write_bundle(bundle2, out_dir, result)

    merged: List[str] = []
    for x in (result.get("failures") or []) + (failures2 or []):
        if x and x not in merged:
            merged.append(x)
    result["status"] = "reject"
    result["failures"] = merged or ["REJECT"]
    result["strategy"] = "network_overpass"
    _write_reject(out_dir, building_poi_id, poi_name, result["failures"], 0, 0)
    return result


def _write_bundle(bundle: Dict[str, Any], out_dir: Path, result: Dict[str, Any]) -> Dict[str, Any]:
    out_dir.mkdir(parents=True, exist_ok=True)
    bid = int(bundle["buildingPoiId"])
    out = out_dir / f"{bid}.json"
    out.write_text(json.dumps(bundle, ensure_ascii=False, indent=2), encoding="utf-8")
    result["status"] = "ok"
    result["outputPath"] = str(out)
    result["nodeCount"] = len(bundle.get("nodes") or [])
    result["edgeCount"] = len(bundle.get("edges") or [])
    result["failures"] = []
    return result


def _write_reject(out_dir: Path, building_poi_id: int, poi_name: str, failures: List[str], n: int, e: int) -> None:
    reject = out_dir / "rejected" / f"{building_poi_id}.json"
    reject.parent.mkdir(parents=True, exist_ok=True)
    reject.write_text(
        json.dumps(
            {"buildingPoiId": building_poi_id, "name": poi_name, "failures": failures, "nodes": n, "edges": e},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def count_attributed_rooms(elements: List[Dict[str, Any]]) -> int:
    total = 0
    for el in elements:
        tags = el.get("tags") or {}
        if not isinstance(tags, dict):
            continue
        indoor = str(tags.get("indoor", "")).lower()
        hw = str(tags.get("highway", "")).lower()
        if indoor == "room" and hw not in {"elevator", "steps"}:
            total += 1
    return total


def collect_indoor_by_footprint(
    poi_rows: List[Dict[str, Any]],
    *,
    building_registry: List[Dict[str, Any]],
    overpass_elements: List[Dict[str, Any]],
    out_dir: Path,
    max_buildings: int = 12,
    poi_types: Optional[Set[str]] = None,
) -> List[Dict[str, Any]]:
    from osm_building_geo import (
        element_osm_key,
        filter_indoor_pool,
        min_distance_to_building_m,
        poi_osm_ref,
        subset_indoor_for_building,
    )

    allowed = {t.lower() for t in (poi_types or DEFAULT_INDOOR_POI_TYPES)}
    candidates = [
        row
        for row in poi_rows
        if str(row.get("type") or "").lower() in allowed
        and row.get("latitude") is not None
        and row.get("longitude") is not None
    ]
    pri = {"library": 0, "teaching": 1, "lab": 2, "dormitory": 3, "medical": 4, "sports": 5, "gate": 6, "scenic_spot": 7}

    def sort_key(row: Dict[str, Any]) -> Tuple[int, str]:
        t = str(row.get("type") or "").lower()
        return (pri.get(t, 99), str(row.get("name") or ""))

    candidates.sort(key=sort_key)
    candidates = candidates[: max_buildings]

    registry_by_key = {
        (str(row["osmType"]).lower(), int(row["osmId"])): row for row in building_registry if row.get("osmId") is not None
    }
    indoor_pool = filter_indoor_pool(overpass_elements)
    remaining_keys: Set[Tuple[str, int]] = {element_osm_key(el) for el in indoor_pool}
    assignments: Dict[Tuple[str, int], List[Dict[str, Any]]] = {}

    results: List[Dict[str, Any]] = []
    for row in candidates:
        bid = int(row["id"])
        area_id = int(row.get("areaId") or 0)
        lat = float(row["latitude"])
        lng = float(row["longitude"])
        name = str(row.get("name") or "")
        ptype = str(row.get("type") or "")
        result: Dict[str, Any] = {
            "buildingPoiId": bid,
            "areaId": area_id,
            "name": name,
            "type": ptype,
            "status": "reject",
            "failures": [],
            "outputPath": None,
            "strategy": "building_footprint",
            "attributedRooms": 0,
            "buildingOsmId": None,
        }
        ref = poi_osm_ref(row)
        subset: List[Dict[str, Any]] = []
        if ref:
            result["buildingOsmId"] = ref[1]
            reg = registry_by_key.get(ref)
            if reg:
                pool = [el for el in indoor_pool if element_osm_key(el) in remaining_keys]
                subset = subset_indoor_for_building(
                    pool,
                    reg,
                    buffer_m=P0_BUILDING_BUFFER_M,
                )
                if len(subset) > P0_MAX_CLAIM_ELEMENTS:
                    subset.sort(key=lambda el: min_distance_to_building_m(el, reg))
                    subset = subset[:P0_MAX_CLAIM_ELEMENTS]
                assignments[ref] = subset
                for el in subset:
                    remaining_keys.discard(element_osm_key(el))
        result["attributedRooms"] = count_attributed_rooms(subset)

        if not ref:
            result["failures"] = ["NO_OSM_REF"]
            _write_reject(out_dir, bid, name, result["failures"], 0, 0)
            results.append(result)
            continue
        if not subset:
            pool = [el for el in indoor_pool if element_osm_key(el) in remaining_keys]
            near = filter_elements_near(pool, lat, lng, radius_m=min(120.0, P0_BUILDING_BUFFER_M))
            if near:
                subset = near
                result["strategy"] = "building_footprint+radius_fallback"
                result["attributedRooms"] = count_attributed_rooms(subset)
                for el in subset:
                    remaining_keys.discard(element_osm_key(el))
        if not subset:
            result["failures"] = ["EMPTY_SUBSET"]
            _write_reject(out_dir, bid, name, result["failures"], 0, 0)
            results.append(result)
            continue

        anchor_lat, anchor_lng = lat, lng
        reg = registry_by_key.get(ref)
        centroid = reg.get("centroid") if isinstance(reg, dict) else None
        if isinstance(centroid, list) and len(centroid) >= 2:
            anchor_lng, anchor_lat = float(centroid[0]), float(centroid[1])

        bundle, failures = try_build_bundle_from_subset(
            subset,
            origin_lat=anchor_lat,
            origin_lng=anchor_lng,
            building_poi_id=bid,
            area_id=area_id,
        )
        if bundle is not None:
            results.append(_write_bundle(bundle, out_dir, result))
            continue
        result["failures"] = failures or ["REJECT"]
        _write_reject(out_dir, bid, name, result["failures"], 0, 0)
        results.append(result)

    update_indoor_manifest(out_dir, results)
    return results


def collect_indoor_for_pois(
    poi_rows: List[Dict[str, Any]],
    *,
    out_dir: Path,
    user_agent: str,
    radius: int = 80,
    sleep_s: float = 1.0,
    poi_types: Optional[Set[str]] = None,
    overpass_elements: Optional[List[Dict[str, Any]]] = None,
    building_registry: Optional[List[Dict[str, Any]]] = None,
    max_buildings: int = 12,
) -> List[Dict[str, Any]]:
    if building_registry and overpass_elements:
        return collect_indoor_by_footprint(
            poi_rows,
            building_registry=building_registry,
            overpass_elements=overpass_elements,
            out_dir=out_dir,
            max_buildings=max_buildings,
            poi_types=poi_types,
        )

    allowed = {t.lower() for t in (poi_types or DEFAULT_INDOOR_POI_TYPES)}
    candidates = [
        row
        for row in poi_rows
        if str(row.get("type") or "").lower() in allowed
        and row.get("latitude") is not None
        and row.get("longitude") is not None
    ]
    # Prefer library/teaching first when many candidates
    pri = {"library": 0, "teaching": 1, "lab": 2, "dormitory": 3, "medical": 4, "sports": 5, "gate": 6, "scenic_spot": 7}

    def sort_key(row: Dict[str, Any]) -> Tuple[int, str]:
        t = str(row.get("type") or "").lower()
        return (pri.get(t, 99), str(row.get("name") or ""))

    candidates.sort(key=sort_key)
    candidates = candidates[: max_buildings]

    results: List[Dict[str, Any]] = []
    for i, row in enumerate(candidates):
        if i > 0 and sleep_s > 0 and overpass_elements is None:
            time.sleep(sleep_s)
        bid = int(row["id"])
        area_id = int(row.get("areaId") or 0)
        lat = float(row["latitude"])
        lng = float(row["longitude"])
        name = str(row.get("name") or "")
        ptype = str(row.get("type") or "")
        results.append(
            collect_indoor_for_poi(
                bid,
                area_id,
                lat,
                lng,
                radius=radius,
                out_dir=out_dir,
                user_agent=user_agent,
                poi_name=name,
                poi_type=ptype,
                overpass_elements=overpass_elements,
            )
        )
    update_indoor_manifest(out_dir, results)
    return results


def update_indoor_manifest(out_dir: Path, results: List[Dict[str, Any]]) -> None:
    manifest_path = out_dir / "manifest.json"
    existing: List[Dict[str, Any]] = []
    if manifest_path.exists():
        try:
            data = json.loads(manifest_path.read_text(encoding="utf-8"))
            if isinstance(data, list):
                existing = data
        except Exception:
            existing = []
    by_id = {int(item.get("buildingPoiId", 0)): item for item in existing if isinstance(item, dict)}
    for row in results:
        if row.get("status") != "ok":
            continue
        bid = int(row["buildingPoiId"])
        by_id[bid] = {
            "buildingPoiId": bid,
            "name": row.get("name") or "",
            "areaId": row.get("areaId"),
            "completenessScore": 1.0,
        }
    manifest_path.write_text(json.dumps(list(by_id.values()), ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--building-poi-id", type=int, required=True)
    parser.add_argument("--area-id", type=int, required=True)
    parser.add_argument("--lat", type=float, required=True)
    parser.add_argument("--lng", type=float, required=True)
    parser.add_argument("--radius", type=int, default=80)
    parser.add_argument("--out-dir", type=Path, default=Path("src/main/resources/dev-seed/indoor"))
    parser.add_argument("--user-agent", default="BUPT-Travel-IndoorSeed/1.0")
    parser.add_argument(
        "--from-overpass-json",
        type=Path,
        default=None,
        help="Optional local raw/overpass.json to avoid network (uses new raw parser)",
    )
    args = parser.parse_args()

    elements: Optional[List[Dict[str, Any]]] = None
    if args.from_overpass_json and args.from_overpass_json.exists():
        raw = json.loads(args.from_overpass_json.read_text(encoding="utf-8"))
        elements = raw.get("elements", [])

    row = collect_indoor_for_poi(
        args.building_poi_id,
        args.area_id,
        args.lat,
        args.lng,
        radius=args.radius,
        out_dir=args.out_dir,
        user_agent=args.user_agent,
        overpass_elements=elements,
    )
    if row["status"] == "ok":
        update_indoor_manifest(args.out_dir, [row])
        print(f"Wrote {row['outputPath']} nodes={row.get('nodeCount')} edges={row.get('edgeCount')}")
        return 0
    print(f"REJECT/ERROR building {args.building_poi_id}: {row.get('failures')}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
