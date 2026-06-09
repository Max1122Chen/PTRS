# S2 展示数据治理 — 执行计划（负责人拍板 v2）

> **状态**：R1–R6（脚本重构 + 沙河单包 + dev-seed 瘦身）已完成；**R7–R12** 按本文推进。  
> **负责人**：Max1122Chen  
> **最后更新**：2026-06-08

---

## 1. 目标摘要

| 维度 | 决策 |
|------|------|
| **真源** | `osm-data/*/latest/*.append.json` + `dev-seed/*.json`；答辩与日常演示 **JSON-only** |
| **多景区** | 负责人提供若干景点/校园名称 → 脚本批量拉取 OSM 包 |
| **室内** | 前端人工走查错位 → 登记 → 人工校正 bundle |
| **衍生数据** | 校正完成后，各景区随机补 **日记**、**美食**（美食须挂在餐厅/食堂类 POI 上） |
| **规模口径** | 设施 **type 枚举 ≥10**（OSM 抓不全可种子补齐）；景区 **≥200** 条记录可通过 **别名/ID 复用** 同一套地图数据达成 |

---

## 2. 运行模式：JSON-only（已定稿）

### 2.1 原则

- **答辩与 dev 演示不依赖 MySQL**：业务数据仅来自 JSON 种子 + OSM append 文件，经 `DevSeedDataLoader` / `IndoorDevSeedLoader` 写入 `InMemoryStore`。
- **写库路径**：若 Mapper 被调用且连接失败，须跳过并保留内存态（既有 `ignore-db-connection-failure` 策略）。
- **配置**：`spring.profiles.active=dev`；`app.debug.ignore-db-connection-failure=true`；`app.storage.preload.enabled=false`（或等价：不阻塞于 DB 预加载，以 seed 为主）。

### 2.2 实施项（S2-R10）

- [ ] 文档与 README 明确「JSON-only 启动命令」
- [ ] `application-dev.yml` 默认关闭 DB 预加载依赖（若尚未固化则补配置）
- [ ] 验收：`mvn spring-boot:run -Dspring-boot.run.profiles=dev` 在无 MySQL 时全功能可演示

---

## 3. 多景区 OSM 采集（S2-R7）

### 3.1 协作流程

```mermaid
flowchart LR
  A["负责人给出名称列表"]
  B["Agent 调 osm_seed.py 逐包拉取"]
  C["写入 osm-data + 更新 map-imports"]
  D["负责人前端走查室内"]
  E["登记错位清单"]
  F["Agent 手改 indoor / manual_assign"]
  G["衍生 seed：日记 + 美食"]

  A --> B --> C --> D --> E --> F --> G
```

### 3.2 输入（负责人提供）

- 景点/校园 **中文或检索用名称** 列表（可含城市/区县消歧）。
- 可选：Nominatim `place_id` 或 `osm-type` + `osm-id`（若名称歧义大）。

### 3.3 执行命令（Agent）

与现有管理端/验证脚本一致，示例：

```bash
python scripts/osm_seed.py \
  --skip-config \
  --target-name "<景区显示名>" \
  --query "<Nominatim 检索串>" \
  --output-dir src/main/resources/osm-data \
  --run-name latest \
  --map-imports src/main/resources/dev-seed/map-imports.json
```

- 每成功一包：在 `map-imports.json` 追加对应 `scenic_areas` / `pois` / `roads` / `facilities` 路径。
- 跑完后：`python scripts/validate_osm_output.py`（或 `verify_osm_outdoor.py`）+ 后端 dev 启动 smoke。

### 3.4 室内错位校正（S2-R8）

| 步骤 | 负责人 | Agent |
|------|--------|-------|
| 1 | 在 `/scenic` 选 areaId，双击 `indoorAvailable` POI，逐层查看 | — |
| 2 | 记录错位：buildingPoiId、level、错误 room/走廊、期望归属 | — |
| 3 | 提交清单（可贴 `docs/Tech/OSM Indoor Manual Attribution.md` 表格或 issue 列表） | 按清单改 `latest/indoor/*.json` 或 `indoor_manual_assign.json` |
| 4 | 复测通过 | 更新该包 `report.md` / HANDOFF |

**登记文档**：[OSM Indoor Manual Attribution.md](./OSM%20Indoor%20Manual%20Attribution.md)

---

## 4. 衍生 seed：日记与美食（S2-R9）

**前置条件**：对应 OSM 包室外 + 室内（如需）校正完成。

### 4.1 日记（`diaries.json` 等）

- 为 **每个 canonical 景区**（及需要展示的 alias）随机生成若干条演示日记。
- 字段：标题、正文、关联用户（种子用户 id）、`diary_destinations` 指向景区名/id。
- 数量：按答辩需要自定（建议每 canonical 包 2–5 条，alias 可复用或略作变体）。

### 4.2 美食（`foods.json` + `restaurants.json`）

**硬约束**：美食必须定位在 **餐厅/食堂类 POI** 上，禁止纯随机经纬度。

| 字段/关系 | 规则 |
|-----------|------|
| `Restaurant.poiId` | 取自该 `areaId` 下 POI，类型为 `restaurant` / `canteen` / `fast_food` / `food_court` 等（OSM `amenity` 或 `poi-types` 映射） |
| `Food.restaurantId` | 关联上表餐厅 |
| 坐标 | 继承 POI 的 lat/lng（或餐厅节点 id 走路网） |
| 数量 | 每景区至少若干条，保证美食 Tab 可演示 |

**POI 筛选优先级**：

1. OSM 已写入 POI 的餐饮类 building/amenity  
2. `facilities.append` 中 type 为餐饮的设施节点（若与 POI 共用图节点 id）  
3. 不足时可在 **已有餐饮 POI 上** 增开虚拟窗口（仍绑同一 poiId），不凭空造点  

### 4.3 生成方式

- 优先 **脚本化**（`scripts/seed_derived.py` 或等价，待 R9 实施时新建）：读 canonical area 的 POI/设施列表 → 随机采样餐饮锚点 → 写 JSON。
- 手工微调：菜名、菜系、热度、评分。

---

## 5. 设施种类 ≥10（S2-R11a）

### 5.1 口径（负责人拍板）

- **验收统计**以系统内 **`Facility.type` 去重种类数 ≥10** 为准。
- OSM 脚本 **尽量** 映射更多 tag；**抓不全时** 允许用种子补全缺失类型（可挂在真实或占位节点上），不阻塞答辩。

### 5.2 类型枚举（目标 ≥10，实施时写入 `config/facility-types.json`）

| type 码 | 中文 | OSM 来源（优先） |
|---------|------|------------------|
| `toilet` | 卫生间 | `amenity=toilets` |
| `restaurant` | 饭店 | `amenity=restaurant` |
| `canteen` | 食堂 | `amenity=canteen` |
| `cafe` | 咖啡馆 | `amenity=cafe` |
| `fast_food` | 快餐 | `amenity=fast_food` |
| `supermarket` | 超市 | `shop=supermarket` |
| `convenience` | 便利店 | `shop=convenience` |
| `library` | 图书馆 | `amenity=library` |
| `hospital` | 医疗点 | `amenity=hospital/clinic` |
| `atm` | 取款机 | `amenity=atm` |
| `parking` | 停车场 | `amenity=parking` |
| `info` | 游客中心 | `amenity=information` |
| `shop` | 商店 | `shop=*`（泛化兜底） |

- 后端 `FacilityService` / 前端设施筛选与上表对齐。
- `validate_osm_output.py` 增加「distinct facility types ≥10」检查（含 seed 合并后）。

---

## 6. 景区数量 ≥200：别名复用（S2-R11b）

### 6.1 口径（负责人拍板）

- 需求 **「至少 200 个景区/校园」** 指 **`scenic_areas` 表/索引中的记录条数 ≥200**。
- **不要求** 200 份独立 OSM 全量抓取；允许多个 **areaId** 指向 **同一 canonical 地图包**（POI/道路/设施/室内图数据复用）。

### 6.2 实现思路（R11b 编码时择一或组合）

**方案 A — 加载期别名（推荐）**

- 新增 `dev-seed/scenic-area-aliases.json`：

```json
{
  "aliases": [
    { "id": 10001, "name": "示例大学东区", "canonicalAreaId": 252 },
    { "id": 10002, "name": "示例大学西区", "canonicalAreaId": 252 }
  ]
}
```

- `DevSeedDataLoader` 插入 alias 行后，对 POI/道路/设施查询按 `canonicalAreaId` 解析（或插入时复制 metadata 仅改 id/name/标签）。

**方案 B — 种子复制**

- 脚本生成 200 条 `ScenicArea` 记录，随机分配 `canonicalAreaId` 指针字段；地图数据仍只存一份。

### 6.3 约束

- **ID 空间**：alias id 与 OSM 包内真实 id 不冲突（建议使用 10000+ 段或独立 registry）。
- **演示**：推荐列表/搜索应能搜到 alias 名称；进入 `/scenic?areaId=<aliasId>` 后地图与 canonical 一致。
- **答辩说明**：架构支持 1000+ 扩展；本期以 **少量高质量 OSM 包 + 别名扩表** 满足规模指标。

---

## 7. 任务清单与状态

| ID | 内容 | 依赖 | 状态 |
|----|------|------|------|
| R1–R5 | OSM 脚本重构（设施/室内/建筑面） | — | ✅ 完成 |
| R6 | dev-seed 地图 JSON 清空 + map-imports 单包 | R1–R5 | ✅ 完成 |
| **R7** | 负责人给名称 → 批量 osm_seed 拉包 | — | ⏳ 待名称列表 |
| **R8** | 室内错位：前端走查 → 人工校正 | R7 | ⏳ 待走查 |
| **R9** | 衍生 seed：日记 + 美食（绑餐饮 POI） | R8 | ⏳ 待做 |
| **R10** | JSON-only 配置与文档固化 | — | ⏳ 待做 |
| **R11a** | 设施 type 枚举 ≥10 + 校验 | R7 或 seed | ⏳ 待做 |
| **R11b** | 景区 alias ≥200 | R7 canonical 包稳定 | ⏳ 待做 |
| **R12** | 合并验收：规模表 §9.7 + smoke | R9–R11 | ⏳ 待做 |

---

## 8. 验收检查（R12）

- [ ] `scenic_areas` 记录数 ≥200（含 alias）
- [ ] `Facility.type` 去重 ≥10
- [ ] `facilities` 记录数 ≥50（可 OSM + seed）
- [ ] 道路边 ≥200（通常 OSM 已满足）
- [ ] 每 canonical 包 POI ≥20
- [ ] 室内演示 ≥1 POI（建议 ≥3）
- [ ] 每 canonical 包有日记、有绑餐饮 POI 的美食
- [ ] dev profile 无 MySQL 可启动并完成主流程演示
- [ ] `mvn test` + `npm run build` 通过

---

## 9. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-06-08 | 负责人 S2 v2 拍板：多包采集协作流、JSON-only、设施枚举≥10、景区 alias≥200、美食绑餐饮 POI |
