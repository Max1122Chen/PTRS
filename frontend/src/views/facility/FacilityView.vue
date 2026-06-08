<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import {
  apiFacilityDetail,
  apiFacilityNearby,
  apiFacilitySearch,
  apiScenicDetail,
  apiScenicSearchByKeyword,
  type Facility,
  type FacilityNearbyVO,
  type ScenicArea,
} from '../../lib/api'

const tab = ref<'nearby' | 'search'>('nearby')
const loading = ref(false)

const nearbyForm = reactive({
  lat: undefined as number | undefined,
  lng: undefined as number | undefined,
  radius: undefined as number | undefined,
  type: '',
  areaId: undefined as number | undefined,
})
const nearbyList = ref<FacilityNearbyVO[]>([])

const searchForm = reactive({
  keyword: '',
  type: '',
  areaId: undefined as number | undefined,
})
const searchList = ref<Facility[]>([])

const nearbyAreaOpts = ref<ScenicArea[]>([])
const searchAreaOpts = ref<ScenicArea[]>([])
const nearbyAreaLoading = ref(false)
const searchAreaLoading = ref(false)
let nearbyAreaSeq = 0
let searchAreaSeq = 0

async function remoteNearbyArea(keyword: string) {
  const q = keyword.trim()
  if (!q) {
    nearbyAreaSeq++
    nearbyAreaOpts.value = []
    return
  }
  const seq = ++nearbyAreaSeq
  nearbyAreaLoading.value = true
  try {
    nearbyAreaOpts.value = await apiScenicSearchByKeyword({ keyword: q, limit: 50 })
  } finally {
    if (seq === nearbyAreaSeq) nearbyAreaLoading.value = false
  }
}

async function remoteSearchArea(keyword: string) {
  const q = keyword.trim()
  if (!q) {
    searchAreaSeq++
    searchAreaOpts.value = []
    return
  }
  const seq = ++searchAreaSeq
  searchAreaLoading.value = true
  try {
    searchAreaOpts.value = await apiScenicSearchByKeyword({ keyword: q, limit: 50 })
  } finally {
    if (seq === searchAreaSeq) searchAreaLoading.value = false
  }
}

const detail = ref<Facility | null>(null)
const detailOpen = ref(false)

async function ensureLocation() {
  if (nearbyForm.lat != null && nearbyForm.lng != null) return true

  if (!('geolocation' in navigator)) {
    ElMessage.warning('浏览器不支持定位功能')
    return false
  }

  return new Promise<boolean>((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        nearbyForm.lat = pos.coords.latitude
        nearbyForm.lng = pos.coords.longitude
        resolve(true)
      },
      async (err) => {
        // 定位失败时，若已选择景区则使用景区中心点作为兜底坐标，避免整条功能不可用。
        if (nearbyForm.areaId != null) {
          try {
            const scenic = await apiScenicDetail(nearbyForm.areaId)
            if (scenic.latitude != null && scenic.longitude != null) {
              nearbyForm.lat = scenic.latitude
              nearbyForm.lng = scenic.longitude
              ElMessage.info('定位被拒绝，已使用景区中心点进行附近设施查询')
              resolve(true)
              return
            }
          } catch {
            // ignore and fall through to warning below
          }
        }

        ElMessage.warning(`获取定位失败：${err.message || '未知原因'}`)
        resolve(false)
      },
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 },
    )
  })
}

async function loadNearby() {
  const ok = await ensureLocation()
  if (!ok) return
  loading.value = true
  try {
    nearbyList.value = await apiFacilityNearby({
      lat: nearbyForm.lat as number,
      lng: nearbyForm.lng as number,
      radius: nearbyForm.radius,
      type: nearbyForm.type || undefined,
      areaId: nearbyForm.areaId,
    })
  } finally {
    loading.value = false
  }
}

async function loadSearch() {
  loading.value = true
  try {
    searchList.value = (await apiFacilitySearch({
      keyword: searchForm.keyword || undefined,
      type: searchForm.type || undefined,
      areaId: searchForm.areaId,
    })) as Facility[]
  } finally {
    loading.value = false
  }
}

async function openDetail(id: number) {
  detail.value = await apiFacilityDetail(id)
  detailOpen.value = true
}

onMounted(() => {
  // 不在这里自动请求；点击“查询”时才会尝试浏览器定位后调用后端
})
</script>

<template>
  <div class="page">
    <el-card class="glass" shadow="never">
      <template #header>
        <div style="font-weight: 900">设施查询</div>
      </template>

      <el-tabs v-model="tab">
        <el-tab-pane label="附近设施" name="nearby">
          <div class="formRow nearbyRow">
            <el-input-number
              v-model="nearbyForm.radius"
              :min="50"
              :step="50"
              :controls="false"
              placeholder="距离"
              class="nearbyRadius"
            />
            <el-input v-model="nearbyForm.type" placeholder="类型" clearable class="nearbyType" />
            <el-select
              v-model="nearbyForm.areaId"
              filterable
              remote
              clearable
              :reserve-keyword="false"
              placeholder="景区（可选，输入名称关键字）"
              :remote-method="remoteNearbyArea"
              :loading="nearbyAreaLoading"
              class="nearbyArea"
            >
              <el-option
                v-for="o in nearbyAreaOpts"
                :key="o.id"
                :label="`${o.name}（ID ${o.id}）`"
                :value="o.id"
              />
            </el-select>
            <el-button type="primary" :loading="loading" class="nearbyBtn" @click="loadNearby">查询</el-button>
          </div>

          <div class="muted" style="margin-top: 8px; font-size: 12px; color: var(--text-2)">
            查询附近时会优先使用浏览器定位；若定位被拒绝且已选择景区，则自动降级为景区中心点。
          </div>


          <el-table :data="nearbyList" v-loading="loading" style="width: 100%; margin-top: 16px" @row-click="(r: FacilityNearbyVO)=>r.facility?.id != null && openDetail(r.facility.id)">
            <el-table-column label="ID" width="80">
              <template #default="{ row }">{{ row.facility?.id }}</template>
            </el-table-column>
            <el-table-column label="名称">
              <template #default="{ row }">{{ row.facility?.name }}</template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="{ row }">{{ row.facility?.type }}</template>
            </el-table-column>
            <el-table-column label="距离(m)" width="120">
              <template #default="{ row }">{{ row.pathDistance ?? row.geoDistance }}</template>
            </el-table-column>
            <el-table-column label="位置">
              <template #default="{ row }">{{ row.facility?.location }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="搜索" name="search">
          <div class="formRow searchRow">
            <el-select
              v-model="searchForm.areaId"
              filterable
              remote
              clearable
              :reserve-keyword="false"
              placeholder="景区（可选，输入名称关键字）"
              :remote-method="remoteSearchArea"
              :loading="searchAreaLoading"
              class="searchArea"
            >
              <el-option
                v-for="o in searchAreaOpts"
                :key="o.id"
                :label="`${o.name}（ID ${o.id}）`"
                :value="o.id"
              />
            </el-select>
            <el-input v-model="searchForm.type" placeholder="类型（可选）" clearable class="searchType" />
            <el-input v-model="searchForm.keyword" placeholder="关键字（可选）" clearable class="searchKeyword" />
            <el-button type="primary" :loading="loading" class="searchBtn" @click="loadSearch">查询</el-button>
          </div>


          <el-table :data="searchList" v-loading="loading" style="width: 100%; margin-top: 16px" @row-click="(r: Facility)=>openDetail(r.id)">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="type" label="类型" width="120" />
            <el-table-column prop="location" label="位置" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-drawer v-model="detailOpen" :with-header="false" size="420px" @close="detail=null">
      <div v-if="detail" class="drawer glass">
        <div style="font-weight: 900; font-size: 18px">{{ detail.name }}</div>
        <div class="muted" style="margin-top: 6px">{{ detail.type }}</div>
        <el-divider />
        <div class="muted">位置：{{ detail.location || '—' }}</div>
        <div class="muted">经纬度：{{ detail.latitude ?? '—' }}, {{ detail.longitude ?? '—' }}</div>
        <div style="margin-top: 10px">{{ detail.description || '暂无描述' }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.nearbyRow {
  flex-wrap: nowrap; /* 让半径/类型/景区/查询在桌面端尽量保持同一排 */
}

.nearbyRadius {
  width: 170px;
}

.nearbyType {
  width: 160px;
}

.nearbyArea {
  flex: 1;
  min-width: 220px;
}

.nearbyBtn {
  flex-shrink: 1;
  min-width: 96px;
}

.searchRow {
  flex-wrap: nowrap;
}

.searchArea {
  flex: 1.4;
  min-width: 220px;
}

.searchType {
  flex: 0.9;
  min-width: 140px;
}

.searchKeyword {
  flex: 1;
  min-width: 160px;
}

.searchBtn {
  flex-shrink: 0;
  min-width: 96px;
}

@media (max-width: 780px) {
  .nearbyRow {
    flex-wrap: wrap;
  }

  .searchRow {
    flex-wrap: wrap;
  }
}
.hint {
  margin-top: 8px;
  font-size: 12px;
}
.drawer {
  padding: 16px;
  height: 100%;
  box-sizing: border-box;
}
</style>

