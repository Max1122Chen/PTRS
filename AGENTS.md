# AGENTS.md

本文件是本仓库的 AI 协作入口（跨会话、跨模型）。

## 1. 快速启动（每次新会话都执行）
1. 先阅读 `docs/AI/PROJECT_CONTEXT.md`，理解项目目标、架构、约束、当前进展。
2. 再阅读 `docs/AI/WORKFLOW.md`，按团队流程执行任务与日志更新。
3. 开始编码前，先在 `docs/AI/HANDOFF.md` 的今日区块写下“本次目标”。
4. 完成编码后，更新 `docs/AI/HANDOFF.md`：变更文件、验证结果、风险、下一步。

## 2. 演示与无数据库（内存为主）
- 答辩/演示可不依赖 MySQL：使用 **`dev` profile** + `app.debug.ignore-db-connection-failure=true` + `app.storage.preload.enabled=false`，以 **内存 + dev-seed + osm-data** 为主。
- 启动：`mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`；前端 `cd frontend && npm run dev`。
- **AIGC 日记动画**及所有「内存 + 可选写库」功能：数据库不可用时**不得**因 Mapper 失败导致整条链路失败；写库失败应跳过并保留内存态（见 `docs/AI/PROJECT_CONTEXT.md` §3）。
- 即梦/LibTV 本地密钥：复制 `src/main/resources/config/jimeng-animation.example.yml` 为 **`jimeng-animation.yml`**（已 `.gitignore`），或使用环境变量。

## 3. 不可违反的项目约束
- 数据结构课程设计要求：核心算法必须自主实现，不得依赖外部闭源算法服务。
- 数据结构课程实现要求：核心数据结构与算法模块不得直接使用 Java 内置集合实现（ArrayList/Map/Set/PriorityQueue 等），需使用自定义结构（如 MyList/MyMap/MySet/MyPriorityQueue）。
- 搜索/模糊/全文检索/关联匹配：不得在 SQL 层用 LIKE/REGEXP/FULLTEXT/JOIN 完成检索计算。
- 必须采用：数据库预加载 -> 内存索引 -> 算法检索。
- 前后端接口响应统一：`code`/`data`/`message`。

## 4. 代码与文档同步原则
- 新增或修改功能时，必须同步：
  - 需求编号（FR）映射
  - API 说明
  - 开发日志
- 若实现与文档不一致，优先更新文档并明确“决策原因”。

## 5. 任务执行风格
- 小步提交：每次只完成一个逻辑变更。
- 优先补测试：至少补关键服务层单元测试。
- 先验证再交付：说明运行命令和结果。

## 6. 参考文件
- 验收与答辩：`docs/验收/README.md`、根目录 `README.md`
- 项目上下文：`docs/AI/PROJECT_CONTEXT.md`
- 协作流程：`docs/AI/WORKFLOW.md`
- 会话交接：`docs/AI/HANDOFF.md`
- 需求文档：`docs/Requirements/Requirements Documendation.md`
- 开发规范：`docs/Development Specification/Specification.md`
- 技术设计：`docs/Tech/Technical Design Document.md`
