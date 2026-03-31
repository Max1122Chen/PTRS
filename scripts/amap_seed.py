#!/usr/bin/env python3
"""Generate review-only seed data from AMap Web API.

This script does NOT modify existing seed files. It outputs review JSON files
for manual review.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import os
import time
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.parse import urlencode
from urllib.request import urlopen


AMAP_BASE = "https://restapi.amap.com"
SEED_DIR = Path("src/main/resources/dev-seed")
DEFAULT_OUTPUT_DIR = Path("src/main/resources/amap-data")


def now_iso() -> str:
    return dt.datetime.now().replace(microsecond=0).isoformat()


def load_amap_key(repo_root: Path, cli_key: Optional[str]) -> str:
    if cli_key:
        return cli_key.strip()
    env_key = os.getenv("AMAP_KEY", "").strip()
    if env_key:
        return env_key

    cfg_file = repo_root / "scripts" / "config" / "amap_seed_config.json"
    if cfg_file.exists():
        try:
            cfg = json.loads(cfg_file.read_text(encoding="utf-8"))
            cfg_key = str(cfg.get("amap_web_key", "")).strip()
            if cfg_key:
                return cfg_key
        except Exception:
            pass

    env_file = repo_root / "frontend" / ".env"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            s = line.strip()
            if s.startswith("VITE_AMAP_KEY="):
                value = s.split("=", 1)[1].strip().strip('"').strip("'")
                if value:
                    return value
    raise RuntimeError(
        "AMAP Web key not found. Use --amap-key, AMAP_KEY env, or scripts/config/amap_seed_config.json(amap_web_key)."
    )


class AMapClient:
    def __init__(self, key: str, request_interval_s: float = 0.25, max_retry: int = 4) -> None:
        self.key = key
        self.request_interval_s = max(0.0, request_interval_s)
        self.max_retry = max(0, max_retry)
        self._last_request_at = 0.0

    def _wait_interval(self) -> None:
        if self.request_interval_s <= 0:
            return
        now = time.monotonic()
        wait_s = self.request_interval_s - (now - self._last_request_at)
        if wait_s > 0:
            time.sleep(wait_s)

    def get(self, path: str, params: Dict[str, object]) -> Dict[str, object]:
        all_params = {"key": self.key}
        all_params.update(params)
        url = f"{AMAP_BASE}{path}?{urlencode(all_params)}"
        last_info = "unknown error"
        for attempt in range(self.max_retry + 1):
            self._wait_interval()
            with urlopen(url, timeout=20) as resp:
                body = resp.read().decode("utf-8", errors="replace")
            self._last_request_at = time.monotonic()
            data = json.loads(body)
            if data.get("status") == "1":
                return data

            info = str(data.get("info", "unknown error"))
            last_info = info
            if "CUQPS_HAS_EXCEEDED_THE_LIMIT" in info and attempt < self.max_retry:
                time.sleep(min(5.0, 0.6 * (attempt + 1)))
                continue
            raise RuntimeError(f"AMap API error at {path}: {info}")

        raise RuntimeError(f"AMap API error at {path}: {last_info}")

    def text_search(self, keywords: str, city: str, page: int = 1, offset: int = 20) -> Dict[str, object]:
        return self.get(
            "/v3/place/text",
            {
                "keywords": keywords,
                "city": city,
                "citylimit": "true",
                "extensions": "base",
                "offset": offset,
                "page": page,
            },
        )

    def around_search(
        self,
        location: str,
        keywords: str,
        radius: int,
        page: int = 1,
        offset: int = 20,
    ) -> Dict[str, object]:
        return self.get(
            "/v3/place/around",
            {
                "location": location,
                "keywords": keywords,
                "radius": radius,
                "sortrule": "distance",
                "extensions": "base",
                "offset": offset,
                "page": page,
            },
        )

    def walking_route(self, origin: str, destination: str) -> Dict[str, object]:
        return self.get(
            "/v3/direction/walking",
            {
                "origin": origin,
                "destination": destination,
            },
        )


def parse_location(loc: str) -> Optional[Tuple[float, float]]:
    if not loc or "," not in loc:
        return None
    a, b = loc.split(",", 1)
    try:
        return float(a), float(b)
    except ValueError:
        return None


def read_max_id(name: str) -> int:
    path = SEED_DIR / f"{name}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    return max(int(x.get("id", 0)) for x in data)


def select_campus(pois: List[Dict[str, object]], target_name: str) -> Dict[str, object]:
    def score(item: Dict[str, object]) -> int:
        name = str(item.get("name", ""))
        ptype = str(item.get("type", ""))
        score_val = 0
        if target_name in name:
            score_val += 50
        if "执信" in name:
            score_val += 20
        if "校区" in name:
            score_val += 10
        if ptype == "学校":
            score_val += 10
        if item.get("location"):
            score_val += 5
        return score_val

    ranked = sorted(pois, key=score, reverse=True)
    if not ranked:
        raise RuntimeError("No candidate campus found from AMap text search.")
    campus = ranked[0]
    if not campus.get("location"):
        raise RuntimeError("Top campus candidate has no location.")
    return campus


def distance_m(lng1: float, lat1: float, lng2: float, lat2: float) -> float:
    # Haversine
    r = 6371000.0
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def dedup_points(items: List[Dict[str, object]]) -> List[Dict[str, object]]:
    seen = set()
    out = []
    for it in items:
        loc = parse_location(str(it.get("location", "")))
        if not loc:
            continue
        key = (str(it.get("name", "")).strip(), round(loc[0], 6), round(loc[1], 6))
        if key in seen:
            continue
        seen.add(key)
        out.append(it)
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate review-only data from AMap.")
    parser.add_argument("--target-name", default="广州市执信中学（执信南路校区）")
    parser.add_argument("--search-keyword", default="广州市执信中学 执信路校区")
    parser.add_argument("--city", default="广州")
    parser.add_argument("--radius", type=int, default=700)
    parser.add_argument("--pages", type=int, default=2)
    parser.add_argument("--offset", type=int, default=20)
    parser.add_argument("--amap-key", default="")
    parser.add_argument("--config", default="scripts/config/amap_seed_config.json")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--request-interval", type=float, default=0.35)
    parser.add_argument("--max-retry", type=int, default=5)
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    os.chdir(repo_root)

    key = ""
    if args.amap_key:
        key = args.amap_key.strip()
    elif os.getenv("AMAP_KEY", "").strip():
        key = os.getenv("AMAP_KEY", "").strip()
    else:
        cfg_path = Path(args.config)
        if not cfg_path.is_absolute():
            cfg_path = repo_root / cfg_path
        if cfg_path.exists():
            cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
            key = str(cfg.get("amap_web_key", "")).strip()
            if not key:
                raise RuntimeError(
                    f"Config file exists but amap_web_key is empty: {cfg_path}. Please fill an AMap Web Service key."
                )
        if not key:
            key = load_amap_key(repo_root, args.amap_key)
    client = AMapClient(key, request_interval_s=args.request_interval, max_retry=args.max_retry)

    try:
        text_raw = client.text_search(args.search_keyword, args.city, page=1, offset=30)
    except RuntimeError as exc:
        msg = str(exc)
        if "USERKEY_PLAT_NOMATCH" in msg:
            raise RuntimeError(
                "Configured key is not a Web Service key (USERKEY_PLAT_NOMATCH). "
                "Please use an AMap Web Service key in scripts/config/amap_seed_config.json -> amap_web_key."
            ) from exc
        raise
    text_pois = text_raw.get("pois", [])
    if not isinstance(text_pois, list) or not text_pois:
        raise RuntimeError("AMap text search returned no POIs.")

    campus = select_campus(text_pois, args.target_name)
    campus_loc = parse_location(str(campus.get("location", "")))
    if not campus_loc:
        raise RuntimeError("Campus location parse failed.")

    poi_keywords = [
        ("校门", "gate"),
        ("教学楼", "teaching"),
        ("图书馆", "library"),
        ("实验楼", "lab"),
        ("食堂", "canteen"),
        ("体育馆", "service"),
        ("行政楼", "service"),
        ("广场", "scenic_spot"),
        ("步道", "scenic_spot"),
        ("纪念", "scenic_spot"),
    ]

    fac_keywords = [
        ("卫生间", "toilet"),
        ("医务室", "hospital"),
        ("服务中心", "service"),
        ("打印", "printer"),
        ("单车", "bike"),
    ]

    around_raw: Dict[str, List[Dict[str, object]]] = {}
    poi_candidates: List[Dict[str, object]] = []
    fac_candidates: List[Dict[str, object]] = []

    center = f"{campus_loc[0]},{campus_loc[1]}"
    for keyword, poi_type in poi_keywords:
        for page in range(1, args.pages + 1):
            resp = client.around_search(center, keyword, args.radius, page=page, offset=args.offset)
            items = resp.get("pois", [])
            around_raw.setdefault(f"poi:{keyword}", []).extend(items if isinstance(items, list) else [])
            if not isinstance(items, list):
                continue
            for p in items:
                loc = parse_location(str(p.get("location", "")))
                if not loc:
                    continue
                d = distance_m(campus_loc[0], campus_loc[1], loc[0], loc[1])
                if d > args.radius * 1.15:
                    continue
                poi_candidates.append(
                    {
                        "name": p.get("name", ""),
                        "location": p.get("location", ""),
                        "address": p.get("address", ""),
                        "distance": p.get("distance", ""),
                        "amap_type": p.get("type", ""),
                        "mapped_type": poi_type,
                        "source_keyword": keyword,
                    }
                )

    for keyword, fac_type in fac_keywords:
        for page in range(1, args.pages + 1):
            resp = client.around_search(center, keyword, args.radius, page=page, offset=args.offset)
            items = resp.get("pois", [])
            around_raw.setdefault(f"facility:{keyword}", []).extend(items if isinstance(items, list) else [])
            if not isinstance(items, list):
                continue
            for p in items:
                loc = parse_location(str(p.get("location", "")))
                if not loc:
                    continue
                d = distance_m(campus_loc[0], campus_loc[1], loc[0], loc[1])
                if d > args.radius * 1.15:
                    continue
                fac_candidates.append(
                    {
                        "name": p.get("name", ""),
                        "location": p.get("location", ""),
                        "address": p.get("address", ""),
                        "distance": p.get("distance", ""),
                        "amap_type": p.get("type", ""),
                        "mapped_type": fac_type,
                        "source_keyword": keyword,
                    }
                )

    poi_clean = dedup_points(poi_candidates)
    fac_clean = dedup_points(fac_candidates)

    if len(poi_clean) < 4:
        raise RuntimeError("Too few POI candidates collected. Adjust keywords/radius/pages.")

    scenic_id = read_max_id("scenic_areas") + 1
    poi_id = read_max_id("buildings") + 1
    fac_id = read_max_id("facilities") + 1
    road_id = read_max_id("roads") + 1

    ts = now_iso()

    scenic_area = {
        "id": scenic_id,
        "name": args.target_name,
        "description": "Generated from AMap API (review required).",
        "location": str(campus.get("address", "")) or str(campus.get("name", "")),
        "longitude": round(campus_loc[0], 6),
        "latitude": round(campus_loc[1], 6),
        "type": "campus",
        "rating": 0.0,
        "heat": 0,
        "openTime": "待核实",
        "ticketPrice": "待核实",
        "createTime": ts,
        "updateTime": ts,
    }

    poi_rows = []
    for item in poi_clean[:16]:
        loc = parse_location(str(item.get("location", "")))
        if not loc:
            continue
        poi_rows.append(
            {
                "id": poi_id,
                "name": str(item.get("name", "")).strip() or f"POI-{poi_id}",
                "type": str(item.get("mapped_type", "service")),
                "description": f"AMap keyword={item.get('source_keyword', '')}, review required",
                "location": str(item.get("address", "")).strip() or str(item.get("name", "")).strip(),
                "longitude": round(loc[0], 6),
                "latitude": round(loc[1], 6),
                "parentId": None,
                "areaId": scenic_id,
                "createTime": ts,
                "updateTime": ts,
            }
        )
        poi_id += 1

    fac_rows = []
    for item in fac_clean[:12]:
        loc = parse_location(str(item.get("location", "")))
        if not loc:
            continue
        fac_rows.append(
            {
                "id": fac_id,
                "name": str(item.get("name", "")).strip() or f"FAC-{fac_id}",
                "type": str(item.get("mapped_type", "service")),
                "description": f"AMap keyword={item.get('source_keyword', '')}, review required",
                "location": str(item.get("address", "")).strip() or str(item.get("name", "")).strip(),
                "longitude": round(loc[0], 6),
                "latitude": round(loc[1], 6),
                "areaId": scenic_id,
                "createTime": ts,
                "updateTime": ts,
            }
        )
        fac_id += 1

    # Build roads from walking routes on first 8 POIs (sequential chain).
    walking_raw = []
    roads = []
    chain = poi_rows[:8]
    for i in range(len(chain) - 1):
        a = chain[i]
        b = chain[i + 1]
        origin = f"{a['longitude']},{a['latitude']}"
        destination = f"{b['longitude']},{b['latitude']}"
        try:
            wr = client.walking_route(origin, destination)
            walking_raw.append({"origin": origin, "destination": destination, "raw": wr})
            paths = wr.get("route", {}).get("paths", []) if isinstance(wr.get("route", {}), dict) else []
            if not paths:
                continue
            dist = float(paths[0].get("distance", 0))
            if dist <= 0:
                continue
            roads.append(
                {
                    "id": road_id,
                    "startId": a["id"],
                    "endId": b["id"],
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
        except Exception as exc:  # noqa: BLE001
            walking_raw.append({"origin": origin, "destination": destination, "error": str(exc)})

    out_root = Path(args.output_dir)
    stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    out_dir = out_root / f"amap_zhixin_review_{stamp}"
    raw_dir = out_dir / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)

    (out_dir / "scenic_areas.append.json").write_text(
        json.dumps([scenic_area], ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (out_dir / "buildings.append.json").write_text(
        json.dumps(poi_rows, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (out_dir / "facilities.append.json").write_text(
        json.dumps(fac_rows, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (out_dir / "roads.append.json").write_text(
        json.dumps(roads, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    (raw_dir / "text_search.json").write_text(json.dumps(text_raw, ensure_ascii=False, indent=2), encoding="utf-8")
    (raw_dir / "around_search.json").write_text(json.dumps(around_raw, ensure_ascii=False, indent=2), encoding="utf-8")
    (raw_dir / "walking_route.json").write_text(json.dumps(walking_raw, ensure_ascii=False, indent=2), encoding="utf-8")

    report = [
        "# AMap Data Report",
        "",
        f"- generatedAt: {ts}",
        f"- targetName: {args.target_name}",
        f"- city: {args.city}",
        f"- center: {campus_loc[0]:.6f},{campus_loc[1]:.6f}",
        f"- scenicCount: 1",
        f"- poiCount: {len(poi_rows)}",
        f"- facilityCount: {len(fac_rows)}",
        f"- roadCount: {len(roads)}",
        "",
        "## Notes",
        "- This folder is review-only output.",
        "- Existing seed data is unchanged.",
        "- Fields like rating/heat/openTime/ticketPrice are placeholders and require manual confirmation.",
    ]
    (out_dir / "report.md").write_text("\n".join(report), encoding="utf-8")

    print(f"Output written to: {out_dir}")
    print(f"POI={len(poi_rows)}, Facilities={len(fac_rows)}, Roads={len(roads)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
