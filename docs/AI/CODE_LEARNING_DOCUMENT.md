# 代码学习文档：个性化旅游推荐系统

> 适用场景：验收前快速理解项目结构、核心代码和算法答辩口径。  
> 阅读建议：先看「10 分钟快速复习版」，再按「学习路线」回到代码逐层阅读。

## 1. 项目整体介绍

### 1.1 项目是做什么的

本项目是一个“个性化旅游推荐系统”，面向数据结构课程设计。它不是单纯展示景点列表，而是把推荐、路线规划、设施查询、美食推荐、旅行日记、室内导航和后台数据管理串成一个完整旅游应用。

项目的课程重点是：数据从数据库或 JSON 种子加载后，运行时主要在内存结构中检索、匹配、排序和规划路径，核心算法不能依赖 SQL `LIKE`、`JOIN`、`FULLTEXT` 或外部闭源算法服务。

### 1.2 核心功能

- 景区推荐：热门景区、个性化推荐、景区详情、标签筛选。
- 路线规划：两点路线、多点路线、按距离或时间规划、按交通工具过滤道路。
- 景区工作台：同一页面内查看地图、路线、设施、美食、POI 详情。
- 设施查询：按当前位置或地图锚点查找附近厕所、服务点等设施。
- 美食推荐：按热度、评分、距离综合排序，并支持美食搜索和评分评论。
- 旅行日记：日记发布、列表、详情、搜索、评分评论、附件上传。
- AIGC 日记动画：根据日记生成旅游视频任务，支持外部即梦/LibTV 配置。
- 室内导航：双击可进入室内的建筑，按楼层显示室内图并规划室内路径。
- 后台管理：新增景区、POI、道路、美食，开发模式下支持 OSM 数据采集。

### 1.3 技术栈

后端：Spring Boot 3.5.0、Java 21、MyBatis-Plus、MySQL、可选 Redis、Spring Security + JWT、Jackson、自定义数据结构包 `com.travel.ds`。

前端：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts、Axios。

数据与脚本：`dev-seed` 演示数据、`osm-data` 地图包、`scripts` 数据采集与校验脚本。

### 1.4 项目运行流程

演示模式推荐不用 MySQL，使用 `dev` profile：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
cd frontend
npm run dev
```

运行时总体流程：

```text
浏览器 Vue 页面
  -> frontend/src/lib/api.ts 封装 HTTP 请求
  -> Spring Boot Controller 接收 /api/**
  -> ServiceImpl 执行业务逻辑
  -> InMemoryStore 读取内存数据和索引
  -> algorithm / indoor / storage.search 执行算法
  -> ApiResponse(code/data/message) 返回
  -> 前端 Pinia 状态更新，ECharts/Element Plus 渲染
```

启动阶段数据加载流程：

```text
TravelSystemApplication 启动
  -> InMemoryDataLoader
  -> 若 DB 预加载启用：从 Mapper 读数据库，写入 InMemoryStore
  -> 若 dev 模式或 DB 不可用：DevSeedDataLoader 读取 dev-seed/osm-data JSON
  -> IndoorDevSeedLoader 读取室内 JSON
  -> IndoorGraphRegistry 把室内 bundle 转成室内图
  -> InMemoryStore 重建 Trie、NGram 等索引
```

## 2. 项目目录结构解析

### 2.1 根目录

- `pom.xml`：后端 Maven 配置，定义 Spring Boot、MyBatis-Plus、JWT、Druid、Redis、Volc SDK、测试依赖等。
- `README.md`：项目入口说明。
- `AGENTS.md`：AI 协作和课程约束说明。
- `frontend/`：Vue 前端工程。
- `src/main/java/com/travel/`：后端主代码。
- `src/main/resources/`：配置文件、种子数据、OSM 数据。
- `src/test/java/com/travel/`：后端单元测试。
- `docs/`：需求、技术设计、验收、AI 交接文档。
- `scripts/`：数据生成、OSM 采集、验收校验脚本。
- `skills/`：本地 AI 协作技能说明，不是运行核心代码。
- `ExploreScape-Travel-website-main/`：早期静态页面资源，现核心前端在 `frontend/`。

### 2.2 后端目录：`src/main/java/com/travel`

- `TravelSystemApplication.java`：Spring Boot 启动入口。
- `controller/`：REST API 控制层，负责 URL 映射、参数接收、统一响应。
- `service/`：业务接口。
- `service/impl/`：业务实现，是最该重点阅读的目录。
- `model/entity/`：实体类，对应数据库表或 JSON 数据模型。
- `model/dto/`：请求参数对象。
- `model/vo/`：返回给前端的视图对象。
- `mapper/`：MyBatis-Plus Mapper，负责数据库 CRUD。
- `storage/`：内存数据仓库、数据加载器、搜索索引。
- `storage/search/`：前缀 Trie、N-Gram 倒排索引。
- `algorithm/`：通用算法，包括 Dijkstra、Graph、TopK。
- `ds/`：自定义数据结构，如 ArrayList、HashMap、PriorityQueue。
- `indoor/`：室内导航图模型、室内路径规划、楼层处理。
- `security/`：JWT、安全过滤器、登录态工具。
- `config/`：跨域、动画配置、室内配置等。
- `animation/`：日记动画生成任务、提示词构造、外部服务客户端。
- `common/`：统一响应和分页对象。
- `exception/`：全局异常处理。
- `util/`：地理距离、交通模式编码等工具。

核心代码优先级：

```text
TravelSystemApplication
  -> controller/*
  -> service/impl/*
  -> storage/InMemoryStore.java
  -> algorithm/*
  -> storage/search/*
  -> ds/*
  -> indoor/*
```

### 2.3 后端资源目录：`src/main/resources`

- `application.yml`：默认配置，含数据库、JWT 等配置。
- `application-dev.yml`：演示/开发配置，支持无数据库运行。
- `dev-seed/*.json`：用户、兴趣、景区、道路、设施、美食、日记、评论等演示数据。
- `osm-data/**/latest/*.json`：OSM 采集后的景区、道路、POI、设施、室内地图包。
- `config/poi-types.json`：POI 类型字典。
- `config/facility-types.json`：设施类型字典。
- `config/jimeng-animation.example.yml`：即梦动画配置示例。

### 2.4 前端目录：`frontend`

- `package.json`：前端依赖和脚本。
- `vite.config.ts`：Vite 配置。
- `src/main.ts`：Vue 应用入口，挂载 Pinia、Router、Element Plus 和全局样式。
- `src/router/index.ts`：前端路由配置和登录权限守卫。
- `src/lib/http.ts`：Axios 实例、拦截器、统一响应处理。
- `src/lib/api.ts`：所有后端接口封装和 TypeScript 类型。
- `src/stores/auth.ts`：登录用户状态。
- `src/stores/scenicHub.ts`：景区工作台共享状态。
- `src/layouts/AppLayout.vue`：主布局和导航。
- `src/views/HomePageView.vue`：首页。
- `src/views/HomeView.vue`：推荐页。
- `src/views/scenic/ScenicHubView.vue`：景区工作台主页面。
- `src/views/scenic/components/ScenicMapCanvas.vue`：ECharts 地图/拓扑/室内图核心组件。
- `src/views/scenic/components/panels/*.vue`：路线、设施、美食、POI 详情面板。
- `src/views/diary/*`：日记列表、编辑、详情。
- `src/views/food/*`：美食详情等。
- `src/views/admin/AdminView.vue`：后台管理。

## 3. 项目架构分析

### 3.1 分层架构

后端采用典型 Spring Boot 分层：

```text
Controller 层：接收请求，调用 Service，包装 ApiResponse
Service 层：业务规则、算法调用、内存数据查询、可选写库
Storage 层：InMemoryStore 保存运行时数据和索引
Mapper 层：MyBatis-Plus 数据库访问，演示时可以不依赖
Algorithm 层：图、最短路、TopK、搜索索引、自定义数据结构
Model 层：Entity / DTO / VO
```

前端采用 Vue 单页应用结构：

```text
Router -> View 页面 -> Pinia Store -> api.ts -> Element Plus/ECharts 渲染
```

### 3.2 模块协作方式

以“景区工作台路线规划”为例：

```text
用户进入 /scenic?areaId=252&tab=route
  -> ScenicHubView 从 query 同步 areaId/tab 到 scenicHub store
  -> ScenicMapCanvas 调 apiMapData 和 apiRoutePoiCandidates
  -> 后端 RouteController.getMapData
  -> RouteServiceImpl.loadGraph(areaId)
  -> InMemoryStore.findRoadsByAreaId(areaId)
  -> Graph.addUndirectedEdge 构造邻接表
  -> 前端 ECharts 渲染节点和边
  -> 用户选择起终点，RoutePanel 调 apiPlanRoute
  -> RouteServiceImpl.plan
  -> Dijkstra.shortestPath
  -> 返回 path/distance/time
  -> scenicHub.routePath 更新
  -> ScenicMapCanvas 高亮路径
```

### 3.3 数据关系

```text
ScenicArea 景区
  1 -> N Poi 景点/路网节点/建筑
  1 -> N Road 道路边
  1 -> N Facility 设施
  1 -> N Restaurant 餐厅
  1 -> N Food 美食
  N -> N Tag，通过 ScenicAreaTag 关联
  N -> N Diary，通过 DiaryDestination 关联

User 用户
  1 -> N UserInterest 用户兴趣
  1 -> N UserBehavior 行为记录
  1 -> N Diary 日记
  1 -> N Comment 评论

Poi 建筑节点
  可关联 IndoorBuildingBundle 室内图
```

### 3.4 统一响应

接口统一返回 `ApiResponse<T>`，包含 `code/data/message`。这样前端 `http.ts` 可以统一处理错误、登录态和提示信息。

## 4. 代码逐文件讲解：核心文件

### 4.1 `TravelSystemApplication.java`

职责：后端启动入口。`@SpringBootApplication` 会触发组件扫描，把 `@RestController`、`@Service`、`@Component` 等类注册进 Spring 容器。

```java
@SpringBootApplication
public class TravelSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelSystemApplication.class, args);
    }
}
```

### 4.2 `common/ApiResponse.java` 与 `PageData.java`

- `ApiResponse`：保证前后端响应都有 `code/data/message`。
- `PageData<T>`：用于景区列表、推荐列表这类分页结果，包含 `list` 和 `total`。

为什么这样写：前端不用猜接口格式，所有接口都按同一套结构解析。

### 4.3 Controller 层

#### `RecommendationController.java`

职责：景区推荐接口入口。

主要接口：

- `GET /api/recommendation`：景区列表。
- `GET /api/recommendation/personalized`：个性化推荐。
- `GET /api/recommendation/hot`：热门景区。
- `GET /api/recommendation/detail/{id}`：景区详情。
- `GET /api/recommendation/scenic-search`：景区名称搜索。

调用关系：

```text
RecommendationController -> RecommendationServiceImpl -> InMemoryStore
```

#### `RouteController.java`

职责：路线规划接口入口。

主要接口：

- `POST /api/route`：两点路线规划。
- `POST /api/route/multi-point`：多点路线规划。
- `GET /api/route/map-data`：景区地图数据。
- `GET /api/route/poi-candidates`：可选起终点 POI。
- `GET /api/route/poi-types`：POI 类型字典。

调用关系：

```text
RouteController -> RouteServiceImpl -> Graph + Dijkstra -> InMemoryStore
```

#### `FacilityController.java`

职责：设施查询接口入口。

主要接口：

- `GET /api/facility/nearby`：附近设施。
- `GET /api/facility/search`：设施搜索。
- `GET /api/facility/detail/{id}`：设施详情。

设施查询支持经纬度入口，也支持地图锚点 `anchorPoiId`。

#### `FoodController.java`

职责：美食推荐、搜索、详情、评分。

主要接口：

- `GET /api/food/recommendation`
- `GET /api/food/search`
- `GET /api/food/detail/{id}`
- `GET /api/food/detail-view/{id}`
- `POST /api/food/rate`

`detail-view` 比 `detail` 多返回餐厅名、景区名、评论列表，更适合前端详情页。

#### `DiaryController.java` 与 `DiaryAnimationController.java`

职责：日记 CRUD、搜索、评分、附件上传、动画生成任务。

主要接口：

- `POST /api/diary`：创建日记。
- `GET /api/diary`：日记列表。
- `GET /api/diary/{id}`：日记详情。
- `PUT /api/diary/{id}`：编辑日记。
- `DELETE /api/diary/{id}`：删除日记。
- `GET /api/diary/search`：日记搜索。
- `POST /api/diary/rate`：评分评论。
- `POST /api/diary/{id}/animation/generate`：生成动画任务。
- `GET /api/diary/animation/jobs/{jobId}`：查询任务状态。

#### `IndoorController.java`

职责：室内导航接口入口。

主要接口：

- `GET /api/indoor/buildings`：列出有室内图的建筑。
- `GET /api/indoor/{buildingPoiId}/meta`：建筑楼层、入口等元信息。
- `GET /api/indoor/{buildingPoiId}/floor/{level}`：某楼层室内节点和边。
- `POST /api/indoor/{buildingPoiId}/plan`：室内路径规划。

#### `AuthController.java`、`BehaviorController.java`、`AdminController.java`

- `AuthController`：注册、登录、刷新 token、用户兴趣、头像、个人信息。
- `BehaviorController`：记录用户浏览/收藏/喜欢行为。
- `AdminController`：管理端新增数据，开发模式 OSM 数据采集。

### 4.4 Service 层核心实现

#### `RecommendationServiceImpl.java`

职责：景区列表、热门推荐、个性化推荐。

个性化推荐核心公式：

```text
matchScore = Σ(userInterestWeight * scenicTagWeight)
heatNorm = scenic.heat / maxHeat
ratingNorm = scenic.rating / 5
score = 0.7 * matchScore + 0.2 * heatNorm + 0.1 * ratingNorm
```

关键逻辑：

1. 从 `InMemoryStore` 获取候选景区。
2. 可按类型或标签关键词筛选。
3. 取热度靠前的一批候选，减少计算量。
4. 读取用户兴趣权重。
5. 读取景区标签权重。
6. 做标签别名归一化，例如“自然”映射为 `nature`。
7. 计算综合分数并排序。
8. 返回 `ScenicAreaRecommendVO`，包含 `score` 和 `reason`。

答辩说法：

> 个性化推荐不是简单按热度排序，而是把用户兴趣和景区标签做向量匹配。用户兴趣有权重，景区标签也有权重，两者相乘累加得到兴趣匹配分，再融合热度和评分，得到最终推荐分数，并返回推荐理由。

#### `RouteServiceImpl.java`

职责：路线规划、地图数据、POI 候选点。

两点路线规划流程：

```text
plan(request)
  -> loadGraph(areaId)
  -> resolveTransportMode(vehicle)
  -> buildVehicleFilter(mode)
  -> 根据 strategy 选择距离权重或时间权重
  -> dijkstra.shortestPath(graph, startId, endId, weightFunc, edgeFilter)
  -> 计算 distance/time
  -> 返回 RoutePlanVO
```

交通工具过滤：

```java
private EdgeFilter buildVehicleFilter(TransportMode mode) {
    String modeCode = mode.code();
    return edge -> edge.getModeCongestion().containsKey(modeCode);
}
```

时间权重：

```text
effectiveSpeed = min(vehicleSpeed, roadSpeed) * congestion
time = distance / effectiveSpeed
```

多点路线规划：

1. 起点固定。
2. 终点固定或回到起点。
3. 中间点数量最多 20。
4. 先用 Dijkstra 预计算点与点之间最短路。
5. 用状态压缩动态规划求访问中间点的最优顺序。
6. 再把每一段最短路径拼成完整路径。

多点 DP 状态：

```text
dp[mask][last] = 从起点出发，已访问 mask 集合，最后停在 last 中间点的最小代价
```

答辩说法：

> 两点路径用 Dijkstra。多点路径不是贪心，而是先求关键点之间的最短路，再用状态压缩 DP 求中间点访问顺序，最后拼接每段路径。这样能保证中间点数量可控时得到全局最优顺序。

#### `FacilityServiceImpl.java`

职责：附近设施查询和设施搜索。

附近设施流程：

```text
nearbyByAnchor(anchorPoiId, radius, type, areaId)
  -> resolveAnchor 查 POI 或设施坐标
  -> store.findFacilitiesByAreaIdAndType
  -> GeoUtil.distanceMeters 先按直线距离过滤半径
  -> loadGraph(areaId)
  -> Dijkstra 计算锚点到设施的路网距离
  -> 优先按 pathDistance 排序，缺失时按 geoDistance 排序
```

为什么先地理距离再路网距离：全量跑 Dijkstra 成本高，先用半径做粗筛，再对候选集计算路网距离。

#### `FoodServiceImpl.java`

职责：美食推荐、搜索、详情、评分。

推荐评分公式：

```text
heatScore = food.heat / maxHeat
ratingScore = food.rating / 5
distScore = 1 - distance / radius
score = wh * heatScore + wr * ratingScore + wd * distScore
```

默认权重：热度 0.3，评分 0.5，距离 0.2。

TopK 优化：

```java
List<FoodRecommendVO> top =
    DsConvert.toJavaList(topKSelector.selectTopK(candidates, topN, comparator));
```

为什么这样写：只需要前 `page * size` 个结果，不必完整排序全部美食，符合 Top-K 场景。

#### `DiaryServiceImpl.java`

职责：旅行日记创建、列表、详情、修改、删除、搜索、评分。

重点：

- 日记和目的地是多对多关系，通过 `DiaryDestination` 管理。
- 评论通过 `Comment` 表/内存结构管理。
- 演示模式下采用“内存优先，可选写库”，数据库失败不能让主流程 500。
- 搜索走 `InMemoryStore.searchDiaries`，使用 N-Gram 倒排索引。

#### `IndoorServiceImpl.java` 与 `IndoorPathPlanner.java`

职责：室内图查询和室内路径规划。

室内路径流程：

```text
前端双击 indoorAvailable 的建筑 POI
  -> apiIndoorMeta(buildingPoiId)
  -> apiIndoorFloor(buildingPoiId, level)
  -> 用户选室内起点/终点
  -> apiIndoorPlan
  -> IndoorServiceImpl.plan
  -> IndoorPathPlanner.plan
  -> Dijkstra.shortestPath
  -> 返回 path、distanceMeters、timeSec、segments、instructions
```

`segments` 用来按楼层切分路径，`instructions` 用来生成可读文字指引。

### 4.5 Storage 层

#### `InMemoryStore.java`

职责：运行时内存数据中心。

保存的数据包括用户、兴趣、行为、景区、POI、道路、设施、美食、餐厅、日记、评论、室内图、景区标签和检索索引。

重要索引：

```text
usersById / userIdByUsername / userIdByEmail
scenicAreasById / scenicAreaIdsByType
poiIdsByAreaId
roadIdsByAreaId
facilityIdsByAreaId / facilityIdsByType
foodIdsByAreaId
diaryDestinationIdsByDiaryId / diaryIdsByDestinationId
facilityNGramIndex / foodNGramIndex / diaryFullTextIndex
```

为什么要有 InMemoryStore：

1. 满足课程约束：检索、匹配、排序在内存和应用层完成。
2. 支持无数据库演示：JSON seed 直接进入内存即可运行。
3. 降低频繁查询数据库的开销。

#### `InMemoryDataLoader.java`

职责：应用启动时加载数据。如果 `app.storage.preload.enabled=true`，尝试从数据库 Mapper 加载；如果关闭预加载或数据库连接失败，调用 `DevSeedDataLoader` 加载 JSON。

#### `DevSeedDataLoader.java`

职责：读取 `dev-seed` 和 `osm-data` JSON，包括用户、兴趣、景区、景区 alias、POI、道路、设施、餐厅、美食、日记、目的地、评论和 map-imports 指向的 OSM 地图包。

#### `IndoorDevSeedLoader.java`

职责：读取室内 JSON 数据，优先读取 `osm-data/.../latest/indoor/`，也兼容旧的 `dev-seed/indoor/`。

### 4.6 Algorithm 层

#### `Graph.java`

职责：图结构，邻接表实现。道路默认双向，所以添加一条道路会写入两条有向边。

```java
public void addUndirectedEdge(long startId, long endId, double distance, double speed,
                              Map<String, Double> modeCongestion) {
    addDirectedEdge(startId, endId, distance, speed, modeCongestion);
    addDirectedEdge(endId, startId, distance, speed, modeCongestion);
}
```

#### `Edge.java`

职责：图的一条边。

- `targetId`：目标节点。
- `distance`：道路长度。
- `speed`：道路理想速度。
- `modeCongestion`：不同交通工具的可通行和拥堵系数。

#### `Dijkstra.java`

职责：最短路径算法。

核心思想：

```text
dist[start] = 0
把 start 放入优先队列
while 队列不空:
  取出当前距离最小的节点 u
  遍历 u 的所有边
  如果边被交通工具过滤器禁止，跳过
  如果 dist[u] + weight(edge) 更小，就更新 dist[v] 和 prev[v]
最后用 prev 从终点反推路径
```

关键代码：

```java
if (nd < dist.getOrDefault(v, Double.MAX_VALUE)) {
    dist.put(v, nd);
    prev.put(v, u);
    pq.add(new Node(v, nd));
}
```

为什么这样写：Dijkstra 适用于非负边权，项目中距离和时间都非负，所以适合用于路线规划和室内导航。

#### `TopKSelector.java`

职责：从候选集中选出分数最高的前 K 个。

核心思想：维护一个大小为 K 的小顶堆。堆顶是当前 TopK 里最差的元素。新元素比堆顶好时，替换堆顶。

复杂度：

```text
全排序：O(n log n)
TopK：O(n log k)
```

### 4.7 搜索索引

#### `PrefixTrieIdIndex.java`

职责：前缀 Trie，支持前缀搜索。

插入时从 root 开始逐字符建节点，每经过一个字符节点，就把 id 加入 `candidateIds`；查询时沿 Trie 走到 prefix 最后一个字符，返回该节点候选 id。

#### `NGramInvertedIndex.java`

职责：N-Gram 倒排索引，用于模糊/全文检索。

例子：

```text
文本：颐和园
2-gram：颐和、和园
3-gram：颐和园
建立映射：
颐和 -> [id]
和园 -> [id]
颐和园 -> [id]
```

查询时把 query 也切成 2-gram/3-gram，找到每个 term 对应的 id 列表，对 id 命中次数计分，按命中次数降序返回候选。

答辩说法：

> 搜索不是在 SQL 中模糊匹配，而是把文本切成 N-Gram 建倒排索引。查询时统计候选对象命中的片段数量，命中越多相关度越高。

### 4.8 自定义数据结构：`com.travel.ds`

核心文件：

- `ArrayList.java`
- `LinkedList.java`
- `HashMap.java`
- `HashSet.java`
- `PriorityQueue.java`
- `List.java`
- `Map.java`
- `Set.java`
- `Collections.java`
- `DsConvert.java`

作用：

- 算法层使用自定义 `List/Map/Set/PriorityQueue`，满足课程要求。
- Service 层和前端接口仍可以使用 Java 标准集合，边界处用 `DsConvert` 转换。

答辩说法：

> 我们没有在核心算法里直接使用 Java 内置集合，而是在 `com.travel.ds` 实现了自己的线性表、哈希表、集合和优先队列。业务层为了和 Spring/Jackson 兼容仍使用 Java 集合，算法边界通过 DsConvert 做转换。

### 4.9 前端核心文件

#### `frontend/src/main.ts`

职责：创建 Vue 应用，注册 Pinia、Router、Element Plus、全局样式。

#### `frontend/src/router/index.ts`

职责：页面路由和权限守卫。主要路由包括 `/home`、`/recommend`、`/scenic`、`/food/:id`、`/diary`、`/admin`、`/profile`。

守卫逻辑：

```text
如果页面 requiresAuth 且用户未登录 -> /login
如果页面要求 ADMIN 但用户不是 ADMIN -> /home
```

#### `frontend/src/lib/api.ts`

职责：前端 API 封装和类型定义。它是前后端契约的集中位置。

#### `frontend/src/stores/scenicHub.ts`

职责：景区工作台跨组件共享状态，包括 `areaId`、`focusPoiId`、`panelTab`、`poiCandidates`、`routePath`、`facilityResults`、`foodWeights`。

为什么要用 Pinia：地图组件、路线面板、设施面板、美食面板需要共享同一个景区上下文。

#### `ScenicHubView.vue`

职责：景区工作台主页面。

```text
读取 route.query.areaId/tab
  -> 写入 scenicHub store
  -> 渲染 ScenicMapCanvas
  -> 渲染 RoutePanel / FacilityPanel / FoodPanel
地图加载完成
  -> onMapLoaded(candidates, details)
  -> store 保存候选点和 POI 名称映射
```

#### `ScenicMapCanvas.vue`

职责：景区拓扑图、设施高亮、路径高亮、室内图。

关键能力：

- 调 `apiMapData` 获取路网节点和边。
- 调 `apiRoutePoiCandidates` 获取可选 POI。
- 用 ECharts graph series 渲染路网。
- 单击节点：设置当前焦点 POI。
- 双击 `indoorAvailable` 节点：进入室内导航。
- 显示路径高亮和设施高亮。
- 室内模式下调 `apiIndoorMeta`、`apiIndoorFloor`、`apiIndoorPlan`。

#### `RoutePanel.vue`

职责：路线规划表单。两点规划调用 `apiPlanRoute`，多点规划调用 `apiPlanRouteMulti`，规划成功后 `hub.setRoutePath(result.path)` 触发地图高亮。

#### `FacilityPanel.vue`

职责：附近设施和设施搜索。使用当前 `focusPoiId` 作为锚点，调用设施接口，结果写入 `hub.facilityResults`，并同步地图高亮。

#### `FoodPanel.vue`

职责：美食推荐、搜索、详情抽屉。推荐时传入 `areaId`、`anchorPoiId`、权重；详情调用 `apiFoodDetailView`。

## 5. 核心功能实现原理与答辩口径

### 5.1 个性化景区推荐

功能入口：

- 前端：`HomeView.vue`
- API：`apiRecommendationPersonalized`
- 后端：`RecommendationController.personalized`
- 实现：`RecommendationServiceImpl.personalized`

执行步骤：

```text
用户打开推荐页
  -> 前端请求 /api/recommendation/personalized
  -> 后端取用户兴趣
  -> 取景区标签权重
  -> 标签别名归一化
  -> 计算 matchScore、heatNorm、ratingNorm
  -> 综合打分排序
  -> 返回推荐列表和 reason
```

老师问“这个推荐怎么实现的”：

> 我们把用户兴趣和景区标签都看成带权重的特征。每个景区会根据标签和用户兴趣的匹配度得到一个匹配分，再融合景区热度和评分。公式是 `0.7 * 兴趣匹配 + 0.2 * 热度归一化 + 0.1 * 评分归一化`。数据来自内存仓库，不依赖 SQL 推荐。

### 5.2 路线规划

功能入口：

- 前端：`ScenicHubView.vue` + `RoutePanel.vue`
- API：`/api/route`、`/api/route/multi-point`
- 后端：`RouteController`
- 实现：`RouteServiceImpl`
- 算法：`Graph` + `Dijkstra`

老师问“为什么不用直接暴力全排列”：

> 暴力全排列是 O(n!)，中间点稍多就不可用。这里先预计算点间最短路，再用状态压缩 DP，复杂度约 O(2^m * m^2)，在课程项目限制的 20 个中间点内更可控。

### 5.3 设施查询

功能入口：

- 前端：`FacilityPanel.vue`
- API：`/api/facility/nearby`、`/api/facility/search`
- 后端：`FacilityServiceImpl`

老师问“为什么既有直线距离又有路径距离”：

> 直线距离用于快速过滤候选，路径距离用于最终排序。这样既保证性能，也更符合真实游览距离。

### 5.4 美食推荐

功能入口：

- 前端：`FoodPanel.vue`
- API：`/api/food/recommendation`
- 后端：`FoodServiceImpl.recommend`
- 算法：`TopKSelector`

老师问“TopK 的意义”：

> 如果候选很多但只展示前几页，没有必要全量排序。TopK 用大小为 K 的小顶堆，时间复杂度从全排序的 O(n log n) 降到 O(n log k)。

### 5.5 模糊搜索

功能入口：

- 设施搜索：`InMemoryStore.searchFacilities`
- 美食搜索：`InMemoryStore.searchFoods`
- 日记搜索：`InMemoryStore.searchDiaries`
- 索引实现：`NGramInvertedIndex`

老师问“有没有用 SQL LIKE”：

> 没有。数据启动后加载到 InMemoryStore，设施、美食、日记搜索通过 N-Gram 倒排索引完成，查询时统计关键词片段命中次数，按相关度返回。

### 5.6 室内导航

功能入口：

- 前端：`ScenicMapCanvas.vue` 双击建筑
- API：`/api/indoor/**`
- 后端：`IndoorServiceImpl`
- 算法：`IndoorPathPlanner` 复用 Dijkstra

老师问“室内和室外有什么区别”：

> 算法本质一样，都是图最短路。区别是室内图多了楼层、节点类型和跨层边，规划结果会按楼层分段，并生成电梯、楼梯、房间等文字指引。

## 6. 数据结构与数据库说明

### 6.1 主要实体/表

#### `User`

用户表：`id`、`username`、`password`、`email`、`nickname`、`avatar`、`role`。

#### `UserInterest`

用户兴趣表：`userId`、`interestType`、`weight`。推荐系统通过它和景区标签匹配。

#### `ScenicArea`

景区表：`id`、`name`、`description`、`location`、`longitude/latitude`、`type`、`rating`、`heat`、`openTime`、`ticketPrice`、运行时补充的 `tags`。

#### `Tag` 与 `ScenicAreaTag`

标签和景区标签关联。`ScenicAreaTag` 通过 `scenicAreaId`、`tagId`、`weight` 表示某景区拥有某标签及其强度。

#### `Poi`

景点/路网节点/建筑节点：`id`、`name`、`type`、`longitude/latitude`、`parentId`、`indoorAvailable`、`osmIndoorRef`、`areaId`。

#### `Road`

道路表：`startId`、`endId`、`distance`、`speed`、`modeProfile`、`areaId`。`Road` 会被 `RouteServiceImpl.loadGraph` 转成 `Graph`。

#### `Facility`

设施表：`name`、`type`、`description`、`location`、`longitude/latitude`、`areaId`。

#### `Restaurant` 与 `Food`

`Restaurant` 保存餐厅位置；`Food` 保存菜品信息，并通过 `restaurantId` 关联餐厅。`Food` 重要字段包括 `cuisine`、`price`、`rating`、`heat`、`restaurantId`、`areaId`。

#### `Diary` 与 `DiaryDestination`

`Diary` 保存日记内容、图片、视频、动画、热度和评分；`DiaryDestination` 用 `diaryId` 和 `destinationId` 表示日记关联景区。

#### `Comment`

评论表：`userId`、`targetId`、`targetType`、`content`、`rating`。通过 `targetType + targetId` 关联到不同业务对象。

### 6.2 CRUD 如何实现

项目同时支持两条路径：

```text
数据库路径：Controller -> Service -> Mapper -> MySQL
演示路径：Controller -> Service -> InMemoryStore -> JSON seed 数据
```

演示模式下，新增/修改类功能通常会先更新内存；如果需要写数据库，则写库失败时跳过，保证演示不因 MySQL 不可用中断。

### 6.3 自定义数据结构和核心算法结构

- 图：邻接表 `Graph`
- 最短路：`Dijkstra`
- 堆：`com.travel.ds.PriorityQueue`
- 哈希表：`com.travel.ds.HashMap`
- 集合：`com.travel.ds.HashSet`
- 线性表：`com.travel.ds.ArrayList`
- 前缀索引：`PrefixTrieIdIndex`
- 倒排索引：`NGramInvertedIndex`
- TopK：`TopKSelector`

## 7. 接口 / 页面 / 交互说明

### 7.1 推荐页 `/recommend`

作用：展示热门景区和个性化推荐。

主要接口：

- `GET /api/recommendation/hot`
- `GET /api/recommendation/personalized`
- `GET /api/tags`

用户操作：

```text
选择筛选条件或模式
  -> HomeView 调 api.ts
  -> RecommendationServiceImpl 计算列表
  -> 前端展示景区卡片、推荐理由
  -> 点击进入 /scenic?areaId=xxx
```

### 7.2 景区工作台 `/scenic`

作用：地图、路线、设施、美食的一体化工作台。

主要接口：

- `GET /api/route/map-data`
- `GET /api/route/poi-candidates`
- `POST /api/route`
- `POST /api/route/multi-point`
- `GET /api/facility/nearby`
- `GET /api/food/recommendation`
- `GET /api/indoor/**`

交互：

```text
选择景区 -> 加载地图
单击 POI -> 设置焦点
路线面板规划 -> 地图高亮路线
设施面板查询 -> 地图高亮设施
美食面板推荐 -> 按权重推荐附近美食
双击可进入建筑 -> 切换室内模式
```

### 7.3 日记页 `/diary`

作用：旅行日记列表、详情、编辑、评分、动画生成。

主要接口：

- `GET /api/diary`
- `POST /api/diary`
- `GET /api/diary/{id}`
- `PUT /api/diary/{id}`
- `DELETE /api/diary/{id}`
- `GET /api/diary/search`
- `POST /api/diary/rate`
- `POST /api/diary/{id}/animation/generate`

### 7.4 美食详情 `/food/:id`

作用：展示美食、餐厅、景区、评论，并支持评分。

主要接口：

- `GET /api/food/detail-view/{id}`
- `POST /api/food/rate`

### 7.5 后台 `/admin`

作用：新增景区、POI、道路、美食，开发模式下导入 OSM。

主要接口：

- `POST /api/admin/scenic-area`
- `POST /api/admin/poi`
- `POST /api/admin/road`
- `POST /api/admin/food`
- `POST /api/admin/dev/import-place`
- `POST /api/admin/dev/generate-from-osm`

权限：前端路由要求登录且 `role=ADMIN`。

## 8. 验收答辩重点

### 8.1 老师可能问的问题与回答

**问题 1：你的项目核心算法有哪些？**

回答：

> 主要有 Dijkstra 最短路径、状态压缩 DP 多点路线、TopK 小顶堆选择、Trie 前缀索引、N-Gram 倒排索引，以及自定义 List/Map/Set/PriorityQueue。路线和室内导航用 Dijkstra，美食推荐用 TopK，搜索用 N-Gram 倒排索引。

**问题 2：路线规划怎么实现？**

回答：

> 道路数据先从内存仓库按景区取出，构造成邻接表图。两点规划用 Dijkstra，根据策略选择距离权重或时间权重，并用交通工具过滤器过滤不可通行道路。返回节点路径后，再计算距离和时间。

**问题 3：多点路线怎么实现？**

回答：

> 多点路线先把起点、终点、中间点之间的最短路预计算出来，然后用状态压缩 DP 求中间点访问顺序，最后把每一段最短路径拼接成完整路线。

**问题 4：推荐算法怎么实现？**

回答：

> 个性化推荐根据用户兴趣权重和景区标签权重计算匹配分，再融合热度和评分。公式是 `0.7 * 兴趣匹配 + 0.2 * 热度 + 0.1 * 评分`，并返回推荐理由。

**问题 5：搜索有没有用数据库 LIKE？**

回答：

> 没有。数据启动后加载到 InMemoryStore，设施、美食、日记搜索通过 N-Gram 倒排索引完成，查询时统计关键词片段命中次数，按相关度返回。

**问题 6：自定义数据结构在哪里体现？**

回答：

> 在 `com.travel.ds` 包中实现了 ArrayList、HashMap、HashSet、PriorityQueue 等；算法层的 Dijkstra、Graph、TopK、搜索索引使用这些自定义结构，业务层边界再通过 DsConvert 转成 Java 集合。

**问题 7：无数据库时为什么还能运行？**

回答：

> dev profile 下关闭数据库预加载，DevSeedDataLoader 会读取 dev-seed 和 osm-data JSON 写入 InMemoryStore。DB 写入失败时部分功能会保留内存态，不中断演示流程。

**问题 8：室内导航怎么做？**

回答：

> 室内数据也是图，节点包含楼层和类型，边包含走廊、电梯、楼梯等。进入室内后，前端按楼层请求节点和边，规划时后端在室内图上跑 Dijkstra，并返回分楼层路径和文字指引。

**问题 9：前后端如何交互？**

回答：

> 前端 `api.ts` 封装所有请求，页面通过 Pinia 保存状态；后端 Controller 接收 `/api/**` 请求，Service 调 InMemoryStore 和算法模块，统一用 `ApiResponse` 返回。

**问题 10：项目亮点是什么？**

回答：

> 亮点是把数据结构算法落到了真实业务：景区推荐、路线规划、设施查询、美食排序、全文搜索、室内导航都不是孤立 demo，而是通过内存数据仓库和前端地图交互连成完整系统。

### 8.2 项目亮点

- 内存优先架构，支持无数据库演示。
- Dijkstra + 多点 DP + TopK + 倒排索引，算法场景丰富。
- 自定义数据结构在算法层落地，贴合课程要求。
- 景区工作台把地图、路线、设施、美食、室内导航整合在一个页面。
- OSM 数据和 dev-seed 结合，数据规模较大。
- 前后端统一接口结构，API 封装清晰。

### 8.3 项目不足

- `application.yml` 中仍有明文数据库配置风险，生产环境应改为环境变量。
- Java 版本与部分早期文档基线可能不一致。
- 个性化推荐仍是可解释的规则模型，不是复杂机器学习模型。
- 多点路线限制中间点数量，点太多时状态压缩 DP 会指数增长。
- 部分功能依赖 seed 数据质量，例如室内图连通性、道路节点是否完整。

### 8.4 可改进方向

- 增加更完善的用户行为反馈，让推荐权重动态学习。
- 对多点路线增加启发式算法，支持更多中间点。
- 搜索索引增加去重、字段权重和拼音支持。
- 室内导航增加转向提示和跨楼层更细指令。
- 把配置敏感信息迁移到环境变量或密钥管理。
- 增加端到端测试和前端交互测试。

## 9. 学习路线

建议按下面顺序阅读：

```text
1. docs/AI/PROJECT_CONTEXT.md
2. README.md
3. pom.xml / frontend/package.json
4. TravelSystemApplication.java
5. application-dev.yml
6. InMemoryDataLoader.java / DevSeedDataLoader.java / InMemoryStore.java
7. controller/RecommendationController.java / RouteController.java / FacilityController.java / FoodController.java / IndoorController.java
8. service/impl/RecommendationServiceImpl.java
9. service/impl/RouteServiceImpl.java
10. algorithm/graph/Graph.java / Dijkstra.java
11. algorithm/TopKSelector.java
12. storage/search/NGramInvertedIndex.java / PrefixTrieIdIndex.java
13. ds/ArrayList.java / HashMap.java / PriorityQueue.java
14. indoor/IndoorPathPlanner.java / IndoorServiceImpl.java
15. frontend/src/main.ts / router/index.ts / lib/api.ts
16. frontend/src/stores/scenicHub.ts
17. frontend/src/views/scenic/ScenicHubView.vue
18. frontend/src/views/scenic/components/ScenicMapCanvas.vue
19. frontend/src/views/scenic/components/panels/RoutePanel.vue / FacilityPanel.vue / FoodPanel.vue
20. src/test/java 下的算法和服务测试
```

如果时间很紧，优先读：

```text
RouteServiceImpl.java
Dijkstra.java
RecommendationServiceImpl.java
FoodServiceImpl.java
TopKSelector.java
NGramInvertedIndex.java
InMemoryStore.java
ScenicMapCanvas.vue
api.ts
```

## 10. 10 分钟快速复习版

### 0-2 分钟：项目是什么

这是一个个性化旅游推荐系统，包含推荐、路线、设施、美食、日记、室内导航和后台管理。课程重点是数据结构和算法落地，核心逻辑在内存和自定义数据结构中完成。

### 2-4 分钟：架构怎么讲

```text
Vue 前端
  -> api.ts
  -> Spring Controller
  -> ServiceImpl
  -> InMemoryStore
  -> Algorithm / Search / Indoor
```

演示时不用 MySQL，`dev-seed` 和 `osm-data` JSON 会加载到内存。

### 4-6 分钟：算法怎么讲

- 路线规划：道路转图，Dijkstra 求最短路。
- 多点路线：关键点两两 Dijkstra + 状态压缩 DP。
- 美食推荐：热度、评分、距离综合打分，用 TopK 小顶堆取前 N。
- 搜索：N-Gram 倒排索引，不用 SQL LIKE。
- 室内导航：室内节点边构图，复用 Dijkstra，并按楼层分段。
- 自定义 DS：算法层使用 `com.travel.ds` 的 List/Map/Set/PriorityQueue。

### 6-8 分钟：核心文件

```text
后端入口：TravelSystemApplication.java
统一响应：ApiResponse.java
内存仓库：InMemoryStore.java
推荐：RecommendationServiceImpl.java
路线：RouteServiceImpl.java
最短路：Dijkstra.java
图结构：Graph.java
TopK：TopKSelector.java
搜索：NGramInvertedIndex.java
室内：IndoorPathPlanner.java
前端接口：frontend/src/lib/api.ts
景区工作台：ScenicHubView.vue + ScenicMapCanvas.vue
```

### 8-10 分钟：答辩金句

- “本项目不是直接查数据库展示，而是启动时预加载到内存，运行时用内存索引和算法计算。”
- “路线规划把道路抽象成图，边权可以是距离，也可以是时间，交通工具通过边过滤器控制。”
- “多点路线用状态压缩 DP 求访问顺序，不是简单贪心。”
- “模糊搜索用 N-Gram 倒排索引，不依赖 SQL LIKE/FULLTEXT。”
- “算法层使用自定义数据结构，业务层通过 DsConvert 和 Java 集合衔接，兼顾课程要求和工程兼容。”
- “室内导航和室外路线本质都是图最短路，只是室内多了楼层、节点类型和跨层边。”
