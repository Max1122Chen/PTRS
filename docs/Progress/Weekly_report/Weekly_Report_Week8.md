# 个性化旅游推荐系统 - 第八周工作周报

成员：陈逸 程小路

## 1. 第八周（2026-04-20 至 2026-04-26）：路网交通工具枚举化 + 分模式拥堵度落地

### 1.1 工作目标

本周重点为：

- 将道路可通行交通工具从字符串改为结构化模型（modeProfile），并按交通工具维度计算拥堵度与最短时间。
- 先同步需求文档与技术设计文档，再完成后端、脚本、前端联动改造。
- 前端在“选中路径”时展示每段道路可通行交通工具及对应拥堵度。
- 保持代码质量与可维护性，确保编译与构建通过。

### 1.2 具体完成工作

#### 1.2.1 需求与技术口径同步

- **需求文档**（`docs/Requirements/Requirements Documendation.md`）：
  - FR-004-2/FR-004-3 调整为“按交通工具维度拥堵度 + 选中路径展示模式与拥堵度”。
  - 明确支持 WALK、BIKE、SHUTTLE 三种交通工具模式。

- **技术设计文档**（`docs/Tech/Technical Design Document.md`）：
  - roads 表结构字段改为 `mode_profile`（JSON Map<String, Double>）。
  - `/api/admin/road` 接口参数改为 `modeProfile`。
  - 图结构示例更新为 `Map<String, Double> modeCongestion`。

#### 1.2.2 路网交通工具枚举化与编解码

- **新增 TransportMode 枚举**（`src/main/java/com/travel/model/enums/TransportMode.java`）：
  - 定义 WALK("walk")、BIKE("bike")、SHUTTLE("shuttle") 三种交通工具。
  - 提供 `fromCode()` 方法支持从字符串解析，增强类型安全。

- **新增 ModeProfileCodec**（`src/main/java/com/travel/util/ModeProfileCodec.java`）：
  - 统一 `modeProfile` JSON 编解码，支持 `encode()` / `decode()` 操作。
  - 自动归一化交通工具代码与拥堵度值 [0,1]，确保数据一致性。

#### 1.2.3 后端路网模型重构

- **数据模型**：
  - `Road` 实体：移除旧字段 `congestion`、`vehicleType`，改用 `modeProfile`。
  - `Edge` 类：将 `congestion/vehicleType` 改为 `modeCongestion`，适配新图结构。
  - `Graph` 类：同步适配新图结构，支持按模式计算路径。

- **业务逻辑**：
  - `RouteServiceImpl`：改为按交通工具（TransportMode）计算最短路径和时间，支持返回 `modeCongestion` 和 `allowedModes`。
  - `FacilityServiceImpl`：同步适配新图结构，确保附近设施查询正常。

#### 1.2.4 数据与脚本同步

- **数据文件**：
  - `roads.json` 和 `roads.append.json`（多个数据集）：批量将旧字段迁移到 `modeProfile`。

- **采集脚本**：
  - `osm_seed.py`：新增 OSM tag 推断交通工具模式，为每条道路生成可重现随机拥堵度 [0,1]，输出 `modeProfile`。
  - `amap_seed.py`：道路输出字段改为 `modeProfile`，保持与新模型一致。

#### 1.2.5 前端交互与类型定义

- **类型定义**：
  - `api.ts`：新增图数据结构类型定义（TransportModeCode、ModeCongestionProfile、RoadEdge），确保 TypeScript 类型安全。

- **路线规划页**：
  - `RoutePlannerView.vue`：tooltip 展示“可通行交通工具+拥堵度”，结果区新增路段明细，展示每段允许模式及拥堵度。

- **管理端**：
  - `AdminView.vue`：道路表单改为选择交通工具并配置各模式拥堵度，提交 `modeProfile`，提升管理体验。

### 1.3 验证与质量门禁

- **后端编译**：`mvn -DskipTests compile`（BUILD SUCCESS）。
- **前端构建**：`npm run build`（仅有既有 chunk size 提示，无编译错误）。
- **关键回归项**：
  - 路线规划接口返回按交通工具维度的拥堵度数据。
  - 前端能正确展示可通行交通工具及对应拥堵度。
  - 管理端道路表单能正确提交 `modeProfile` 数据。

### 1.4 风险与应对

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| 旧数据迁移不完整导致运行时异常 | 中 | 低 | 已完成 roads.json 和 roads.append.json 批量迁移，确保数据一致性 |
| 前端图数据类型的 TS 类型定义可能不完整 | 中 | 低 | 已定义 RoadEdge 等核心类型，后续根据实际使用场景补充 |
| 多数据集同步更新可能遗漏 | 中 | 中 | 下周验证所有地图数据的 modeProfile 字段一致性，确保无遗漏 |
| 交通工具模式扩展时需同步修改多处代码 | 中 | 中 | 采用枚举和编解码统一管理，后续扩展只需修改 TransportMode 枚举 |

### 1.5 进度评估

- **完成度**：约 90%（路网交通工具枚举化核心功能已完成，前后端联动改造完成）。
- **里程碑**：支持按交通工具计算最短路径和时间，前端展示模式与拥堵度，提升路线规划的准确性和用户体验。
- **交付物**：
  - TransportMode 枚举与 ModeProfileCodec 工具类
  - 后端路网模型重构（Edge/Graph/RouteServiceImpl）
  - 前端路线规划页交互升级（模式与拥堵度展示）
  - 数据文件与脚本同步更新
  - 需求与技术文档口径对齐

### 1.6 下周计划（2026-04-27 至 2026-05-03）

- 验证所有地图数据的 modeProfile 字段一致性，确保无遗漏。
- 补齐路网模块关键服务层单元测试与接口回归脚本。
- 推进 FR-016 数据备份恢复方案的细化与落地。
- 开始准备项目结项材料，包括技术总结和演示文档。
- 优化前端路线规划页的用户体验，提升交互流畅度。

---

**编写日期**：2026-04-26
