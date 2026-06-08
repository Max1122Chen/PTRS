#!/usr/bin/env python3
"""Building footprint helpers for OSM outdoor/indoor attribution (R2/R3)."""
from __future__ import annotations

import math
import re
from typing import Any, Dict, List, Optional, Set, Tuple

OsmKey = Tuple[str, int]


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


def is_building_way(el: Dict[str, Any], tags: Dict[str, object]) -> bool:
    if str(el.get("type", "")).lower() != "way":
        return False
    building = str(tags.get("building", "")).strip().lower()
    return bool(building) and building not in {"no", "false", "0"}


def registry_key(osm_type: str, osm_id: int) -> OsmKey:
    return str(osm_type).lower(), int(osm_id)


def poi_osm_ref(poi_row: Dict[str, Any]) -> Optional[OsmKey]:
    osm_type = poi_row.get("osmType")
    osm_id = poi_row.get("osmId")
    if osm_type and osm_id is not None:
        try:
            return registry_key(str(osm_type), int(osm_id))
        except (TypeError, ValueError):
            pass
    desc = str(poi_row.get("description") or "")
    m = re.search(r"OSM source=(\w+):(\d+)", desc)
    if m:
        return registry_key(m.group(1), int(m.group(2)))
    return None


def polygon_area_sq_deg(polygon: List[List[float]]) -> float:
    if len(polygon) < 3:
        return float("inf")
    area = 0.0
    for i in range(len(polygon)):
        x1, y1 = polygon[i][0], polygon[i][1]
        x2, y2 = polygon[(i + 1) % len(polygon)][0], polygon[(i + 1) % len(polygon)][1]
        area += x1 * y2 - x2 * y1
    return abs(area) / 2.0


def _deg_delta_to_meters(lon: float, lat: float, d_lon: float, d_lat: float) -> float:
    dx = d_lon * 111320.0 * math.cos(math.radians(lat))
    dy = d_lat * 110540.0
    return math.hypot(dx, dy)


def _point_to_segment_dist_deg(px: float, py: float, x1: float, y1: float, x2: float, y2: float) -> float:
    dx, dy = x2 - x1, y2 - y1
    if abs(dx) < 1e-15 and abs(dy) < 1e-15:
        return math.hypot(px - x1, py - y1)
    t = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)))
    cx, cy = x1 + t * dx, y1 + t * dy
    return math.hypot(px - cx, py - cy)


def distance_point_to_polygon_m(lon: float, lat: float, polygon: List[List[float]]) -> float:
    if len(polygon) < 2:
        return float("inf")
    best_deg = float("inf")
    for i in range(len(polygon)):
        x1, y1 = polygon[i][0], polygon[i][1]
        x2, y2 = polygon[(i + 1) % len(polygon)][0], polygon[(i + 1) % len(polygon)][1]
        best_deg = min(best_deg, _point_to_segment_dist_deg(lon, lat, x1, y1, x2, y2))
    return _deg_delta_to_meters(lon, lat, best_deg, 0.0)


def point_in_polygon(lon: float, lat: float, polygon: List[List[float]]) -> bool:
    if len(polygon) < 3:
        return False
    inside = False
    j = len(polygon) - 1
    for i in range(len(polygon)):
        xi, yi = polygon[i][0], polygon[i][1]
        xj, yj = polygon[j][0], polygon[j][1]
        intersects = (yi > lat) != (yj > lat)
        if intersects:
            x_cross = (xj - xi) * (lat - yi) / (yj - yi + 1e-15) + xi
            if lon < x_cross:
                inside = not inside
        j = i
    return inside


def extract_way_polygon(el: Dict[str, Any]) -> Optional[List[List[float]]]:
    geometry = el.get("geometry")
    if not isinstance(geometry, list) or len(geometry) < 3:
        return None
    polygon: List[List[float]] = []
    for p in geometry:
        if not isinstance(p, dict) or "lon" not in p or "lat" not in p:
            continue
        polygon.append([float(p["lon"]), float(p["lat"])])
    return polygon if len(polygon) >= 3 else None


def polygon_bbox(polygon: List[List[float]]) -> List[float]:
    lngs = [p[0] for p in polygon]
    lats = [p[1] for p in polygon]
    return [min(lngs), min(lats), max(lngs), max(lats)]


def pick_name(tags: Dict[str, object], fallback: str) -> str:
    for key in ("name:zh", "name", "official_name", "alt_name"):
        val = tags.get(key)
        if isinstance(val, str) and val.strip():
            return val.strip()
    return fallback


def build_building_registry(elements: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    registry: List[Dict[str, Any]] = []
    for el in elements:
        if not isinstance(el, dict):
            continue
        tags = el.get("tags") or {}
        if not isinstance(tags, dict) or not is_building_way(el, tags):
            continue
        polygon = extract_way_polygon(el)
        if not polygon:
            continue
        osm_id = el.get("id")
        osm_type = str(el.get("type", "way"))
        if osm_id is None:
            continue
        centroid = element_center(el)
        entry: Dict[str, Any] = {
            "osmType": osm_type,
            "osmId": int(osm_id),
            "name": pick_name(tags, f"building-{osm_id}"),
            "buildingTag": str(tags.get("building", "")),
            "polygon": polygon,
            "bbox": polygon_bbox(polygon),
        }
        if centroid:
            entry["centroid"] = [centroid[0], centroid[1]]
        registry.append(entry)
    registry.sort(key=lambda r: (str(r.get("name", "")), int(r["osmId"])))
    return registry


def is_indoor_related_element(el: Dict[str, Any], tags: Dict[str, object]) -> bool:
    indoor = str(tags.get("indoor", "")).strip().lower()
    hw = str(tags.get("highway", "")).strip().lower()
    if indoor in {"room", "door", "corridor", "area"}:
        return True
    if str(el.get("type", "")).lower() == "node" and indoor in {"room", "door"}:
        return True
    if hw in {"corridor", "elevator", "steps"} and (indoor == "yes" or indoor in {"room", "corridor"}):
        return True
    if hw == "footway" and str(tags.get("indoor", "")).lower() == "yes":
        return True
    return False


def sample_points_for_element(el: Dict[str, Any]) -> List[Tuple[float, float]]:
    points: List[Tuple[float, float]] = []
    if str(el.get("type", "")).lower() == "node" and "lat" in el and "lon" in el:
        points.append((float(el["lon"]), float(el["lat"])))
        return points
    geometry = el.get("geometry")
    if isinstance(geometry, list):
        for p in geometry:
            if isinstance(p, dict) and "lon" in p and "lat" in p:
                points.append((float(p["lon"]), float(p["lat"])))
    center = element_center(el)
    if center and center not in points:
        points.append(center)
    return points


def assign_element_to_building(
    el: Dict[str, Any],
    registry: List[Dict[str, Any]],
) -> Optional[OsmKey]:
    points = sample_points_for_element(el)
    if not points:
        return None
    hits: List[Tuple[float, OsmKey]] = []
    for entry in registry:
        polygon = entry.get("polygon")
        if not isinstance(polygon, list):
            continue
        key = registry_key(str(entry["osmType"]), int(entry["osmId"]))
        for lon, lat in points:
            if point_in_polygon(lon, lat, polygon):
                hits.append((polygon_area_sq_deg(polygon), key))
                break
    if not hits:
        return None
    hits.sort(key=lambda t: t[0])
    return hits[0][1]


def element_osm_key(el: Dict[str, Any]) -> Tuple[str, int]:
    return str(el.get("type", "x")), int(el.get("id", 0))


def element_matches_building(
    el: Dict[str, Any],
    building_entry: Dict[str, Any],
    *,
    buffer_m: float = 0.0,
) -> bool:
    polygon = building_entry.get("polygon")
    if not isinstance(polygon, list):
        return False
    for lon, lat in sample_points_for_element(el):
        if point_in_polygon(lon, lat, polygon):
            return True
        if buffer_m > 0 and distance_point_to_polygon_m(lon, lat, polygon) <= buffer_m:
            return True
    return False


def subset_indoor_for_building(
    elements: List[Dict[str, Any]],
    building_entry: Dict[str, Any],
    *,
    buffer_m: float = 0.0,
) -> List[Dict[str, Any]]:
    out: List[Dict[str, Any]] = []
    for el in elements:
        if not isinstance(el, dict):
            continue
        tags = el.get("tags") or {}
        if not isinstance(tags, dict) or not is_indoor_related_element(el, tags):
            continue
        if element_matches_building(el, building_entry, buffer_m=buffer_m):
            out.append(el)
    return out


def min_distance_to_building_m(el: Dict[str, Any], building_entry: Dict[str, Any]) -> float:
    polygon = building_entry.get("polygon")
    if not isinstance(polygon, list):
        return float("inf")
    best = float("inf")
    for lon, lat in sample_points_for_element(el):
        if point_in_polygon(lon, lat, polygon):
            return 0.0
        best = min(best, distance_point_to_polygon_m(lon, lat, polygon))
    return best


def assign_indoor_to_nearest_building(
    elements: List[Dict[str, Any]],
    registry: List[Dict[str, Any]],
    *,
    buffer_m: float = 0.0,
) -> Dict[OsmKey, List[Dict[str, Any]]]:
    assignments: Dict[OsmKey, List[Dict[str, Any]]] = {}
    for el in elements:
        if not isinstance(el, dict):
            continue
        tags = el.get("tags") or {}
        if not isinstance(tags, dict) or not is_indoor_related_element(el, tags):
            continue
        best_key: Optional[OsmKey] = None
        best_dist = float("inf")
        for entry in registry:
            dist = min_distance_to_building_m(el, entry)
            if dist < best_dist:
                best_dist = dist
                best_key = registry_key(str(entry["osmType"]), int(entry["osmId"]))
        if best_key is None or best_dist > buffer_m:
            continue
        assignments.setdefault(best_key, []).append(el)
    return assignments


def assign_indoor_elements_to_buildings(
    elements: List[Dict[str, Any]],
    registry: List[Dict[str, Any]],
) -> Dict[OsmKey, List[Dict[str, Any]]]:
    assignments: Dict[OsmKey, List[Dict[str, Any]]] = {}
    for el in elements:
        if not isinstance(el, dict):
            continue
        tags = el.get("tags") or {}
        if not isinstance(tags, dict) or not is_indoor_related_element(el, tags):
            continue
        key = assign_element_to_building(el, registry)
        if key is None:
            continue
        assignments.setdefault(key, []).append(el)
    return assignments


def filter_indoor_pool(elements: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    out: List[Dict[str, Any]] = []
    for el in elements:
        if not isinstance(el, dict):
            continue
        tags = el.get("tags") or {}
        if isinstance(tags, dict) and is_indoor_related_element(el, tags):
            out.append(el)
    return out


def load_building_registry(path) -> List[Dict[str, Any]]:
    import json
    from pathlib import Path

    p = Path(path)
    if not p.exists():
        return []
    data = json.loads(p.read_text(encoding="utf-8"))
    if isinstance(data, list):
        return [row for row in data if isinstance(row, dict)]
    return []
