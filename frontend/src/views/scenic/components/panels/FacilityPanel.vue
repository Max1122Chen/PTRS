<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { apiFacilityDetail, apiFacilityNearby, apiFacilitySearch, type Facility, type FacilityNearbyVO } from '../../../../lib/api'
import { useScenicHubStore } from '../../../../stores/scenicHub'

const hub = useScenicHubStore()
const loading = ref(false)
const tab = ref<'nearby' | 'search'>('nearby')
const detail = ref<Facility | null>(null)
const detailOpen = ref(false)

const form = reactive({
  type: '',
  keyword: '',
})

async function loadNearby() {
  if (hub.areaId == null) {
    ElMessage.warning('请先选择景区')
    return
  }
  if (hub.focusPoiId == null) {
    ElMessage.warning('请先在地图上选择景点或场所作为锚点')
    return
  }
  loading.value = true
  try {
    const rows = await apiFacilityNearby({
      areaId: hub.areaId,
      anchorPoiId: hub.focusPoiId,
      radius: hub.facilityRadius,
      type: form.type || undefined,
    })
    hub.setFacilityResults(rows)
    if (!rows.length) ElMessage.info('范围内未找到设施')
  } finally {
    loading.value = false
  }
}

async function loadSearch() {
  if (hub.areaId == null) {
    ElMessage.warning('请先选择景区')
    return
  }
  if (hub.focusPoiId == null) {
    ElMessage.warning('请先在地图上选择锚点 POI')
    return
  }
  loading.value = true
  try {
    const rows = (await apiFacilitySearch({
      keyword: form.keyword || undefined,
      type: form.type || undefined,
      areaId: hub.areaId,
      anchorPoiId: hub.focusPoiId,
      radius: hub.facilityRadius,
      limit: 50,
    })) as FacilityNearbyVO[]
    hub.setFacilityResults(rows)
    if (!rows.length) ElMessage.info('未找到匹配设施')
  } finally {
    loading.value = false
  }
}

async function openDetail(row: FacilityNearbyVO) {
  const id = row.facility?.id
  if (id == null) return
  detail.value = await apiFacilityDetail(id)
  detailOpen.value = true
}

function onRowEnter(id: number) {
  hub.hoveredFacilityId = id
}

function onRowLeave() {
  hub.hoveredFacilityId = null
}

function selectFacilityRow(row: FacilityNearbyVO) {
  const f = row.facility
  if (f?.id == null) return
  hub.setFocusPoi(f.id, f.name || `设施 ${f.id}`)
  hub.hoveredFacilityId = f.id
}
</script>

<template>
  <div class="panel">
    <el-tabs v-model="tab">
      <el-tab-pane label="附近" name="nearby" />
      <el-tab-pane label="类别搜索" name="search" />
    </el-tabs>

    <div class="anchor muted">
      锚点：<strong>{{ hub.focusPoiId != null ? hub.focusPoiName : '未选择（请点地图 POI 或设施）' }}</strong>
    </div>

    <el-form label-position="top" size="small">
      <el-form-item label="范围（米）">
        <el-segmented
          v-model="hub.facilityRadius"
          :options="[
            { label: '200', value: 200 },
            { label: '500', value: 500 },
            { label: '1000', value: 1000 },
          ]"
        />
      </el-form-item>
      <el-form-item label="设施类型">
        <el-input v-model="form.type" clearable placeholder="如 toilet / restaurant" />
      </el-form-item>
      <el-form-item v-if="tab === 'search'" label="关键词">
        <el-input v-model="form.keyword" clearable placeholder="类别或名称模糊" />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="tab === 'nearby' ? loadNearby() : loadSearch()">
        查询
      </el-button>
    </el-form>

    <el-table
      v-if="hub.facilityResults.length"
      :data="hub.facilityResults"
      size="small"
      class="result-table"
      highlight-current-row
      @row-mouseenter="(row: FacilityNearbyVO) => row.facility?.id != null && onRowEnter(row.facility.id)"
      @row-mouseleave="onRowLeave"
      @row-click="(row: FacilityNearbyVO) => row.facility?.id != null && selectFacilityRow(row)"
    >
      <el-table-column label="名称" min-width="100">
        <template #default="{ row }">{{ row.facility?.name || '-' }}</template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ row.facility?.type || '-' }}</template>
      </el-table-column>
      <el-table-column label="路径距离" width="88">
        <template #default="{ row }">
          {{ row.pathDistance != null ? `${row.pathDistance.toFixed(0)} m` : row.geoDistance != null ? `~${row.geoDistance.toFixed(0)} m` : '-' }}
        </template>
      </el-table-column>
      <el-table-column width="56">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="detailOpen" title="设施详情" size="360px">
      <template v-if="detail">
        <p><b>{{ detail.name }}</b></p>
        <p class="muted">类型：{{ detail.type || '-' }}</p>
        <p class="muted">位置：{{ detail.location || '-' }}</p>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.anchor {
  font-size: 13px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
}
.result-table {
  margin-top: 8px;
}
</style>
