# S4 自制数据结构 — 替换范围与答辩口径

> **状态（2026-06-09）**：核心算法层已完成 `com.travel.ds` 替换；服务/Controller 边界保留 JDK 集合并经 `DsConvert` 转换。

---

## 1. 替换范围（已实施）

| 包 / 模块 | 类 | 使用的 ds 结构 |
|-----------|-----|----------------|
| `com.travel.algorithm.graph` | `Graph` | `HashMap`, `ArrayList` |
| | `Dijkstra` | `HashMap`, `PriorityQueue`, `ArrayList` |
| | `Edge` | `HashMap`, `Collections.unmodifiableMap` |
| | `PathResult` | `List`（接口） |
| `com.travel.algorithm` | `TopKSelector` | `PriorityQueue`, `ArrayList`, `Collections.sort` |
| `com.travel.storage.search` | `PrefixTrieIdIndex` | `HashMap`, `ArrayList` |
| | `NGramInvertedIndex` | `HashMap`, `ArrayList`, `Collections.sort` |

**单元测试**：`CustomDataStructuresParityTest`、`DijkstraTest`、`TopKSelectorTest`、`SearchIndexTest`。

---

## 2. 允许使用 JDK 集合的层（答辩口径）

| 层级 | 说明 |
|------|------|
| **Controller / VO / DTO** | JSON 序列化、Spring 绑定，使用 `java.util.List/Map` |
| **Service 编排** | 业务流程、分页、MyBatis 实体组装 |
| **持久化 / 配置** | Jackson 解码 `ModeProfileCodec`、dev-seed 加载 |
| **框架胶水** | Spring Security、MyBatis-Plus 等第三方 API 要求的类型 |

**边界约定**：Service 向 `Graph` 传参时用 `DsConvert.copyStringDoubleMap()`；算法返回的 `PathResult.getPath()` 在写 VO 时用 `DsConvert.toJavaList()`。

---

## 3. 必须使用自制结构的层

- **图算法**：Dijkstra、邻接表、Top-K 堆
- **内存检索索引**：前缀 Trie、N-Gram 倒排
- **课程验收口径**：上述模块的**实现主体**不得使用 `java.util.ArrayList/HashMap/PriorityQueue` 等作为最终存储结构

实现位于 `src/main/java/com/travel/ds/`（`ArrayList`、`HashMap`、`HashSet`、`PriorityQueue`、`Collections` 等）。

---

## 4. 未纳入本期替换（可书面说明）

- `InMemoryStore` 业务缓存（非算法主体，仍用 JDK）
- `RouteServiceImpl` TSP 状态 DP 数组（原生 `double[][]`，非集合）
- 室内 `IndoorPathPlanner` 分段/文案组装（服务层展示逻辑）

---

## 5. 验证命令

```powershell
mvn -q "-Dtest=CustomDataStructuresParityTest,DijkstraTest,TopKSelectorTest,SearchIndexTest" test
mvn test
```
