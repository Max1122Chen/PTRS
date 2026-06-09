# 验收冲刺项登记

> **用途**：记录收尾冲刺的五类工作及其**依赖顺序**。
> **状态（2026-06-08）**：S2 R1–R6 完成；**S2 R7–R12** 按 [S2 执行计划](../Tech/S2%20Data%20Governance%20Execution%20Plan.md) 推进；S3 景区 Hub 主体已 commit。
> **基线**：`main` @ `4f049e6`（2026-06-08）

---

## 1. 冲刺项一览（负责人优先级排序）

| 顺序 | ID | 主题 | 负责人理解（难易/先后） | 状态 |
|------|-----|------|-------------------------|------|
| **①** | **S2** | 展示数据治理 + **地图采集重构** | **最好做、最先做**；见 §2、[S2 执行计划](../Tech/S2%20Data%20Governance%20Execution%20Plan.md) | R1–R6 ✅；**R7–R12 进行中** |
| **②** | **S3** | 业务功能纠偏 | 景区工作台 + 锚点 API | **主体完成**；S3-ROUTE-01 待做 |
| **③** | **S1** | 前端导航与视觉 | 在数据与业务口径稳定后再改 | 待需求 |
| **④** | **S4** | 自制数据结构 | 课程硬约束，范围待指示 | 待需求 |
| **⑤** | **S5** | 验收参考资料 | **最后**定稿（依赖前几项结果） | 待需求 |

---

## 2. S2 数据治理 — 已对齐的方向（待执行）

### 2.1 原则

1. **`osm-data` 为业务真源**：景区、POI、道路、设施（修正后）、室内图均来自 OSM 采集管线。
2. **dev-seed 瘦身**：除**用户相关**外，删除早期测试用假数据（景区、POI、道路、日记、评价、美食、设施等）。
   - **保留候选**：`users.json`、`user_interests.json`（及鉴权演示所需最小集）。
   - **删除/清空候选**：`scenic_areas`、`buildings`、`roads`、`facilities`、`foods`、`restaurants`、`diaries`、`comments`、`diary_destinations`、`tags` 等业务假数据（以负责人最终清单为准）。
3. **衍生数据后置**：日记、美食、设施等**手工/种子补充**，须在**确定保留哪些 osm-data 包之后**再写，且与真源景区 ID 对齐。
4. **运行模式（已定稿）**：答辩与 dev 演示 **JSON-only** — 仅 `dev-seed` + `osm-data` JSON，`InMemoryStore` 承载业务；**不依赖 MySQL**（`dev` profile + `ignore-db-connection-failure`）。详见 [S2 执行计划 §2](../Tech/S2%20Data%20Governance%20Execution%20Plan.md)。

### 2.2 S2 阶段二：多包采集 + 衍生 seed + 规模口径（负责人 2026-06-08 拍板）

> 完整流程：[S2 Data Governance Execution Plan.md](../Tech/S2%20Data%20Governance%20Execution%20Plan.md)

| 阶段 | 内容 | 负责分工 |
|------|------|----------|
| **R7** | 负责人给景点/校园名称 → Agent 调 `osm_seed.py` 批量拉包 → 更新 `map-imports.json` | 名称：负责人；脚本：Agent |
| **R8** | 负责人前端走查室内错位 → 登记 → Agent 手改 `indoor/*.json` | 走查：负责人；校正：Agent |
| **R9** | 校正完成后各景区随机补 **日记**、**美食**（美食 **必须** 绑餐厅/食堂类 POI） | Agent + 脚本 |
| **R10** | JSON-only 配置与 README 固化 | Agent |
| **R11a** | 设施 `type` **枚举 ≥10**（OSM 不足则 seed 补齐） | Agent |
| **R11b** | 景区记录 **≥200**：alias id **复用** canonical 地图数据，不需 200 份 OSM | Agent |
| **R12** | 规模表 + smoke 合并验收 | 共同 |

### 2.3 S2 子任务：采集脚本缺口（R1–R5 已处理，留存对照）

负责人指出当前 **OSM 抓取与需求口径不匹配**，需在选定 osm-data 包之前或并行修正脚本：

#### A. 服务设施（`facilities`）筛选不足

| 需求口径（FR-006） | 当前脚本行为（`osm_seed.py`） | 缺口 |
|--------------------|-------------------------------|------|
| 商店、饭店、洗手间、图书馆、食堂、超市、咖啡馆等作为**设施**查询 | `classify_facility()` 仅覆盖 toilet / hospital / bike / service / printer 等少量 `amenity` | 超市、`shop=chemist/supermarket`、`amenity=cafe` 等**未进 facilities** |
| 设施与 POI 分工 | 图书馆、餐厅、商店等多被 `classify_poi()` 写入 **POI/buildings**，而非 `facilities.append.json` | 前端「设施查询」数据源与需求语义不一致 |
| 设施种类 ≥10 | 合并后仅 **6** 种 | 种类数不达标，根因在标签映射与分流逻辑 |

**待办（登记）**：扩展 `classify_facility` / OSM tag 映射；明确「设施 vs 可路线 POI」分流规则；与 `poi-types.json`、后端 `FacilityService` 对齐。

#### B. 室内数据（`indoor`）过严丢弃

| 现象 | 可能原因（待验证） |
|------|-------------------|
| `latest/indoor/rejected/*.json` 大量存在 | `indoor_seed.evaluate_completeness()`：≥2 room、≥3 corridor、walkable 连通等门禁过严 |
| 负责人判断 raw Overpass **含丰富室内要素** | 房间 way 质心、MST 合成走廊、竖向边等启发式仍不足以让 bundle 过关 |
| 仅少数 POI `indoorAvailable=true` | 与需求「OSM 有数据则应尽量利用」不符 |

**待办（登记）**：审计 rejected 原因分布；放宽或分级完整度（演示级 vs 严格级）；改进从 `raw/overpass.json` 抽室内特征的逻辑。

### 2.4 OSM 包策略（负责人 2026-06-08 拍板，R7 扩展）

| 项 | 决策 |
|----|------|
| 首批真源包 | `osm-data/北京邮电大学-沙河校区-鸿雁路-.../`（areaId **252**，已入库） |
| 后续包 | 负责人提供名称列表，**逐包** osm_seed 拉取并挂入 `map-imports.json` |
| 已删旧包 | 师大北路、南丰路旧沙箱、执信、贵阳一中 |
| 衍生 seed | **R8 室内校正完成后** 再写日记/美食；美食绑餐饮 POI |
| 规模 | 设施 type ≥10（枚举+seed）；景区 ≥200（**alias 复用** canonical 数据） |

### 2.4 S2 执行顺序（阶段一 ✅，阶段二 ⏳）

**阶段一（已完成）**：脚本重构 → 沙河迭代 → dev-seed 瘦身 → map-imports 挂沙河包

**阶段二**（详见 [S2 执行计划](../Tech/S2%20Data%20Governance%20Execution%20Plan.md)）：

```mermaid
flowchart LR
  R7["R7 多包 OSM 拉取"]
  R8["R8 室内人工校正"]
  R9["R9 日记+美食 seed"]
  R10["R10 JSON-only"]
  R11["R11 规模 alias+设施枚举"]
  R12["R12 合并验收"]

  R7 --> R8 --> R9
  R7 --> R11
  R10 --> R12
  R9 --> R12
  R11 --> R12
```

### 2.5 室内归属结论（2026-06-08 确认）

- **脚本现状**：`building_footprint` + P0 缓冲已落地；节点写入 `parentId`。
- **错位问题**：沙河 OSM room 与 building 面地理错位，自动归属不可靠（图书馆/报告厅装入 `N-*`，公共教学楼装入 `S4-*`）。
- **决策（2026-06-08）**：**后期人工校正** `latest/indoor/*.json`；登记见 [OSM Indoor Manual Attribution.md](../Tech/OSM%20Indoor%20Manual%20Attribution.md)；预留 `indoor_manual_assign.json`（脚本未读）。
- **采集重构详情**：[OSM Map Data Collection Refactor.md](../Tech/OSM%20Map%20Data%20Collection%20Refactor.md)。

---

## 3. 依赖关系（与负责人优先级一致）

```mermaid
flowchart TB
  S2["① S2 数据治理<br/>+ 采集脚本修正"]
  S3["② S3 业务纠偏"]
  S1["③ S1 前端优化"]
  S4["④ S4 自制 DS"]
  S5["⑤ S5 验收资料"]

  S2 --> S3
  S3 --> S1
  S3 --> S4
  S2 --> S5
  S3 --> S5
  S1 --> S5
  S4 --> S5
```

| 关系 | 说明 |
|------|------|
| **S2 → S3** | 设施/室内/景区真源决定功能验收样本 |
| **S3 → S1** | 纠偏后的功能边界决定导航与页面 |
| **S2 → S1** | 演示路径依赖最终 osm-data 包 |
| **S3/S4 → S5** | 文档须反映最终功能与数据结构 |
| **S5 最后** | README/算法说明/启动流程依赖前几项定稿 |

**当前共识**：按 **S2 → S3 → S1 → S4 → S5** 推进；S2 内**先修采集脚本再定包再清 seed**；分项具体需求仍由负责人后续给出。

---

## 4. 各冲刺项待澄清项（占位，供后续填入）

### S1 前端导航与视觉
- [ ] 一级导航最终中文文案
- [ ] 二级导航是否调整
- [ ] 视觉问题清单（由负责人逐条指出）

### S2 展示数据治理 + 地图采集重构
- [x] 方向：dev-seed 仅保留用户相关；osm-data 为业务真源；衍生数据后置
- [x] R1–R5：脚本重构 + 沙河迭代
- [x] R6：dev-seed 地图 JSON 清空 + map-imports 单包（沙河）
- [x] **JSON-only** 运行模式拍板（见 [S2 执行计划 §2](../Tech/S2%20Data%20Governance%20Execution%20Plan.md)）
- [x] **规模口径**拍板：设施 type 枚举 ≥10；景区 ≥200 用 alias 复用 canonical 数据
- [ ] **R7** 负责人提供名称 → 批量 osm_seed 拉包
- [ ] **R8** 室内 bundle 人工校正（负责人走查 → Agent 改 JSON）
- [ ] **R9** 衍生 seed：日记 + 美食（美食绑餐厅/食堂 POI）
- [ ] **R10** JSON-only 配置/README 固化
- [ ] **R11a** 设施 type 枚举 ≥10 落地 + 校验
- [ ] **R11b** scenic-area-aliases ≥200
- [ ] **R12** §9.7 规模表 + smoke 验收

### S3 需求与实现纠偏

> 负责人确认：**不在路线页修补**，新建 **景区一体化工作台**（FR-017）。算法复用，改 IA + 锚点 API + 小范围后端入参。设计：[Scenic Hub Page Design.md](../Tech/Scenic%20Hub%20Page%20Design.md)；需求 v1.7。

| ID | 类型 | 描述 | 处置 |
|----|------|------|------|
| **S3-UI-01** | FR-017 | `/route`、`/facility`、`/food` 三页平行 → 单一 `/scenic` | **新建** `ScenicHubView` + panels；旧路由 redirect |
| **S3-FAC-01** | FR-006-1 / FR-017-1 | 设施「附近」以 **GPS** 为锚，非选中 POI | API `anchorPoiId`；前端弃 Geolocation 主流程 |
| **S3-FAC-02** | FR-006-1 | 路径距离相对最近设施节点，非相对锚点 POI | `FacilityServiceImpl.nearby` 改锚点图节点 |
| **S3-FAC-03** | FR-006-3 | 类别模糊搜索缺路径距离排序 | search 结果补 pathDistance |
| **S3-FAC-04** | FR-006-4 | 设施仅列表展示，地图无联动 | 查询后高亮设施节点 + 突出名称；列表悬停联动 |
| **S3-FOOD-01** | FR-013 / FR-013-1 | 距离为 **直线**（`GeoUtil`），非路网 | 推荐/搜索改路径距离 + `anchorPoiId` |
| **S3-FOOD-02** | FR-017 | 美食独立页，无景区地图上下文 | 迁入 `FoodPanel` |
| **S3-ROUTE-01** | FR-004 | 多点 TSP **偶发**「无法规划到达路径」 | 失败返回不可达段提示；连通性/交通工具过滤排查 |
| **S3-DOC-01** | — | 需求 §9 与实现错位 | **已完成** Requirements v1.7 + 本表 |

- [x] 景区页前端实现（S3-UI-01）
- [x] 设施/美食锚点 API（S3-FAC-* / S3-FOOD-*）
- [ ] 多点路线失败提示（S3-ROUTE-01）

### S4 自制数据结构替换
- [ ] 替换范围（包/类列表，负责人指示）
- [ ] 答辩口径：哪些层可用 JDK、哪些必须用自制结构

### S5 验收参考资料
- [ ] 文档落点（根 README、`docs/验收/` 等）
- [ ] 算法说明深度与示例
- [ ] 与 S2/S3/S4 定稿后的同步时间点

---

## 5. 工作方式

1. 负责人在会话中给出**某冲刺项的具体需求**。
2. 评估是否影响已登记的其他项；必要时**调整 §2 依赖或顺序**。
3. 小步实施 → 验证 → 更新 `HANDOFF.md` 与本文件对应条目的状态。
4. **S5 验收资料**建议在 S2/S3/S1/S4 主体口径稳定后集中定稿（可边做边记素材）。

---

## 6. 地图数据采集重构（摘要）

> 完整版：[docs/Tech/OSM Map Data Collection Refactor.md](../Tech/OSM%20Map%20Data%20Collection%20Refactor.md)（**待审核**）

| 维度 | 现状 | 重构目标 |
|------|------|----------|
| **设施** | `classify_facility` 过窄；商店/饭店/咖啡馆等进 POI | 按 FR-006 进入 `facilities.append` |
| **室内归属** | POI 单点 + 半径 80m | **建筑 `building` way 多边形** + `parentId` |
| **室内数据源** | 共享 campus raw + 二次 Overpass | **单次 raw** + `building_registry` |
| **迭代** | — | 北邮沙河沙箱重抓直至 §3.3 达标表满足 |

**审核通过后**：按技术文档 §4 执行 R1→R5（脚本重构 + 自行迭代），R6 再做 dev-seed 瘦身。

---

## 7. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-06-08 | 初建：登记 S1–S5 与依赖关系；状态均为「待需求」，不拍板、不实施 |
| 2026-06-08 | 负责人优先级：**S2→S3→S1→S4→S5**；S2 明确 dev-seed 瘦身 + osm 真源；登记设施/室内采集缺口 |
| 2026-06-08 | S2 拍板：现有 osm-data 均可弃；**暂留北邮沙河** 作抓取迭代沙箱；闭环见 §2.4 |
| 2026-06-08 | 新增 [OSM Map Data Collection Refactor.md](../Tech/OSM%20Map%20Data%20Collection%20Refactor.md)：现状、目标、设施/室内重构方案、沙河迭代；**待审核后编码** |
| 2026-06-08 | S3 景区 Hub commit（`4f049e6`）；S3 主体完成 |
| 2026-06-08 | S2 v2 拍板：多包协作流、JSON-only、设施枚举≥10、景区 alias≥200、美食绑餐饮 POI；新增 [S2 执行计划](../Tech/S2%20Data%20Governance%20Execution%20Plan.md) |
