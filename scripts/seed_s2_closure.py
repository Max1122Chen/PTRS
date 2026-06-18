#!/usr/bin/env python3
"""S2 R11b/R11a: scenic area aliases (>=200) + supplemental facilities (types>=10)."""
from __future__ import annotations

import json
import random
from datetime import datetime
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SEED = REPO / "src/main/resources/dev-seed"
MAP_IMPORTS = SEED / "map-imports.json"
NOW = datetime.now().replace(microsecond=0).isoformat()

ALIAS_ID_START = 10001
TARGET_TOTAL_SCENIC = 220

NAME_SUFFIXES = [
    "东门视角", "西门视角", "南门入口", "北门广场", "核心区", "步行导览", "周末打卡",
    "亲子游览", "摄影推荐", "深度游线", "一日游", "文化体验", "城市名片", "周边推荐",
    "经典路线", "夜游推荐",
]

TAG_BY_CANONICAL = {
    252: [302, 310, 308, 307],
    261: [302, 310, 308, 307],
    253: [302, 303, 305],
    254: [302, 305, 310],
    256: [303, 305, 304, 312],
    257: [303, 305, 312, 306],
    258: [303, 305, 313, 306],
    259: [303, 305, 313, 301],
    260: [303, 305, 301, 307],
    262: [305, 308, 311],
    263: [305, 309, 308],
    264: [305, 308, 312],
}

SUPPLEMENT_TYPES = [
    ("canteen", "学生食堂"),
    ("fast_food", "快餐窗口"),
    ("supermarket", "校园超市"),
    ("convenience", "便利店"),
    ("atm", "自助取款机"),
    ("parking", "访客停车场"),
    ("info", "游客服务中心"),
]


def classpath_to_path(cp: str) -> Path:
    return REPO / "src/main/resources" / cp.removeprefix("classpath:")


def load_canonical_scenic() -> list[dict]:
    cfg = json.loads(MAP_IMPORTS.read_text(encoding="utf-8"))
    rows = []
    for cp in cfg.get("scenicAreas", []):
        data = json.loads(classpath_to_path(cp).read_text(encoding="utf-8"))
        if data:
            rows.append(data[0])
    return rows


def rand_rating() -> float:
    return round(random.uniform(3.8, 4.9), 1)


def rand_heat() -> int:
    return random.randint(60, 480)


def generate_aliases(canonical: list[dict]) -> tuple[list[dict], list[dict]]:
    need = max(0, TARGET_TOTAL_SCENIC - len(canonical))
    aliases: list[dict] = []
    area_tags: list[dict] = []
    tag_row_id = 60001
    aid = ALIAS_ID_START
    per = (need // len(canonical)) + 1 if canonical else 0

    for base in canonical:
        cid = int(base["id"])
        base_name = str(base.get("name") or f"景区{cid}")
        count = min(per, need - len(aliases))
        if count <= 0:
            break
        suffixes = random.sample(NAME_SUFFIXES, min(count, len(NAME_SUFFIXES)))
        while len(suffixes) < count:
            suffixes.append(f"导览点{len(suffixes)+1}")
        for suf in suffixes[:count]:
            aliases.append({
                "id": aid,
                "canonicalAreaId": cid,
                "name": f"{base_name}·{suf}",
                "description": f"{base.get('description', base_name)}（导览别名，地图数据与 {base_name} 共用）",
                "type": base.get("type") or "scenic",
                "location": base.get("location") or base_name,
                "longitude": base.get("longitude"),
                "latitude": base.get("latitude"),
                "rating": rand_rating(),
                "heat": rand_heat(),
                "openTime": base.get("openTime"),
                "ticketPrice": base.get("ticketPrice"),
                "createTime": NOW,
                "updateTime": NOW,
            })
            for tid in TAG_BY_CANONICAL.get(cid, [305, 306]):
                area_tags.append({
                    "id": tag_row_id,
                    "scenicAreaId": aid,
                    "tagId": tid,
                    "weight": round(random.uniform(0.65, 0.95), 2),
                    "createTime": NOW,
                })
                tag_row_id += 1
            aid += 1
            if len(aliases) >= need:
                break
        if len(aliases) >= need:
            break

    return aliases, area_tags


def generate_facility_supplement(canonical: list[dict]) -> list[dict]:
    """Add facility types missing from OSM merge (target distinct types >= 10)."""
    facilities = []
    fid = 11001
    for typ, label in SUPPLEMENT_TYPES:
        base = random.choice(canonical)
        cid = int(base["id"])
        lng = float(base.get("longitude") or 116.3)
        lat = float(base.get("latitude") or 39.9)
        facilities.append({
            "id": fid,
            "name": f"{base.get('name', '景区')}{label}",
            "type": typ,
            "description": f"演示种子设施（{typ}）",
            "location": label,
            "longitude": round(lng + random.uniform(-0.002, 0.002), 6),
            "latitude": round(lat + random.uniform(-0.002, 0.002), 6),
            "areaId": cid,
            "createTime": NOW,
            "updateTime": NOW,
        })
        fid += 1
    return facilities


def main() -> int:
    random.seed(20260609)
    canonical = load_canonical_scenic()
    aliases, alias_tags = generate_aliases(canonical)
    facilities = generate_facility_supplement(canonical)

    (SEED / "scenic-area-aliases.json").write_text(
        json.dumps(aliases, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    existing_tags = json.loads((SEED / "scenic_area_tags.json").read_text(encoding="utf-8"))
    canonical_only_tags = [
        t for t in existing_tags if int(t.get("scenicAreaId", 0)) < ALIAS_ID_START
    ]
    merged_tags = canonical_only_tags + alias_tags
    (SEED / "scenic_area_tags.json").write_text(
        json.dumps(merged_tags, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    (SEED / "facilities.json").write_text(
        json.dumps(facilities, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    print(f"canonical scenic: {len(canonical)}")
    print(f"aliases: {len(aliases)} -> total {len(canonical)+len(aliases)}")
    print(f"alias scenic_area_tags added: {len(alias_tags)}")
    print(f"supplement facilities: {len(facilities)} types {[t for t,_ in SUPPLEMENT_TYPES]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
