<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  apiFoodRecommendation,
  apiFoodSearch,
  apiScenicSearchByKeyword,
  type Food,
  type FoodRecommendVO,
  type ScenicArea,
} from '../../lib/api'

const route = useRoute()
const router = useRouter()
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

async function openFoodRecRow(row: FoodRecommendVO) {
  const rq: Record<string, string> = {
    ft: 'r',
    fa: String(rec.areaId ?? ''),
    fp: String(rec.page),
    fs: rec.sort || '',
  }
  await router.replace({ path: '/food', query: rq })
  await router.push(`/food/${row.id}`)
}

async function openFoodSearchRow(row: Food) {
  const rq: Record<string, string> = {
    ft: 's',
    fp: String(q.page),
    fk: q.keyword || '',
    fc: q.cuisine || '',
  }
  if (q.areaId != null) rq.fa = String(q.areaId)
  await router.replace({ path: '/food', query: rq })
  await router.push(`/food/${row.id}`)
}

onMounted(async () => {
  const rq = route.query
  if (rq.ft === 'r') {
    tab.value = 'recommend'
    if (typeof rq.fa === 'string' && rq.fa) rec.areaId = Number(rq.fa)
    rec.page = rq.fp ? Number(rq.fp) || 1 : 1
    rec.sort = (rq.fs as '' | 'heat' | 'rating' | 'distance') || ''
    if (rec.areaId != null) await loadRec()
  } else if (rq.ft === 's') {
    tab.value = 'search'
    if (typeof rq.fa === 'string' && rq.fa) q.areaId = Number(rq.fa)
    else q.areaId = undefined
    q.keyword = typeof rq.fk === 'string' ? rq.fk : ''
    q.cuisine = typeof rq.fc === 'string' ? rq.fc : ''
    q.page = rq.fp ? Number(rq.fp) || 1 : 1
    await loadSearch()
  }
  if (rq.ft) {
    await router.replace({ path: '/food' })
  }
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
              class="recArea"
            >
              <el-option
                v-for="o in recAreaOpts"
                :key="o.id"
                :label="o.name"
                :value="o.id"
              />
            </el-select>

            <el-select v-model="rec.sort" placeholder="排序（可选）" class="sortControl" clearable>
              <el-option label="热度" value="heat" />
              <el-option label="评分" value="rating" />
              <el-option label="距离" value="distance" />
            </el-select>

            <!-- 右侧：获取推荐 -->
            <el-button type="primary" :loading="loading" class="getRecBtn" @click="loadRec">搜索</el-button>
          </div>

          <el-table
            :data="recList"
            v-loading="loading"
            style="width: 100%; margin-top: 16px"
            @row-click="(r: FoodRecommendVO) => void openFoodRecRow(r)"
          >
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
              class="searchArea"
            >
              <el-option
                v-for="o in qAreaOpts"
                :key="o.id"
                :label="o.name"
                :value="o.id"
              />
            </el-select>

            <el-input v-model="q.keyword" placeholder="关键词（可选）" clearable class="kwControl" />
            <el-input v-model="q.cuisine" placeholder="菜系（可选）" clearable class="cuisineControl" />

            <el-button type="primary" :loading="loading" class="searchBtn" @click="loadSearch">搜索</el-button>
          </div>

          <el-table
            :data="list"
            v-loading="loading"
            style="width: 100%; margin-top: 16px"
            @row-click="(r: Food) => void openFoodSearchRow(r)"
          >
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
.recommendRow {
  flex-wrap: nowrap;
}

.recArea {
  flex: 1;
  min-width: 200px;
}

.sortControl {
  width: 140px;
  flex-shrink: 0;
}

.getRecBtn {
  flex-shrink: 0;
  min-width: 96px;
}

.searchRow {
  flex-wrap: nowrap;
}

.searchArea {
  flex: 1.4;
  min-width: 200px;
}

.kwControl {
  flex: 1;
  min-width: 140px;
}

.cuisineControl {
  flex: 0.9;
  min-width: 130px;
}

.searchBtn {
  flex-shrink: 0;
  min-width: 96px;
}

@media (max-width: 780px) {
  .recommendRow,
  .searchRow {
    flex-wrap: wrap;
  }
}
</style>

