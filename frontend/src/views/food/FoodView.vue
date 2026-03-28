<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import {
  apiFoodRecommendation,
  apiFoodSearch,
  apiScenicSearchByKeyword,
  type Food,
  type FoodRecommendVO,
  type ScenicArea,
} from '../../lib/api'

const loading = ref(false)
const tab = ref<'recommend' | 'search'>('recommend')

const rec = reactive({
  areaId: undefined as number | undefined,
  sort: '' as '' | 'heat' | 'rating' | 'distance',
  page: 1,
  size: 10,
})
const recList = ref<FoodRecommendVO[]>([])
const recLocation = reactive({
  lat: undefined as number | undefined,
  lng: undefined as number | undefined,
})

const q = reactive({
  keyword: '',
  cuisine: '',
  areaId: undefined as number | undefined,
  page: 1,
  size: 10,
})
const list = ref<Food[]>([])

const recAreaOpts = ref<ScenicArea[]>([])
const qAreaOpts = ref<ScenicArea[]>([])
const recAreaLoading = ref(false)
const qAreaLoading = ref(false)
let recAreaSeq = 0
let qAreaSeq = 0

async function remoteRecArea(keyword: string) {
  const q = keyword.trim()
  if (!q) {
    recAreaSeq++
    recAreaOpts.value = []
    return
  }
  const seq = ++recAreaSeq
  recAreaLoading.value = true
  try {
    recAreaOpts.value = await apiScenicSearchByKeyword({ keyword: q, limit: 50 })
  } finally {
    if (seq === recAreaSeq) recAreaLoading.value = false
  }
}

async function remoteQArea(keyword: string) {
  const q = keyword.trim()
  if (!q) {
    qAreaSeq++
    qAreaOpts.value = []
    return
  }
  const seq = ++qAreaSeq
  qAreaLoading.value = true
  try {
    qAreaOpts.value = await apiScenicSearchByKeyword({ keyword: q, limit: 50 })
  } finally {
    if (seq === qAreaSeq) qAreaLoading.value = false
  }
}

async function loadRec() {
  if (rec.areaId == null) {
    ElMessage.warning('请先选择景区，再获取美食推荐')
    return
  }

  if (rec.sort === 'distance' && (recLocation.lat == null || recLocation.lng == null)) {
    await tryResolveLocation()
  }

  loading.value = true
  try {
    const weights =
      rec.sort === 'heat'
        ? { wHeat: 1, wRating: 0, wDistance: 0 }
        : rec.sort === 'rating'
          ? { wHeat: 0, wRating: 1, wDistance: 0 }
          : rec.sort === 'distance'
            ? { wHeat: 0, wRating: 0, wDistance: 1 }
            : {}
    const params: any = {
      areaId: rec.areaId,
      page: rec.page,
      size: rec.size,
    }
    if (recLocation.lat != null && recLocation.lng != null) {
      params.lat = recLocation.lat
      params.lng = recLocation.lng
    }
    Object.assign(params, weights)
    recList.value = await apiFoodRecommendation(params)
  } finally {
    loading.value = false
  }
}

async function tryResolveLocation(): Promise<void> {
  if (!navigator.geolocation) {
    ElMessage.info('当前浏览器不支持定位，已使用景区中心点进行距离估计')
    return
  }

  await new Promise<void>((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        recLocation.lat = pos.coords.latitude
        recLocation.lng = pos.coords.longitude
        resolve()
      },
      () => {
        ElMessage.info('定位不可用，已回退使用景区中心点进行距离估计')
        resolve()
      },
      { enableHighAccuracy: true, timeout: 5000 },
    )
  })
}

async function loadSearch() {
  loading.value = true
  try {
    list.value = await apiFoodSearch({
      keyword: q.keyword || undefined,
      cuisine: q.cuisine || undefined,
      areaId: q.areaId,
      page: q.page,
      size: q.size,
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // areaId 需要用户选择；避免在未填写时直接调用后端
})
</script>

<template>
  <div class="page">
    <el-card class="glass" shadow="never">
      <template #header>
        <div style="font-weight: 900">美食</div>
      </template>

      <el-tabs v-model="tab">
        <el-tab-pane label="推荐" name="recommend">
          <div class="formRow recommendRow">
            <el-select
              v-model="rec.areaId"
              filterable
              remote
              :reserve-keyword="false"
              placeholder="景区（必填，输入名称关键字）"
              :remote-method="remoteRecArea"
              :loading="recAreaLoading"
              style="min-width: 200px"
            >
              <el-option
                v-for="o in recAreaOpts"
                :key="o.id"
                :label="o.name"
                :value="o.id"
              />
            </el-select>

            <el-select v-model="rec.sort" placeholder="排序（可选）" class="control sortControl" clearable>
              <el-option label="热度" value="heat" />
              <el-option label="评分" value="rating" />
              <el-option label="距离" value="distance" />
            </el-select>

            <!-- 右侧：获取推荐 -->
            <el-button type="primary" :loading="loading" class="getRecBtn" @click="loadRec">搜索</el-button>
          </div>

          <el-table :data="recList" v-loading="loading" style="width: 100%; margin-top: 16px" @row-click="(r: FoodRecommendVO)=>$router.push(`/food/${r.id}`)">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="restaurantName" label="餐厅" width="140" />
            <el-table-column prop="cuisine" label="菜系" width="120" />
            <el-table-column prop="price" label="价格" width="120" />
            <el-table-column prop="rating" label="评分" width="120" />
            <el-table-column prop="heat" label="热度" width="120" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="搜索" name="search">
          <div class="formRow searchRow">
            <el-select
              v-model="q.areaId"
              filterable
              remote
              clearable
              :reserve-keyword="false"
              placeholder="景区（必填，输入名称关键字）"
              :remote-method="remoteQArea"
              :loading="qAreaLoading"
              style="min-width: 200px"
            >
              <el-option
                v-for="o in qAreaOpts"
                :key="o.id"
                :label="o.name"
                :value="o.id"
              />
            </el-select>

            <el-input v-model="q.keyword" placeholder="关键词 keyword" clearable class="control kwControl" />
            <el-input v-model="q.cuisine" placeholder="菜系 cuisine" clearable class="control cuisineControl" />

            <el-button type="primary" :loading="loading" class="searchBtn" @click="loadSearch">搜索</el-button>
          </div>

          <el-table :data="list" v-loading="loading" style="width: 100%; margin-top: 16px" @row-click="(r: Food)=>$router.push(`/food/${r.id}`)">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="cuisine" label="菜系" width="120" />
            <el-table-column prop="price" label="价格" width="120" />
            <el-table-column prop="rating" label="评分" width="120" />
            <el-table-column prop="heat" label="热度" width="120" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.formRow {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.recommendRow {
  flex-wrap: nowrap; /* 桌面端尽量保持同一排 */
}

.control {
  flex-shrink: 1; /* 允许在较窄宽度下自动压缩，避免按钮被挤出画面 */
}

.sortControl {
  width: 120px;
}

.getRecBtn {
  flex-shrink: 1;
  min-width: 120px;
}

.searchRow {
  flex-wrap: nowrap;
}

.kwControl {
  width: 150px;
}

.cuisineControl {
  width: 140px;
}

.searchBtn {
  flex-shrink: 1;
  min-width: 98px;
}

@media (max-width: 780px) {
  .recommendRow,
  .searchRow {
    flex-wrap: wrap;
  }
}

.hint {
  margin-top: 8px;
  font-size: 12px;
}
</style>

