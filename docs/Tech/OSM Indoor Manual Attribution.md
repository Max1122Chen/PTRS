# OSM 室内归属 — 已知问题与人工校正登记

> **状态**：已登记（2026-06-08）  
> **决策**：自动采集的 `building_footprint` 归属**不足以**表达沙河校区真实拓扑；**后期由人工校正** `indoor/*.json`（或本目录下的覆盖配置），脚本侧暂不继续扩大几何启发式。

## 1. 问题摘要

沙河校区 OSM 中 `indoor=room` way 与 `building=*` 轮廓**地理错位**，且校区层级（公共教学楼 → S4 区 / N 区）未在 OSM 建模。当前脚本用「建筑面 + 缓冲 + 认领顺序」分配 room，会产生：

| 现象 | 沙河实例 |
|------|----------|
| A 栋 POI 装入 B 栋 room | 图书馆 bundle 含 `N-105` 等（属公共教学楼） |
| 同一逻辑楼被拆到多栋 | `N-*` 分散在图书馆 / 报告厅；`S4-*` 挂在公共教学楼 POI |
| 应有室内锚点的区无面 | **S4 区**仅为 `node/8626186257`，无 `building` 多边形，无法作室内父建筑 |

**负责人现场确认（2026-06-08）**：

- 学术报告厅、图书馆抓到的 room → 实际应属 **公共教学楼**
- 公共教学楼抓到的 room（`S4-*`）→ 实际应属 **S4 区**

分析脚本：`scripts/_analyze_indoor_misattrib.py`（对鸿雁路 `latest/raw/overpass.json`）。

## 2. 根因（分层）

1. **OSM 源数据**：room 质心不在任何 building 多边形内；S4 区缺 building way；多栋 POI 抢同一片 room 点云。
2. **采集脚本**：150 m 缓冲 + 图书馆优先认领放大错位（见 [OSM Map Data Collection Refactor.md](./OSM%20Map%20Data%20Collection%20Refactor.md) §5）。

## 3. 人工校正方式（后期执行）

### 3.1 直接改 bundle（推荐用于答辩/demo）

路径：

`src/main/resources/osm-data/北京邮电大学-沙河校区-鸿雁路-沙河镇-昌平区-北京市-102206-中国/latest/indoor/`

- 编辑或删除错误建筑的 `{buildingPoiId}.json`
- 将 room 节点移到正确 bundle；`parentId` 与 `buildingPoiId` 保持一致
- 可为 S4 区新建 bundle（`buildingPoiId` = POI `900022208`），待完整度满足 `IndoorSeedCompleteness`

### 3.2 覆盖配置（待实现，已预留）

`latest/indoor_manual_assign.json`：记录 room 名前缀 / OSM way id → `buildingPoiId` 映射；`indoor_seed` 读取后覆盖自动分配（**尚未编码**）。

### 3.3 改 OSM（长期）

在 iD/JOSM 为 S4 区补 `building` 面，将 `S4-*` / `N-*` room 多边形移入对应建筑内后重抓。

## 4. 自动采集侧暂缓项

- 不再扩大 P0 缓冲或「最近建筑」兜底
- 图书馆 / 报告厅等**无 room 落在轮廓内**的 POI，默认不生成 indoor（待人工开启）
- 名称前缀规则（`S4-` / `N-`）列入 `indoor_manual_assign` 实现 backlog

## 5. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-06-08 | 负责人确认错位案例；登记人工校正策略；R6 数据治理继续 |
