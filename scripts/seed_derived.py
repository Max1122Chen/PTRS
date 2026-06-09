#!/usr/bin/env python3
"""Generate dev-seed derived data (R9): restaurants, foods, diaries, comments, scenic metadata."""
from __future__ import annotations

import json
import random
import re
from datetime import datetime
from pathlib import Path
from typing import Any

REPO = Path(__file__).resolve().parents[1]
SEED = REPO / "src/main/resources/dev-seed"
MAP_IMPORTS = SEED / "map-imports.json"
NOW = datetime.now().replace(microsecond=0).isoformat()

TAG_NAME_TO_ID = {
    "nature": 301,
    "campus": 302,
    "history": 303,
    "museum": 304,
    "culture": 305,
    "photo": 306,
    "walk": 307,
    "food": 308,
    "art": 309,
    "science": 310,
    "night": 311,
    "architecture": 312,
    "lake": 313,
}

FOOD_KEYWORDS = ("食堂", "餐厅", "饭店", "咖啡", "快餐", "美食", "餐饮", "食阁", "食街", "面馆", "火锅", "小吃", "奶茶", "烘焙", "西餐", "日料")
FOOD_POI_TYPES = {"restaurant", "shop", "service"}
FOOD_FACILITY_TYPES = {"restaurant", "canteen", "fast_food", "cafe", "food_court"}

BUPT_AREA_IDS = {252, 261}

AREA_META: dict[int, dict[str, Any]] = {
    252: {
        "type": "campus",
        "tags": ["campus", "science", "food", "walk"],
        "description": "北京邮电大学沙河校区，智慧校园示范区，教学楼与食堂分布紧凑，适合路线与设施演示。",
        "openTime": "全天开放（教学区按课表）",
        "ticketPrice": "免费",
    },
    253: {
        "type": "campus",
        "tags": ["campus", "history", "culture"],
        "description": "广州市执信中学执信路校区，百年名校，红砖校舍与运动场相映，适合校园导览体验。",
        "openTime": "工作日 8:00-18:00",
        "ticketPrice": "免费",
    },
    254: {
        "type": "campus",
        "tags": ["campus", "culture", "science"],
        "description": "贵阳一中观山湖校区，现代化寄宿制中学，绿化充足，适合校园设施与路线规划演示。",
        "openTime": "工作日 8:00-18:00",
        "ticketPrice": "免费",
    },
    256: {
        "type": "scenic",
        "tags": ["history", "culture", "museum", "architecture"],
        "description": "故宫博物院，明清皇家宫殿建筑群，中轴线宫殿与珍宝馆闻名中外。",
        "openTime": "8:30-17:00（周一闭馆）",
        "ticketPrice": "60元",
    },
    257: {
        "type": "scenic",
        "tags": ["history", "culture", "architecture", "photo"],
        "description": "天坛公园，明清皇帝祭天场所，祈年殿与回音壁是标志性景观。",
        "openTime": "6:00-22:00",
        "ticketPrice": "15元起",
    },
    258: {
        "type": "scenic",
        "tags": ["history", "culture", "lake", "nature", "photo"],
        "description": "颐和园，皇家园林典范，昆明湖与万寿山相映，长廊佛香阁值得一游。",
        "openTime": "6:30-18:00",
        "ticketPrice": "30元",
    },
    259: {
        "type": "scenic",
        "tags": ["history", "culture", "lake", "nature"],
        "description": "圆明园遗址公园，中西合璧园林遗址，大水法与西洋楼遗迹见证历史。",
        "openTime": "7:00-19:00",
        "ticketPrice": "10元",
    },
    260: {
        "type": "scenic",
        "tags": ["history", "culture", "nature", "walk"],
        "description": "地坛公园，明清祭地场所，银杏大道与方泽坛是秋季打卡热点。",
        "openTime": "6:00-21:00",
        "ticketPrice": "2元",
    },
    261: {
        "type": "campus",
        "tags": ["campus", "science", "food", "walk"],
        "description": "北京邮电大学西土城路校区，海淀核心区位，教学楼与食堂密集，适合美食与路线演示。",
        "openTime": "全天开放（教学区按课表）",
        "ticketPrice": "免费",
    },
    262: {
        "type": "mall",
        "tags": ["culture", "food", "night", "photo"],
        "description": "广州正佳广场，天河商圈大型购物中心，餐饮品牌集中，适合美食推荐演示。",
        "openTime": "10:00-22:00",
        "ticketPrice": "免费",
    },
    263: {
        "type": "mall",
        "tags": ["culture", "art", "food"],
        "description": "广州动漫星城，地下动漫商业街区，二次元文化与小吃店铺林立。",
        "openTime": "10:00-21:30",
        "ticketPrice": "免费",
    },
    264: {
        "type": "mall",
        "tags": ["culture", "food", "architecture"],
        "description": "广州天环广场，珠江新城时尚地标，环形建筑与精品餐饮汇聚。",
        "openTime": "10:00-22:00",
        "ticketPrice": "免费",
    },
}

DISH_POOL = {
    "食堂": [
        "红烧肉盖饭", "麻辣香锅", "番茄鸡蛋面", "宫保鸡丁", "鱼香肉丝", "酸辣土豆丝", "紫菜蛋花汤",
        "扬州炒饭", "水饺", "牛肉面", "炸鸡排饭", "凉拌黄瓜", "豆浆", "包子", "煎饺", "烤红薯",
        "麻辣烫", "黄焖鸡米饭", "皮蛋瘦肉粥", "糖醋里脊",
    ],
    "餐厅": [
        "招牌烤鸭", "清蒸鲈鱼", "干煸四季豆", "蒜蓉西兰花", "小炒黄牛肉", "酸菜鱼", "葱爆羊肉",
        "白切鸡", "佛跳墙", "龙井虾仁",
    ],
    "咖啡": ["拿铁", "美式咖啡", "卡布奇诺", "摩卡", "抹茶拿铁", "焦糖玛奇朵", "热巧克力", "柠檬茶"],
    "快餐": ["鸡腿堡", "牛肉饭", "鸡米花", "薯条", "鸡翅套餐", "意面", "披萨角", "沙拉碗"],
    "小吃": ["章鱼小丸子", "烤肠", "鸡蛋仔", "糖葫芦", "钵钵鸡", "凉皮", "肉夹馍", "煎饼果子"],
    "西餐": ["牛排", "奶油蘑菇汤", "凯撒沙拉", "意大利面", "烤鸡翅", "芝士焗饭", "罗宋汤", "提拉米苏"],
    "default": ["特色套餐", "招牌小食", "时令菜品", "经典主食", "人气饮品", "甜品", "凉菜拼盘", "热汤"],
}

CUISINE_POOL = ["中餐", "川菜", "粤菜", "面食", "快餐", "咖啡", "轻食", "小吃", "西餐", "日料"]

DIARY_TEMPLATES = [
    ("{place}一日游", "今天逛了{place}，路线规划很顺，重点景点都打卡到了。"),
    ("{place}漫步记", "在{place}慢慢走了一圈，人少的时候体验更好，推荐工作日前往。"),
    ("{place}拍照攻略", "分享几个{place}出片机位，下午侧光最适合拍建筑。"),
    ("{place}美食探店", "在{place}附近吃了几家店，整体性价比不错。"),
    ("{place}周末随笔", "周末来{place}放松，设施查询和路线功能很好用。"),
]

COMMENT_DIARY = [
    "写得很详细，收藏了！",
    "路线描述清晰，对我很有帮助。",
    "照片机位实用，下次按这个走。",
    "攻略靠谱，已转发同学。",
    "内容真实，推荐阅读。",
    "节奏安排合理，不走冤枉路。",
]

COMMENT_SCENIC = [
    "风景很好，值得推荐。",
    "人文底蕴深厚，讲解也到位。",
    "交通便利，入园体验顺畅。",
    "适合周末放松散步。",
]

COMMENT_FOOD = [
    "味道不错，会再来。",
    "分量足，性价比高。",
    "口味正宗，服务也好。",
]


def classpath_to_path(cp: str) -> Path:
    return REPO / "src/main/resources" / cp.removeprefix("classpath:")


def is_food_poi(poi: dict) -> bool:
    name = str(poi.get("name") or "")
    typ = str(poi.get("type") or "").lower()
    if typ in FOOD_POI_TYPES and any(k in name for k in FOOD_KEYWORDS):
        return True
    if typ == "restaurant":
        return True
    if any(k in name for k in FOOD_KEYWORDS):
        return True
    return False


def is_food_facility(f: dict) -> bool:
    return str(f.get("type") or "").lower() in FOOD_FACILITY_TYPES


def pick_dish_pool(restaurant_name: str, is_canteen: bool) -> list[str]:
    if is_canteen:
        return DISH_POOL["食堂"]
    name = restaurant_name
    for key in ("咖啡", "快餐", "小吃", "西餐"):
        if key in name:
            return DISH_POOL[key]
    if "餐厅" in name or "饭店" in name:
        return DISH_POOL["餐厅"]
    return DISH_POOL["default"]


def collect_anchors(pois: list, facilities: list, area_id: int) -> list[dict]:
    anchors: list[dict] = []
    seen: set[str] = set()

    def add(name: str, lng: float, lat: float, location: str, is_canteen: bool) -> None:
        key = f"{name}|{lng}|{lat}"
        if key in seen:
            return
        seen.add(key)
        anchors.append({
            "name": name,
            "longitude": lng,
            "latitude": lat,
            "location": location or name,
            "areaId": area_id,
            "is_canteen": is_canteen,
        })

    for p in pois:
        if not is_food_poi(p):
            continue
        name = str(p.get("name") or "餐厅")
        is_canteen = "食堂" in name
        lng, lat = p.get("longitude"), p.get("latitude")
        if lng is None or lat is None:
            continue
        add(name, float(lng), float(lat), str(p.get("location") or name), is_canteen)

    for f in facilities:
        if not is_food_facility(f):
            continue
        name = str(f.get("name") or "餐饮点")
        is_canteen = "食堂" in name or str(f.get("type")) == "canteen"
        lng, lat = f.get("longitude"), f.get("latitude")
        if lng is None or lat is None:
            continue
        add(name, float(lng), float(lat), str(f.get("location") or name), is_canteen)

    return anchors


def synthetic_anchors(area_id: int, area_name: str, count: int) -> list[dict]:
    """Fallback when OSM has few dining POIs."""
    templates = [
        ("{base}游客餐厅", False),
        ("{base}快餐角", False),
        ("{base}小吃街", False),
        ("{base}景观咖啡", False),
        ("{base}食堂", True),
    ]
    base = re.sub(r"[（(].*[)）]", "", area_name).strip()[:6]
    out = []
    for i in range(count):
        tpl, is_canteen = templates[i % len(templates)]
        name = tpl.format(base=base)
        out.append({
            "name": name,
            "longitude": 116.3 + area_id * 0.001 + i * 0.0001,
            "latitude": 39.9 + area_id * 0.001 + i * 0.0001,
            "location": name,
            "areaId": area_id,
            "is_canteen": is_canteen,
        })
    return out


def rand_rating() -> float:
    return round(random.uniform(3.8, 4.9), 1)


def rand_heat() -> int:
    return random.randint(50, 500)


def main() -> int:
    random.seed(20260609)
    cfg = json.loads(MAP_IMPORTS.read_text(encoding="utf-8"))
    areas: list[dict] = []

    for cp in cfg["scenicAreas"]:
        path = classpath_to_path(cp)
        rows = json.loads(path.read_text(encoding="utf-8"))
        if rows:
            areas.append({"scenic_path": path, "scenic": rows[0], "pois_cp": None, "fac_cp": None})

    pois_cps = cfg.get("pois", [])
    fac_cps = cfg.get("facilities", [])
    for i, a in enumerate(areas):
        if i < len(pois_cps):
            a["pois_cp"] = pois_cps[i]
        if i < len(fac_cps):
            a["fac_cp"] = fac_cps[i]

    scenic_area_tags: list[dict] = []
    tag_row_id = 5001
    restaurants: list[dict] = []
    foods: list[dict] = []
    rest_id = 5001
    food_id = 6001

    for item in areas:
        scenic = item["scenic"]
        area_id = int(scenic["id"])
        meta = AREA_META.get(area_id, {
            "type": "scenic",
            "tags": ["culture", "photo"],
            "description": f"{scenic.get('name', '景区')}，适合导览与推荐演示。",
            "openTime": "9:00-18:00",
            "ticketPrice": "免费",
        })

        scenic["type"] = meta["type"]
        scenic["description"] = meta["description"]
        scenic["openTime"] = meta["openTime"]
        scenic["ticketPrice"] = meta["ticketPrice"]
        scenic["rating"] = rand_rating()
        scenic["heat"] = rand_heat()
        scenic["updateTime"] = NOW

        item["scenic_path"].write_text(json.dumps([scenic], ensure_ascii=False, indent=2), encoding="utf-8")

        for tag_name in meta["tags"]:
            tid = TAG_NAME_TO_ID.get(tag_name)
            if tid is None:
                continue
            scenic_area_tags.append({
                "id": tag_row_id,
                "scenicAreaId": area_id,
                "tagId": tid,
                "weight": round(random.uniform(0.7, 1.0), 2),
                "createTime": NOW,
            })
            tag_row_id += 1

        pois = []
        facilities = []
        if item.get("pois_cp"):
            pois = json.loads(classpath_to_path(item["pois_cp"]).read_text(encoding="utf-8"))
        if item.get("fac_cp"):
            facilities = json.loads(classpath_to_path(item["fac_cp"]).read_text(encoding="utf-8"))

        anchors = collect_anchors(pois, facilities, area_id)
        target_rest = random.randint(3, 6)
        if len(anchors) < target_rest:
            anchors.extend(synthetic_anchors(area_id, str(scenic.get("name") or ""), target_rest - len(anchors)))
        random.shuffle(anchors)
        anchors = anchors[:target_rest]

        is_bupt = area_id in BUPT_AREA_IDS
        for anc in anchors:
            restaurants.append({
                "id": rest_id,
                "name": anc["name"],
                "description": "校园食堂" if anc["is_canteen"] else "景区餐饮",
                "location": anc["location"],
                "longitude": anc["longitude"],
                "latitude": anc["latitude"],
                "areaId": area_id,
                "createTime": NOW,
                "updateTime": NOW,
            })
            pool = pick_dish_pool(anc["name"], anc["is_canteen"])
            if is_bupt and anc["is_canteen"]:
                dish_count = 20
            elif is_bupt:
                dish_count = random.randint(8, 12)
            else:
                dish_count = random.randint(5, 8)
            chosen = random.sample(pool, min(dish_count, len(pool)))
            while len(chosen) < dish_count:
                chosen.append(random.choice(pool))
            for dish_name in chosen[:dish_count]:
                foods.append({
                    "id": food_id,
                    "name": dish_name,
                    "cuisine": random.choice(CUISINE_POOL),
                    "description": f"{anc['name']}招牌菜品",
                    "price": round(random.uniform(8, 68), 2),
                    "rating": rand_rating(),
                    "heat": random.randint(30, 400),
                    "restaurantId": rest_id,
                    "areaId": area_id,
                    "createTime": NOW,
                    "updateTime": NOW,
                })
                food_id += 1
            rest_id += 1

    # Users: expand to 11 (10 USER + 1 ADMIN)
    users = json.loads((SEED / "users.json").read_text(encoding="utf-8"))
    existing_ids = {u["id"] for u in users}
    new_users = [
        (107, "dev_frank", "Frank"),
        (108, "dev_grace", "Grace"),
        (109, "dev_henry", "Henry"),
        (110, "dev_ivy", "Ivy"),
        (111, "dev_jack", "Jack"),
    ]
    for uid, uname, nick in new_users:
        if uid not in existing_ids:
            users.append({
                "id": uid,
                "username": uname,
                "password": "dev123456",
                "email": f"{uname}@example.com",
                "nickname": nick,
                "avatar": "",
                "role": "USER",
                "createTime": NOW,
                "updateTime": NOW,
            })

    user_ids = [u["id"] for u in users if u.get("role") == "USER"]
    interests = json.loads((SEED / "user_interests.json").read_text(encoding="utf-8"))
    interest_types = list(TAG_NAME_TO_ID.keys())[:10]
    next_iid = max((i["id"] for i in interests), default=1000) + 1
    for u in users:
        if u["id"] == 106:
            continue
        if any(i["userId"] == u["id"] for i in interests):
            if u["id"] >= 107:
                pass
            else:
                continue
        picks = random.sample(interest_types, 3)
        for j, it in enumerate(picks):
            interests.append({
                "id": next_iid,
                "userId": u["id"],
                "interestType": it,
                "weight": round(0.9 - j * 0.15, 2),
                "createTime": NOW,
            })
            next_iid += 1

    # Diaries
    diaries: list[dict] = []
    diary_dests: list[dict] = []
    diary_id = 2001
    dest_id = 3001
    area_list = [item["scenic"] for item in areas]
    for scenic in area_list:
        place = str(scenic.get("name") or "景区")
        area_id = int(scenic["id"])
        for title_tpl, content_tpl in random.sample(DIARY_TEMPLATES, min(2, len(DIARY_TEMPLATES))):
            diaries.append({
                "id": diary_id,
                "userId": random.choice(user_ids),
                "title": title_tpl.format(place=place),
                "content": content_tpl.format(place=place),
                "images": "[]",
                "videos": "[]",
                "heat": random.randint(40, 280),
                "rating": rand_rating(),
                "createTime": NOW,
                "updateTime": NOW,
            })
            diary_dests.append({
                "id": dest_id,
                "diaryId": diary_id,
                "destinationId": area_id,
                "createTime": NOW,
            })
            dest_id += 1
            diary_id += 1

    # Comments: diary ratings + some scenic/food
    comments: list[dict] = []
    comment_id = 4001
    for d in diaries:
        for _ in range(random.randint(1, 2)):
            comments.append({
                "id": comment_id,
                "userId": random.choice(user_ids),
                "targetId": d["id"],
                "targetType": "diary",
                "content": random.choice(COMMENT_DIARY),
                "rating": rand_rating(),
                "createTime": NOW,
                "updateTime": NOW,
            })
            comment_id += 1

    for scenic in random.sample(area_list, min(6, len(area_list))):
        comments.append({
            "id": comment_id,
            "userId": random.choice(user_ids),
            "targetId": int(scenic["id"]),
            "targetType": "scenic",
            "content": random.choice(COMMENT_SCENIC),
            "rating": rand_rating(),
            "createTime": NOW,
            "updateTime": NOW,
        })
        comment_id += 1

    for f in random.sample(foods, min(12, len(foods))):
        comments.append({
            "id": comment_id,
            "userId": random.choice(user_ids),
            "targetId": f["id"],
            "targetType": "food",
            "content": random.choice(COMMENT_FOOD),
            "rating": rand_rating(),
            "createTime": NOW,
            "updateTime": NOW,
        })
        comment_id += 1

    # Write seed files
    (SEED / "restaurants.json").write_text(json.dumps(restaurants, ensure_ascii=False, indent=2), encoding="utf-8")
    (SEED / "foods.json").write_text(json.dumps(foods, ensure_ascii=False, indent=2), encoding="utf-8")
    (SEED / "users.json").write_text(json.dumps(users, ensure_ascii=False, indent=2), encoding="utf-8")
    (SEED / "user_interests.json").write_text(json.dumps(interests, ensure_ascii=False, indent=2), encoding="utf-8")
    (SEED / "diaries.json").write_text(json.dumps(diaries, ensure_ascii=False, indent=2), encoding="utf-8")
    (SEED / "diary_destinations.json").write_text(json.dumps(diary_dests, ensure_ascii=False, indent=2), encoding="utf-8")
    (SEED / "comments.json").write_text(json.dumps(comments, ensure_ascii=False, indent=2), encoding="utf-8")
    (SEED / "scenic_area_tags.json").write_text(json.dumps(scenic_area_tags, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Scenic areas updated: {len(areas)}")
    print(f"Restaurants: {len(restaurants)}, Foods: {len(foods)}")
    print(f"Users: {len(users)}, Diaries: {len(diaries)}, Comments: {len(comments)}")
    print(f"Scenic area tags: {len(scenic_area_tags)}")
    bupt_foods = sum(1 for f in foods if f["areaId"] in BUPT_AREA_IDS)
    print(f"BUPT foods: {bupt_foods}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
