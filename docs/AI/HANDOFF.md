# HANDOFF LOG

> 用途：跨会话、跨 AI 的最小必要交接记录。
> 规则：每次开发结束后追加，不要覆盖历史；已解决的同类问题应合并为结果导向记录；每条记录需标注负责人（git 用户）。

## 2026-04-13（会话目标：路线规划多点最优路径）
### 会话目标
- 支持路线页多点规划“最优访问顺序”而非按输入顺序直连。
- 新增“是否回到起点”开关：开启时最终回到第一个点；关闭时最终结束在最后一个点。
- 保持现有 `code/data/message` 返回结构与两点规划兼容性。

## 2026-04-13（FR-004 路线页多点输入体验升级）
### 负责人
- Ryemon（3267348244@qq.com）

### 新增/完成功能
- 路线页多点规划输入由“手输逗号分隔节点 ID”升级为“地点多选下拉”。
- 新增“访问顺序（可调整）”列表，支持上移/下移以手动控制多点顺序。
- 保留并强化规则提示：第一个点为起点；未回起点时最后一个点为终点。
- 与后端 `returnToStart` 开关联动保持一致（闭环/非闭环）。

### 验证结果
- 前端构建通过：`npm.cmd run build`（Vite build success）。
- 编辑器诊断通过：`RoutePlannerView.vue` 无新增 lints。

### 变更文件
- frontend/src/views/route/RoutePlannerView.vue
- docs/AI/HANDOFF.md

## 2026-04-13（FR-004 多点最优路径 + 回起点开关）
### 负责人
- Ryemon（3267348244@qq.com）

### 新增/完成功能
- 后端多点规划从“按输入顺序拼接”升级为“全局最优访问顺序”：
  - 第一个点固定为起点；
  - 未开启回起点时，最后一个点固定为终点；
  - 开启 `returnToStart=true` 时，最终回到第一个点形成闭环路径。
- `MultiPointRouteRequest` 新增 `returnToStart` 字段，兼容旧请求（默认 false）。
- 路线页新增“闭环设置”开关并透传 `returnToStart`，多点输入提示同步更新。
- 最优顺序求解采用位压缩 DP（TSP 路径变体），并在中间点超过 20 时给出友好错误提示。

### 验证结果
- 后端编译通过：`mvn -DskipTests compile`（BUILD SUCCESS）。
- 前端构建通过：`npm.cmd run build`（Vite build success）。
- 编辑器诊断通过：本次改动文件无新增 lints。

### 变更文件
- src/main/java/com/travel/model/dto/route/MultiPointRouteRequest.java
- src/main/java/com/travel/service/RouteService.java
- src/main/java/com/travel/service/impl/RouteServiceImpl.java
- frontend/src/lib/api.ts
- frontend/src/views/route/RoutePlannerView.vue
- docs/AI/HANDOFF.md

## 2026-03-31（会话目标：OpenStreetMap 采集脚本与样例审批）
### 会话目标
- 新增 OSM 采集脚本（Nominatim + Overpass）抓取校园内部可用数据。
- 映射到现有 `scenic_areas/buildings/facilities/roads` 结构并输出“仅审核草案”。
- 先提供样例结果供审批，不改写现有 seed 文件。

### 已完成
- 新增脚本 `scripts/osm_seed_draft.py`：
  - 基于 Nominatim 获取目标校园中心与 OSM 对象。
  - 基于 Overpass 抓取校园对象内（优先 area）命名要素、设施与步行道路候选。
  - 映射输出 `scenic_areas/buildings/facilities/roads` 四类审核草案。
- 新增配置文件 `scripts/config/osm_seed_config.json`。
- 已执行脚本并产出审核样例：
  - `docs/AI/data-drafts/osm_zhixin_review_20260331_102247/`
- 修正输出命名：草案文件由 `buildings.append.json` 调整为 `pois.append.json`（语义与 POI 对齐）。
- 增加 `--apply-seed` 开关：在用户批准后可将本次采集结果增量写入 dataseed。
- 调整输出路径：默认输出改为 `src/main/resources/osm-drafts/`，并按景区名拆分目录（每个景区单独文件夹）。
- 减少重复抓取：新增景区上下文缓存 `src/main/resources/osm-drafts/<景区>/_context.json`；后续执行优先复用缓存，不再重复基础定位请求。
- 已执行写入模式并验证：
  - 命令：`python scripts/osm_seed_draft.py --config scripts/config/osm_seed_config.json --apply-seed`
  - 结果：成功追加 `scenic_areas +1 / buildings(POI) +9 / facilities +1 / roads +8`
  - 当前 seed 规模：`scenic_areas=17, buildings=16, facilities=8, roads=16`

### 验证
- 命令：`python scripts/osm_seed_draft.py --config scripts/config/osm_seed_config.json`
- 结果：成功输出样例，统计为 `POI=9, Facilities=1, Roads=8`。

### 变更文件
- scripts/osm_seed_draft.py
- scripts/config/osm_seed_config.json
- docs/AI/HANDOFF.md

## 2026-03-31（后端配置化读取新增地图数据）
### 会话目标
- 通过配置文件声明后端需要读取的新增地图数据文件，而不是写死读取路径。

### 已完成
- `DevSeedDataLoader` 增加 `app.dev-seed.map-import-config` 配置支持。
- 新增配置模型 `MapImportConfig`，支持按清单导入：`scenicAreas / pois / buildings(兼容) / roads / facilities`。
- 导入逻辑支持多文件合并，按 `id` 去重覆盖，避免重复加载时同 ID 冲突。
- 新增默认配置文件 `src/main/resources/dev-seed/map-imports.json`。
- 已将当前 OSM 样例目录挂载到 `map-imports.json` 作为配置化导入示例。

### 验证
- `mvn -DskipTests compile` 通过。

### 变更文件
- src/main/java/com/travel/storage/DevSeedDataLoader.java
- src/main/resources/application.yml
- src/main/resources/dev-seed/map-imports.json
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-04-13（管理员 OSM 搜索错误透传）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 修复管理员 `OSM` 候选查询“静默空数组”问题：服务层在 `0` 结果且存在上游异常/非 `2xx` 时，抛出带细节原因的异常（含 query 变体、HTTP 状态码或异常类型）。
- 控制器 `/api/admin/dev/osm-search` 增加异常兜底，将上述原因以 `ApiResponse.failure(502, message)` 返回给前端，便于界面直接提示具体失败原因。
- 新增服务端日志：记录 Nominatim 非 `2xx` 和单次 query 异常，便于在后端控制台快速定位（如 `429` 限流）。

### 验证结果
- 后端编译通过：`mvn -DskipTests compile`（BUILD SUCCESS）。

### 变更文件
- src/main/java/com/travel/service/impl/AdminServiceImpl.java
- src/main/java/com/travel/controller/AdminController.java
- docs/AI/HANDOFF.md

## 2026-03-31（管理端任务：禁用 axios timeout 以防超时误报）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 原因摘要
- 管理端调用 `/api/admin/dev/generate-from-osm`、`/api/admin/dev/import-place` 属于长耗时任务。
- 前端全局 axios timeout 可能导致提前 abort，并显示“请求超时”误报。

### 已采取修复
- 在 `frontend/src/lib/http.ts` 的请求拦截器中：对上述 URL 将 `config.timeout` 设为 `0`（禁用客户端超时）。

### 验证方式
- 重启/重新加载前端后再次执行管理员“地名导入 -> 选择后生成”，确认不再出现“请求超时”。

## 2026-03-31（管理员导入/生成超时误报修复：前端 axios timeout）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 原因摘要
- 管理端 `/api/admin/dev/generate-from-osm` / `/api/admin/dev/import-place` 会执行 Python 采集并可能触发前端 build，单次耗时经常超过全局 axios 默认 `20000ms`。
- 超时后前端会走 axios error 分支，误提示“网络错误（无法连接后端）”，尽管后端任务可能仍在执行。

### 已采取修复
- 在 `frontend/src/lib/http.ts` 里对上述 URL 将 `config.timeout` 提高到 `180000ms`（3分钟）。

### 验证方式
- 重启/重新加载前端后，再执行管理员“地名导入 -> 选择后生成 / 导入”，确认不再出现“无法连接后端”超时误报。

## 2026-03-31（管理员任务日志中文乱码修复）
### 会话目标
- 修复“脚本执行日志返回前端时中文显示乱码”的问题。

### 已完成
- 后端子进程日志读取统一为 UTF-8：
  - `AdminServiceImpl#exec` 中 `InputStreamReader` 显式使用 `StandardCharsets.UTF_8`。
- 为 Python 子进程显式设置 UTF-8 输出环境：
  - `PYTHONIOENCODING=utf-8`
  - `PYTHONUTF8=1`

### 验证
- 后端编译：`mvn -f d:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/pom.xml -DskipTests compile` 通过。

### 变更文件
- src/main/java/com/travel/service/impl/AdminServiceImpl.java
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（重复判定优化 + 失败残留自动回收）
### 会话目标
- 解决“仅生成目录与 context 但未生成 latest 数据时，被误判已存在”的问题。

### 旧依据（问题来源）
- 旧逻辑按 OSM 实体身份匹配到 `_context.json` / `latest/raw/nominatim_top.json` 即判定重复，未区分是否存在可用 latest 数据。

### 新依据（已落地）
- 判定“已存在重复”必须同时满足：
  - OSM 实体身份匹配（`place_id` 或 `osm_type+osm_id`）。
  - 对应景区目录下存在完整且非空的 `latest/scenic_areas.append.json`、`latest/pois.append.json`、`latest/roads.append.json`。

### 失败回收（已落地）
- 在生成前会先清理该 OSM 实体对应的“不完整残留目录”（如只有 `_context.json` 或 latest 不完整）。
- 当 seed 执行失败（`seed_failed`）时，会再次触发同样的清理，避免下次被误判重复。
- 返回结果新增 `recycledIncompletePaths`，用于前端展示回收了哪些路径。

### 验证
- 后端编译：`mvn -f d:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/pom.xml -DskipTests compile` 通过。

### 变更文件
- src/main/java/com/travel/service/impl/AdminServiceImpl.java
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（修复：按选中 OSM 实体判重，避免关键词误拦截）
### 会话目标
- 解决“已存在北京邮电大学（沙河校区）时，再录入北京邮电大学（其他校区）被误判重复”的问题。

### 根因
- 旧逻辑在生成前按 `placeName` 调用本地 `contains` 模糊匹配判重，未使用管理员选中的 OSM 实体标识。

### 已完成
- 前端：
  - 管理页 OSM 单选由字符串 `displayName` 改为选中对象。
  - 生成请求新增 `selectedOsm`（`placeId/osmType/osmId/displayName/name`）。
- 后端：
  - `generate-from-osm` 接口透传 `selectedOsm` 唯一标识。
  - 服务层改为“精确判重优先”：扫描 `src/main/resources/osm-data` 下 `_context.json` 与 `latest/raw/nominatim_top.json`，按 `place_id` 或 `osm_type+osm_id` 判断是否同一实体。
  - 仅当“同一 OSM 实体”且非 `force` 时跳过；模糊本地匹配仅作为提示字段返回，不再阻断。
  - 返回结果新增：`exactDuplicate`、`selectedPlaceId`、`selectedOsmType`、`selectedOsmId`、`fuzzyLocalMatchesCount`、`fuzzyExists`。

### 验证
- 后端：`mvn -f d:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/pom.xml -DskipTests compile`。
- 前端：`npm.cmd run build` 通过。

### 变更文件
- src/main/java/com/travel/service/AdminService.java
- src/main/java/com/travel/controller/AdminController.java
- src/main/java/com/travel/service/impl/AdminServiceImpl.java
- frontend/src/lib/api.ts
- frontend/src/views/admin/AdminView.vue
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（ID 冲突双方案落地：全量来源发现 + 带锁注册表分配）
### 会话目标
- 一次性落地两套 ID 防冲突方案：
  - 方案A：分配前扫描全量来源（base seed + map-imports + osm-data）获取各实体最大 ID。
  - 方案B：引入仓库级 ID 注册表并加锁，支持并发场景下的原子号段分配。

### 已完成
- `scripts/osm_seed.py` 新增：
  - `discover_existing_max_ids()`：聚合扫描 `scenic_areas/buildings/facilities/roads` 的已用最大 ID。
  - `allocate_id_ranges()`：基于“全量最大 ID + 注册表高水位”分配新号段。
  - 文件锁机制（`*.lock`）确保注册表更新原子性，避免并发抢号。
  - 新增参数 `--id-registry`（默认 `src/main/resources/dev-seed/id-registry.json`）。
- 生成流程改造：
  - 先使用临时 ID 构建图与路网。
  - 生成完成后统一重映射为全局真实 ID（景区/POI/设施/道路全覆盖）。
  - 道路端点同步映射到新的 POI ID，保证引用一致性。
- 报告补充：`Map Imports` 区块追加 `idRegistryFile` 方便追踪。

### 验证
- `python -m py_compile scripts/osm_seed.py scripts/validate_osm_output.py` 通过。

### 变更文件
- scripts/osm_seed.py
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（OSM 生成目录命名改为基于 OSM 命中地址）
### 会话目标
- 调整 OSM 生成脚本目录命名策略：不再使用管理员输入名，改为使用 OSM 实际命中地址名，避免输入简称导致目录语义不完整。

### 已完成
- `scripts/osm_seed.py` 调整目录命名逻辑：
  - 先执行 Nominatim 查询得到命中结果 `top`。
  - 新增 `resolve_matched_dir_key()`，优先使用 `display_name`（其次 `name`）作为目录 key 来源。
  - `scenic_slug` 改为由该命中地址名生成。
- 报告增强：`report.md` 增加 `matchedAddressName` 与 `outputDirSlug` 字段，便于排查目录来源。

### 验证
- 执行 `python scripts/osm_seed.py --help`，参数解析与脚本语法正常。

### 变更文件
- scripts/osm_seed.py
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（OSM 景区名改为命中结果）
### 会话目标
- 在目录名基于 OSM 命中的基础上，进一步将生成的景区名也改为 OSM 匹配结果，避免管理员输入简称导致景区名不完整。

### 已完成
- `scripts/osm_seed.py` 新增 `resolve_matched_scenic_name()`：
  - 优先使用 Nominatim 命中 `name`，其次 `display_name`，最后回退 `target-name`。
- `scenic_areas.append.json` 的 `name` 字段改由匹配结果填充，不再直接使用管理员输入。
- 报告字段补充：
  - `targetNameInput`
  - `scenicNameMatched`

### 验证
- `python -m py_compile scripts/osm_seed.py` 通过（无语法错误）。

### 变更文件
- scripts/osm_seed.py
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（管理员开发工具按钮风格统一 + 后端重启）
### 会话目标
- 统一管理员开发工具区“查 OSM 候选”和“选择后生成”按钮的颜色风格。
- 完成后端服务重启。

### 已完成
- 前端样式调整：
  - 为开发工具区两个按钮统一使用 `type=primary` 与同一 `dev-action-btn` 样式类。
  - 新增统一主色渐变与 hover 态，消除按钮风格不一致。
- 后端重启：
  - 已释放 8080 端口旧进程并重新启动服务（dev profile）。
  - 启动日志显示 `Tomcat started on port 8080`，服务已拉起。

### 说明
- 启动过程中出现 MySQL 连接失败日志，但由于 dev 配置允许 DB 不可用时回退到内存种子，服务已成功启动并可用。

### 变更文件
- frontend/src/views/admin/AdminView.vue
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（前端生成失败排查：dev 环境管理员校验误拦截）
### 问题现象
- 管理员页面点击“选择后生成”失败，前端提示无权限或执行失败。

### 根因
- `application-dev.yml` 中 `app.security.auth-enabled=false`（开发态关闭鉴权）时，请求可能不带登录态。
- 但 `AdminController` 仍执行 `isAdmin()` 强校验，`SecurityUtil.getCurrentUser()` 为空导致返回 403。
- 结果表现为：开发环境能打开页面，但开发工具接口（`/api/admin/dev/*`）被误拦截。

### 修复
- `AdminController` 新增注入 `app.security.auth-enabled`。
- `isAdmin()` 调整为：当 `auth-enabled=false` 时直接放行（仅开发态生效）；开启鉴权时仍按 ADMIN 角色严格校验。

### 验证
- `mvn -f d:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/pom.xml -DskipTests compile` 通过。

### 变更文件
- src/main/java/com/travel/controller/AdminController.java
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（管理员地名导入交互优化：输入即查重 + OSM 模糊召回 + 生成/构建解耦）
### 会话目标
- 管理员输入地名时自动进行本地已有数据检索（不再依赖手工点击）。
- 保留“查询 OSM”按钮，但增强模糊召回能力（如“北京邮电大学”可召回“北京邮电大学（沙河校区）”等）。
- 管理员从 OSM 候选中选中后再生成数据；生成流程可选择是否自动触发前端构建。

### 已完成
- 前端管理员页面（开发工具）改造：
  - `placeName` 输入框新增防抖自动本地检索（350ms）。
  - 本地检索按钮移除，改为“输入即查重”。
  - 增加 `buildFrontend` 复选框，支持“生成后自动执行前端 build”开关。
  - OSM 按钮文案更新为“支持模糊”，流程为：查 OSM 候选 -> 选中 -> 生成。
- 后端 OSM 候选查询增强：
  - 对同一关键词构造多组 query 变体（原词、去括号主名、追加“校区”），并聚合去重返回。
  - 以 `osm_type + osm_id` 去重，最多返回 20 条候选。
- 后端生成接口增强：
  - `generate-from-osm` 新增 `buildFrontend` 参数（默认 true）。
  - 当 `buildFrontend=false` 时，仅执行采集，不执行前端构建。
- 落地开发工具安全开关：
  - 新增配置 `app.admin.dev-tools.enabled`（默认 true）。
  - `AdminController` 对 `/api/admin/dev/*` 接口统一校验该开关，关闭时返回 403。

### 验证
- 后端：`mvn -f d:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/pom.xml -DskipTests compile` 通过。
- 前端：`npm.cmd run build`（在 `frontend` 目录）通过。

### 变更文件
- src/main/java/com/travel/service/AdminService.java
- src/main/java/com/travel/controller/AdminController.java
- src/main/java/com/travel/service/impl/AdminServiceImpl.java
- src/main/resources/application.yml
- frontend/src/lib/api.ts
- frontend/src/views/admin/AdminView.vue
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（管理员地名导入流程细化 + 资源未新增问题修复）
### 会话目标
- 按架构化流程将“前端调用后端爬数据”拆分为三段：
  1) 本地已有地名匹配查询；
  2) OSM 候选结果查询（仅预览，不生成）；
  3) 管理员选择候选后再生成数据并执行前端 build。
- 排查“前端日志显示成功但资源未新增”的原因并修复。

### 已完成
- 后端新增开发接口：
  - `GET /api/admin/dev/local-place-search?keyword=...`（本地景区匹配）
  - `GET /api/admin/dev/osm-search?keyword=...`（OSM 候选查询）
  - `POST /api/admin/dev/generate-from-osm`（按选中 query 生成并 build）
- 前端管理员“开发工具”改为分步骤交互：显示本地匹配表、OSM 候选表（可选中），再触发生成。
- 修复核心根因：`scripts/osm_seed.py` 新增 `--skip-config`，避免配置文件覆盖命令行地名参数。
- 后端执行脚本时固定到项目根目录，避免工作目录漂移导致输出到错误位置。

### 问题根因（资源未新增）
- 之前后端调用脚本传了 `--target-name/--query`，但 `osm_seed.py` 会读取配置并覆盖参数，导致始终使用固定地名（看似执行成功，实际重复生成旧地点）。
- 同时未显式锁定脚本执行目录，存在相对路径输出不稳定风险。

### 验证
- 后端：`mvn -f ../pom.xml -DskipTests compile` 通过。
- 前端：`npm.cmd run build` 通过。

### 变更文件
- scripts/osm_seed.py
- src/main/java/com/travel/service/AdminService.java
- src/main/java/com/travel/service/impl/AdminServiceImpl.java
- src/main/java/com/travel/controller/AdminController.java
- frontend/src/lib/api.ts
- frontend/src/views/admin/AdminView.vue
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（管理员开发面板增强 + 设施并入 POI 重构方案）
### 会话目标
- 需求1：路线页去除对“POI”概念的用户暴露，统一使用“节点”表达。
- 需求2：管理员前端提供地名一键采集开发面板：输入地名 -> 后端检查是否已有数据 -> 无数据则触发 OSM 脚本 -> 自动执行前端 build。
- 需求3：以 PM 与 Architect 视角给出“设施并入 POI”的重构设计。

### 已完成（代码）
- 路线页文案优化：起点/终点 placeholder 由“POI”改为“节点”。
- 管理端新增开发工具能力：
  - 后端新增 `POST /api/admin/dev/import-place`。
  - 逻辑：检查景区名称/位置是否包含地名；若存在且非强制则跳过；否则执行 `scripts/osm_seed.py`，成功后执行 `frontend` 下 `npm run build`。
  - 返回执行状态、退出码和日志片段，便于前端展示。
- 前端管理员页面新增“开发工具”Tab：支持输入地名、强制重抓开关、查看脚本与构建日志。

### PM + Architect 重构方案（设施并入 POI）
- 目标定义：
  - 将“可在地图展示/可参与检索与路线关联的设施”统一纳入 POI 语义层。
  - `facility` 退化为 POI 的扩展详情（明细表/明细对象），不再与 POI 并行承担“点位实体”职责。
- 分层模型：
  - `poi`：统一点位主实体（id/name/type/location/lng/lat/areaId/...）。
  - `facility_profile`（建议新增）：以 `poi_id` 关联的设施明细（开放时间、服务能力、电话、可达性等）。
  - 路网节点中的 `virtual_node` 仍保留非业务节点语义，不进入候选与用户检索。
- 迁移策略（低风险分阶段）：
  1) 双写阶段：新增设施时同时写 `poi(type=facility_xxx)` + `facility`（兼容老逻辑）。
  2) 读扩展阶段：地图与搜索优先从 `poi` 读，设施详情从 `facility_profile/facility` 读扩展字段。
  3) 收口阶段：逐步下线“设施作为独立地图点”的旧读取路径，仅保留扩展明细职责。
- 接口建议：
  - 新增/扩展 `GET /api/poi` 支持 `type` 过滤（包含 facility 类）。
  - 保留 `GET /api/facility/detail/{id}`，但入参改为 `poiId` 或返回中明确 `poiId` 关联。
  - `map-data` 默认返回统一点位集合（业务 POI）；前端按 `type` 图层化展示。
- 验收标准：
  - 地图点位来源单一（POI），设施在地图上可见且详情可打开。
  - 旧设施接口兼容期内可用，不影响现有页面。
  - 路线候选仍不包含 `virtual_node`。

### 验证
- 后端：`mvn -f ../pom.xml -DskipTests compile` 通过。
- 前端：`npm.cmd run build` 通过。

### 变更文件
- src/main/java/com/travel/service/AdminService.java
- src/main/java/com/travel/service/impl/AdminServiceImpl.java
- src/main/java/com/travel/controller/AdminController.java
- frontend/src/lib/api.ts
- frontend/src/views/admin/AdminView.vue
- frontend/src/views/route/RoutePlannerView.vue
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（路线页圆圈兜底节点回归修复）
### 问题现象
- 前端路线图再次出现“兜底圆圈节点”，路网看起来围成一圈。

### 根因
- 之前将 `map-data` 的 `nodeDetails` 收敛为“仅业务 POI”（用于起终点候选解耦）后，前端 `buildNodePositionMap` 仍只从 `nodeDetails` 取坐标。
- 虚拟道路节点缺失坐标后触发 fallback 圆圈布局，导致可视上出现环状兜底节点。

### 修复
- 后端 `RouteServiceImpl#getMapData` 新增 `nodeGeo` 字段：返回图中所有节点（含 virtual_node）的最小坐标信息（`nodeId/type/longitude/latitude`）。
- 前端 `RoutePlannerView.vue` 改为优先使用 `nodeGeo` 进行布局；`nodeTypeMap` 由 `nodeGeo + nodeDetails` 合并推断节点类型，确保虚拟节点隐藏逻辑生效。
- `api.ts` 同步补充 `nodeGeo` 类型定义。

### 验证
- `mvn -f ../pom.xml -DskipTests compile`：BUILD SUCCESS。
- `npm.cmd run build`：前端构建成功（仅 chunk size warning）。

### 变更文件
- src/main/java/com/travel/service/impl/RouteServiceImpl.java
- frontend/src/lib/api.ts
- frontend/src/views/route/RoutePlannerView.vue
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（迭代目标清单交接 + 前5项启动落地）
### 会话背景（供换 Agent 快速接手）
- 用户要求将“从易到难、低耦合优先”的迭代目标与上下文写入 HANDOFF，并立即开始前 5 项。
- 排序依据：先做对其他系统耦合最低、改动面最小、可快速验证的事项。

### 迭代清单（从易到难）
1. 数据字段瘦身规则固化（virtual_node 最小字段集）
2. OSM 产物自动校验脚本（字段/拓扑/附着率）
3. 生成报告增强（体积与拓扑指标）
4. 前端默认隐藏道路辅助节点（可调试开关）
5. 起终点候选独立接口（与 map-data 解耦）
6. 路网按 area 分块懒加载
7. 后端图结构缓存复用
8. 不可达路径兜底与可解释建议
9. 多策略路径优化（最短距离/最少转折/低拥挤）

### 前 5 项执行状态
- [完成] 第1项：`scripts/osm_seed.py` 已确保 virtual_node 不写 `description/createTime/updateTime`。
- [完成] 第2项：新增 `scripts/validate_osm_output.py`，校验通过。
- [完成] 第3项：`scripts/osm_seed.py` 报告新增附着率、虚拟节点连通、度分布、payload 体积统计。
- [完成] 第4项：`frontend/src/views/route/RoutePlannerView.vue` 新增“显示道路辅助节点”开关，默认隐藏。
- [完成] 第5项：新增后端接口 `GET /api/route/poi-candidates` 并在前端接入独立候选数据源。

### 验证结果
- `python ../scripts/osm_seed.py --config ../scripts/config/osm_seed_config.json`：成功生成 latest。
- `python ../scripts/validate_osm_output.py --dir ../src/main/resources/osm-data/广州市执信中学-执信南路校区/latest`：Validation PASSED。
- `mvn -f ../pom.xml -DskipTests compile`：BUILD SUCCESS。
- `npm.cmd run build`：前端构建成功（仅 chunk size warning）。

### 变更文件
- scripts/osm_seed.py
- scripts/validate_osm_output.py
- src/main/java/com/travel/service/RouteService.java
- src/main/java/com/travel/service/impl/RouteServiceImpl.java
- src/main/java/com/travel/controller/RouteController.java
- frontend/src/lib/api.ts
- frontend/src/views/route/RoutePlannerView.vue
- src/main/resources/osm-data/广州市执信中学-执信南路校区/latest/report.md
- src/main/resources/osm-data/广州市执信中学-执信南路校区/latest/pois.append.json
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（脚本/输出去除 draft 后缀）
### 已完成
- 脚本重命名：`scripts/osm_seed_draft.py` -> `scripts/osm_seed.py`，`scripts/amap_seed_draft.py` -> `scripts/amap_seed.py`。
- 输出目录去除 draft 后缀：OSM 默认输出改为 `src/main/resources/osm-data/`（原 `osm-drafts`）。
- OSM 配置已同步：`scripts/config/osm_seed_config.json` 的 `output_dir` 更新为 `src/main/resources/osm-data`。
- 重新执行脚本生成新样例：`src/main/resources/osm-data/广州市执信中学-执信南路校区/run_20260331_104557/`。
- `map-imports.json` 已切换到新输出路径。

### 说明
- 当前 `roads.append.json` 仍为“按 POI 几何距离排序后近邻连边”的简化生成逻辑，并非直接将 OSM 路网拓扑映射为道路边。

## 2026-03-31（OSM 道路切换为真路网）
### 已完成
- `scripts/osm_seed.py` 道路生成逻辑升级为真路网：
  - Overpass 查询改为 `out geom tags`，提取 highway way 几何折线。
  - 由 highway 折线构建路网图（节点+边权距离）。
  - POI 吸附到最近路网节点后，使用最短路距离生成 POI 间道路边。
- 新样例输出：`src/main/resources/osm-data/广州市执信中学-执信南路校区/run_20260331_105018/`。

### 验证
- 执行：`python scripts/osm_seed.py --config scripts/config/osm_seed_config.json`
- 结果：`POI=9, Facilities=1, Roads=7`。
- 报告中已包含路网统计：`roadNetworkNodes=32`、`roadGraphEdgesApprox=34`、`snappedPoiCount=9`。

## 2026-03-31（真路网伪节点 + latest 覆盖 + map-imports 自动更新）
### 已完成
- `scripts/osm_seed.py` 支持以“非 POI 伪节点”作为道路端点：
  - 从 OSM `highway` 几何构图生成虚拟路网节点（不写入 POI）。
  - 道路由两类边组成：虚拟节点间的 highway 边 + POI 到最近虚拟节点的连接边。
- 输出目录改为固定 `latest`（覆盖旧结果），避免每次累积新 run 目录。
- 脚本执行后自动回写 `src/main/resources/dev-seed/map-imports.json` 到最新 `latest` 路径。
- 后端 `RouteServiceImpl#getMapData` 仅返回 POI 的 `nodeDetails`，非 POI 伪节点不作为 POI 信息突出展示。
- 前端 `RoutePlannerView.vue` 已将非 POI 节点弱化显示（无标签、低透明、更小尺寸）。

### 验证
- `python scripts/osm_seed.py --config scripts/config/osm_seed_config.json`
  - 输出：`src/main/resources/osm-data/广州市执信中学-执信南路校区/latest`
  - 报告显示：`roadCount=43`、`virtualNodeCount=32`、`snappedPoiCount=9`
  - `map-imports.json` 已自动更新到 `classpath:osm-data/.../latest/*.append.json`

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（会话目标：高德采集脚本与审核草案）
### 会话目标
- 编写可执行 Python 脚本，通过高德 Web API 抓取“广州执信中学（执信南路校区）”相关真实地图数据。
- 仅生成审核草案文件（景区/POI/设施/道路），不改动现有 seed 数据。

### 已完成
- 新增脚本 `scripts/amap_seed_draft.py`：
  - 通过高德 Web API 执行文本检索、周边检索、步行路径采集。
  - 自动读取现有 seed 最大 ID，生成 `scenic_areas/buildings/facilities/roads` 审核草案。
  - 输出目录为 `docs/AI/data-drafts/amap_zhixin_review_<timestamp>/`，含 raw 响应与报告。
  - 默认不改写任何现有 seed 文件。
- 新增配置模板 `scripts/config/amap_seed_config.example.json`，支持独立配置 Web 服务 key。
- 新增配置文件 `scripts/config/amap_seed_config.json`（用户已填入 key）。
- 脚本增加请求限速与频控重试参数：`--request-interval`、`--max-retry`，用于缓解 `CUQPS_HAS_EXCEEDED_THE_LIMIT`。
- 已成功产出两批审核草案：
  - `docs/AI/data-drafts/amap_zhixin_review_20260331_090802/`
  - `docs/AI/data-drafts/amap_zhixin_review_20260331_090833/`

### 验证与当前阻塞
- 执行命令：`python scripts/amap_seed_draft.py`
- 已验证：
  - 文本检索接口返回 `status=1, info=OK`，说明当前 key 可用于 Web 服务。
  - 全流程脚本可执行并输出审核草案文件。
- 当前非阻塞风险：周边检索半径内会混入校区外 POI（例如附近高校/社会餐饮），需要人工审核后再落地。

### 下一步
- 在人工审核确认后，再按同口径选择是否将审核通过的数据增量写入 seed。

### 变更文件
- scripts/amap_seed_draft.py
- scripts/config/amap_seed_config.example.json
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（数据建模新要求记录：景区-POI 口径）
### 会话目标
- 仅记录用户最新的数据设计要求，不继续生成或修改任何 seed 数据文件。

### 用户确认的新要求
- 语义口径：一个校区整体作为“景区（scenic_area）”，校区内设施/场所统一作为“POI（buildings 语义层）”。
- 语义细分：校园内可游览节点（如湖、广场、步道、纪念点）归类为 `scenic_spot` 类型 POI。
- 数据来源：后续新样本的经纬度应尽量基于可核查地图来源，禁止凭空拟造坐标。
- 交付流程：先提供样本信息供用户人工审核，用户确认后才允许继续落地到 seed 文件。
- 变更边界：不改动当前既有拟造数据，仅在用户确认后新增符合新口径的数据。

### 本轮执行结果
- 已停止继续生成新样本，仅完成要求记录。

### 变更文件
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（CVE 再复核：维持安全版本）
### 会话目标
- 按用户要求再次升级并验证漏洞依赖，使用 `#appmod-validate-cves-for-java` 复核结果。

### 已完成
- 重新解析当前直接依赖版本并核对关键历史风险构件版本。
- 使用 `#appmod-validate-cves-for-java` 扫描后，结果为无已知待修复 CVE。
- 执行 `mvn -q clean test`，构建与测试通过。

### 结论
- 当前依赖已处于非漏洞版本基线，本轮无新增升级改动。

### 变更文件
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（CVE 复核：当前无需继续升级）
### 会话目标
- 按用户要求再次升级并复核漏洞依赖，使用 `#appmod-validate-cves-for-java` 验证结果。

### 已完成
- 重新解析当前直接依赖版本并复核关键历史高风险构件版本。
- 使用 `#appmod-validate-cves-for-java` 扫描当前依赖集合，结果为无已知待修复 CVE。
- 执行 `mvn -q clean test`，构建与测试通过。

### 结论
- 当前依赖已处于非漏洞版本基线，本轮无新增升级改动。

### 变更文件
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（CVE 依赖复核：无新增升级）
### 会话目标
- 复核当前项目依赖是否仍存在需修复 CVE，并使用 `#appmod-validate-cves-for-java` 验证。

### 已完成
- 读取并确认 `pom.xml` 当前已采用安全版本基线（Spring Boot 3.5.0 + 关键安全覆盖版本）。
- 生成当前解析依赖坐标（116 个）用于复核抽样。
- 使用 `#appmod-validate-cves-for-java` 对直接依赖与关键历史漏洞构件版本执行扫描。
- 运行 `mvn -q clean test` 回归验证通过。

### 验证
- CVE 扫描结果：No known CVEs that need to be fixed are found for the given dependencies.
- 构建/测试：`mvn -q clean test` 通过。

### 变更文件
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-31（CVE 依赖升级与复核）
### 会话目标
- 升级存在漏洞的依赖到无已知漏洞版本，并使用 `#appmod-validate-cves-for-java` 完成修复验证。

### 已完成
- 将 `spring-boot-starter-parent` 从 `3.2.3` 升级到 `3.5.0`。
- 在 `pom.xml` 增加安全版本覆盖：`spring-framework.version=6.2.17`、`spring-security.version=6.5.9`、`tomcat.version=10.1.52`、`netty.version=4.1.125.Final`。
- 显式加入 `net.minidev:json-smart:2.5.2` 与 `org.assertj:assertj-core:3.27.7`（test）以覆盖传递依赖漏洞。
- 执行全量编译与测试通过（`mvn -q clean test`）。
- 对“升级前命中漏洞的关键构件集合”执行复扫，结果为 0 个需修复 CVE。

### 验证
- 构建验证：`mvn -q clean test-compile` 通过。
- 测试验证：`mvn -q clean test` 通过。
- 漏洞验证：`#appmod-validate-cves-for-java` 对以下构件复扫为无已知需修复漏洞：
  - `io.netty:netty-handler:4.1.125.Final`
  - `net.minidev:json-smart:2.5.2`
  - `org.apache.tomcat.embed:tomcat-embed-core:10.1.52`
  - `org.assertj:assertj-core:3.27.7`
  - `org.springframework.security:spring-security-core/web/crypto:6.5.9`
  - `org.springframework:spring-web/spring-webmvc:6.2.17`

### 变更文件
- pom.xml
- docs/AI/HANDOFF.md
- .github/java-upgrade/20260331001104/plan.md
- .github/java-upgrade/20260331001104/progress.md
- .github/java-upgrade/20260331001104/summary.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-30（会话目标：Building 语义升级为 POI）
### 会话目标
- 将领域术语从 Building 统一升级为 POI，并保持现有数据结构与运行行为不变。
- 新增并贯通 POI 类型 `scenic_spot`，同步后端、前端与文档描述。

### 已完成
- 后端模型与数据层完成 POI 命名落地：`Poi` 实体、`PoiMapper`、`InMemoryStore` 的 `insertPoi/findPoiById/findPoisByAreaId`。
- 管理端新增 `POST /api/admin/poi`，并保留 `POST /api/admin/building` 兼容入口。
- 路线地图数据继续返回 `nodeDetails`，节点详情改为基于 POI 查询。
- 前端管理页改为 POI 录入流程，调用 `apiAdminAddPoi`；路线页文案改为 POI，并新增 `scenic_spot` 类型显示。
- `dev-seed/buildings.json` 增加 `scenic_spot` 类型样例（`id=506`）。
- 需求文档与技术设计文档（根目录与 docs 双份）已同步 POI 术语及接口说明。

### 验证
- 后端：`mvn -DskipTests compile` 通过。
- 前端：`npm.cmd run build` 通过。

### 变更文件
- src/main/java/com/travel/model/entity/Poi.java
- src/main/java/com/travel/mapper/PoiMapper.java
- src/main/java/com/travel/controller/AdminController.java
- src/main/java/com/travel/service/AdminService.java
- src/main/java/com/travel/service/impl/AdminServiceImpl.java
- src/main/java/com/travel/storage/InMemoryStore.java
- src/main/java/com/travel/storage/InMemoryDataLoader.java
- src/main/java/com/travel/storage/DevSeedDataLoader.java
- src/main/java/com/travel/service/impl/RouteServiceImpl.java
- frontend/src/lib/api.ts
- frontend/src/views/admin/AdminView.vue
- frontend/src/views/route/RoutePlannerView.vue
- src/main/resources/dev-seed/buildings.json
- Requirements Documendation.md
- docs/Requirements/Requirements Documendation.md
- Technical Design Document.md
- docs/Tech/Technical Design Document.md
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）

## 2026-03-29（主内容收窄 / 顶栏四角圆角 / Home 双滚动条）
### 已完成
- 非 Home 路由：`.es-main-inner` 设为 `max-width: min(1040px, 86vw)` 居中，`.es-main` 使用 `clamp` 左右留白，两侧露出背景；Home 仍走 `es-main--flush`，全宽英雄区不变。
- `.es-nav` 由仅下圆角改为整体 `border-radius: 20px`，`.nav-items` 改为四角 `border-radius: 14px`；顶栏增加水平内边距避免贴边。
- 双滚动条：`html`/`body`/`#app` 去掉 `height:100%` 链，纵向仅 `html` 滚动；`.es-app` 使用 `flex` 列布局且 `overflow-y: visible`，避免壳层再生成滚动条；`.es-home` 用 `100dvh/100vh - 128px` 控制最小高度。

### 验证
- `frontend` 下 `npm run build` 通过。

### 负责人
- Ryemon

## 2026-03-29（全局毛玻璃：分区透明度）
### 已完成
- `premium.css`：定义 `--glass-page` / `--glass-card` / `--glass-input` / `--glass-subtle` 等分层变量，统一 `backdrop-filter` + `saturate`；`el-card`、输入框、标签、次要按钮、抽屉、对话框、分页、下拉浮层改为毛玻璃而非实色白底。
- `explorescape.css`：顶栏 `.es-nav`、主内容 `.es-panel-page` 使用不同白透明度与模糊强度。
- `global.css`：`.glass` 与主题变量对齐。
- `HomeView` 景区卡片、`DiaryListView` 顶栏/搜索条/日记卡片/标签激活态、`Login`/`Register` 侧栏 `hero` 同步为毛玻璃层次。

### 验证
- `frontend` 下 `npm run build` 通过。

### 负责人
- Ryemon

## 2026-03-29（ExploreScape 五张背景图：public 路径与引用）
### 已完成
- 约定资源目录：`frontend/public/explorescape/`，无空格文件名 `bac-4.png`、`bac-3.png`、`bac-2.png`、`bac-2-2.png`、`bac-1.png`；`README.txt` 说明与原版 `bac 4.png` 等对应关系。
- `explorescape.css` 中 `.es-app` 以 `bac-4.png` 为全页底层背景并保留位移动画；`HomePageView` 四层前景与参考站一致并增加中景 `bac-2`。
- 仓库内已放入 1×1 透明 PNG 占位以便 `npm run build` 通过；本地开发请用真实图层 **覆盖同名文件**。
- 首页 `img` 使用 `import.meta.env.BASE_URL + 'explorescape/...'` 拼接，避免打包器将 `/explorescape/...` 当模块解析失败。

### 验证
- `frontend` 下 `npm run build` 通过。

### 负责人
- Ryemon

## 2026-03-29（前端 UI 对齐 ExploreScape）
### 会话目标
- 以 `ExploreScape-Travel-website-main` 的静态 HTML/CSS 为视觉参考改造 `frontend`；保留既有路由、鉴权与后端 API 联调能力。

### 已完成
- 顶栏玻璃导航（文案与参考一致）：Home / About / Reviews / Gallery / Contacts，分别映射 `/home`、`/about`、`/diary`、`/recommend`（含景区详情）、`/profile` 或未登录 `/login`；次级导航保留 推荐 / 路线 / 设施 / 美食 / 管理（管理员）。
- `/home`：英雄区标题与 Kerala 段落、CTA「Explore More」跳转 `/recommend`、装饰层与左右箭头（Font Awesome）；无原站图片时用渐变占位。
- 全局：`Inter` + Font Awesome CDN、`premium.css` 主色 `#16423c`、新增 `explorescape.css`；登录/注册/404 使用 `es-auth-page` 与主壳一致的深色渐变。
- `public/explorescape/README.txt` 说明可选拷贝原站背景图文件名。

### 验证
- `frontend` 目录执行 `npm run build` 通过。

### 变更文件
- `frontend/index.html`、`frontend/src/main.ts`、`frontend/src/router/index.ts`
- `frontend/src/styles/global.css`、`premium.css`、`explorescape.css`
- `frontend/src/layouts/AppLayout.vue`、`frontend/src/views/HomePageView.vue`、`AboutView.vue`、`NotFoundView.vue`、`auth/LoginView.vue`、`auth/RegisterView.vue`
- `frontend/public/explorescape/README.txt`

### 负责人
- Ryemon

## 2026-03-29（日记列表顶部标签：随用户兴趣）
### 已完成
- `DiaryListView`：固定「历史/古镇/…」改为 `apiGetInterest()` 拉取兴趣，按权重降序生成 chips；「推荐」固定为首项。
- 筛选用规范键与目的地景区标签 `normalizeInterestKey` 对齐；未登录仅「推荐」；兴趣变更后 `watch` 刷新。
- 排除 `isExcludedTagPickerKey`（如普通景区）。

### 变更文件
- `frontend/src/views/diary/DiaryListView.vue`

## 2026-03-29（个人中心快速添加 + 排除「普通景区」）
### 已完成
- `ProfileView`：「快速添加常用标签」改为 `apiTagsList()`（与首页同源），启动时 `loadTagCatalog`。
- `interestTags.isExcludedTagPickerKey`：下拉统一排除展示名为「普通景区」的项；`HomeView` 的合并标签同步过滤。
- `TagServiceImpl`：`GET /api/tags` 侧过滤 `name` 为「普通景区」的标签行。

### 变更文件
- `frontend/src/lib/interestTags.ts`、`frontend/src/views/HomeView.vue`、`frontend/src/views/profile/ProfileView.vue`
- `src/main/java/com/travel/service/impl/TagServiceImpl.java`

## 2026-03-29（个性化标签筛选与展示一致）
### 原因
- 筛选仅用景区关联标签名的子串匹配，未做与兴趣侧一致的 **canonicalize**（中英别名），导致「下拉键」与库里标签名不一致时筛空。
- 首页卡片在 `tags` 为空时用 **景区 type** 展示标签，但筛选未考虑 **type**，造成「看得见、筛不出」。

### 修复
- `RecommendationServiceImpl#containsTagKeyword`：规范键相等 + 原子串匹配；无关联标签时用 `ScenicArea.type` 回退。
- 单测：`RecommendationServiceImplTest` 增补中文/英文关键字与仅 type 场景。

### 变更文件
- `src/main/java/com/travel/service/impl/RecommendationServiceImpl.java`
- `src/test/java/com/travel/service/impl/RecommendationServiceImplTest.java`

## 2026-03-29（首页推荐标签下拉：tags 表）
### 已完成
- 新增 `GET /api/tags`：从 `InMemoryStore` 返回 `tags` 表预加载数据（`TagVO`：id/name/type），匿名可访问。
- `HomeView` 智能推荐标签下拉改为请求 `apiTagsList()`，与库表一致；失败时回退 `COMMON_INTEREST_KEYS`。
- `SecurityConfig`：`GET /api/tags` permitAll。

### 验证
- `mvn -DskipTests compile`、`frontend npm run build` 通过。

### 变更文件
- `src/main/java/com/travel/storage/InMemoryStore.java`
- `src/main/java/com/travel/model/vo/TagVO.java`、`TagService.java`、`TagServiceImpl.java`、`TagController.java`
- `src/main/java/com/travel/security/SecurityConfig.java`
- `frontend/src/lib/api.ts`、`frontend/src/views/HomeView.vue`

## 2026-03-29（用户兴趣持久化）
### 已完成
- `UserServiceImpl` 在 `replaceUserInterests` 之后调用 `UserInterestMapper`：按 `user_id` 删除再插入，使兴趣与权重写入 `user_interests` 表，重启后 `InMemoryDataLoader` 预加载可恢复。
- 覆盖 `updateInterests` 与 `recordEngagement` 两条路径；落库失败时 `warn` 且不抛错，避免无库连接场景下接口 500（不复用方法级 `@Transactional`）。

### 验证
- `mvn test` 通过。

### 变更文件
- `src/main/java/com/travel/service/impl/UserServiceImpl.java`
- `src/test/java/com/travel/service/impl/UserServiceImplTest.java`

## 2026-03-28（仅 dev-seed、不预载 DB）
### 说明（问答，无代码变更）
- 仅用 `dev-seed` JSON、不向内存灌库表数据：设 `app.storage.preload.enabled=false` 且 `app.dev-seed.enabled=true`（见 `InMemoryDataLoader#load`）。
- 若仍连不上 MySQL，Spring/Druid 启动是否失败取决于其它是否访问数据源；`application-dev` 中 `ignore-db-connection-failure` 仅作用于预加载 catch 分支。

## 2026-03-28
### 会话目标
- 建立统一 AI 协作与记忆机制，降低换会话/换模型的上下文丢失。

### 已完成
- 新建 AGENTS 入口与 AI 工作流文档。
- 汇总当前实现状态、差距和风险到 PROJECT_CONTEXT。

### 关键发现
- 内存索引检索结构已存在（Trie + NGram）。
- 路线规划核心算法已实现（图 + Dijkstra）。
- 自动化测试缺失。
- 数据备份恢复/AIGC动画/压缩存储尚未落地。

### 下次优先任务（建议）
1. 建立后端 `src/test` 基础测试骨架，先覆盖 Route/Food/Diary 关键路径。
2. 修复配置安全问题：移除明文数据库密码，改环境变量。
3. 补齐开发日志，保证文档进度和代码进度一致。

### 变更文件
- AGENTS.md
- docs/AI/PROJECT_CONTEXT.md
- docs/AI/WORKFLOW.md
- docs/AI/HANDOFF.md

## 2026-03-28（约束澄清补充）
### 会话目标
- 将“数据库仅持久化、运行时内存处理”与“核心模块禁用 Java 内置集合实现”明确写入项目文档。

### 已完成
- 在需求文档（根目录与 docs 目录）补充课程实现约束与数据库职责约束。
- 在开发规范文档（根目录与 docs 目录）补充“数据结构实现约束（课程要求）”。
- 在技术设计文档（根目录与 docs 目录）补充课程约束说明。
- 在 AI 协作文档（AGENTS/WORKFLOW/PROJECT_CONTEXT）同步新增硬约束。

### 关键决策
- 数据库定位为持久化层，不承担核心检索/匹配/排序/关联计算。
- 课程考核范围内的核心数据结构与算法模块，禁止直接使用 Java 内置集合实现作为最终实现。

### 变更文件
- Requirements Documendation.md
- docs/Requirements/Requirements Documendation.md
- Specification.md
- docs/Development Specification/Specification.md
- Technical Design Document.md
- docs/Tech/Technical Design Document.md
- AGENTS.md
- docs/AI/WORKFLOW.md
- docs/AI/PROJECT_CONTEXT.md
- docs/AI/HANDOFF.md

## 2026-03-28（阶段变更总览）
### 记录目的
- 汇总截至当前会话的全部关键变更，作为对外同步和阶段验收依据。

### 新增文件
- AGENTS.md
- .github/copilot-instructions.md
- CLAUDE.md
- GEMINI.md
- .cursorrules
- docs/AI/PROJECT_CONTEXT.md
- docs/AI/WORKFLOW.md
- docs/AI/HANDOFF.md

### 已修改文件
- Requirements Documendation.md
- docs/Requirements/Requirements Documendation.md
- Specification.md
- docs/Development Specification/Specification.md
- Technical Design Document.md
- docs/Tech/Technical Design Document.md
- AGENTS.md
- docs/AI/PROJECT_CONTEXT.md
- docs/AI/WORKFLOW.md
- docs/AI/HANDOFF.md

### 变更主题归档
- 建立跨会话、跨 AI 的统一协作入口与记忆机制。
- 明确检索约束：数据库仅持久化，运行时采用“预加载 -> 内存索引/数据结构 -> 应用层算法”。
- 明确课程实现约束：核心数据结构与算法模块不得直接使用 Java 内置集合实现，需采用自定义结构（MyList/MyMap/MySet/MyPriorityQueue）。
- 在需求、规范、技术设计、AI 协作文档中完成双份（根目录与 docs）同步。

### 当前状态备注
- 文档层约束已统一。
- 代码层仍存在大量 `java.util` 集合使用，后续需按模块逐步替换并补测试。

## 2026-03-28（项目计划书初稿）
### 会话目标
- 产出课程设计“项目计划书”混合版初稿，突出需求实现主线并降低 AI 章节比重。

### 已完成
- 新建计划书文档：`docs/Project Plan/Project Plan.md`。
- 完成项目计划书主体结构与正文初稿，覆盖目标、范围、约束、WBS、里程碑、质量、风险、交付与验收。
- AI 辅助开发章节采用轻量定位（提效工具），强调项目核心评分仍是需求实现与约束合规。
- 文档结构按 Markdown -> HTML -> Word 迁移友好原则编排。

### 变更文件
- docs/Project Plan/Project Plan.md
- docs/AI/HANDOFF.md

## 2026-03-28（成员分工与数据结构优先级优化）
### 会话目标
- 按真实团队成员信息细化分工，并强化数据结构实现在计划中的优先级与可见度。

### 已完成
- 将成员分工替换为真实信息：陈逸（组长）、程小路，并明确“全栈参与+偏重分工”。
- 将 WBS、RACI、周计划中的负责人与协作关系同步改为真实姓名。
- 新增“数据结构实现优先级”章节，明确 MyList/MyMap/MySet/MyPriorityQueue 的前置实现策略。
- 在周计划（第2-8周）与里程碑退出标准中前置并强化数据结构替换工作。
- 调整部分表达为更接地气、可执行导向，减少空泛表述。

### 变更文件
- docs/Project Plan/Project Plan.md
- docs/AI/HANDOFF.md

## 2026-03-28（项目经理级细化迭代）
### 会话目标
- 在既有全学期计划基础上进一步细化到可执行管理层面，提升专业性与落地性。

### 已完成
- 在计划书中新增 WBS 三级任务包（含 FR 关联、前置依赖、负责人、DoD）。
- 新增 RACI 责任矩阵与固定会议节奏（计划会、复盘会、Gate评审会）。
- 新增关键路径与依赖关系表，明确延误影响与纠偏动作。
- 新增阶段化测试计划明细（进入条件/退出条件/产出）。
- 新增 AI 全流程 SOP、人工确认点、AI相关风险与控制策略。
- 新增 FR-里程碑-交付物追踪表，强化验收可追踪性。

### 变更文件
- docs/Project Plan/Project Plan.md
- docs/AI/HANDOFF.md

## 2026-03-28（项目计划书重构 v1.0）
### 会话目标
- 按真实项目标准重构项目计划书，增强可执行性与落地细节，降低空泛描述。

### 已完成
- 对 `docs/Project Plan/Project Plan.md` 执行推倒重写，升级为可执行版本。
- 增加核心需求清单与优先级、技术栈总览、关键模块实施计划。
- 增加迭代节奏、里程碑退出条件、质量门禁、风险触发信号与升级机制。
- 调整 AI 章节为低比重辅助定位，强调需求实现与工程执行主线。
- 保留 Markdown 到 HTML 再到 Word 的格式迁移规范，确保后续排版可控。

### 变更文件
- docs/Project Plan/Project Plan.md
- docs/AI/HANDOFF.md

## 2026-03-28（全学期计划重构与AI章节修正）
### 会话目标
- 将项目计划改为面向全学期（16周）的详细执行计划，并修正 AI 章节表达偏差。

### 已完成
- 将项目周期改为 16 周全期计划，替换原 6 周短期迭代描述。
- 按课程教学节点建立 Gate 表，明确每个课堂节点对应目标与必交付。
- 重写周计划为第1周到第16周逐周任务清单，明确“何时做什么、输出什么”。
- 增加里程碑退出标准（M1-M4）与可量化判定条件。
- 重写 AI 章节为中等篇幅：明确 AI 在需求、设计、开发、测试、交付全流程参与方式，并补充治理机制与效果度量。

### 变更文件
- docs/Project Plan/Project Plan.md
- docs/AI/HANDOFF.md

## 2026-03-28（分工均衡与交叉协作修订）
### 会话目标
- 将项目分工从“单人负责”改为“共同执行、各有侧重、平均贡献”的表达方式。

### 已完成
- 重写 WBS 责任字段：由单一“负责人”改为“主责+协作”，并在任务层面轮换主责，体现两人共同投入。
- 调整角色职责描述：明确“组长负责节奏协调，不代表主要功劳归属”。
- 重写 RACI 矩阵：核心工作项统一为双人共同执行与共同负责，避免割裂分工。
- 保留“侧重”而非“独占”：陈逸偏算法与架构收敛，程小路偏前端体验与测试闭环。

### 变更文件
- docs/Project Plan/Project Plan.md
- docs/AI/HANDOFF.md

## 2026-03-28（计划书去标注与HTML排版导出）
### 会话目标
- 移除计划书中的编号型标注信息，输出更易读的 HTML 版本。

### 已完成
- 清理计划书中的编号标注字段与空标注列（如需求编号/WBS编码/Gate/风险编号等）。
- 修复自动清理后出现的残留异常文本，统一为可读表达。
- 重新生成带样式的 HTML 文档，优化标题层次、表格样式、移动端阅读与整体视觉排版。

### 变更文件
- docs/Project Plan/Project Plan.md
- docs/Project Plan/Project Plan.html
- docs/AI/HANDOFF.md

## 2026-03-28（新增 git-commit-mentor skill）
### 会话目标
- 将 `minEngine` 仓库中的 `git-commit-mentor` 协作技能引入当前项目，统一提交信息生成流程。

### 已完成
- 新增技能文件：`skills/git-commit-mentor/SKILL.md`。
- 在 Copilot 指令入口中增加该技能触发约定与安全约束（未经用户批准不得执行 `git commit`）。

### 变更文件
- skills/git-commit-mentor/SKILL.md
- .github/copilot-instructions.md
- docs/AI/HANDOFF.md

## 2026-03-28（自定义数据结构框架首版落地）
### 会话目标
- 先盘点项目中 `java.util` 数据结构使用现状，再提供同名接口/实现的自定义数据结构包，支持后续通过替换 import 解耦。

### 已完成
- 完成 `java.util` 使用清单扫描（去重）：ArrayList、Collections、Comparator、Date、HashMap、Iterator、List、Map、NoSuchElementException、Objects、PriorityQueue、Set。
- 新增自定义数据结构包 `com.travel.ds`，接口名与 Java 习惯保持一致：`Collection`、`List`、`Set`、`Map`、`Queue`、`Deque`。
- 新增实现类：`ArrayList`、`LinkedList`、`HashMap`、`HashSet`、`PriorityQueue`、`ArrayDeque`。
- 新增工具类：`Collections`（含 `sort/reverse/swap`）。
- 通过编辑器诊断验证新增目录无语法错误（当前环境无 Maven 命令，未执行 `mvn compile`）。

### 变更文件
- src/main/java/com/travel/ds/Collection.java
- src/main/java/com/travel/ds/List.java
- src/main/java/com/travel/ds/Set.java
- src/main/java/com/travel/ds/Map.java
- src/main/java/com/travel/ds/Queue.java
- src/main/java/com/travel/ds/Deque.java
- src/main/java/com/travel/ds/ArrayList.java
- src/main/java/com/travel/ds/LinkedList.java
- src/main/java/com/travel/ds/HashMap.java
- src/main/java/com/travel/ds/HashSet.java
- src/main/java/com/travel/ds/PriorityQueue.java
- src/main/java/com/travel/ds/ArrayDeque.java
- src/main/java/com/travel/ds/Collections.java
- docs/AI/HANDOFF.md

## 2026-03-28（数据库连接失败忽略开关）
### 会话目标
- 支持在非数据库开发场景下启动应用：数据库连接失败时可按开关忽略并继续运行。

### 已完成
- 在 `InMemoryDataLoader` 增加配置开关：
  - `app.storage.preload.enabled`（是否执行启动预加载，默认 `true`）
  - `app.debug.ignore-db-connection-failure`（数据库连接失败时是否忽略，默认 `false`）
- 在 `application.yml` 新增上述配置项与说明注释。
- 实测结果：
  - 依赖解析成功（`mvn dependency:resolve` -> `BUILD SUCCESS`）
  - 编译成功（`mvn -DskipTests compile` -> `BUILD SUCCESS`）
  - 启动时设置 `APP_DEBUG_IGNORE_DB_CONNECTION_FAILURE=true`，数据库连接失败被告警后跳过预加载，应用仍成功启动（Tomcat 8080 started）。

### 变更文件
- src/main/java/com/travel/storage/InMemoryDataLoader.java
- src/main/resources/application.yml
- docs/AI/HANDOFF.md

## 2026-03-28（开发环境默认关闭鉴权）
### 会话目标
- 增加用户登录鉴权开关，并在开发环境默认关闭鉴权，提升无数据库场景下的开发与调试效率。

### 已完成
- 在安全配置中增加开关：`app.security.auth-enabled`（默认 `true`）。
- 当开关为 `false` 时，放行全部请求并跳过 JWT 过滤链注入。
- 在主配置中设置默认值为开启鉴权。
- 新增 `application-dev.yml`，在 `dev` profile 下默认将鉴权开关设为关闭。
- 验证结果：
  - `mvn -DskipTests compile` 编译成功。
  - 使用 `SPRING_PROFILES_ACTIVE=dev` 启动时，日志出现 `Security auth is disabled by config: app.security.auth-enabled=false`。
  - 同时在数据库不可达场景下，应用仍可启动并监听 8080 端口（依赖此前数据库失败忽略开关）。

### 变更文件
- src/main/java/com/travel/security/SecurityConfig.java
- src/main/resources/application.yml
- src/main/resources/application-dev.yml
- docs/AI/HANDOFF.md

## 2026-03-28（开发种子数据自动加载）
### 会话目标
- 在开发环境数据库不可用时，自动注入 5~10 条可测种子数据，保障前端联调与检索/推荐/路径功能验证。

### 已完成
- 新增 `DevSeedDataLoader`，覆盖以下内存数据：用户、兴趣、景区、标签及关联、建筑、道路、设施、餐厅、美食、游记、游记目的地、评论。
- 在 `InMemoryDataLoader` 中接入种子数据回填触发点：
  - 预加载显式关闭时触发
  - 数据库连接失败且开启忽略开关时触发
- 新增配置：
  - `app.dev-seed.enabled`（全局默认 `false`）
  - 在 `application-dev.yml` 中默认开启 `app.dev-seed.enabled=true`
  - 在 `application-dev.yml` 中默认开启 `app.debug.ignore-db-connection-failure=true`
- 验证结果：
  - `mvn -DskipTests compile` -> BUILD SUCCESS
  - `mvn spring-boot:run -Dspring-boot.run.profiles=dev` 启动日志出现 `Dev seed data loaded successfully`。
  - 接口验证通过：
    - `/api/recommendation/hot?page=1&size=3` 返回 5 条景区总量中的分页结果
    - `/api/food/search?areaId=201&page=1&size=5` 返回种子美食数据
    - `/api/route/map-data?areaId=201` 返回节点与边数据

### 变更文件
- src/main/java/com/travel/storage/DevSeedDataLoader.java
- src/main/java/com/travel/storage/InMemoryDataLoader.java
- src/main/resources/application.yml
- src/main/resources/application-dev.yml
- docs/AI/HANDOFF.md

## 2026-03-28（开发种子数据改造为 JSON 文件驱动）
### 会话目标
- 将 `DevSeedDataLoader` 中写死测试数据迁移为资源目录 JSON 文件，并补齐全量数据类型，确保无库开发可覆盖核心联调场景。

### 已完成
- 重构 `DevSeedDataLoader`：改为从 `app.dev-seed.path` 指定目录读取 JSON 并注入内存仓库。
- 新增 13 个 seed 文件（全量覆盖）：
  - `users`、`user_interests`、`scenic_areas`、`tags`、`scenic_area_tags`
  - `buildings`、`roads`、`facilities`、`restaurants`、`foods`
  - `diaries`、`diary_destinations`、`comments`
- 保持原有回退触发策略不变：数据库预加载失败时（dev 下开启忽略）自动加载种子数据。
- 新增配置：`app.dev-seed.path: classpath:dev-seed`。

### 验证结果
- `mvn -DskipTests compile` -> BUILD SUCCESS。
- `mvn spring-boot:run -Dspring-boot.run.profiles=dev` 启动日志显示：
  - `Dev seed data loaded from JSON path classpath:dev-seed successfully`
  - 已加载计数：users=6, scenicAreas=6, facilities=7, foods=8, diaries=6, comments=8。
- 接口验证通过：
  - `/api/recommendation/hot?page=1&size=10`
  - `/api/diary?page=1&size=10`
  - `/api/facility/search?limit=20`

### 变更文件
- src/main/java/com/travel/storage/DevSeedDataLoader.java
- src/main/resources/application.yml
- src/main/resources/dev-seed/users.json
- src/main/resources/dev-seed/user_interests.json
- src/main/resources/dev-seed/scenic_areas.json
- src/main/resources/dev-seed/tags.json
- src/main/resources/dev-seed/scenic_area_tags.json
- src/main/resources/dev-seed/buildings.json
- src/main/resources/dev-seed/roads.json
- src/main/resources/dev-seed/facilities.json
- src/main/resources/dev-seed/restaurants.json
- src/main/resources/dev-seed/foods.json
- src/main/resources/dev-seed/diaries.json
- src/main/resources/dev-seed/diary_destinations.json
- src/main/resources/dev-seed/comments.json
- docs/AI/HANDOFF.md

## 2026-03-28（自定义数据结构可用性与 java.util 一致性验证）
### 会话目标
- 验证 `com.travel.ds` 全部自定义数据结构可用，并与 `java.util` 对应容器在核心行为上保持一致。

### 已完成
- 修正原有基线测试中 `ArrayList#indexOf` 的错误断言。
- 新增对照测试文件 `CustomDataStructuresParityTest`，覆盖：
  - `ArrayList`、`LinkedList`、`HashMap`、`HashSet`、`PriorityQueue`、`ArrayDeque`、`Collections`
  - 核心行为：增删改查、空值处理、越界/空容器异常、迭代与顺序、排序/反转/交换效果
  - 对照方式：与 `java.util` 同操作、同断言结果
- 修复实现差异：`PriorityQueue#offer` 增加 null 拒绝（抛 `NullPointerException`），对齐 `java.util.PriorityQueue`。

### 验证结果
- 执行：`mvn "-Dtest=CustomDataStructuresTest,CustomDataStructuresParityTest" test -q`
- 结果：`TESTS_OK`（全部通过）。

### 变更文件
- src/main/java/com/travel/ds/PriorityQueue.java
- src/test/java/com/travel/ds/CustomDataStructuresTest.java
- src/test/java/com/travel/ds/CustomDataStructuresParityTest.java
- docs/AI/HANDOFF.md

## 2026-03-28（提交信息换行格式修复与 commit-mentor 强化）
### 会话目标
- 修复最近一次提交正文中 `\\n` 字面量导致的格式问题，并增强 `git-commit-mentor` 规则避免复发。

### 已完成
- 准备对最近一次提交执行 amend，改为真实多行提交正文（不含 `\\n` 字面量）。
- 更新 `skills/git-commit-mentor/SKILL.md`：新增提交执行硬规则，强制使用真实多行文本并优先 `git commit -F`。
- 更新 `.github/copilot-instructions.md`：补充仓库级禁止 `\\n` 字面量规则。

### 变更文件
- skills/git-commit-mentor/SKILL.md
- .github/copilot-instructions.md
- docs/AI/HANDOFF.md

## 2026-03-28（阶段合并摘要：推荐链路与体验优化）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 兴趣权重闭环：统一前后端字段为 `type/weight`，支持“标签:权重”输入与服务端校验。
- 种子账号登录：dev-seed 明文密码启动时自动 BCrypt 编码，开发账号可直接登录。
- 联调稳定性修复：美食推荐响应兼容、设施定位拒绝降级、日记详情无库环境可用。
- 体验优化：后端 devtools 热更新接入，美食页面去技术 ID 化并增强名称展示。

### 验证结果
- 关键接口与页面回归通过（登录、推荐、设施、日记详情、兴趣保存/回显）。
- 前后端构建/编译通过（含 `mvn -DskipTests compile`、`npm.cmd run build`）。

### 主要变更文件（合并）
- frontend/src/lib/api.ts
- frontend/src/views/profile/ProfileView.vue
- frontend/src/views/facility/FacilityView.vue
- frontend/src/views/food/FoodView.vue
- frontend/src/views/food/FoodDetailView.vue
- src/main/java/com/travel/storage/DevSeedDataLoader.java
- src/main/java/com/travel/service/impl/UserServiceImpl.java
- src/main/java/com/travel/service/impl/DiaryServiceImpl.java
- src/main/java/com/travel/model/dto/auth/InterestItemRequest.java
- src/main/java/com/travel/model/dto/auth/UpdateInterestRequest.java
- pom.xml
- docs/AI/HANDOFF.md

## 2026-03-28（功能合并记录：美食详情可读化 + 兴趣回显）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 美食详情支持展示真实“餐厅名称/所属景区名称”。
- 用户中心支持兴趣与权重回显（登录后自动读取）。
- 前端兼容 `detail-view` 嵌套响应结构，统一为页面可直接消费的数据模型。

### 验证结果
- `GET /api/auth/interest` 返回 `200`。
- `GET /api/food/detail-view/901` 返回 `200`。
- 前端构建通过：`npm.cmd run build`。

### 变更文件
- src/main/java/com/travel/controller/AuthController.java
- src/main/java/com/travel/controller/FoodController.java
- src/main/java/com/travel/service/FoodService.java
- src/main/java/com/travel/service/UserService.java
- src/main/java/com/travel/service/impl/FoodServiceImpl.java
- src/main/java/com/travel/service/impl/UserServiceImpl.java
- src/main/java/com/travel/model/vo/food/FoodDetailVO.java
- src/main/java/com/travel/model/vo/auth/InterestItemVO.java
- frontend/src/lib/api.ts
- frontend/src/views/food/FoodDetailView.vue
- frontend/src/views/profile/ProfileView.vue
- docs/AI/HANDOFF.md

## 2026-03-28（流程需求新增：HANDOFF 精简与负责人）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增规则
- HANDOFF 中同一功能/bug 若已解决，后续记录自动合并为“结果导向”条目。
- 记录默认省略中间排障曲折，仅保留实现结果、验证结论、变更文件。
- 每条 HANDOFF 新增 `负责人` 字段，按当前仓库 git 用户填写。

### 变更文件
- docs/AI/WORKFLOW.md
- docs/AI/HANDOFF.md

## 2026-03-28（美食推荐：算法正确性校验与前端距离对接）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 推荐算法健壮性增强：权重参数负值会被归零；若三项权重总和非正，自动回退默认权重 `0.3/0.5/0.2`。
- 新增美食推荐算法单测：覆盖评分排序、分页、距离过滤、异常权重回退等核心路径。
- 前端“距离排序”接入浏览器定位：可用时传 `lat/lng` 给后端；不可用时提示并回退景区中心点估算。

### 验证结果
- 后端单测通过：`FoodServiceImplRecommendationTest`（`passed=4, failed=0`）。
- 前端构建通过：`npm.cmd run build`。

### 变更文件
- src/main/java/com/travel/service/impl/FoodServiceImpl.java
- src/test/java/com/travel/service/impl/FoodServiceImplRecommendationTest.java
- frontend/src/views/food/FoodView.vue
- docs/AI/HANDOFF.md

## 2026-03-28（景点推荐验证 + 兴趣可视化升级）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 扩充景点测试数据：新增多类型景点（nature/history/photo/science/art/culture/food/hiking/night）及对应标签权重关系。
- 景点推荐算法正确性验证：新增 `RecommendationServiceImplTest`，覆盖个性化匹配排序、标签关键字过滤、热度排序。
- 修复推荐稳定性问题：推荐服务对候选集合使用可变副本排序，避免不可变列表触发异常。
- 用户兴趣体验升级：个人中心改为“兴趣行编辑（标签+权重）+ 饼图可视化”，支持常用标签快速添加与实时图表反馈。

### 验证结果
- 后端推荐相关测试通过：`passed=8, failed=0`（含 `RecommendationServiceImplTest` 与 `FoodServiceImplRecommendationTest`）。
- 前端构建通过：`npm.cmd run build`。

### 变更文件
- src/main/resources/dev-seed/scenic_areas.json
- src/main/resources/dev-seed/tags.json
- src/main/resources/dev-seed/scenic_area_tags.json
- src/main/resources/dev-seed/user_interests.json
- src/main/java/com/travel/service/impl/RecommendationServiceImpl.java
- src/test/java/com/travel/service/impl/RecommendationServiceImplTest.java
- frontend/src/views/profile/ProfileView.vue
- docs/AI/HANDOFF.md

## 2026-03-28（行为学习偏好 V1：点赞/收藏驱动兴趣更新）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 新增行为采集接口：`POST /api/behavior/engage`，支持 `SCENIC/FOOD + LIKE/FAVORITE/VIEW`。
- 新增行为数据结构：`UserBehavior`（内存落地）与 `EngagementRequest`。
- 兴趣学习逻辑落地：行为会按目标标签权重转化为兴趣增量，并与现有兴趣合并（上限 5.0）。
- 前端对接：景点详情与美食详情新增“点赞/收藏”按钮，点击后触发行为上报并提示“已学习偏好”。

### 验证结果
- 后端服务测试通过：`UserServiceImplTest`、`RecommendationServiceImplTest`、`FoodServiceImplRecommendationTest`（总计 `passed=13, failed=0`）。
- 前端构建通过：`npm.cmd run build`。

### 变更文件
- src/main/java/com/travel/model/entity/UserBehavior.java
- src/main/java/com/travel/model/dto/behavior/EngagementRequest.java
- src/main/java/com/travel/controller/BehaviorController.java
- src/main/java/com/travel/storage/InMemoryStore.java
- src/main/java/com/travel/service/UserService.java
- src/main/java/com/travel/service/impl/UserServiceImpl.java
- src/test/java/com/travel/service/impl/UserServiceImplTest.java
- frontend/src/lib/api.ts
- frontend/src/views/scenic/ScenicDetailView.vue
- frontend/src/views/food/FoodDetailView.vue
- docs/AI/HANDOFF.md

## 2026-03-28（问题修复：点赞与兴趣保存 500）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 原因与修复
- 根因：`UserServiceImpl` 的用户侧内存写操作（注册/兴趣更新/行为采集）使用了事务注解，在无数据库连接的 dev-seed 场景下会触发事务取连接并抛 500。
- 修复：移除上述内存操作方法的事务依赖，保留纯内存更新路径。

### 验证结果
- `PUT /api/auth/interest` 返回 `200`。
- `POST /api/behavior/engage` 返回 `200`。
- `GET /api/auth/interest` 返回 `200` 且可读回更新后的兴趣权重。
- 编译/测试通过：`BACKEND_COMPILE_OK`，`UserServiceImplTest passed=5 failed=0`。

### 变更文件
- src/main/java/com/travel/service/impl/UserServiceImpl.java
- docs/AI/HANDOFF.md

## 2026-03-28（兴趣体验修复：中文回显 + 两位小数 + 多标签学习完整增强）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 兴趣标签中文回显：前端新增兴趣标签映射工具，个人中心与推荐页标签统一显示中文名称。
- 兴趣权重精度统一：手动配置改为两位小数（`step=0.01`），后端保存与行为学习增量统一按两位小数归一化。
- 修复“点赞多标签只增强部分标签”：后端在兴趣更新与行为学习时统一做中英别名归一化（canonical key），并合并同义标签后再增权，确保景区多标签都参与学习。

### 验证结果
- 后端测试通过：`UserServiceImplTest`、`RecommendationServiceImplTest`（`passed=11, failed=0`）。
- 前端构建通过：`npm.cmd run build`。

### 变更文件
- src/main/java/com/travel/service/impl/UserServiceImpl.java
- src/main/java/com/travel/service/impl/RecommendationServiceImpl.java
- src/test/java/com/travel/service/impl/UserServiceImplTest.java
- frontend/src/lib/interestTags.ts
- frontend/src/views/profile/ProfileView.vue
- frontend/src/views/HomeView.vue
- docs/AI/HANDOFF.md

## 2026-03-28（登录态修复：dev 放行模式下仍可识别当前用户）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 原因与修复
- 根因：`app.security.auth-enabled=false` 时仅做了 `permitAll`，未挂载 JWT 过滤器，导致 `SecurityUtil.getCurrentUserId()` 始终为空，前端登录后调用个性化/兴趣接口仍提示“未登录”。
- 修复：在 `SecurityConfig` 的 dev 放行分支中同样注入 `JwtAuthenticationFilter`，实现“放行权限 + 保留登录态解析”。

### 验证结果
- `POST /api/auth/login`（dev 种子账号）返回 token。
- 携带 token 调用：`GET /api/auth/interest` 返回 `200`。
- 携带 token 调用：`GET /api/recommendation/personalized` 返回 `200`。

### 变更文件
- src/main/java/com/travel/security/SecurityConfig.java
- docs/AI/HANDOFF.md

## 2026-03-30（路线规划交互优化：节点ID -> 起止位置）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 后端 `GET /api/route/map-data` 增强：在 `nodes/edges` 之外新增 `nodeDetails`，返回节点对应建筑信息（`nodeId/name/type/location/经纬度/areaId`）。
- 前端路线页改造：起点/终点从“手输节点 ID”改为“起止位置下拉选择（建筑）”，并将图节点标签显示为建筑名称。
- 切换景区时会自动刷新地图并重置起止位置，避免跨景区节点错选。

### 验证结果
- 后端编译通过：`mvn -DskipTests compile`。
- 前端构建通过：`npm.cmd run build`。

### 变更文件
- src/main/java/com/travel/storage/InMemoryStore.java
- src/main/java/com/travel/service/impl/RouteServiceImpl.java
- frontend/src/lib/api.ts
- frontend/src/views/route/RoutePlannerView.vue
- docs/AI/HANDOFF.md

## 2026-03-30（路线图真实坐标化：禁拖拽 + 经纬度布局）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 前端路线图从 `force` 布局切换为 `none` 固定布局，节点不再允许手动拖拽（`draggable=false`）。
- 使用后端 `nodeDetails` 中建筑经纬度进行坐标映射显示，节点相对位置贴近真实地理关系。
- 对缺失经纬度节点提供兜底圆环布局，确保图始终可渲染。

### 验证结果
- 前端类型检查与构建通过：`npm.cmd run build`。

### 变更文件
- frontend/src/views/route/RoutePlannerView.vue
- docs/AI/HANDOFF.md

## 2026-03-30（路线图可读性优化：标签避让 + 对比增强 + 丰富节点提示）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 节点标签避免与图标重叠：标签位置改为 `right`，设置 `distance` 与 `hideOverlap`，减少文本遮挡。
- 标签可读性增强：文本改为深色，并增加浅色背景、边框与内边距，避免与背景颜色接近。
- 悬浮信息增强：节点 tooltip 新增建筑名称、节点ID、建筑类型（中文映射）、位置、经纬度；道路 tooltip 显示起终点与距离。

### 验证结果
- 前端构建通过：`npm.cmd run build`。

### 变更文件
- frontend/src/views/route/RoutePlannerView.vue
- docs/AI/HANDOFF.md

## 2026-03-28（推荐景点改造：从列表输出到真实个性化推荐）
### 负责人
- Max1122Chen（max1122chen@126.com）

### 新增/完成功能
- 修复“个性化推荐看起来像普通列表”的核心问题：增加兴趣/标签归一化与中英别名映射（如“自然”->`nature`、“夜景”->`night`），避免兴趣语言不一致导致匹配分失效。
- 优化推荐打分：提升兴趣匹配在总分中的权重（`0.7*match + 0.2*heat + 0.1*rating`），使结果更体现用户偏好而非仅热门排序。
- 增加推荐可解释性：个性化结果返回 `reason` 字段，展示“匹配了哪个兴趣标签及强度”或热门/高分兜底理由。
- 前端推荐页升级：登录后默认进入“智能推荐”标签页，卡片展示推荐分与推荐理由，区分“热门列表”和“个性化推荐”。

### 验证结果
- 推荐服务测试通过：`RecommendationServiceImplTest passed=5 failed=0`（新增中文兴趣别名命中与推荐理由断言）。
- 编辑器诊断通过：本次改动文件无编译/类型错误。

### 变更文件
- src/main/java/com/travel/service/impl/RecommendationServiceImpl.java
- src/main/java/com/travel/model/vo/recommendation/ScenicAreaRecommendVO.java
- src/test/java/com/travel/service/impl/RecommendationServiceImplTest.java
- frontend/src/views/HomeView.vue
- docs/AI/HANDOFF.md


## 2026-03-31（OSM道路节点字段瘦身 + 体验与架构优化评估）
### 会话目标
- 优化 POI 生成数据：道路虚拟节点不再记录 description/createTime/updateTime，降低内存占用。
- 基于 project-manager 与 architect 视角评估现有方案，提出下一阶段设计与体验优化清单。

### 已完成
- 修改脚本 `scripts/osm_seed.py`：仅对 `type=virtual_node` 节点移除 `description/createTime/updateTime` 字段；保留必要字段（id/name/type/location/longitude/latitude/parentId/areaId）。
- 重新生成 latest 数据：`python ../scripts/osm_seed.py --config ../scripts/config/osm_seed_config.json`。
- 程序化校验通过：virtual_node=32，违规字段出现次数=0。

### 验证
- 执行：`python ../scripts/osm_seed.py --config ../scripts/config/osm_seed_config.json`
- 执行：`python -c "..."` 校验 virtual_node 中是否含 description/createTime/updateTime
- 结果：均通过。

### 变更文件
- scripts/osm_seed.py
- src/main/resources/osm-data/广州市执信中学-执信南路校区/latest/pois.append.json
- docs/AI/HANDOFF.md

### 负责人
- Max1122Chen（max1122chen@126.com）
