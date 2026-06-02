# 个性化旅游推荐系统 - 第十二周工作周报

成员：陈逸、程小路

## 1. 第十二周（2026-05-18 至 2026-05-24）：稳定 2 与 FR-004-5 室内导航落地 + 全站体验收敛

### 1.1 工作目标

本周对照《项目计划书》第 12 周「稳定 2」与第十一周遗留项，重点为：

- **稳定 2**：统一数据校验与错误码口径；补需求/技术设计/交接文档一致性；执行第一轮全链路回归并整理缺陷清单（计划交付：回归测试报告 v1、文档对齐记录）。
- **功能扩展**：落实 **FR-004-5 室内导航**（OSM 室内要素自动采集、室内图加载、路线页室外/室内 ECharts 切换、多层 Dijkstra 路径规划）。
- **体验与账号**：收敛登录态、个人资料与全站 UI；补齐设施/美食等表单控件对齐；为答辩演示准备可重复环境（dev profile + dev-seed / osm-data 地图包）。

### 1.2 具体完成工作

#### 1.2.1 本周主干提交

- **提交**：`6947349`（2026-05-20）  
  **主题**：`feat(indoor): 室内导航与地图包室内图采集/加载链路`  
- **变更规模**：154 个文件（含后端室内模块、路线页、采集脚本、四校区 osm-data 重采、需求/技设/室内实现计划、单元测试及第十一周周报材料等）。

#### 1.2.2 FR-004-5 室内导航（本周核心交付）

**后端**

- 新增 `com.travel.indoor.*`：`IndoorGraphRegistry`、`IndoorPathPlanner`（自建图 + Dijkstra，边权为米；竖向边使用 `app.indoor.vertical-edge-distance-meters`）、`IndoorSeedCompleteness`（完整度门禁）、`IndoorLevelLabel` 等。
- `IndoorController` / `IndoorServiceImpl`：建筑列表、楼层、室内规划等 REST 接口；`Poi.indoorAvailable` 与 `RouteServiceImpl` 中 `nodeDetails[].indoorAvailable` 对外暴露。
- 种子与热加载：`IndoorDevSeedLoader`、`IndoorMapPackPaths`、`IndoorSeedReloader`；从 `osm-data/*/latest/indoor` 与 `map-imports.json` 联动加载；`dev-seed/indoor/502.json` 作为图书馆演示包。
- 管理端：`AdminOsmCollectService` / `AdminOsmCollectServiceImpl` 异步 OSM 采集（室内外分阶段），避免长任务 HTTP 502；`application-dev.yml` 排除 `osm-data/**`、`dev-seed/indoor/**` 触发 DevTools 误重启。

**前端**

- `RoutePlannerView.vue`：室外拓扑图识别 `indoorAvailable` POI → 点击进入室内视图；楼层切换；层内起终点与路径高亮；返回室外图。
- `frontend/src/lib/api.ts`：室内相关类型与接口封装；`vite.config.ts` 代理超时调整以适配长采集任务。

**采集脚本与数据**

- `scripts/indoor_seed.py`（新建）、`scripts/osm_seed.py`（扩展 `--collect-indoor`、室内 POI 过滤、Nominatim lookup 顺序、Overpass `around` 回退等）。
- `scripts/refresh_map_packs_with_indoor.py`、`scripts/sync_map_imports.py`、`scripts/verify_osm_outdoor.py`。
- 四校区地图包重采与 indoor manifest；示例：**沙河校区-学苑路** `areaId=248` 含室内建筑 `900020599`（HANDOFF 记录已验证 `GET /api/indoor/buildings?areaId=248`）。

**文档与测试**

- 需求文档、技术设计文档同步 FR-004-5～5-4；新增 `docs/Tech/Indoor Navigation Implementation Plan.md`、`docs/sql/migration_indoor_navigation.sql`。
- 单测：`IndoorPathPlannerTest`、`IndoorSeedCompletenessTest`、`IndoorDevSeedBundleTest`、`IndoorLevelLabelTest`、`IndoorMapPackPathsTest`、`Indoor900020327CompletenessTest` 等。

#### 1.2.3 稳定 2：文档对齐与阶段材料（进行中 / 已局部完成）

- **第九～十周阶段周报**：补写 `Weekly_Report_Week9-10.md`（计划目标、中检演示与日记权限联调口径），与项目计划第 9～10 周节点对齐。
- **交接记录**：`docs/AI/HANDOFF.md` 持续追加 2026-05-20 室内/OSM/Nominatim/DevTools 等多项修复与验证结论（共十余条）。
- **接口与校验**：认证模块补充 `GET /api/auth/me`、`POST /api/auth/avatar`（2MB、jpg/png 限制，落盘 `data/media`）；统一使用 `ApiResponse` 的 `code`/`message` 返回未登录、用户不存在等场景（工作区已改，待提交）。
- **全链路回归 v1**：已针对室内、路线、管理端 OSM 采集、`mvn test`（Indoor*）、`npm run build` 做模块级验证；**正式缺陷分级清单与独立《回归测试报告 v1》文档尚待合入仓库**（见 1.5）。

#### 1.2.4 前端体验与账号能力（工作区迭代，部分未提交）

- **布局与样式**：`AppLayout.vue` 导航与登录态展示优化；`explorescape.css`、`premium.css`、新增 `ui-enhancements.css`、`form-row.css`（设施/美食筛选行控件对齐，2026-05-23 已 build 验证）。
- **组件**：`UserAvatar.vue`；个人资料页 `ProfileView.vue` 重构（头像、兴趣等）；`DiaryListView.vue`、各业务列表/详情页样式统一。
- **AI 配置（本地）**：`frontend/src/lib/aiConfig.ts`、`stores/aiConfig.ts` 按用户 ID 持久化 endpoint/model/apiKey，供 About/旅行顾问等页面可选配置（密钥仅存浏览器 localStorage，不入库）。

#### 1.2.5 其它

- `dev-seed/diaries.json` 演示日记条目微调，便于列表/详情/动画联调。
- 第十一周周报 HTML/DOCX 已随 `6947349` 入库；本周产出本 Markdown 周报。

### 1.3 验证与质量门禁

| 类别 | 命令 / 场景 | 结果 |
|------|-------------|------|
| 后端单测 | `mvn test`（含 `Indoor*` 套件） | BUILD SUCCESS（提交记录与 HANDOFF） |
| 前端构建 | `npm run build` | SUCCESS（室内路线页、form-row 改动后均已构建） |
| 室内演示 | dev 启动；`areaId=201` 图书馆 502；`areaId=248` 学苑路室内楼 | 室外图可进室内、楼层切换与规划可用（HANDOFF 2026-05-20） |
| 管理端采集 | Admin OSM 异步任务轮询 | 避免同步 502；采集结束 `IndoorSeedReloader` 热加载 |
| 认证（工作区） | 登录后 `/api/auth/me`、头像上传 | 本地联调通过（待提交后纳入 CI） |

**关键回归项（第一轮抽样）**

- 室外 `POST /api/route` 不受室内模块影响；仅 `indoorAvailable=true` 的 POI 可进室内视图。
- OSM 种子：Nominatim lookup、Overpass 504 回退、BUPT 沙河校区别名/DevTools 误重启问题已按 HANDOFF 修复。
- 日记 AIGC（FR-009-5）：沿用第十一周能力；**答辩环境即梦/LibTV 端到端出片**仍依赖合作方密钥，本周未新增阻断性代码缺陷。
- 设施/美食/风景等列表页：表单行对齐后布局无错位（`form-row.css`）。

### 1.4 风险与应对

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| OSM 室内数据稀疏，多数 POI `indoorAvailable=false` | 中 | 高 | 完整度门禁 + 演示固定 POI（502/900020599）；答辩说明覆盖策略 |
| 地图包体积大、git diff 噪声高 | 低 | 高 | 评审材料以功能与接口为主；种子变更单独附录 |
| 稳定 2「错误码统一」未全仓扫完 | 中 | 中 | 第 13 周继续收敛 Controller 校验文案；输出缺陷清单 v2 |
| 回归测试报告 v1 未独立成文 | 中 | 中 | 将本周表格与 HANDOFF 条目整理为 `docs/Progress/` 下正式报告 |
| 工作区 UI/认证改动未提交 | 中 | 中 | 答辩前合并提交并再跑一轮 `mvn test` + `npm run build` |
| AIGC 厂商密钥/配额 | 高 | 中 | 演示前实机验证；保留 LibTV 回退与 `[JIMENG_DONE_NO_URL]` 诊断 |

### 1.5 进度评估

- **完成度**：约 **82%**（相对第 12 周计划）。  
  - **已达成**：FR-004-5 主链路贯通；室内相关文档/单测/四校区数据管线；模块级回归与 HANDOFF 可追溯。  
  - **部分达成**：稳定 2 的「全仓错误码统一」与「回归测试报告 v1」独立文档。  
  - **延续至下周**：预验收材料汇编、AIGC 答辩环境实机、未提交的前端/认证改动合入。

- **里程碑**：
  - 满足计划书第 12 周「稳定 2」中的**文档对齐**与**第一轮回归（模块级）**；
  - 超出原计划节奏：提前交付 **FR-004-5 室内导航** 可演示版本，增强路线模块创新性。

- **交付物**：
  - 已入库：`6947349` 室内导航全栈实现、技设/需求/室内方案、Indoor 单测、osm-data 地图包与脚本
  - 本文件：`Weekly_Report_Week12.md`
  - 工作区待提交：全站 UI、认证/me/头像、form-row、aiConfig 等
  - 待补：《回归测试报告 v1》正式稿（可由本节 1.3 表格扩展）

### 1.6 下周计划（2026-05-25 至 2026-05-31，第 13 周「优化」）

- 按第 13 周计划：路径计算与检索响应性能对比记录；继续改进交互细节（列表空态、加载态、移动端适配抽查）。
- 合入并回归本周工作区改动；完成《回归测试报告 v1》与缺陷分级清单定稿。
- 答辩环境端到端：推荐 → 路线（含室内演示 POI）→ 设施/美食 → 日记检索 → AIGC 动画（可选）。
- 启动第 14 周预验收材料（演示脚本、风险关闭表、NFR-006 创新性说明）骨架。

---

**编写日期**：2026-05-24

**依据提交**：`69473495e6ee5fc54e68246e966c7e02ef6680ee`（及截至当日的未提交工作区变更说明）
