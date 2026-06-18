#!/usr/bin/env python3
"""Expand diaries to 100+ via alias scenic destinations (same pattern as scenic-area-aliases)."""
from __future__ import annotations

import json
import random
from datetime import datetime
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SEED = REPO / "src/main/resources/dev-seed"
MAP_IMPORTS = SEED / "map-imports.json"
NOW = datetime.now().replace(microsecond=0).isoformat()

DIARY_ALIAS_ID_START = 2101
DIARY_DEST_ID_START = 3101
TARGET_TOTAL_DIARIES = 120
COMMENT_SNIPPETS = [
    "路线描述清晰，值得收藏。",
    "别名导览视角很新颖，推荐。",
    "内容和景区匹配度高。",
    "照片好看，想去同款打卡。",
    "设施与路线信息很实用。",
]


def classpath_to_path(cp: str) -> Path:
    return REPO / "src/main/resources" / cp.removeprefix("classpath:")


def load_canonical_names() -> dict[int, str]:
    cfg = json.loads(MAP_IMPORTS.read_text(encoding="utf-8"))
    names: dict[int, str] = {}
    for cp in cfg.get("scenicAreas", []):
        rows = json.loads(classpath_to_path(cp).read_text(encoding="utf-8"))
        if rows:
            row = rows[0]
            names[int(row["id"])] = str(row.get("name") or f"景区{row['id']}")
    return names


def strip_alias_rows(rows: list[dict], id_key: str, start: int) -> list[dict]:
    return [r for r in rows if int(r.get(id_key, 0)) < start]


def main() -> int:
    random.seed(20260618)

    base_diaries = json.loads((SEED / "diaries.json").read_text(encoding="utf-8"))
    base_dests = json.loads((SEED / "diary_destinations.json").read_text(encoding="utf-8"))
    aliases = json.loads((SEED / "scenic-area-aliases.json").read_text(encoding="utf-8"))
    comments = json.loads((SEED / "comments.json").read_text(encoding="utf-8"))
    users = json.loads((SEED / "users.json").read_text(encoding="utf-8"))
    user_ids = [int(u["id"]) for u in users if u.get("role") == "USER"]

    canonical_names = load_canonical_names()
    diary_to_canonical = {int(d["diaryId"]): int(d["destinationId"]) for d in base_dests}

    aliases_by_canonical: dict[int, list[dict]] = {}
    for a in aliases:
        cid = int(a["canonicalAreaId"])
        aliases_by_canonical.setdefault(cid, []).append(a)

    # Keep only canonical seed diaries (id < DIARY_ALIAS_ID_START)
    diaries = strip_alias_rows(base_diaries, "id", DIARY_ALIAS_ID_START)
    diary_dests = strip_alias_rows(base_dests, "id", DIARY_DEST_ID_START)
    comments = [c for c in comments if not (c.get("targetType") == "diary" and int(c.get("targetId", 0)) >= DIARY_ALIAS_ID_START)]

    need = max(0, TARGET_TOTAL_DIARIES - len(diaries))
    per = (need // len(diaries)) + 1 if diaries else 0

    diary_id = DIARY_ALIAS_ID_START
    dest_id = DIARY_DEST_ID_START
    comment_id = max((int(c["id"]) for c in comments), default=4000) + 1

    for base in diaries:
        bid = int(base["id"])
        canonical = diary_to_canonical.get(bid)
        if canonical is None:
            continue
        pool = aliases_by_canonical.get(canonical, [])
        if not pool:
            continue
        random.shuffle(pool)
        canonical_name = canonical_names.get(canonical, "")
        count = min(per, len(pool), need - (diary_id - DIARY_ALIAS_ID_START))
        if count <= 0:
            break
        for alias in pool[:count]:
            alias_name = str(alias.get("name") or f"景区{alias['id']}")
            title = str(base.get("title") or "游记")
            content = str(base.get("content") or "")
            if canonical_name and canonical_name in title:
                title = title.replace(canonical_name, alias_name)
            else:
                title = f"{alias_name}·{title}"
            if canonical_name and canonical_name in content:
                content = content.replace(canonical_name, alias_name)
            else:
                content = f"在{alias_name}的记录：{content}"

            diaries.append({
                "id": diary_id,
                "userId": int(base.get("userId") or random.choice(user_ids)),
                "title": title,
                "content": content + f"（导览别名视角，与 {canonical_name or canonical} 共用地图数据）",
                "images": base.get("images") or "[]",
                "videos": base.get("videos") or "[]",
                "heat": max(30, int(base.get("heat") or 80) + random.randint(-25, 35)),
                "rating": round(min(5.0, max(3.2, float(base.get("rating") or 4.0) + random.uniform(-0.3, 0.3))), 1),
                "createTime": NOW,
                "updateTime": NOW,
            })
            diary_dests.append({
                "id": dest_id,
                "diaryId": diary_id,
                "destinationId": int(alias["id"]),
                "createTime": NOW,
            })
            if random.random() < 0.7:
                comments.append({
                    "id": comment_id,
                    "userId": random.choice(user_ids),
                    "targetId": diary_id,
                    "targetType": "diary",
                    "content": random.choice(COMMENT_SNIPPETS),
                    "rating": round(random.uniform(3.8, 5.0), 1),
                    "createTime": NOW,
                    "updateTime": NOW,
                })
                comment_id += 1
            diary_id += 1
            dest_id += 1
            if len(diaries) >= TARGET_TOTAL_DIARIES:
                break
        if len(diaries) >= TARGET_TOTAL_DIARIES:
            break

    (SEED / "diaries.json").write_text(json.dumps(diaries, ensure_ascii=False, indent=2), encoding="utf-8")
    (SEED / "diary_destinations.json").write_text(json.dumps(diary_dests, ensure_ascii=False, indent=2), encoding="utf-8")
    (SEED / "comments.json").write_text(json.dumps(comments, ensure_ascii=False, indent=2), encoding="utf-8")

    alias_count = len(diaries) - len(strip_alias_rows(base_diaries, "id", DIARY_ALIAS_ID_START))
    print(f"canonical diaries: {len(strip_alias_rows(base_diaries, 'id', DIARY_ALIAS_ID_START))}")
    print(f"alias diaries added: {alias_count}")
    print(f"total diaries: {len(diaries)}")
    print(f"diary_destinations: {len(diary_dests)}")
    print(f"comments: {len(comments)}")
    return 0 if len(diaries) >= 100 else 1


if __name__ == "__main__":
    raise SystemExit(main())
