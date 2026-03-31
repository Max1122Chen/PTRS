<script setup lang="ts">
import { onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  apiAdminAddFood,
  apiAdminGenerateFromOsm,
  apiAdminAddPoi,
  apiAdminAddRoad,
  apiAdminAddScenicArea,
  apiAdminListScenicAreas,
  apiAdminSearchLocalPlace,
  apiAdminSearchOsm,
  type ScenicArea,
} from '../../lib/api'

const loading = ref(false)
const scenicList = ref<ScenicArea[]>([])
const total = ref(0)
const q = reactive({ page: 1, size: 10, type: '' as string | '' })

const scenicForm = reactive<Partial<ScenicArea>>({
  name: '',
  description: '',
  location: '',
  longitude: undefined,
  latitude: undefined,
  type: '',
  openTime: '',
  ticketPrice: '',
})

const poiForm = reactive<any>({ name: '', type: '', description: '', location: '', longitude: null, latitude: null, areaId: null })
const roadForm = reactive<any>({
  startId: null,
  endId: null,
  distance: undefined,
  speed: undefined,
  congestion: undefined,
  vehicleType: '',
  areaId: null,
})
const foodForm = reactive<any>({
  name: '',
  cuisine: '',
  description: '',
  price: undefined,
  areaId: null,
  restaurantId: null,
})
const devForm = reactive({
  placeName: '',
  force: false,
  buildFrontend: true,
})
const devRunning = ref(false)
const localSearching = ref(false)
const devResult = ref<any>(null)
const localMatches = ref<any[]>([])
const osmCandidates = ref<any[]>([])
const selectedOsm = ref<any | null>(null)
const selectedOsmKey = ref('')
let localSearchTimer: number | null = null

function buildOsmCandidateKey(row: any) {
  if (!row) return ''
  return `${row.osmType || ''}:${row.osmId || ''}:${row.placeId || ''}`
}

async function loadScenic() {
  loading.value = true
  try {
    const data = await apiAdminListScenicAreas({
      page: q.page,
      size: q.size,
      type: q.type || undefined,
    })
    scenicList.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function addScenic() {
  await apiAdminAddScenicArea(scenicForm)
  ElMessage.success('添加景区成功')
  await loadScenic()
}

async function addPoi() {
  if (!poiForm.name || !poiForm.type || !poiForm.location || !poiForm.areaId) {
    ElMessage.warning('请补全新增 POI 所需字段：name、type、location、areaId')
    return
  }
  await apiAdminAddPoi(poiForm)
  ElMessage.success('添加 POI 成功')
}

async function addRoad() {
  if (!roadForm.startId || !roadForm.endId || roadForm.distance == null || roadForm.speed == null || roadForm.congestion == null || !roadForm.areaId) {
    ElMessage.warning('请补全新增道路所需字段：startId、endId、distance、speed、congestion、areaId')
    return
  }
  await apiAdminAddRoad(roadForm)
  ElMessage.success('添加道路成功')
}

async function addFood() {
  if (!foodForm.name || !foodForm.cuisine || foodForm.price == null || !foodForm.areaId || !foodForm.restaurantId) {
    ElMessage.warning('请补全新增美食所需字段：name、cuisine、price、areaId、restaurantId')
    return
  }
  await apiAdminAddFood(foodForm)
  ElMessage.success('添加美食成功')
}

async function searchLocalPlace() {
  const keyword = devForm.placeName.trim()
  if (!keyword) {
    localMatches.value = []
    return
  }
  localSearching.value = true
  try {
    localMatches.value = await apiAdminSearchLocalPlace(keyword)
  } finally {
    localSearching.value = false
  }
}

async function searchOsmCandidates() {
  if (!devForm.placeName.trim()) {
    ElMessage.warning('请输入地名')
    return
  }
  devRunning.value = true
  try {
    osmCandidates.value = await apiAdminSearchOsm(devForm.placeName.trim())
    selectedOsm.value = null
    selectedOsmKey.value = ''
    ElMessage.success(`OSM 查询完成：${osmCandidates.value.length} 条`)
  } finally {
    devRunning.value = false
  }
}

async function runImportPlace() {
  if (!devForm.placeName.trim()) {
    ElMessage.warning('请输入地名')
    return
  }
  if (!selectedOsm.value) {
    ElMessage.warning('请先从 OSM 候选中选择一条记录')
    return
  }

  devRunning.value = true
  try {
    devResult.value = await apiAdminGenerateFromOsm({
      placeName: devForm.placeName.trim(),
      query: String(selectedOsm.value.displayName || '').trim(),
      selectedOsm: {
        placeId: selectedOsm.value.placeId,
        osmType: selectedOsm.value.osmType,
        osmId: selectedOsm.value.osmId,
        displayName: selectedOsm.value.displayName,
        name: selectedOsm.value.name,
      },
      force: devForm.force,
      buildFrontend: devForm.buildFrontend,
    })
    if (devResult.value?.status === 'success') {
      ElMessage.success(devForm.buildFrontend ? '数据生成与前端构建完成' : '数据生成完成（已跳过构建）')
    } else if (devResult.value?.status === 'skipped') {
      ElMessage.info('已存在数据，任务跳过')
    } else {
      ElMessage.warning(devResult.value?.message || '执行完成，但状态异常')
    }
  } finally {
    devRunning.value = false
  }
}

watch(
  () => devForm.placeName,
  (value) => {
    if (localSearchTimer != null) {
      window.clearTimeout(localSearchTimer)
      localSearchTimer = null
    }

    const keyword = value.trim()
    if (!keyword) {
      localMatches.value = []
      return
    }

    localSearchTimer = window.setTimeout(() => {
      void searchLocalPlace()
    }, 350)
  },
)

onBeforeUnmount(() => {
  if (localSearchTimer != null) {
    window.clearTimeout(localSearchTimer)
  }
})

onMounted(loadScenic)
</script>

<template>
  <div class="page">
    <el-card class="glass" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <div style="font-weight: 900">管理员数据管理</div>
          <div class="muted" style="font-size: 12px">后端会校验 role=ADMIN，否则返回 403</div>
        </div>
      </template>

      <el-tabs>
        <el-tab-pane label="开发工具">
          <div class="grid">
            <div class="glass block">
              <div style="font-weight: 900; margin-bottom: 10px">地名导入（分步骤）</div>
              <el-form label-position="top">
                <el-form-item label="地名">
                  <el-input v-model="devForm.placeName" placeholder="例如：广州市执信中学（执信南路校区）" />
                </el-form-item>
                <el-form-item>
                  <el-checkbox v-model="devForm.force">忽略已存在数据，强制重抓</el-checkbox>
                </el-form-item>
                <el-form-item>
                  <el-checkbox v-model="devForm.buildFrontend">生成后自动执行前端 build</el-checkbox>
                </el-form-item>
                <div class="muted" style="margin-bottom: 10px">输入地名后会自动进行本地匹配（防抖 350ms）</div>
                <div class="formRow">
                  <el-button type="primary" class="dev-action-btn" :loading="devRunning" @click="searchOsmCandidates">1) 查 OSM 候选（支持模糊）</el-button>
                  <el-button type="primary" class="dev-action-btn" :loading="devRunning" @click="runImportPlace">2) 选择后生成</el-button>
                </div>
              </el-form>

              <el-divider />
              <div style="font-weight: 700; margin-bottom: 6px">本地匹配结果 <span class="muted" v-if="localSearching">（检索中）</span></div>
              <el-table :data="localMatches" size="small" style="width: 100%; margin-bottom: 10px">
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="name" label="名称" min-width="150" />
                <el-table-column prop="location" label="位置" min-width="180" />
              </el-table>

              <div style="font-weight: 700; margin-bottom: 6px">OSM 候选结果（单选）</div>
              <el-table
                :data="osmCandidates"
                size="small"
                style="width: 100%"
                highlight-current-row
                @current-change="(row:any)=> { selectedOsm = row || null; selectedOsmKey = buildOsmCandidateKey(row) }"
              >
                <el-table-column label="选择" width="70">
                  <template #default="scope">
                    <el-radio v-model="selectedOsmKey" :label="buildOsmCandidateKey(scope.row)" @change="selectedOsm = scope.row"> </el-radio>
                  </template>
                </el-table-column>
                <el-table-column prop="name" label="名称" min-width="120" />
                <el-table-column prop="displayName" label="OSM展示名" min-width="220" />
                <el-table-column prop="osmType" label="类型" width="90" />
                <el-table-column prop="osmId" label="OSM ID" width="110" />
              </el-table>
            </div>

            <div class="glass block">
              <div style="font-weight: 900; margin-bottom: 10px">执行结果</div>
              <div v-if="!devResult" class="muted">尚未执行任务</div>
              <div v-else>
                <div class="muted">status：{{ devResult.status }}</div>
                <div class="muted">message：{{ devResult.message }}</div>
                <div class="muted">exists：{{ devResult.exists }}</div>
                <div class="muted">seedExitCode：{{ devResult.seedExitCode ?? '-' }}</div>
                <div class="muted">buildExitCode：{{ devResult.buildExitCode ?? '-' }}</div>
                <el-divider />
                <div style="font-weight: 700; margin-bottom: 6px">脚本日志（截断）</div>
                <el-input :model-value="devResult.seedOutput || ''" type="textarea" :rows="8" readonly />
                <div style="font-weight: 700; margin: 10px 0 6px">构建日志（截断）</div>
                <el-input :model-value="devResult.buildOutput || ''" type="textarea" :rows="8" readonly />
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="景区管理">
          <div class="grid">
            <div class="glass block">
              <div style="font-weight: 900; margin-bottom: 10px">新增景区</div>
              <el-form label-position="top">
                <el-form-item label="name">
                  <el-input v-model="scenicForm.name" placeholder="景区名称（scenic_areas.name）" />
                </el-form-item>
                <el-form-item label="type">
                  <el-input v-model="scenicForm.type" placeholder="景区类型（例如：校园/普通景区）" />
                </el-form-item>
                <el-form-item label="location">
                  <el-input v-model="scenicForm.location" placeholder="地理位置（例如：XX市XX区）" />
                </el-form-item>
                <div class="row">
                  <el-form-item label="latitude">
                    <el-input-number
                      v-model="scenicForm.latitude"
                      :step="0.0001"
                      :controls="false"
                      placeholder="纬度 latitude"
                      style="width: 100%"
                    />
                  </el-form-item>
                  <el-form-item label="longitude">
                    <el-input-number
                      v-model="scenicForm.longitude"
                      :step="0.0001"
                      :controls="false"
                      placeholder="经度 longitude"
                      style="width: 100%"
                    />
                  </el-form-item>
                </div>
                <el-form-item label="openTime">
                  <el-input v-model="scenicForm.openTime" placeholder="开放时间（字符串，例如 08:00-18:00）" />
                </el-form-item>
                <el-form-item label="ticketPrice">
                  <el-input v-model="scenicForm.ticketPrice" placeholder="票价（字符串，例如 50元）" />
                </el-form-item>
                <el-form-item label="description">
                  <el-input v-model="scenicForm.description" type="textarea" :rows="4" placeholder="景区简介（description）" />
                </el-form-item>
                <el-button type="primary" :loading="loading" @click="addScenic">提交</el-button>
              </el-form>
            </div>

            <div class="glass block">
              <div style="font-weight: 900; margin-bottom: 10px">景区列表</div>
              <div class="formRow">
                <el-input v-model="q.type" placeholder="type(可选)" clearable />
                <el-button @click="q.page=1; loadScenic()" :loading="loading">查询</el-button>
              </div>
              <el-table :data="scenicList" v-loading="loading" style="width: 100%; margin-top: 14px">
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="name" label="名称" />
                <el-table-column prop="type" label="类型" width="120" />
                <el-table-column prop="location" label="位置" />
              </el-table>
              <div style="margin-top: 14px; display: flex; justify-content: center">
                <el-pagination
                  background
                  layout="prev, pager, next, total"
                  :page-size="q.size"
                  :current-page="q.page"
                  :total="total"
                  @current-change="(p:number)=>{q.page=p; loadScenic()}"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="POI / 道路 / 美食">
          <div class="grid3">
            <div class="glass block">
              <div style="font-weight: 900; margin-bottom: 10px">新增 POI</div>
              <el-form label-position="top">
                <el-form-item label="name">
                  <el-input v-model="poiForm.name" placeholder="POI 名称（buildings.name）" />
                </el-form-item>
                <el-form-item label="type">
                  <el-input v-model="poiForm.type" placeholder="POI 类型（如 scenic_spot / teaching）" />
                </el-form-item>
                <el-form-item label="location">
                  <el-input v-model="poiForm.location" placeholder="POI 位置（buildings.location）" />
                </el-form-item>
                <el-form-item label="areaId">
                  <el-input-number
                    v-model="poiForm.areaId"
                    :min="1"
                    :controls="false"
                    placeholder="所属景区/校园 ID（area_id）"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-button type="primary" @click="addPoi">提交</el-button>
              </el-form>
            </div>

            <div class="glass block">
              <div style="font-weight: 900; margin-bottom: 10px">新增道路</div>
              <el-form label-position="top">
                <div class="row">
                  <el-form-item label="startId">
                    <el-input-number v-model="roadForm.startId" :min="1" :controls="false" placeholder="起点节点 ID" style="width: 100%" />
                  </el-form-item>
                  <el-form-item label="endId">
                    <el-input-number v-model="roadForm.endId" :min="1" :controls="false" placeholder="终点节点 ID" style="width: 100%" />
                  </el-form-item>
                </div>
                <el-form-item label="distance">
                  <el-input-number v-model="roadForm.distance" :min="0" :controls="false" placeholder="距离（distance）" style="width: 100%" />
                </el-form-item>
                <el-form-item label="speed">
                  <el-input-number v-model="roadForm.speed" :min="0" :controls="false" placeholder="理想速度（speed）" style="width: 100%" />
                </el-form-item>
                <el-form-item label="congestion">
                  <el-input-number v-model="roadForm.congestion" :min="0.1" :step="0.1" :controls="false" placeholder="拥挤度（0~1）" style="width: 100%" />
                </el-form-item>
                <el-form-item label="vehicleType"><el-input v-model="roadForm.vehicleType" placeholder="walk,bike,shuttle" /></el-form-item>
                <el-form-item label="areaId">
                  <el-input-number v-model="roadForm.areaId" :min="1" :controls="false" placeholder="所属景区/校园 ID（area_id）" style="width: 100%" />
                </el-form-item>
                <el-button type="primary" @click="addRoad">提交</el-button>
              </el-form>
            </div>

            <div class="glass block">
              <div style="font-weight: 900; margin-bottom: 10px">新增美食</div>
              <el-form label-position="top">
                <el-form-item label="name"><el-input v-model="foodForm.name" placeholder="美食名称（foods.name）" /></el-form-item>
                <el-form-item label="cuisine"><el-input v-model="foodForm.cuisine" placeholder="菜系（foods.cuisine）" /></el-form-item>
                <el-form-item label="price">
                  <el-input-number v-model="foodForm.price" :min="0" :controls="false" placeholder="价格（price）" style="width: 100%" />
                </el-form-item>
                <el-form-item label="areaId">
                  <el-input-number v-model="foodForm.areaId" :min="1" :controls="false" placeholder="所属景区 ID（area_id）" style="width: 100%" />
                </el-form-item>
                <el-form-item label="restaurantId">
                  <el-input-number v-model="foodForm.restaurantId" :min="1" :controls="false" placeholder="餐厅/建筑 ID（restaurant_id）" style="width: 100%" />
                </el-form-item>
                <el-form-item label="description">
                  <el-input v-model="foodForm.description" type="textarea" :rows="3" placeholder="简介（description）" />
                </el-form-item>
                <el-button type="primary" @click="addFood">提交</el-button>
              </el-form>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 16px;
}
.grid3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.block {
  padding: 16px;
}
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.formRow {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.formRow :deep(.dev-action-btn.el-button--primary) {
  background: linear-gradient(120deg, #2f6bff 0%, #3a86ff 100%);
  border-color: #2f6bff;
  color: #fff;
}

.formRow :deep(.dev-action-btn.el-button--primary:hover) {
  background: linear-gradient(120deg, #2a5fe6 0%, #3378de 100%);
  border-color: #2a5fe6;
}
@media (max-width: 1080px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .grid3 {
    grid-template-columns: 1fr;
  }
}
</style>

