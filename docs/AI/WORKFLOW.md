# WORKFLOW

## 1. 目标
建立可跨会话、跨 AI（Copilot/Claude/Gemini/Cursor 等）复用的统一工作流。

## 2. 标准流程（每次开发）
1. 读取上下文：先看 `docs/AI/PROJECT_CONTEXT.md`。
2. 锁定任务：明确 FR 编号、影响模块、验收标准。
3. 变更实现：仅修改与目标相关文件，避免无关重构。
4. 本地验证：至少做构建/关键路径验证。
5. 记录交接：更新 `docs/AI/HANDOFF.md`。
6. 同步文档：若接口/行为变化，更新对应设计或规范文档。

## 3. 分工建议（双人+AI）
- 成员 A：需求/前端主责
- 成员 B：后端/算法主责
- AI：
  - 生成初稿与重构建议
  - 编写测试样例
  - 做一致性检查（需求 vs 代码 vs API）

## 4. 提示词模板（通用）

### 4.1 新会话启动模板
"""
你正在接手 BUPT_PersonalizedTravelRecommendationSystem。
先阅读 docs/AI/PROJECT_CONTEXT.md 与 docs/AI/WORKFLOW.md。
然后用以下格式回复：
1) 你对项目现状的理解（不超过10行）
2) 本次任务计划（3-5步）
3) 需要我确认的风险点
确认后再开始改代码。
"""

### 4.2 功能开发模板
"""
目标 FR：FR-xxx
本次只实现：xxx
约束：
- 搜索相关必须内存索引，不得用 SQL LIKE/JOIN 做检索
- 核心算法与数据结构不得直接使用 Java 内置集合实现（ArrayList/Map/Set/PriorityQueue 等），需使用自定义结构
- 保持现有 API 返回结构 code/data/message
完成后请输出：
- 修改文件清单
- 验证方式与结果
- 对 docs/AI/HANDOFF.md 的更新内容
"""

### 4.3 代码评审模板
"""
请对以下改动做 review，重点关注：
1) 行为回归风险
2) 需求覆盖是否完整（关联 FR 编号）
3) 性能和安全问题
4) 是否违反内存检索约束
按严重程度输出问题，并给出建议修复。
"""

## 4.1 演示模式（无数据库 / 内存为主）
- 启动：`spring.profiles.active=dev`，并依赖 `application-dev.yml` 中的 `app.debug.ignore-db-connection-failure=true`。
- 实现约束：**AIGC 动画**及任何「先内存、后可选落库」的接口，在 DB 不可用时**禁止**因写库异常导致任务失败；须捕获连接类异常并仅更新 `InMemoryStore`（与 `PROJECT_CONTEXT` §3 一致）。

## 5. 文档更新规则
- `docs/AI/PROJECT_CONTEXT.md`：当架构、环境基线、功能状态发生变化时更新。
- `docs/AI/HANDOFF.md`：每次会话都要追加（不可覆盖历史）。
- 技术文档：接口、架构、数据结构变更时同步更新。

## 5.1 HANDOFF 记录规范（精简）
- 每条记录必须包含 `负责人`，以当前仓库 `git config user.name` 为准（可附 `git config user.email`）。
- 同一功能/同一 bug 在后续会话已解决时，优先“合并更新”到最近相关记录，避免重复追加长篇排障过程。
- HANDOFF 以“结果导向”记录：仅保留 `实现了什么`、`如何验证`、`影响文件`，中间排障曲折默认省略。
- 若必须保留背景，仅写 1 行“原因摘要”，避免复制日志与长栈信息。

## 6. 质量门禁（建议）
- 必过项：编译通过、核心流程可跑通。
- 强烈建议：新增或变更核心逻辑时补单元测试。
- 发布前：执行一次“需求覆盖检查表”。
