# 验收参考资料（S5）

> **定稿日期**：2026-06-09  
> **前置冲刺**：S2 数据治理（R8 室内校正搁置）、S3 景区工作台、S4 自制数据结构、S1 导航已定稿。

本目录为**答辩与课程验收**的一站式材料，与 `docs/Requirements/Requirements Documendation.md` §9 及 `docs/AI/SPRINT_CLOSURE.md` 同步。

---

## 文档索引

| 序号 | 文件 | 用途 |
|------|------|------|
| 01 | [01-启动与运行.md](01-启动与运行.md) | 环境、启动命令、dev 无库模式、可选 MySQL / AIGC 配置 |
| 02 | [02-答辩演示脚本.md](02-答辩演示脚本.md) | 现场逐步操作（约 15–20 分钟主链路） |
| 03 | [03-核心算法说明.md](03-核心算法说明.md) | 算法原理、代码落点、自制 DS、检索约束 |
| 04 | [04-FR完成度与裁剪说明.md](04-FR完成度与裁剪说明.md) | FR/NFR 状态、本期裁剪、部分实现书面口径 |
| 05 | [05-验收检查清单.md](05-验收检查清单.md) | 答辩前勾选与回归命令 |

---

## 一分钟结论（评委速览）

| 维度 | 结论 |
|------|------|
| **运行模式** | `dev` profile：**JSON-only**，`dev-seed` + `osm-data` → `InMemoryStore`，可不启 MySQL |
| **主入口** | 游览 → 推荐选景区 → **景区工作台** `/scenic?areaId=252`（路线/设施/美食/室内） |
| **核心算法** | 自实现 Dijkstra、多点 TSP（状态压缩 DP）、Top-K 堆、前缀 Trie + N-Gram；存储用 `com.travel.ds` |
| **检索约束** | 模糊/全文/关联匹配在**内存索引**完成，不用 SQL LIKE/JOIN 做检索计算 |
| **数据规模** | `python scripts/validate_s2_closure.py` 8/8 OK（**220** 景区、15 设施 type、20007 道路等） |
| **待答辩现场** | FR-009-5 AIGC 动画需配置火山密钥后实机出片 1 条 |

---

## 相关技术文档

- [S2 执行计划](../Tech/S2%20Data%20Governance%20Execution%20Plan.md)
- [景区工作台设计](../Tech/Scenic%20Hub%20Page%20Design.md)
- [自制数据结构范围](../Tech/Custom%20Data%20Structures%20Scope.md)
- [室内导航方案](../Tech/Indoor%20Navigation%20Implementation%20Plan.md)
