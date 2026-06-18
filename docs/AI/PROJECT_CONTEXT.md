# PROJECT_CONTEXT

最后更新：2026-06-09

## 1. 项目定位
- 名称：个性化旅游推荐系统（数据结构课程设计）
- 目标：实现推荐、路线规划、设施查询、美食推荐、日记管理与后台管理。
- 重点：数据结构与算法在业务中的落地能力。

## 2. 当前技术栈（已落地）
- 前端：Vue 3 + TypeScript + Vite + Pinia + Element Plus + ECharts
- 后端：Spring Boot 3.2.x + MyBatis-Plus + MySQL + Redis(可选)
- 鉴权：JWT + Spring Security

## 3. 演示与「无数据库 / 内存为主」模式（团队约定 — **JSON-only 已定稿**）
- **场景**：答辩与日常演示 **不依赖 MySQL**；业务数据仅来自 **`dev-seed` + `osm-data` JSON**，经 `DevSeedDataLoader` 写入 `InMemoryStore`。
- **启动要求**：使用 **`spring.profiles.active=dev`**，并开启 `app.debug.ignore-db-connection-failure=true`；**`app.storage.preload.enabled=false`**（见 `application-dev.yml`）。
- **启动命令**（无需 MySQL）：
  ```powershell
  mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
  cd frontend; npm run dev
  ```
- **S2 执行细节**：[S2 Data Governance Execution Plan.md](../Tech/S2%20Data%20Governance%20Execution%20Plan.md) §2（R10 已固化）。
- **一致性要求**：凡在「DB 可能不可用」下仍需成功的路径（含 **FR-009-5 日记动画**：写 `animation_url`、下载落盘、内存更新日记），**不得因 Mapper 写库失败而中断**；写库须包在 try/catch 中，识别连接类异常后跳过并仅保留内存态（与 `DiaryServiceImpl` 等既有模式一致）。
- **其他功能**：新增若涉及「写库 + 内存双写」，须同样遵守上述回退策略，避免演示现场仅因未起 MySQL 而 500。

## 4. 架构事实（已验证）
- 控制器目录：`src/main/java/com/travel/controller`
- 服务实现目录：`src/main/java/com/travel/service/impl`
- 算法目录：`src/main/java/com/travel/algorithm`
- 内存数据层：`src/main/java/com/travel/storage`
- 启动预加载：`InMemoryDataLoader` 在启动时把数据库数据写入 `InMemoryStore`
- 检索索引：前缀 Trie 与 NGram 倒排索引在内存中执行检索
- 课程硬约束：数据库用于持久化；运行时核心查询/匹配/排序在内存与应用层完成
- 课程硬约束：核心算法与数据结构模块不得直接使用 Java 内置集合实现，需使用自定义结构（MyList/MyMap/MySet/MyPriorityQueue 等）

## 5. 核心功能实现状态（按 FR，详见需求文档 §9.2）

| 状态 | 数量 | 代表条目 |
|------|------|----------|
| **已实现** | 38 | FR-001/003、FR-004-1~3/004-5、FR-005~006、FR-007~008（除 8-2/8-6）、FR-009-1~4、FR-010~012/014 |
| **部分实现** | 7 | FR-002、FR-004（缺覆盖最多）、FR-008-2/8-6、FR-009、FR-013/13-3 |
| **待验收** | 1 | FR-009-5（代码已落地，需实机出片） |
| **本期不交付** | 3 | FR-004-4、FR-015、FR-016 |

> NFR-007 自研 DS：算法层已替换，见 `docs/Tech/Custom Data Structures Scope.md`。

- 基线提交：`main` @ `74a2e03`（室内导航 + UI/个人中心 PR 已合并）。
- 完整对照表：`docs/Requirements/Requirements Documendation.md` **§9.2**。

## 6. 需求-实现差距（收尾周优先）
- **答辩必做**：FR-009-5 配置密钥并完成 1 次实机动画出片；室内导航演示脚本（areaId 248/201）。
- **已知部分实现**（可书面说明）：FR-002 详情字段不全、FR-008-2 无管理端日记治理、FR-008-6/FR-013 无深度个性化。
- **数据规模（2026-06-13）**：12 canonical + 208 alias = **220 景区**；212 设施（15 type）；20003 道路；452 美食；24 日记。验收见 `scripts/validate_s2_closure.py`。
- **课程约束**：`com.travel.ds` 已在 **算法层**（Dijkstra/Graph/TopK/检索索引）落地；Service 边界见 `DsConvert` 与 [Custom Data Structures Scope.md](../Tech/Custom%20Data%20Structures%20Scope.md)。
- **工程**：`application.yml` 明文 DB 密码；开发日志与 HANDOFF 已持续更新。

## 7. 重要工程风险
- 文档要求 Node 16 / Java 11，但后端 `pom.xml` 当前为 Java 21，存在环境基线不一致。
- `application.yml` 出现明文数据库账号密码，应改为环境变量注入。
- 开发日志仅记录到 2026-03-09，和当前实现进度不一致。

## 8. 团队开发约定（当前建议版）
- 每次任务必须绑定 FR 编号。
- 每次任务结束必须更新 `docs/AI/HANDOFF.md`。
- 重要技术决策必须记录在 `docs/Tech/Architecture Review v2.md` 或新建 ADR 文档。
- 提交信息使用中文，格式：`[类型] 描述`。

## 9. 验收与答辩（S5）

- 入口：[docs/验收/README.md](../验收/README.md)
- 启动：[docs/验收/01-启动与运行.md](../验收/01-启动与运行.md)
- 演示：[docs/验收/02-答辩演示脚本.md](../验收/02-答辩演示脚本.md)
- 算法：[docs/验收/03-核心算法说明.md](../验收/03-核心算法说明.md)

## 10. 快速定位命令（给任何 AI）
- 查看后端入口：`src/main/java/com/travel/TravelSystemApplication.java`
- 查看前端入口：`frontend/src/main.ts`
- 查看路由：`frontend/src/router/index.ts`
- 查看接口封装：`frontend/src/lib/api.ts`
- 查看推荐控制器：`src/main/java/com/travel/controller/RecommendationController.java`
- 查看路线服务：`src/main/java/com/travel/service/impl/RouteServiceImpl.java`
- 查看搜索索引：`src/main/java/com/travel/storage/search`
