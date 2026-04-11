<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import {
  apiMapData,
  apiPlanRoute,
  apiPlanRouteMulti,
  apiRoutePoiCandidates,
  apiRoutePoiTypes,
  apiScenicSearchByKeyword,
  type PoiTypeDictItem,
  type RoutePoiCandidate,
  type ScenicArea,
} from '../../lib/api'

type Edge = {
  startId: number
  endId: number
  distance: number
  speed: number
  congestion: number
  vehicleType?: string
}

const loading = ref(false)
type RouteNodeDetail = {
  nodeId: number
  name: string
  type?: string
  location?: string
  longitude?: number
  latitude?: number
  areaId?: number
}

type RouteNodeGeo = {
  nodeId: number
  type?: string
  longitude?: number
  latitude?: number
}

const map = ref<{ nodes: number[]; nodeDetails?: RouteNodeDetail[]; nodeGeo?: RouteNodeGeo[]; edges: Edge[] } | null>(null)
const poiCandidates = ref<RoutePoiCandidate[]>([])
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const form = reactive({
  areaId: undefined as number | undefined,
  startId: null as number | null,
  endId: null as number | null,
  vehicle: '' as string,
  strategy: '' as '' | 'distance' | 'time',
  multiPoints: '',
  showRoadNodes: false,
})

const result = ref<{ path: number[]; distance: number; time: number } | null>(null)

const areaOpts = ref<ScenicArea[]>([])
const poiTypeOptions = ref<PoiTypeDictItem[]>([])
const areaLoading = ref(false)
let areaSeq = 0

const nodeOptions = computed(() => {
  if (poiCandidates.value.length > 0) {
    return poiCandidates.value
  }
  const details = map.value?.nodeDetails ?? []
  if (details.length > 0) {
    return details
  }
  return (map.value?.nodes ?? []).map((id) => ({ nodeId: id, name: `节点${id}` }))
})

const nodeLabelMap = computed(() => {
  const out: Record<number, string> = {}
  nodeOptions.value.forEach((node) => {
    out[node.nodeId] = node.name || `节点${node.nodeId}`
  })
  return out
})

const nodeDetailMap = computed(() => {
  const out: Record<number, RouteNodeDetail> = {}
  ;(map.value?.nodeDetails ?? []).forEach((node) => {
    out[node.nodeId] = node
  })
  return out
})

const nodeTypeMap = computed(() => {
  const out: Record<number, string | undefined> = {}
  ;(map.value?.nodeGeo ?? []).forEach((node) => {
    out[node.nodeId] = node.type
  })
  ;(map.value?.nodeDetails ?? []).forEach((node) => {
    if (node.type) out[node.nodeId] = node.type
  })
  return out
})

const hasSelectedArea = computed(() => form.areaId != null)
const poiTypeLabelMap = computed(() => {
  const out: Record<string, string> = {}
  poiTypeOptions.value.forEach((item) => {
    const key = item.code?.trim().toLowerCase()
    if (key) out[key] = item.label || item.code
  })
  return out
})

function poiTypeLabel(type?: string) {
  if (!type) return '未分类'
  const key = type.trim().toLowerCase()
  return poiTypeLabelMap.value[key] || type
}

async function loadPoiTypes() {
  try {
    poiTypeOptions.value = await apiRoutePoiTypes()
  } catch {
    poiTypeOptions.value = []
  }
}

function buildNodePositionMap() {
  const details = map.value?.nodeGeo ?? map.value?.nodeDetails ?? []
  const positioned = details.filter(
    (n) => typeof n.longitude === 'number' && Number.isFinite(n.longitude) && typeof n.latitude === 'number' && Number.isFinite(n.latitude),
  )

  const positions: Record<number, { x: number; y: number }> = {}
  if (!positioned.length) return positions

  const lngs = positioned.map((n) => Number(n.longitude))
  const lats = positioned.map((n) => Number(n.latitude))
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const lngSpan = Math.max(maxLng - minLng, 0.000001)
  const latSpan = Math.max(maxLat - minLat, 0.000001)

  // 将经纬度映射到稳定画布坐标：经度向右递增，纬度向上递增（屏幕 y 反向）。
  positioned.forEach((node) => {
    const x = ((Number(node.longitude) - minLng) / lngSpan) * 1000
    const y = ((maxLat - Number(node.latitude)) / latSpan) * 700
    positions[node.nodeId] = { x, y }
  })

  return positions
}

async function remoteArea(keyword: string) {
  const q = keyword.trim()
  if (!q) {
    areaSeq++
    areaOpts.value = []
    return
  }
  const seq = ++areaSeq
  areaLoading.value = true
  try {
    areaOpts.value = await apiScenicSearchByKeyword({ keyword: q, limit: 50 })
  } finally {
    if (seq === areaSeq) areaLoading.value = false
  }
}

function renderGraph(highlightPath?: number[]) {
  if (!chartEl.value || !map.value) return
  if (!chart) chart = echarts.init(chartEl.value)

  const labels = nodeLabelMap.value
  const details = nodeDetailMap.value
  const types = nodeTypeMap.value
  const showPoiNodes = hasSelectedArea.value || Boolean(highlightPath?.length)
  const positionMap = buildNodePositionMap()
  const fallbackRadius = 280
  const fallbackCenterX = 500
  const fallbackCenterY = 350
  const total = Math.max(map.value.nodes.length, 1)

  const nodes = map.value.nodes.map((id, idx) => {
    const fallbackAngle = (idx / total) * Math.PI * 2
    const fallback = {
      x: fallbackCenterX + Math.cos(fallbackAngle) * fallbackRadius,
      y: fallbackCenterY + Math.sin(fallbackAngle) * fallbackRadius,
    }
    const pos = positionMap[id] ?? fallback
    const isVirtual = ((types[id] || details[id]?.type || '').trim().toLowerCase() === 'virtual_node')
    const isPoi = Boolean(details[id])
    const showRoadNode = form.showRoadNodes || !isVirtual
    const isHighlighted = Boolean(highlightPath?.includes(id))
    const visiblePoi = isPoi && showPoiNodes
    return {
      id: String(id),
      name: isVirtual && !showRoadNode ? '' : visiblePoi ? labels[id] || String(id) : '',
      nodeId: id,
      nodeType: details[id]?.type,
      nodeLocation: details[id]?.location,
      longitude: details[id]?.longitude,
      latitude: details[id]?.latitude,
      x: pos.x,
      y: pos.y,
      symbolSize: isVirtual && !showRoadNode ? 0 : visiblePoi ? (isHighlighted ? 18 : 10) : isHighlighted ? 8 : 4,
      itemStyle: visiblePoi
        ? isHighlighted
          ? { color: 'rgba(204,120,92,0.95)' }
          : isVirtual
            ? showRoadNode
              ? { color: 'rgba(255,255,255,0.1)' }
              : { color: 'rgba(255,255,255,0.0)' }
            : { color: 'rgba(255,255,255,0.65)' }
        : isHighlighted
          ? { color: 'rgba(204,120,92,0.55)' }
          : { color: 'rgba(255,255,255,0.12)' },
      label: visiblePoi && !isVirtual ? undefined : { show: false },
    }
  })

  const pathSet = new Set<string>()
  if (highlightPath && highlightPath.length > 1) {
    for (let i = 0; i < highlightPath.length - 1; i++) {
      pathSet.add(`${highlightPath[i]}-${highlightPath[i + 1]}`)
      pathSet.add(`${highlightPath[i + 1]}-${highlightPath[i]}`)
    }
  }

  const links = map.value.edges.map((e) => {
    const key = `${e.startId}-${e.endId}`
    const isOnPath = pathSet.has(key)
    return {
      source: String(e.startId),
      target: String(e.endId),
      value: e.distance,
      lineStyle: isOnPath
        ? { width: 3, color: 'rgba(204,120,92,0.95)' }
        : { width: 1, color: 'rgba(255,255,255,0.12)' },
    }
  })

  chart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(21, 19, 17, 0.92)',
      borderColor: 'rgba(255, 255, 255, 0.16)',
      textStyle: { color: '#f5eee6' },
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          const data = params.data || {}
          if (!data.nodeType && !data.nodeLocation && typeof data.longitude !== 'number') {
            return `<div>道路节点（ID：${data.nodeId ?? '-'}）</div>`
          }
          const typeLabel = poiTypeLabel(data.nodeType)
          const location = data.nodeLocation || '未知位置'
          const lng = typeof data.longitude === 'number' ? data.longitude.toFixed(6) : '-'
          const lat = typeof data.latitude === 'number' ? data.latitude.toFixed(6) : '-'
          return [
            `<div style="font-weight:700;margin-bottom:4px;">${data.name || '未命名节点'}</div>`,
            `<div>节点ID：${data.nodeId ?? '-'}</div>`,
            `<div>POI类型：${typeLabel}</div>`,
            `<div>位置：${location}</div>`,
            `<div>经纬度：${lng}, ${lat}</div>`,
          ].join('')
        }
        if (params.dataType === 'edge') {
          return `道路：${params.data?.source} → ${params.data?.target}<br/>距离：${Number(params.data?.value ?? 0).toFixed(1)} m`
        }
        return ''
      },
    },
    series: [
      {
        type: 'graph',
        layout: 'none',
        roam: true,
        draggable: false,
        label: {
          show: true,
          position: 'right',
          distance: 8,
          color: 'rgba(58, 43, 28, 0.98)',
          backgroundColor: 'rgba(255, 245, 232, 0.88)',
          borderColor: 'rgba(183, 141, 103, 0.45)',
          borderWidth: 1,
          borderRadius: 4,
          padding: [2, 4],
          fontSize: 11,
        },
        labelLayout: {
          hideOverlap: true,
        },
        data: nodes,
        links,
      },
    ],
  })
}

async function loadMap() {
  if (form.areaId == null) {
    map.value = null
    poiCandidates.value = []
    result.value = null
    form.startId = null
    form.endId = null
    form.multiPoints = ''
    chart?.clear()
    return
  }

  loading.value = true
  try {
    const [mapData, candidates] = await Promise.all([
      apiMapData({ areaId: form.areaId }),
      apiRoutePoiCandidates({ areaId: form.areaId }),
    ])
    map.value = mapData
    poiCandidates.value = candidates
    result.value = null
    form.startId = null
    form.endId = null
    form.multiPoints = ''
    renderGraph()
  } finally {
    loading.value = false
  }
}

async function plan() {
  if (!form.startId || !form.endId) {
    ElMessage.warning('请先选择起点位置和终点位置')
    return
  }
  loading.value = true
  try {
    result.value = await apiPlanRoute({
      areaId: form.areaId,
      startId: Number(form.startId),
      endId: Number(form.endId),
      vehicle: form.vehicle || undefined,
      strategy: form.strategy || undefined,
    })
    renderGraph(result.value.path)
  } finally {
    loading.value = false
  }
}

async function planMulti() {
  const points = form.multiPoints
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isFinite(n))
  if (points.length < 2) {
    ElMessage.warning('多点规划至少需要 2 个位置（用逗号分隔节点ID）')
    return
  }

  loading.value = true
  try {
    result.value = await apiPlanRouteMulti({
      areaId: form.areaId,
      points,
      vehicle: form.vehicle || undefined,
      strategy: form.strategy || undefined,
    })
    renderGraph(result.value.path)
  } finally {
    loading.value = false
  }
}

watch(
  () => form.areaId,
  () => {
    void loadMap()
  },
)

onMounted(() => {
  void loadPoiTypes()
})
</script>

<template>
  <div class="page">
    <div class="grid">
      <el-card class="glass" shadow="never">
        <template #header>
          <div style="font-weight: 900">路线规划</div>
        </template>

        <el-form label-position="top">
          <el-form-item label="景区">
            <el-select
              v-model="form.areaId"
              filterable
              remote
              clearable
              :reserve-keyword="false"
              placeholder="输入关键字"
              :remote-method="remoteArea"
              :loading="areaLoading"
              style="width: 100%"
            >
              <el-option
                v-for="o in areaOpts"
                :key="o.id"
                :label="`${o.name}（ID ${o.id}）`"
                :value="o.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="交通工具">
            <el-segmented
              v-model="form.vehicle"
              :options="[
                { label: '步行', value: 'walk' },
                { label: '自行车', value: 'bike' },
                { label: '电瓶车', value: 'shuttle' },
              ]"
            />
          </el-form-item>
          <el-form-item label="策略">
            <el-radio-group v-model="form.strategy">
              <el-radio-button label="distance">最短距离</el-radio-button>
              <el-radio-button label="time">最短时间</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="路网调试视图">
            <el-switch
              v-model="form.showRoadNodes"
              active-text="显示道路辅助节点"
              inactive-text="隐藏道路辅助节点"
              @change="renderGraph(result?.path)"
            />
          </el-form-item>

          <div class="row">
            <el-form-item label="起点位置（必填）">
              <el-select
                v-model="form.startId"
                filterable
                clearable
                placeholder="请选择起点节点"
                style="width: 100%"
              >
                <el-option
                  v-for="node in nodeOptions"
                  :key="`start-${node.nodeId}`"
                  :label="`${node.name}（ID ${node.nodeId}）`"
                  :value="node.nodeId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="终点位置（必填）">
              <el-select
                v-model="form.endId"
                filterable
                clearable
                placeholder="请选择终点节点"
                style="width: 100%"
              >
                <el-option
                  v-for="node in nodeOptions"
                  :key="`end-${node.nodeId}`"
                  :label="`${node.name}（ID ${node.nodeId}）`"
                  :value="node.nodeId"
                />
              </el-select>
            </el-form-item>
          </div>

          <div class="actions">
            <el-button @click="loadMap" :loading="loading">刷新地图数据</el-button>
            <el-button type="primary" @click="plan" :loading="loading">两点规划</el-button>
          </div>

          <el-divider />

          <el-form-item label="多点规划（用逗号分隔）">
            <el-input v-model="form.multiPoints" placeholder="输入多个节点 ID，用逗号分隔（必填：至少 2 个点）" />
          </el-form-item>
          <el-button type="primary" plain @click="planMulti" :loading="loading">多点规划</el-button>

          <div v-if="result" class="glass result">
            <div style="font-weight: 900">结果</div>
            <div class="muted">path：{{ result.path.join(' → ') }}</div>
            <div class="muted">distance：{{ result.distance.toFixed(2) }} m</div>
            <div class="muted">time：{{ result.time.toFixed(2) }} s</div>
          </div>
        </el-form>
      </el-card>

      <el-card class="glass" shadow="never">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <div style="font-weight: 900">节点 / 路径</div>
            <div class="muted" style="font-size: 12px">当前规划会高亮路径</div>
          </div>
        </template>
        <div ref="chartEl" class="chart" />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 400px 1fr;
  gap: 16px;
}
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.result {
  margin-top: 12px;
  padding: 14px;
}
.hint {
  margin-top: 6px;
  font-size: 12px;
}
.chart {
  height: 560px;
  width: 100%;
}
@media (max-width: 1080px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .chart {
    height: 420px;
  }
}
</style>

