# 个性化旅游推荐系统 - 第十一周工作周报

成员：陈逸 程小路

## 1. 第十一周（2026-05-11 至 2026-05-17）：FR-009-5 日记 AIGC 旅游动画落地 + 答疑前稳定性收敛

### 1.1 工作目标

本周对照《项目计划书》第 11 周「稳定 1」与教学节点「答疑（5 月 14 日）」安排，重点为：

- 落实 **FR-009-5 旅游动画生成**：基于日记文字与图片，服务端异步调用第三方云端视频能力，成片下载落盘并与日记关联。
- 对齐火山即梦官方 **CVSync2AsyncSubmitTask / CVSync2AsyncGetResult** 接口，修复历史 `req_key` 不支持、轮询 `done` 但无 URL 等阻断问题。
- 前端完成日记详情页「提交参数快照 → 任务轮询 → 事件日志 / 停止任务」闭环；密钥由合作方按模板自备，不入库。
- 同批次巩固路线规划、OSM 种子与 dev-seed 数据一致性，并同步需求/技术设计/交接文档。

### 1.2 具体完成工作

#### 1.2.1 本周主干提交

- **提交**：`48ba5c7`（2026-05-10）  
  **主题**：`feat: 日记 AIGC 旅游动画（即梦/LibTV）、共用提示词与前端任务流；路线/地图/OSM 种子与文档同步`  
- **变更规模**：67 个文件，涵盖后端动画模块、前端日记页、配置模板、单元测试、地图种子与多份文档（`HANDOFF`、需求、技术设计等）。

#### 1.2.2 需求与技术口径同步

- **需求文档**（`docs/Requirements/Requirements Documendation.md`）：
  - 明确 **FR-009-5**：可选画幅/风格/时长/补充提示词；异步任务；服务端下载落盘后以本站 URL 关联日记；LibTV 为可选备用会话型链路。
  - 补充 §2.3.3 旅游动画持久化策略与创新点中 AIGC 动画表述。

- **技术设计文档**（`docs/Tech/Technical Design Document.md`）：
  - 日记动画 REST、任务状态机、`AnimationProperties` 配置项、即梦/LibTV 分工与 dev 可选加载 `jimeng-animation.yml` 说明。

- **数据库迁移脚本**：`docs/sql/migration_add_animation_url.sql`（日记表 `animation_url` 字段）。

- **合作伙伴密钥模板**（可提交）：`src/main/resources/config/jimeng-animation.example.yml`；本地 `jimeng-animation.yml` 已加入 `.gitignore`。

#### 1.2.3 后端：日记 AIGC 动画任务链路

- **任务编排**：
  - `DiaryAnimationJob`：内存任务状态、`eventLog`、进度与取消；即梦优先，未出片时回退 **LibTV**。
  - `DiaryAnimationServiceImpl` / `DiaryAnimationController`：提交生成、查询状态、取消、（可选）消息接口；`submitGenerate` 返回 `{ jobId, generationParams }`。
  - `Diary` 实体与 `DiaryServiceImpl`：关联 `animation_url` 字段。

- **即梦（火山视觉）**：
  - `JimengVisualClient`：改用 **CVSync2Async*** 提交与轮询；校验 `ResponseMetadata` / 业务 `code`；默认文生 `req_key` 修正为 `jimeng_ti2v_v30_pro`。
  - `JimengVideoSubmitBuilder`：`prompt` 截断、`frames`/`seed`、图生 `image_urls` 或 `binary_data_base64`、合并 `extra-submit-json`。
  - `JimengVideoUrlExtractor`：从 `resp_json`、`video_result[]`、多类 url 键及 volces/tos 直链解析成片地址；`done` 无 URL 时写 `[JIMENG_DONE_NO_URL]` 便于排查。

- **LibTV 备用**：`LibTvOpenApiClient` 多轮会话、助手拒绝关键词启发式、成片 URL 正则抽取（含大小写不敏感与正文 https 备用规则）。

- **共用提示词**：`AnimationPromptComposer` + `AnimationGenParams`，即梦与 LibTV 共用参数段落文案。

- **配置与依赖**：`AnimationProperties`、`AnimationConfiguration`；`pom.xml` 增加 `volc-sdk-java`；`application.yml` / `application-dev.yml` 占位与环境变量说明。

- **其它同批**：`MediaPathResolver` 公网附件拼接；`RouteServiceImpl` / 路网图结构延续上周交通工具维度改造后的联调项。

#### 1.2.4 前端：日记详情动画任务流

- **`frontend/src/lib/api.ts`**：动画任务类型、`generationParams`、任务状态/日志/取消等 API 封装。
- **`DiaryDetailView.vue`**：
  - 展示「本次任务参数（提交快照）」；
  - 轮询任务状态、可折叠 **完整事件日志**（默认展开）、**停止任务**；
  - 已精简「与云端服务商多轮沟通」独立 UI 块，降低演示复杂度。
- **`DiaryEditorView.vue`**：编辑页返回日记列表导航优化。

#### 1.2.5 数据、脚本与 dev 演示

- **OSM / 高德种子**：`scripts/osm_seed.py`、`scripts/amap_seed.py` 与多份 `roads.append.json`、`facilities.append.json`、`pois.append.json` 等 dev-seed 同步更新。
- **演示约束**：`dev` profile + `app.debug.ignore-db-connection-failure=true` 下以内存任务为主；写库失败不阻断内存态（与 `AGENTS.md` / `PROJECT_CONTEXT` 一致）。

#### 1.2.6 单元测试与协作说明

- **新增单测**：
  - `AnimationPromptComposerTest`
  - `JimengVideoSubmitBuilderTest`（6 例）
  - `JimengVideoUrlExtractorTest`
- **提交说明**：commit body 含「合作伙伴须知」——各方复制 `jimeng-animation.example.yml` 为本地 `jimeng-animation.yml` 或注入 `VOLCENGINE_*` / `LIBTV_*` 环境变量，禁止提交真实密钥。

### 1.3 验证与质量门禁

- **后端**：`mvn test`（含上述动画相关单测）— BUILD SUCCESS。
- **前端**：`npm run build` — SUCCESS（仅有既有 chunk size 提示）。
- **关键回归项**：
  - 未配置密钥时任务可创建并给出明确失败/回退日志，不拖垮日记主链路。
  - 配置有效即梦 AK/SK 后可提交异步任务并轮询状态；成片 URL 解析失败时有 `[JIMENG_DONE_NO_URL]` 诊断信息。
  - 日记详情页可查看参数快照、事件日志并取消进行中的任务。

### 1.4 风险与应对

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| 火山产品线变更导致 `req_key` 再次不支持 | 高 | 中 | 以控制台文档为准覆盖配置；客户端对 `not supported` 给出配置项提示 |
| 轮询 `status=done` 但响应 JSON 结构变化导致无 URL | 高 | 中 | `JimengVideoUrlExtractor` 多层解析 + 日志片段；按新样本扩展解析规则 |
| 合作方误用方舟 apikey 或密钥未开通产品线 | 中 | 中 | `jimeng-animation.example.yml` 与 commit 说明强调 IAM AK/SK |
| LibTV 额度/异步时长/内容审核 | 中 | 中 | 即梦优先；LibTV 作备用；演示前确认试用额度 |
| 公网 `public-base-url` 不可达导致图生失败 | 中 | 低 | 支持 `binary_data_base64` 回退；演示环境使用可访问基址 |
| 同批大量 OSM 种子 diff 影响评审可读性 | 低 | 高 | 周报与答辩材料以功能模块与接口为主，种子变更单独附录 |

### 1.5 进度评估

- **完成度**：约 88%（FR-009-5 主链路已贯通；厂商侧密钥与成片格式仍依赖部署环境，需在答辩环境做一次端到端实机验证）。
- **里程碑**：
  - 满足第 9 周中检后「日记 + 创新 AIGC」演示能力；
  - 对齐第 11 周「稳定 1」：高优先级 API 对齐、异常与日志可观测、关键单测补齐。
- **交付物**：
  - 后端动画模块（即梦/LibTV/任务状态机/URL 解析/配置模板）
  - 前端日记详情动画任务 UI
  - FR-009-5 与技设文档、SQL 迁移、合作伙伴密钥说明
  - 动画相关单元测试与 `HANDOFF` 记录

### 1.6 下周计划（2026-05-18 至 2026-05-24）

- 按第 12 周「稳定 2」：统一接口错误码与校验文案，执行第一轮全链路回归并输出缺陷清单。
- 在目标答辩环境完成即梦/LibTV 实机出片与落盘回放验证；收集 `[JIMENG_DONE_NO_URL]` 样本完善解析器。
- 补齐日记/动画模块集成测试或接口回归脚本；收敛 dev-seed 与演示数据集体积。
- 开始整理预验收材料（演示脚本、风险关闭表、NFR-006 创新性说明）。

---

**编写日期**：2026-05-17

**依据提交**：`48ba5c72d9f8246e09c572b53a2c7b0c6b53f81b`
