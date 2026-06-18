# 个性化旅游推荐系统（TravelSystem）

北京邮电大学数据结构课程设计 —— 前后端分离的景区游览与推荐平台，支持**个性化推荐**、**景区一体化工作台**（路线 / 设施 / 美食）、**室内导航**、**日记与 AIGC 动画**。

## 快速启动（答辩推荐：无 MySQL）

**环境**：JDK **21**、Maven 3.9+、Node.js 18+（前端 Vite 8）

```powershell
# 终端 1 — 后端（dev profile，JSON-only 内存演示）
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"

# 终端 2 — 前端
cd frontend
npm install
npm run dev
```

浏览器打开：<http://localhost:5173>

- 演示账号：`dev_alice` / `dev123456`（普通用户）
- 管理账号：`dev_admin` / `admin123`

## 验收与答辩文档

完整材料见 **[docs/验收/README.md](docs/验收/README.md)**：

| 文档 | 内容 |
|------|------|
| [01-启动与运行](docs/验收/01-启动与运行.md) | 环境、配置、无库模式、AIGC 密钥 |
| [02-答辩演示脚本](docs/验收/02-答辩演示脚本.md) | 逐步演示路径（推荐 → 景区工作台 → 室内） |
| [03-核心算法说明](docs/验收/03-核心算法说明.md) | Dijkstra、TSP、TopK、内存检索、自制 DS |
| [04-FR完成度与裁剪](docs/验收/04-FR完成度与裁剪说明.md) | 需求对照、本期不交付项 |
| [05-验收检查清单](docs/验收/05-验收检查清单.md) | 答辩前勾选 |

## 数据与规模（2026-06-09）

演示数据来自 `dev-seed/` + `osm-data/`（12 个 canonical OSM 包 + **208** 条景区 alias，合计 **220** 条记录）。校验：

```powershell
python scripts/validate_s2_closure.py
mvn test
cd frontend; npm run build
```

## 项目结构

```
src/main/java/com/travel/     # Spring Boot 后端
  algorithm/                  # 图算法、TopK（com.travel.ds）
  storage/search/             # 前缀 Trie、N-Gram 倒排索引
  ds/                         # 自制数据结构
frontend/                     # Vue 3 + TypeScript + ECharts
docs/                         # 需求、技设、验收、AI 协作文档
scripts/                      # OSM 采集、S2 验收脚本
```

## 协作文档（开发）

- [AGENTS.md](AGENTS.md) — AI 协作入口
- [docs/AI/PROJECT_CONTEXT.md](docs/AI/PROJECT_CONTEXT.md) — 架构与约束
- [docs/Requirements/Requirements Documendation.md](docs/Requirements/Requirements%20Documendation.md) — 需求全文

## 许可证

课程设计项目，仅供教学与答辩使用。
