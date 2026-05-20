<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import {
  apiIndoorFloor,
  apiIndoorMeta,
  apiIndoorPlan,
  apiMapData,
  apiPlanRoute,
  apiPlanRouteMulti,
  apiRoutePoiCandidates,
  apiRoutePoiTypes,
  apiScenicSearchByKeyword,
  type IndoorFloorGraph,
  type IndoorLevelMeta,
  type IndoorNodeDto,
  type IndoorPlanResult,
  type RoadEdge,
  type PoiTypeDictItem,
  type RoutePoiCandidate,
  type ScenicArea,
} from '../../lib/api'

type Edge = RoadEdge

const MODE_LABEL_MAP: Record<string, string> = {
  walk: '步行',
  bike: '自行车',
  shuttle: '电瓶车',
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
  indoorAvailable?: boolean
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
  multiPointIds: [] as number[],
  returnToStart: false,
  showRoadNodes: false,
})

const result = ref<{ path: number[]; distance: number; time: number } | null>(null)

const viewMode = ref<'outdoor' | 'indoor'>('outdoor')
const indoorBuildingPoiId = ref<number | null>(null)
const indoorBuildingName = ref('')
const indoorLevels = ref<IndoorLevelMeta[]>([])
const indoorCurrentLevel = ref('')
const indoorFloorGraph = ref<IndoorFloorGraph | null>(null)
const indoorAllNodes = ref<IndoorNodeDto[]>([])
const indoorStartId = ref<number | null>(null)
const indoorEndId = ref<number | null>(null)
const indoorPlanResult = ref<IndoorPlanResult | null>(null)
let chartClickBound = false

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

const selectedMultiPointLabels = computed(() => {
  const labelMap = nodeLabelMap.value
  return form.multiPointIds.map((id) => ({
    id,
    label: labelMap[id] || `节点${id}`,
  }))
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

const pathSegments = computed(() => {
  const path = result.value?.path ?? []
  const edges = map.value?.edges ?? []
  if (path.length < 2 || edges.length === 0) {
    return []
  }

  const edgeIndex = new Map<string, Edge[]>()
  edges.forEach((edge) => {
    const key = `${edge.startId}-${edge.endId}`
    const list = edgeIndex.get(key)
    if (list) {
      list.push(edge)
      return
    }
    edgeIndex.set(key, [edge])
  })

  const labels = nodeLabelMap.value
  const segments: {
    index: number
    startId: number
    endId: number
    startName: string
    endName: string
    distance: number
    modeCongestion: Record<string, number>
    allowedModes: string[]
  }[] = []

  for (let i = 0; i < path.length - 1; i++) {
    const startId = path[i]
    const endId = path[i + 1]
    const direct = edgeIndex.get(`${startId}-${endId}`)?.[0]
    const reverse = edgeIndex.get(`${endId}-${startId}`)?.[0]
    const edge = direct || reverse
    const modeCongestion = normalizeModeCongestion(edge?.modeCongestion)
    const allowedModes = edge?.allowedModes?.length ? edge.allowedModes : Object.keys(modeCongestion)
    segments.push({
      index: i + 1,
      startId,
      endId,
      startName: labels[startId] || `节点${startId}`,
      endName: labels[endId] || `节点${endId}`,
      distance: edge?.distance ?? 0,
      modeCongestion,
      allowedModes,
    })
  }

  return segments
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

function modeLabel(mode: string) {
  return MODE_LABEL_MAP[mode] || mode
}

function formatCongestion(value: number | undefined) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '-'
  }
  return value.toFixed(2)
}

function normalizeModeCongestion(raw: Record<string, number | undefined> | undefined) {
  const out: Record<string, number> = {}
  Object.entries(raw ?? {}).forEach(([key, value]) => {
    if (typeof value === 'number' && Number.isFinite(value)) {
      out[key] = value
    }
  })
  return out
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

function buildIndoorNodePixelPositions(nodes: IndoorNodeDto[]): Record<number, { x: number; y: number }> | null {
  const positioned = nodes.filter(
    (n) =>
      typeof n.longitude === 'number' &&
      Number.isFinite(n.longitude) &&
      typeof n.latitude === 'number' &&
      Number.isFinite(n.latitude),
  )
  if (!positioned.length) return null

  const lngs = positioned.map((n) => Number(n.longitude))
  const lats = positioned.map((n) => Number(n.latitude))
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const lngSpan = Math.max(maxLng - minLng, 0.000001)
  const latSpan = Math.max(maxLat - minLat, 0.000001)

  const positions: Record<number, { x: number; y: number }> = {}
  positioned.forEach((node) => {
    const x = ((Number(node.longitude) - minLng) / lngSpan) * 1000
    const y = ((maxLat - Number(node.latitude)) / latSpan) * 700
    positions[node.id] = { x, y }
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

function bindOutdoorChartClick() {
  if (!chart || chartClickBound) return
  chart.on('click', (params: any) => {
    if (viewMode.value !== 'outdoor') return
    if (params?.dataType !== 'node') return
    const data = params.data || {}
    if (!data.indoorAvailable) return
    const poiId = Number(data.nodeId)
    if (!Number.isFinite(poiId)) return
    void enterIndoor(poiId, data.name || `POI ${poiId}`)
  })
  chartClickBound = true
}

async function enterIndoor(buildingPoiId: number, name: string) {
  loading.value = true
  try {
    const meta = await apiIndoorMeta(buildingPoiId)
    indoorBuildingPoiId.value = buildingPoiId
    indoorBuildingName.value = meta.name || name
    indoorLevels.value = meta.levels ?? []
    indoorCurrentLevel.value = meta.levels?.[0]?.level ?? '0'
    indoorPlanResult.value = null
    indoorStartId.value = meta.entranceNodeId ?? null
    indoorEndId.value = null
    viewMode.value = 'indoor'
    await refreshIndoorAllNodes()
    await loadIndoorFloor(indoorCurrentLevel.value)
    ElMessage.success(`已进入「${indoorBuildingName.value}」室内导航`)
  } catch (e: any) {
    ElMessage.error(e?.message || '无法加载室内图')
  } finally {
    loading.value = false
  }
}

function exitIndoor() {
  viewMode.value = 'outdoor'
  indoorBuildingPoiId.value = null
  indoorFloorGraph.value = null
  indoorPlanResult.value = null
  renderGraph(result.value?.path)
}

async function refreshIndoorAllNodes() {
  const buildingId = indoorBuildingPoiId.value
  if (buildingId == null) return
  const all: IndoorNodeDto[] = []
  for (const lv of indoorLevels.value) {
    const floor = await apiIndoorFloor(buildingId, lv.level)
    all.push(...(floor.nodes ?? []))
  }
  indoorAllNodes.value = all
}

async function loadIndoorFloor(level: string) {
  const buildingId = indoorBuildingPoiId.value
  if (buildingId == null) return
  loading.value = true
  try {
    indoorCurrentLevel.value = level
    indoorFloorGraph.value = await apiIndoorFloor(buildingId, level)
    renderIndoorGraph(indoorPlanResult.value?.path)
  } finally {
    loading.value = false
  }
}

function renderIndoorGraph(highlightPath?: number[]) {
  if (!chartEl.value || !indoorFloorGraph.value) return
  if (!chart) chart = echarts.init(chartEl.value)

  const floor = indoorFloorGraph.value
  const pathSet = new Set<number>(highlightPath ?? [])
  const geoPos = buildIndoorNodePixelPositions(floor.nodes)

  const nodes = floor.nodes.map((n) => {
    const highlighted = pathSet.has(n.id)
    const xy = geoPos?.[n.id] ?? { x: Number(n.x ?? 0), y: Number(n.y ?? 0) }
    return {
      id: String(n.id),
      name: n.name || `${n.nodeKind || '节点'} ${n.id}`,
      nodeId: n.id,
      nodeKind: n.nodeKind,
      x: xy.x,
      y: xy.y,
      symbolSize: highlighted ? 18 : n.nodeKind === 'room' ? 12 : 10,
      itemStyle: {
        color: highlighted
          ? 'rgba(204,120,92,0.95)'
          : n.nodeKind === 'elevator' || n.nodeKind === 'stairs'
            ? 'rgba(120,180,255,0.85)'
            : 'rgba(255,255,255,0.7)',
      },
    }
  })

  const linkKeys = new Set<string>()
  if (highlightPath && highlightPath.length > 1) {
    for (let i = 0; i < highlightPath.length - 1; i++) {
      linkKeys.add(`${highlightPath[i]}-${highlightPath[i + 1]}`)
      linkKeys.add(`${highlightPath[i + 1]}-${highlightPath[i]}`)
    }
  }

  const links = floor.edges.map((e) => {
    const key = `${e.startNodeId}-${e.endNodeId}`
    const onPath = linkKeys.has(key) || linkKeys.has(`${e.endNodeId}-${e.startNodeId}`)
    return {
      source: String(e.startNodeId),
      target: String(e.endNodeId),
      lineStyle: onPath
        ? { width: 3, color: 'rgba(204,120,92,0.95)' }
        : { width: 1, color: 'rgba(255,255,255,0.2)' },
    }
  })

  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        if (p.dataType !== 'node') return ''
        const d = p.data || {}
        return `<div><b>${d.name}</b></div><div>ID: ${d.nodeId}</div><div>${d.nodeKind || ''}</div>`
      },
    },
    series: [{ type: 'graph', layout: 'none', roam: true, data: nodes, links }],
  })
}

async function planIndoor() {
  const buildingId = indoorBuildingPoiId.value
  if (buildingId == null || indoorStartId.value == null || indoorEndId.value == null) {
    ElMessage.warning('请选择室内起点与终点')
    return
  }
  loading.value = true
  try {
    indoorPlanResult.value = await apiIndoorPlan(buildingId, {
      startNodeId: Number(indoorStartId.value),
      endNodeId: Number(indoorEndId.value),
    })
    if (!indoorPlanResult.value.path?.length) {
      ElMessage.warning('室内路径不连通')
      return
    }
    const firstLevel = indoorPlanResult.value.segments?.[0]?.level
    if (firstLevel) {
      await loadIndoorFloor(firstLevel)
    }
    renderIndoorGraph(indoorPlanResult.value.path)
  } finally {
    loading.value = false
  }
}

function renderGraph(highlightPath?: number[]) {
  if (viewMode.value === 'indoor') {
    renderIndoorGraph(highlightPath)
    return
  }
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
  let fallbackIdx = 0
  const fallbackTotal = Math.max(
    map.value.nodes.filter((id) => !positionMap[id] && Boolean(details[id])).length,
    1,
  )

  const nodes = map.value.nodes
    .filter((id) => {
      // 无经纬度的路网虚拟节点不进入图表，避免 ECharts 环形兜底布局连成一圈
      if (positionMap[id]) return true
      return Boolean(details[id])
    })
    .map((id) => {
    const fallbackAngle = (fallbackIdx++ / fallbackTotal) * Math.PI * 2
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
    const indoorAvailable = Boolean(details[id]?.indoorAvailable)
    return {
      id: String(id),
      name: isVirtual && !showRoadNode ? '' : visiblePoi ? labels[id] || String(id) : '',
      nodeId: id,
      nodeType: details[id]?.type,
      nodeLocation: details[id]?.location,
      longitude: details[id]?.longitude,
      latitude: details[id]?.latitude,
      indoorAvailable,
      x: pos.x,
      y: pos.y,
      symbolSize: isVirtual && !showRoadNode
        ? 0
        : visiblePoi
          ? indoorAvailable
            ? isHighlighted
              ? 20
              : 14
            : isHighlighted
              ? 18
              : 10
          : isHighlighted
            ? 8
            : 4,
      itemStyle: visiblePoi
        ? isHighlighted
          ? { color: 'rgba(204,120,92,0.95)' }
          : indoorAvailable
            ? {
                color: 'rgba(255,220,160,0.9)',
                borderColor: 'rgba(255,200,80,0.95)',
                borderWidth: 2,
              }
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

  const chartNodeIds = new Set(nodes.map((n) => n.id))
  const links = map.value.edges
    .filter((e) => chartNodeIds.has(String(e.startId)) && chartNodeIds.has(String(e.endId)))
    .map((e) => {
    const key = `${e.startId}-${e.endId}`
    const isOnPath = pathSet.has(key)
    const modeCongestion = normalizeModeCongestion(e.modeCongestion)
    const allowedModes = e.allowedModes?.length ? e.allowedModes : Object.keys(modeCongestion)
    return {
      source: String(e.startId),
      target: String(e.endId),
      value: e.distance,
      modeCongestion,
      allowedModes,
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
          const allowedModes = Array.isArray(params.data?.allowedModes) ? params.data.allowedModes : []
          const profile = normalizeModeCongestion(params.data?.modeCongestion)
          const lines = (allowedModes.length > 0 ? allowedModes : Object.keys(profile)).map((mode: string) => {
            return `<div>${modeLabel(String(mode))}：${formatCongestion(profile[String(mode)])}</div>`
          })
          return [
            `<div style="font-weight:700;margin-bottom:4px;">道路：${params.data?.source} → ${params.data?.target}</div>`,
            `<div>距离：${Number(params.data?.value ?? 0).toFixed(1)} m</div>`,
            '<div style="margin-top:4px;">可通行交通工具与拥堵度：</div>',
            lines.length > 0 ? lines.join('') : '<div>未标注</div>',
          ].join('')
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
  bindOutdoorChartClick()
}

async function loadMap() {
  if (viewMode.value === 'indoor') {
    exitIndoor()
  }
  if (form.areaId == null) {
    map.value = null
    poiCandidates.value = []
    result.value = null
    form.startId = null
    form.endId = null
    form.multiPointIds = []
    chart?.clear()
    chartClickBound = false
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
    form.multiPointIds = []
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
  const points = form.multiPointIds.map((n) => Number(n)).filter((n) => Number.isFinite(n))
  if (points.length < 2) {
    ElMessage.warning('多点规划至少需要选择 2 个地点')
    return
  }

  loading.value = true
  try {
    result.value = await apiPlanRouteMulti({
      areaId: form.areaId,
      points,
      returnToStart: form.returnToStart,
      vehicle: form.vehicle || undefined,
      strategy: form.strategy || undefined,
    })
    renderGraph(result.value.path)
  } finally {
    loading.value = false
  }
}

function moveMultiPoint(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= form.multiPointIds.length) return
  const copied = [...form.multiPointIds]
  const temp = copied[index]
  copied[index] = copied[target]
  copied[target] = temp
  form.multiPointIds = copied
}

onMounted(loadMap)

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

          <el-form-item label="多点规划地点（多选）">
            <el-select
              v-model="form.multiPointIds"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择多个地点（至少 2 个）"
              style="width: 100%"
            >
              <el-option
                v-for="node in nodeOptions"
                :key="`multi-${node.nodeId}`"
                :label="`${node.name}（ID ${node.nodeId}）`"
                :value="node.nodeId"
              />
            </el-select>
            <div class="hint muted">第一个点固定为起点；未回起点时最后一个点固定为终点。</div>
          </el-form-item>
          <el-form-item label="闭环设置">
            <el-switch
              v-model="form.returnToStart"
              active-text="回到起点"
              inactive-text="结束于最后一个点"
            />
          </el-form-item>
          <el-form-item v-if="selectedMultiPointLabels.length" label="访问顺序（可调整）">
            <div class="order-list">
              <div v-for="(item, idx) in selectedMultiPointLabels" :key="`order-${item.id}-${idx}`" class="order-row">
                <span class="order-name">{{ idx + 1 }}. {{ item.label }}（ID {{ item.id }}）</span>
                <span class="order-actions">
                  <el-button size="small" text :disabled="idx === 0" @click="moveMultiPoint(idx, -1)">上移</el-button>
                  <el-button
                    size="small"
                    text
                    :disabled="idx === selectedMultiPointLabels.length - 1"
                    @click="moveMultiPoint(idx, 1)"
                  >
                    下移
                  </el-button>
                </span>
              </div>
            </div>
          </el-form-item>
          <el-button type="primary" plain @click="planMulti" :loading="loading">多点规划</el-button>

          <div v-if="result" class="glass result">
            <div style="font-weight: 900">结果</div>
            <div class="muted">path：{{ result.path.join(' → ') }}</div>
            <div class="muted">distance：{{ result.distance.toFixed(2) }} m</div>
            <div class="muted">time：{{ result.time.toFixed(2) }} s</div>
            <div v-if="pathSegments.length" class="segment-list">
              <div v-for="segment in pathSegments" :key="`segment-${segment.index}-${segment.startId}-${segment.endId}`" class="segment-item">
                <div class="segment-header">
                  <span>#{{ segment.index }} {{ segment.startName }} → {{ segment.endName }}</span>
                  <span>{{ segment.distance.toFixed(1) }} m</span>
                </div>
                <div class="muted segment-modes" v-if="segment.allowedModes.length">
                  <span v-for="mode in segment.allowedModes" :key="`mode-${segment.index}-${mode}`" class="mode-badge">
                    {{ modeLabel(mode) }}：拥堵度 {{ formatCongestion(segment.modeCongestion[mode]) }}
                  </span>
                </div>
                <div class="muted segment-modes" v-else>可通行交通工具：未标注</div>
              </div>
            </div>
          </div>
        </el-form>
      </el-card>

      <el-card class="glass" shadow="never">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px">
            <div style="font-weight: 900">
              {{ viewMode === 'indoor' ? `室内：${indoorBuildingName}` : '节点 / 路径' }}
            </div>
            <div v-if="viewMode === 'indoor'" style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
              <el-select
                v-model="indoorCurrentLevel"
                placeholder="楼层"
                style="width: 120px"
                @change="(lv: string) => loadIndoorFloor(lv)"
              >
                <el-option
                  v-for="lv in indoorLevels"
                  :key="lv.level"
                  :label="lv.label || lv.level"
                  :value="lv.level"
                />
              </el-select>
              <el-button size="small" @click="exitIndoor">返回室外地图</el-button>
            </div>
            <div v-else class="muted" style="font-size: 12px">点击带高亮边框 POI 进入室内图</div>
          </div>
        </template>
        <div v-if="viewMode === 'indoor'" class="indoor-panel">
          <div class="row">
            <el-form-item label="室内起点" style="flex: 1; margin-bottom: 8px">
              <el-select v-model="indoorStartId" filterable clearable placeholder="选择起点" style="width: 100%">
                <el-option
                  v-for="n in indoorAllNodes"
                  :key="`is-${n.id}`"
                  :label="`${n.name || n.nodeKind}（${n.level}层 / ID ${n.id}）`"
                  :value="n.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="室内终点" style="flex: 1; margin-bottom: 8px">
              <el-select v-model="indoorEndId" filterable clearable placeholder="选择终点" style="width: 100%">
                <el-option
                  v-for="n in indoorAllNodes"
                  :key="`ie-${n.id}`"
                  :label="`${n.name || n.nodeKind}（${n.level}层 / ID ${n.id}）`"
                  :value="n.id"
                />
              </el-select>
            </el-form-item>
          </div>
          <el-button type="primary" size="small" :loading="loading" @click="planIndoor">室内最短路径</el-button>
          <div v-if="indoorPlanResult?.path?.length" class="glass result" style="margin-top: 10px">
            <div class="muted">path：{{ indoorPlanResult.path.join(' → ') }}</div>
            <div class="muted">distance：{{ indoorPlanResult.distanceMeters?.toFixed(2) }} m</div>
            <div v-if="indoorPlanResult.instructions?.length" class="muted">
              指引：{{ indoorPlanResult.instructions.join(' → ') }}
            </div>
          </div>
        </div>
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
.segment-list {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}
.segment-item {
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  padding: 8px;
}
.segment-header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}
.segment-modes {
  margin-top: 6px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.mode-badge {
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  font-size: 12px;
}
.hint {
  margin-top: 6px;
  font-size: 12px;
}
.order-list {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 10px;
  overflow: hidden;
}
.order-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.order-row:last-child {
  border-bottom: none;
}
.order-name {
  font-size: 13px;
}
.order-actions {
  display: inline-flex;
  gap: 4px;
}
.indoor-panel {
  margin-bottom: 12px;
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

