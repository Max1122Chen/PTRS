# 景区一体化工作台 — 交互与页面设计（S3）

> **状态**：设计稿（2026-06-08）  
> **原则**：**新建** `景区` 页面与状态模型，**不在** `RoutePlannerView` 上叠功能；后端图算法（Dijkstra、TopK、模糊索引）**复用**，主要改 **锚点语义 + API 入参 + 前端 IA**。

## 1. 问题与目标

### 1.1 当前错位（摘要）

| 维度 | 现状 | 目标 |
|------|------|------|
| 信息架构 | `/route`、`/facility`、`/food` 三页平行 | 单一 **`/scenic`**（景区工作台） |
| 上下文 | 各页独立选景区；设施页用 **GPS** | 共享 **`areaId` + `focusPoiId`**（地图选中 POI） |
| 设施「附近」 | 相对用户经纬度 | 相对 **景区内选中景点/场所** |
| 美食距离 | 直线距离 / 景区中心 | 相对 **focusPoi** 的 **路网可达距离**（与 FR-006 一致） |
| 路线 | 功能完整但独占一页 | 作为工作台 **模式之一**，与设施/美食联动 |

### 1.2 设计目标

1. 用户进入景区后，**一张地图、一个选中点、三个能力**（路线 / 设施 / 美食）围绕同一点展开。  
2. 室内导航（FR-004-5）作为地图 **视图切换**，不单独占导航入口。  
3. 旧路由可 **301 式重定向** 到 `/scenic?tab=route|facility|food` 便于书签过渡。

---

## 2. 核心概念：景区工作台状态

```typescript
/** 景区页全局状态（Pinia store: useScenicHubStore） */
interface ScenicHubState {
  areaId: number | null           // 当前景区
  areaMeta: ScenicArea | null     // 名称、开放时间等

  focusPoiId: number | null       // 地图「当前关注」POI（设施/美食锚点）
  focusPoi: PoiCandidate | null

  mode: 'outdoor' | 'indoor'      // 室外拓扑 | 某建筑室内分层
  indoorBuildingPoiId: number | null

  panelTab: 'route' | 'facility' | 'food' | 'poi'  // 右侧/底部面板

  // 路线子状态（从现 RoutePlannerView 抽取）
  routeSubMode: 'two-point' | 'multi-point'
  startId: number | null
  endId: number | null
  multiPointIds: number[]
  vehicle: 'walk' | 'bike' | 'shuttle'
  strategy: 'distance' | 'time'
  routeResult: RoutePlanVO | null

  // 设施/美食查询条件（共享 focusPoiId）
  facilityRadius: 200 | 500 | 1000
  facilityType: string
  facilityKeyword: string
  facilityResults: FacilityVO[]       // 最近一次查询命中
  highlightedFacilityIds: number[]    // 同步到地图高亮层
  hoveredFacilityId: number | null    // 列表悬停 ↔ 地图联动

  foodWeights: { heat: number; rating: number; distance: number }
  foodCuisine: string
  foodKeyword: string
}
```

**规则**：

- 未选 `areaId` → 仅展示景区选择器 + 空状态引导。  
- 选中地图 POI → 更新 `focusPoiId`；设施/美食默认以该点为锚点。  
- 路线模式选起点/终点 → 可同时更新 `focusPoiId`（最后一次点击的 POI）。  
- 进入室内 → `mode=indoor`，面板切到路线子面板（室内规划）；「返回室外」恢复 `mode=outdoor`。

---

## 3. 页面布局（桌面 / 移动）

### 3.1 桌面（≥1024px）— 「地图主舞台 + 上下文面板」

```
┌─────────────────────────────────────────────────────────────────┐
│ 顶栏：景区选择 [北邮沙河 ▼]    景区简介 chip    [进入 Gallery 推荐] │
├──────────────────────────────────┬──────────────────────────────┤
│                                  │  Tab: 路线 | 设施 | 美食 | 详情 │
│                                  ├──────────────────────────────┤
│     ECharts 室外/室内拓扑图        │  （随 Tab 切换表单与结果列表）    │
│     · 点击 POI → focus            │                              │
│     · 路线高亮 path               │  设施 Tab 示例：                │
│     · indoor 角标 POI             │  锚点：图书馆（来自 focus）      │
│                                  │  范围 500m  类型 卫生间          │
│                                  │  [查询] → 列表（路径距离排序）   │
│                                  │                              │
├──────────────────────────────────┴──────────────────────────────┤
│ 可选：当前路径分段 / 选中 POI 简介条（折叠）                          │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 移动 — 「地图全屏 + 底部抽屉」

- 默认：地图 60% 高度，底部 **可拖拽 Sheet**（`panelTab`）。  
- 路线规划结果、设施列表、美食列表在 Sheet 内滚动。  
- 景区切换收到顶栏或 Sheet 顶部。

**不采用**：三个顶级 Tab 各配一张地图（重复加载、上下文丢失）。

---

## 4. 交互模式（地图行为）

地图组件 **`ScenicMapCanvas`**（新建，从 `RoutePlannerView` **抽取**渲染逻辑，非继承整页）：

| 用户操作 | `panelTab` | 地图行为 |
|----------|------------|----------|
| 单击 POI | 任意 | 设 `focusPoiId`；若 `panelTab=poi` 显示详情 |
| 双击 POI | `route` | 两点模式：依次填入起/终点；多点模式：追加途经点 |
| 单击 indoor POI | 任意 | 若 `indoorAvailable`：进入室内视图（FR-004-5-2） |
| 规划成功 | `route` | 高亮 `path` |
| 设施查询成功 | `facility` | 地图上**高亮命中设施节点**并**突出显示设施名称**（见 §5.2.1） |
| 列表悬停设施项 | `facility` | 对应地图节点放大/描边；其余命中项保持次级高亮 |
| 切换 Tab / 清空查询 | 任意 | 清除设施高亮层，恢复默认 POI 样式 |

**与旧路线页差异**：设施/美食 **不再**要求用户去另一页重新选景区；列表始终显示「距 **focusPoi** 的路径距离 X m」；设施结果与地图**双向联动**，非纯列表。

---

## 5. 三个能力在面板内的分布

### 5.1 路线（Tab: route）

保留现有能力，UI 收敛：

- 子模式切换：**两点** / **多点** / **回环**  
- 交通工具、策略（距离/时间）  
- 起终点可从下拉选 POI，或 **「使用当前选中点」** 按钮填入 focus  
- 结果：path 文本 + 分段拥堵度（现有）  
- **多点失败**：展示具体不可达段（S3-ROUTE-01 后端小改，非算法重写）

### 5.2 设施（Tab: facility）

**取消**独立 GPS 定位 Tab。

| 区块 | 内容 |
|------|------|
| 锚点 | 只读展示 `focusPoi.name`；无 focus 时提示「请先在地图选择景点或场所」 |
| 范围 | 200 / 500 / 1000 m（FR-006-1） |
| 类别 | 下拉多选/单选（FR-006-2） |
| 关键词 | 类别名称模糊（FR-006-3） |
| 结果 | 名称、类型、**pathDistance**、直线距离次要展示；**与地图联动**（§5.2.1） |

API：`GET /api/facility/nearby?areaId=&anchorPoiId=&radius=&type=`（**新增或扩展**，弃用纯 lat/lng 为主入口）

#### 5.2.1 设施结果 — 地图高亮与名称突出（FR-006-4）

查询返回后，**列表与地图同步展示**，避免用户只在侧栏看文字、在图上找不到点。

| 层级 | 视觉规则 |
|------|----------|
| **锚点 POI**（`focusPoiId`） | 保持「当前选中」样式（如外圈描边/较大节点），与设施命中样式可区分 |
| **命中设施**（查询结果集） | 节点 **放大** 或 **变色**（如橙/青，与默认灰/蓝 POI 区分）；`label.show = true`，**字号加大、加粗** |
| **悬停联动** | 侧栏列表 `mouseenter` 某设施 → 该节点再放大一级 + 标签高亮；`mouseleave` 恢复为「命中集」统一样式 |
| **点击联动** | 点击列表项 → 地图 `dispatchAction` 聚焦该节点（可选轻量平移，不强制改缩放级别） |
| **清除** | 重新查询、切换 Tab 离开 `facility`、或清空条件 → 移除高亮层，标签恢复默认（未命中设施可隐藏 label 或保持原 tooltip 行为） |

**ECharts 实现要点**（`ScenicMapCanvas`）：

- 设施节点与景点 POI 共用 graph series 时，用 `data` 项级 `itemStyle` / `symbolSize` / `label` 覆盖；或叠加一层仅含命中设施的 **effectScatter / scatter** 作为高亮层（推荐，便于一键 `clearHighlight`）。
- 命中设施须带 **稳定节点 id**（`facilityId` 或绑定的 `poiId`/`graphNodeId`），与 API 返回一致。
- 名称标签默认景区底图可关闭或弱化，**设施查询后强制显示命中项名称**；锚点名称始终显示。

**美食 Tab**：本期可只做列表；若时间允许，可对 Top10 美食关联的窗口/饭店 POI 做同类弱高亮（非 S3 必做）。

### 5.3 美食（Tab: food）

| 区块 | 内容 |
|------|------|
| 锚点 | 同 focusPoi；缺省时用景区中心并提示 |
| 推荐 | 权重滑条（热度/评价/距离）、菜系过滤、Top10 列表 |
| 搜索 | 关键词模糊 + 结果排序（FR-013-3/4） |
| 详情 | 点击条目 → 侧栏或 `/scenic/food/:id` 子路由（可选） |

API：推荐/搜索增加 `anchorPoiId`；距离计算改为与设施相同的路网最短路（**后端小改**，TopK/NGram 不动）。

---

## 6. 导航与路由

### 6.1 新路由

| 路径 | 组件 | 说明 |
|------|------|------|
| `/scenic` | `ScenicHubView.vue` | 主工作台；query `?areaId=252&tab=route` |
| `/scenic/:areaId` | 同上 | 深链景区 |

### 6.2 子导航调整（`AppLayout`）

**原**：推荐 | 路线 | 设施 | 美食  
**新**：推荐 | **景区** | 日记 | …  

`/route`、`/facility`、`/food` → **redirect** `/scenic?tab=...`（保留 query 兼容）。

### 6.3 与推荐页衔接

`Gallery` / 景区卡片「开始游览」→ `router.push({ path: '/scenic', query: { areaId } })`。

---

## 7. 前端模块拆分（建议文件）

```
frontend/src/
  views/scenic/
    ScenicHubView.vue          # 布局壳 + store 绑定
    components/
      ScenicAreaPicker.vue
      ScenicMapCanvas.vue      # 室外/室内 ECharts（从 route 抽取）
      ScenicContextBar.vue     # focus POI 摘要条
      panels/
        RoutePanel.vue
        FacilityPanel.vue
        FoodPanel.vue
        PoiDetailPanel.vue
  stores/scenicHub.ts
```

**废弃**（过渡期保留 re-export 或删除）：`RoutePlannerView.vue`、`FacilityView.vue`、`FoodView.vue` 逻辑迁入 panels。

---

## 8. 后端改动范围（小）

| 项 | 改动 | 算法 |
|----|------|------|
| 设施 nearby | 入参 `anchorPoiId` 替代主 lat/lng | Dijkstra 排序 **已有** |
| 设施 search | 可选：对命中结果补 pathDistance | 索引 **已有** |
| 美食 recommend/search | `anchorPoiId` + 路径距离进 TopK 评分 | TopK **已有** |
| 多点路线 | 失败时返回不可达点对 | TSP/Dijkstra **已有** |

**不做**：重写图存储、重写模糊索引、重写 TopK。

---

## 9. 实施顺序（S3 建议）

1. **文档 + 需求**（本节 + Requirements v1.7）— 负责人确认布局  
2. `useScenicHubStore` + `ScenicHubView` 空壳 + 景区选择 + 地图抽取  
3. `RoutePanel` 迁入（两点/多点/室内）  
4. 后端 `anchorPoiId` 扩展 + `FacilityPanel`  
5. 美食路径距离 + `FoodPanel`  
6. 导航合并、旧路由 redirect、删除三旧页  
7. 多点失败提示优化（可与 2 并行）

---

## 10. 验收场景（沙河 areaId=252）

1. 从推荐进入沙河 → 地图加载 → 点击「图书馆」→ 设施 Tab 查 500m 内卫生间 → 列表按 **路径距离** 排序；**地图上对应卫生间节点高亮且名称加粗可见**。  
2. 同 focus → 美食 Tab Top10，距离列为路网距离。  
3. 路线 Tab：图书馆 → 公共教学楼 两点规划成功；选 3 个 POI 多点规划，失败时提示哪段不可达。  
4. 点击 indoor POI → 室内楼层 → 室内规划 → 返回室外，focus 不变。

---

## 11. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-06-08 | 初稿：S3 景区工作台 IA，区别于旧路线页修补方案 |
| 2026-06-08 | 负责人确认布局方案；新增 §5.2.1 设施结果地图高亮与名称突出（FR-006-4） |
