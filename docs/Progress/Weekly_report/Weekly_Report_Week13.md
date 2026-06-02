# 个性化旅游推荐系统 - 第十三周工作周报

成员：陈逸、程小路

## 1. 第十三周（2026-05-25 至 2026-05-31）：性能与体验优化 + 账号/日记交互收敛

### 1.1 工作目标

本周对照《项目计划书》第 13 周「优化」与第十二周遗留项，重点为：

- **性能调优**：对照 **NFR-001**（系统响应 ≤3s、路径规划 ≤2s、全文检索 ≤1s），在 dev-seed 规模下评估室外/室内路径计算与内存检索链路，形成性能对比记录。
- **可用性与交互**：合入第十二周工作区改动；改进列表空态、加载态、搜索联想防抖、移动端导航与全站视觉一致性。
- **账号与个性化**：补齐 `GET /api/auth/me`、头像上传与个人资料页；本地 AI 助手配置与 About 页联调。
- **质量收敛**：在优化后执行模块回归；为第 14 周预验收准备演示链路与缺陷清单。

### 1.2 具体完成工作

#### 1.2.1 变更概况

- **Git 提交**：本周无新增主干提交；优化与体验改动集中于**工作区**（基于 `6947349` 室内导航基线之上迭代，待答辩前合入）。
- **变更规模**（`git diff --stat` 抽样）：约 24 个文件、+3000 / −2000 行；核心涉及认证、布局、日记列表、个人资料、样式与 dev-seed 微调。

#### 1.2.2 性能优化与对比记录（计划交付：性能对比记录）

**架构基线（优化前已具备，本周复核与记录）**

| 模块 | 实现方式 | 复杂度 / 说明 |
|------|----------|----------------|
| 室外路径规划 | `InMemoryStore` 预加载图 → `RouteServiceImpl` + 自建 `Dijkstra`（MyPriorityQueue） | 单次规划 O((V+E) log V)；dev-seed 路网 JSON 约 1.9 万行量级 |
| 室内路径规划 | `IndoorGraphRegistry` 内存图 → `IndoorPathPlanner` + 同套 Dijkstra | 演示 POI（502 / 900020599）节点规模小，毫秒级 |
| 设施/美食检索 | `NGramInvertedIndex` + 内存过滤 | 禁止 SQL LIKE；候选集截断 `limit * 5` |
| 日记全文检索 | `NGramInvertedIndex`（`diaryFullTextIndex`）+ 用户/目的地过滤 | 候选集截断 `limit * 10`；dev-seed 日记条目少，响应极快 |

**本周前端侧优化（降低无效请求、改善感知性能）**

- **日记列表搜索联想**：`DiaryListView.vue` 对输入做 **180ms 防抖**（`suggestTimer`），并以 `suggestSeq` 丢弃过期响应，避免连击触发检索风暴。
- **加载与空态**：列表区 `v-loading`；无数据时展示「暂无日记」空态，减少用户重复点击。
- **兴趣标签 Chip 过滤**：按用户兴趣权重排序展示，减少全量列表浏览成本。

**NFR-001 对照（dev profile + dev-seed，2026-05-31 验证）**

| 指标 | 目标 | 本周结论 | 验证方式 |
|------|------|----------|----------|
| 路径规划计算 | ≤2s | **满足**（内存图 + Dijkstra；室内单测与沙河校区室外图手工冒烟均秒级返回） | `mvn -q "-Dtest=IndoorPathPlannerTest,IndoorDevSeedBundleTest" test` SUCCESS |
| 全文/关键词检索 | ≤1s | **满足**（内存倒排索引；dev 日记/设施规模下 API 响应为毫秒～百毫秒级） | 日记列表联想 + `apiDiarySearch` 联调 |
| 端到端页面响应 | ≤3s | **满足**（首屏依赖预加载种子；前端 build 约 11.6s，运行时无阻断性长任务） | `npm run build` SUCCESS |

#### 1.2.3 可用性与交互优化（计划交付：优化变更清单）

| 类别 | 文件 / 模块 | 变更要点 |
|------|-------------|----------|
| 全站视觉 | `ui-enhancements.css`、`explorescape.css`、`premium.css` | 主题滚动条、入场动画、卡片 hover；对齐 `code/code1.html` 参考稿 |
| 移动端 | `AppLayout.vue` | `@vueuse/core` `useMediaQuery('(max-width: 720px)')` 抽屉导航；顶栏展示 `UserAvatar` |
| 表单对齐 | `form-row.css`、`FacilityView.vue`、`FoodView.vue` | 搜索行 input/select/button 统一圆角与 placeholder 左对齐（HANDOFF 2026-05-23） |
| 日记列表 | `DiaryListView.vue` | Lookbook 卡片栅格、兴趣 Chip、「推荐/兴趣」切换、搜索联想下拉、排序与空态 |
| 个人资料 | `ProfileView.vue`、`UserAvatar.vue` | 头像上传预览、兴趣 ECharts 分布、AI 配置入口、标签目录与首页一致 |
| 旅行助手 | `AboutView.vue`、`aiConfig.ts`、`stores/aiConfig.ts` | 按用户 ID localStorage 持久化 endpoint/model/apiKey；未配置时禁用发送 |
| 认证 API | `AuthController.java`、`UserServiceImpl.java`、`api.ts`、`auth.ts` | `GET /api/auth/me`、`POST /api/auth/avatar`（2MB、jpg/png）；`ApiResponse` 统一 401/400/404 文案 |
| 其它页面 | `HomeView.vue`、各 Detail/Login/Register | 标签目录加载、样式微调与布局收敛 |

#### 1.2.4 文档与阶段材料

- 输出 **`Weekly_Report_Week12.md`**（第十二周，2026-05-24）。
- 输出本文件 **`Weekly_Report_Week13.md`**。
- **`docs/AI/HANDOFF.md`** 延续记录 form-row、周报产出等条目。
- **《回归测试报告 v1》**：第十二周模块级结论已写入第 12 周周报 §1.3；**独立文档仍待第 14 周预验收前定稿**。

#### 1.2.5 数据

- `dev-seed/diaries.json`：演示日记条目微调，便于列表/检索/详情联调。

### 1.3 验证与质量门禁

| 类别 | 命令 / 场景 | 结果 |
|------|-------------|------|
| 后端单测 | `mvn -q "-Dtest=IndoorPathPlannerTest,IndoorDevSeedBundleTest" test` | BUILD SUCCESS |
| 前端构建 | `npm run build` | SUCCESS（约 11.6s；既有 chunk size 提示） |
| 认证 | 登录 → `/api/auth/me` → 上传头像 → 顶栏/资料页刷新 | 工作区联调通过 |
| 日记 UX | 搜索防抖、Chip 过滤、空态与 loading | 工作区联调通过 |
| 移动端 | ≤720px 抽屉导航、资料页可访问 | 抽查通过 |
| 端到端演示链 | 推荐 → 路线（含室内 POI）→ 设施/美食 → 日记检索 → About 助手 | 主链路可演示；AIGC 动画仍依赖即梦/LibTV 密钥 |

**关键回归项（优化后抽样）**

- 室外/室内路径规划结果与第十二周一致，优化未改动算法契约。
- 日记检索仍走内存索引，未引入 SQL LIKE。
- 头像路径校验（目录穿越防护）与大小/格式限制生效。
- 设施/美食页 form-row 样式不影响原有筛选逻辑。

### 1.4 风险与应对

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| 优化改动尚未 git 合入 | 中 | 高 | 预验收前统一提交并再跑 `mvn test` + `npm run build` |
| 缺少 formal 压测数据 | 低 | 中 | 预验收前补 curl 抽样表；答辩以 dev-seed 规模说明 |
| 工作区 diff 面大、评审噪声 | 低 | 中 | 按「认证 / UI / 日记 / 样式」拆分 commit 或 PR 说明 |
| AIGC 答辩环境未实机出片 | 高 | 中 | 保留 LibTV 回退；演示脚本标注「可选环节」 |
| 《回归测试报告 v1》未独立成文 | 中 | 中 | 第 14 周并入预验收包 |

### 1.5 进度评估

- **完成度**：约 **85%**（相对第 13 周计划）。
  - **已达成**：NFR-001 在 dev 环境下的架构复核与对比记录；交互/移动端/账号/日记列表等优化变更清单落地（工作区）；模块回归通过。
  - **部分达成**：性能数据为定性 + 模块验证，缺独立压测脚本；git 合入与正式优化变更 commit 待办。
  - **延续至下周**：预验收报告、风险关闭清单、回归报告定稿、发布候选版本冻结。

- **里程碑**：
  - 满足计划书第 13 周「优化」中的**体验改进**与**性能对比记录（架构级 + dev 验证）**；
  - 为第 14 周「预验收」准备好可演示的前后端与材料骨架。

- **交付物**：
  - 工作区：认证/me/头像、全站 UI、日记列表增强、form-row、aiConfig 等
  - 文档：`Weekly_Report_Week13.md`、HANDOFF 更新
  - 待补：git 合入提交、独立压测抽样表、《回归测试报告 v1》定稿

### 1.6 下周计划（2026-06-01 至 2026-06-07，第 14 周「预验收」）

- 按第 14 周计划：答疑后完成预验收；核心功能回归通过率目标 ≥95%；风险关闭率 ≥90%。
- 合入本周全部工作区改动；冻结发布候选分支/标签。
- 在目标答辩环境完成即梦/LibTV 端到端出片验证；收集剩余 P1/P2 缺陷并清零阻断项。
