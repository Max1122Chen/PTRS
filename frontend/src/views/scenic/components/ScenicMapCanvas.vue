<script setup lang="ts">
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  apiIndoorFloor,
  apiIndoorMeta,
  apiIndoorPlan,
  apiMapData,
  apiRoutePoiCandidates,
  apiRoutePoiTypes,
  type IndoorFloorGraph,
  type IndoorLevelMeta,
  type IndoorNodeDto,
  type IndoorPlanResult,
  type PoiTypeDictItem,
  type RoadEdge,
  type RoutePoiCandidate,
} from '../../../lib/api'
import type { FacilityHighlight } from '../../../stores/scenicHub'

const MODE_LABEL_MAP: Record<string, string> = {
  walk: '步行',
  bike: '自行车',
  shuttle: '电瓶车',
}

/** 默认路网边（比旧版路线页更粗、更易辨认） */
const ROAD_EDGE_WIDTH = 2.5
const ROAD_EDGE_COLOR = 'rgba(255, 255, 255, 0.45)'
const ROAD_PATH_EDGE_WIDTH = 4
const ROAD_PATH_EDGE_COLOR = 'rgba(204, 120, 92, 0.95)'
const VIRTUAL_NODE_SIZE = 7
const VIRTUAL_NODE_COLOR = 'rgba(255, 255, 255, 0.62)'
const VIRTUAL_NODE_BORDER = 'rgba(255, 255, 255, 0.38)'

const GRAPH_SERIES_ID = 'scenic-outdoor-graph'
const CLICK_DELAY_MS = 240

type GraphRoamState = { zoom: number; center?: number[] }

const props = defineProps<{
  areaId?: number
  focusPoiId?: number | null
  highlightPath?: number[] | null
  facilityHighlights?: FacilityHighlight[]
  hoveredFacilityId?: number | null
  showRoadNodes?: boolean
}>()

const emit = defineEmits<{
  'update:focusPoiId': [id: number | null]
  'focus-select': [payload: { nodeId: number; name: string }]
  'map-loaded': [candidates: RoutePoiCandidate[], details: Record<number, any>]
}>()

type RouteNodeDetail = {
  nodeId: number
  name: string
  type?: string
  location?: string
  longitude?: number
  latitude?: number
  indoorAvailable?: boolean
}

const loading = ref(false)
const map = ref<{ nodes: number[]; nodeDetails?: RouteNodeDetail[]; nodeGeo?: RouteNodeDetail[]; edges: RoadEdge[] } | null>(null)
const poiCandidates = ref<RoutePoiCandidate[]>([])
const poiTypeOptions = ref<PoiTypeDictItem[]>([])
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let chartClickBound = false
let clickDelayTimer: ReturnType<typeof setTimeout> | null = null
let savedOutdoorRoam: GraphRoamState | null = null

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

const nodeOptions = computed(() => {
  if (poiCandidates.value.length > 0) return poiCandidates.value
  const details = map.value?.nodeDetails ?? []
  if (details.length > 0) return details
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

const poiTypeLabelMap = computed(() => {
  const out: Record<string, string> = {}
  poiTypeOptions.value.forEach((item) => {
    const key = item.code?.trim().toLowerCase()
    if (key) out[key] = item.label || item.code
  })
  return out
})

const hasSelectedArea = computed(() => props.areaId != null)

function poiTypeLabel(type?: string) {
  if (!type) return '未分类'
  const key = type.trim().toLowerCase()
  return poiTypeLabelMap.value[key] || type
}

function modeLabel(mode: string) {
  return MODE_LABEL_MAP[mode] || mode
}

function formatCongestion(value: number | undefined) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '-'
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

function projectFacilityLatLng(lat: number, lng: number) {
  const details = map.value?.nodeGeo ?? map.value?.nodeDetails ?? []
  const positioned = details.filter(
    (n) => typeof n.longitude === 'number' && typeof n.latitude === 'number',
  )
  if (!positioned.length) return { x: 500, y: 350 }
  const lngs = positioned.map((n) => Number(n.longitude))
  const lats = positioned.map((n) => Number(n.latitude))
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const lngSpan = Math.max(maxLng - minLng, 0.000001)
  const latSpan = Math.max(maxLat - minLat, 0.000001)
  return {
    x: ((lng - minLng) / lngSpan) * 1000,
    y: ((maxLat - lat) / latSpan) * 700,
  }
}

function captureGraphRoam(): GraphRoamState | null {
  if (!chart) return null
  const series = (chart.getOption() as { series?: Array<{ zoom?: number; center?: number[] }> })?.series?.[0]
  if (!series || series.zoom == null) return null
  return { zoom: series.zoom, center: series.center }
}

function restoreGraphRoam(state: GraphRoamState | null) {
  if (!chart || !state) return
  chart.setOption({
    series: [{ id: GRAPH_SERIES_ID, zoom: state.zoom, center: state.center }],
  })
}

function facilityNameById(id: number): string | undefined {
  return props.facilityHighlights?.find((f) => f.id === id)?.name
}

function isSelectableMapNode(data: Record<string, unknown>) {
  return Boolean(
    data.facilityOnly ||
      data.facilityHit ||
      data.nodeType ||
      data.name ||
      data.indoorAvailable,
  )
}

function emitFocusFromNode(data: Record<string, unknown>) {
  const poiId = Number(data.nodeId)
  if (!Number.isFinite(poiId)) return
  const name = String(data.name || facilityNameById(poiId) || `节点 ${poiId}`)
  emit('update:focusPoiId', poiId)
  emit('focus-select', { nodeId: poiId, name })
}

function bindOutdoorChartClick() {
  if (!chart || chartClickBound) return

  chart.on('click', (params: any) => {
    if (viewMode.value !== 'outdoor') return
    if (params?.dataType !== 'node') return
    const data = params.data || {}
    if (!isSelectableMapNode(data)) return

    if (clickDelayTimer) clearTimeout(clickDelayTimer)
    clickDelayTimer = setTimeout(() => {
      clickDelayTimer = null
      emitFocusFromNode(data)
    }, CLICK_DELAY_MS)
  })

  chart.on('dblclick', (params: any) => {
    if (viewMode.value !== 'outdoor') return
    if (params?.data?.facilityOnly || params?.data?.facilityHit) return
    if (params?.dataType !== 'node') return
    const data = params.data || {}
    if (!data.indoorAvailable) return
    const poiId = Number(data.nodeId)
    if (!Number.isFinite(poiId)) return

    if (clickDelayTimer) {
      clearTimeout(clickDelayTimer)
      clickDelayTimer = null
    }
    void enterIndoor(poiId, data.name || `POI ${poiId}`)
  })

  chartClickBound = true
}

async function enterIndoor(buildingPoiId: number, name: string) {
  savedOutdoorRoam = captureGraphRoam()
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
  const roam = savedOutdoorRoam
  savedOutdoorRoam = null
  viewMode.value = 'outdoor'
  indoorBuildingPoiId.value = null
  indoorFloorGraph.value = null
  indoorPlanResult.value = null
  renderGraph(false)
  restoreGraphRoam(roam)
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

function renderIndoorGraph(highlightPath?: number[], preserveRoam = true) {
  if (!chartEl.value || !indoorFloorGraph.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const roam = preserveRoam ? captureGraphRoam() : null

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
        ? { width: ROAD_PATH_EDGE_WIDTH, color: ROAD_PATH_EDGE_COLOR }
        : { width: 2, color: 'rgba(255, 255, 255, 0.38)' },
    }
  })

  chart.setOption(
    {
      tooltip: {
        trigger: 'item',
        formatter: (p: any) => {
          if (p.dataType !== 'node') return ''
          const d = p.data || {}
          return `<div><b>${d.name}</b></div><div>ID: ${d.nodeId}</div><div>${d.nodeKind || ''}</div>`
        },
      },
      series: [
        {
          id: 'scenic-indoor-graph',
          type: 'graph',
          layout: 'none',
          roam: true,
          edgeSymbol: ['none', 'none'],
          lineStyle: { width: 2, color: 'rgba(255, 255, 255, 0.38)' },
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
          labelLayout: { hideOverlap: true },
          data: nodes,
          links,
        },
      ],
    },
    { replaceMerge: ['series'] },
  )
  restoreGraphRoam(roam)
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
    if (firstLevel) await loadIndoorFloor(firstLevel)
    renderIndoorGraph(indoorPlanResult.value.path)
  } finally {
    loading.value = false
  }
}

function applyHubNodeStyle(node: any, id: number, visiblePoi: boolean) {
  const focusId = props.focusPoiId ?? undefined
  const facilityIds = new Set((props.facilityHighlights ?? []).map((f) => f.id))
  const isFocus = focusId === id
  const isFacilityHit = facilityIds.has(id)
  const facilityName = facilityNameById(id)

  if (isFacilityHit) {
    node.facilityHit = true
    if (facilityName && !node.name) node.name = facilityName
    node.symbolSize = Math.max(node.symbolSize ?? 10, props.hoveredFacilityId === id ? 22 : 16)
    node.itemStyle = {
      color: 'rgba(255,160,60,0.95)',
      borderColor: 'rgba(255,140,40,0.95)',
      borderWidth: 2,
    }
    node.label = {
      show: true,
      fontWeight: 'bold',
      fontSize: props.hoveredFacilityId === id ? 14 : 12,
    }
  }

  if (isFocus) {
    node.symbolSize = Math.max(node.symbolSize ?? 10, 20)
    node.itemStyle = {
      ...(node.itemStyle || {}),
      borderColor: isFacilityHit ? 'rgba(80,220,255,1)' : node.itemStyle?.borderColor || 'rgba(80,220,255,1)',
      borderWidth: 2,
    }
    if (!isFacilityHit && visiblePoi) {
      // POI focus keeps default series label (hideOverlap)
    } else if (facilityName || node.name) {
      node.label = {
        show: true,
        fontWeight: 'bold',
        fontSize: 12,
      }
    }
  }
}

function renderGraph(preserveRoam = true) {
  const highlightPath = props.highlightPath ?? undefined

  if (viewMode.value === 'indoor') {
    renderIndoorGraph(highlightPath, preserveRoam)
    return
  }
  if (!chartEl.value || !map.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const roam = preserveRoam ? captureGraphRoam() : null

  const labels = nodeLabelMap.value
  const details = nodeDetailMap.value
  const types = nodeTypeMap.value
  const showPoiNodes = hasSelectedArea.value || Boolean(highlightPath?.length)
  const positionMap = buildNodePositionMap()
  const showRoadNodeFlag = props.showRoadNodes ?? false
  const fallbackRadius = 280
  const fallbackCenterX = 500
  const fallbackCenterY = 350
  let fallbackIdx = 0
  const fallbackTotal = Math.max(
    map.value.nodes.filter((id) => !positionMap[id] && Boolean(details[id])).length,
    1,
  )

  const facilityHighlightIds = new Set((props.facilityHighlights ?? []).map((f) => f.id))
  const nodes: any[] = map.value.nodes
    .filter((id) => {
      if (positionMap[id]) return true
      if (details[id]) return true
      return facilityHighlightIds.has(id)
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
      const showRoadNode = showRoadNodeFlag || !isVirtual
      const isHighlighted = Boolean(highlightPath?.includes(id))
      const visiblePoi = isPoi && showPoiNodes
      const indoorAvailable = Boolean(details[id]?.indoorAvailable)

      const node: any = {
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
        symbolSize:
          isVirtual && !showRoadNode
            ? 0
            : isVirtual && showRoadNode
              ? VIRTUAL_NODE_SIZE
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
                  : 5,
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
                  ? {
                      color: VIRTUAL_NODE_COLOR,
                      borderColor: VIRTUAL_NODE_BORDER,
                      borderWidth: 1,
                    }
                  : { color: 'rgba(255,255,255,0.0)' }
                : { color: 'rgba(255,255,255,0.65)' }
          : isHighlighted
            ? { color: 'rgba(204,120,92,0.55)' }
            : isVirtual && showRoadNode
              ? {
                  color: VIRTUAL_NODE_COLOR,
                  borderColor: VIRTUAL_NODE_BORDER,
                  borderWidth: 1,
                }
              : { color: 'rgba(255,255,255,0.28)' },
        label: visiblePoi && !isVirtual ? undefined : { show: false },
      }

      applyHubNodeStyle(node, id, visiblePoi)
      return node
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
          ? { width: ROAD_PATH_EDGE_WIDTH, color: ROAD_PATH_EDGE_COLOR, opacity: 1 }
          : { width: ROAD_EDGE_WIDTH, color: ROAD_EDGE_COLOR, opacity: 0.9 },
      }
    })

  const graphNodeIdSet = new Set(nodes.map((n) => n.nodeId))
  if (props.facilityHighlights?.length) {
    for (const f of props.facilityHighlights) {
      if (f.latitude == null || f.longitude == null) continue
      if (graphNodeIdSet.has(f.id)) continue
      const pos = projectFacilityLatLng(f.latitude, f.longitude)
      const hovered = props.hoveredFacilityId === f.id
      const isFocus = props.focusPoiId === f.id
      nodes.push({
        id: `fac-${f.id}`,
        name: f.name,
        nodeId: f.id,
        facilityOnly: true,
        facilityHit: true,
        x: pos.x,
        y: pos.y,
        symbolSize: hovered || isFocus ? 22 : 16,
        itemStyle: {
          color: isFocus ? 'rgba(80,200,255,0.95)' : hovered ? 'rgba(255,200,80,1)' : 'rgba(255,140,40,0.92)',
          borderColor: isFocus ? 'rgba(80,220,255,1)' : undefined,
          borderWidth: isFocus ? 2 : 0,
        },
        label: { show: true, fontWeight: 'bold', fontSize: hovered || isFocus ? 14 : 12 },
      })
    }
  }

  chart.setOption(
    {
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(21, 19, 17, 0.92)',
        borderColor: 'rgba(255, 255, 255, 0.16)',
        textStyle: { color: '#f5eee6' },
        formatter: (params: any) => {
          if (params.data?.facilityOnly) {
            return `<div style="font-weight:700">${params.data.name || '设施'}</div>`
          }
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
          id: GRAPH_SERIES_ID,
          type: 'graph',
          layout: 'none',
          roam: true,
          draggable: false,
          edgeSymbol: ['none', 'none'],
          lineStyle: {
            width: ROAD_EDGE_WIDTH,
            color: ROAD_EDGE_COLOR,
            opacity: 0.9,
          },
          emphasis: {
            focus: 'adjacency',
            lineStyle: { width: ROAD_PATH_EDGE_WIDTH },
          },
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
          labelLayout: { hideOverlap: true },
          data: nodes,
          links,
        },
      ],
    },
    { replaceMerge: ['series'] },
  )
  restoreGraphRoam(roam)
  bindOutdoorChartClick()
}

async function loadMap() {
  if (viewMode.value === 'indoor') exitIndoor()
  if (props.areaId == null) {
    map.value = null
    poiCandidates.value = []
    chart?.clear()
    chartClickBound = false
    return
  }

  loading.value = true
  try {
    const [mapData, candidates] = await Promise.all([
      apiMapData({ areaId: props.areaId }),
      apiRoutePoiCandidates({ areaId: props.areaId }),
    ])
    map.value = mapData as any
    poiCandidates.value = candidates
    const details: Record<number, any> = {}
    ;(mapData.nodeDetails ?? []).forEach((n: any) => {
      details[n.nodeId] = n
    })
    candidates.forEach((c) => {
      details[c.nodeId] = { ...details[c.nodeId], ...c }
    })
    emit('map-loaded', candidates, details)
    renderGraph(false)
  } finally {
    loading.value = false
  }
}

watch(() => props.areaId, () => void loadMap())
watch(
  () => [props.focusPoiId, props.highlightPath, props.facilityHighlights, props.hoveredFacilityId, props.showRoadNodes],
  () => renderGraph(true),
  { deep: true },
)

onMounted(() => {
  void loadPoiTypes()
  void loadMap()
})

onUnmounted(() => {
  if (clickDelayTimer) clearTimeout(clickDelayTimer)
  chart?.dispose()
  chart = null
  chartClickBound = false
})

defineExpose({ reload: loadMap, renderGraph })
</script>

<template>
  <div class="map-wrap">
    <div class="map-header">
      <span class="title">{{ viewMode === 'indoor' ? `室内：${indoorBuildingName}` : '景区拓扑图' }}</span>
      <div v-if="viewMode === 'indoor'" class="indoor-tools">
        <el-select v-model="indoorCurrentLevel" size="small" style="width: 110px" @change="(lv: string) => loadIndoorFloor(lv)">
          <el-option v-for="lv in indoorLevels" :key="lv.level" :label="lv.label || lv.level" :value="lv.level" />
        </el-select>
        <el-button size="small" @click="exitIndoor">返回室外</el-button>
      </div>
      <span v-else class="hint muted">单击 POI/设施设为锚点；双击金色边框 POI 进入室内图</span>
    </div>
    <div v-if="viewMode === 'indoor'" class="indoor-form">
      <el-select v-model="indoorStartId" size="small" filterable clearable placeholder="室内起点" style="flex: 1">
        <el-option v-for="n in indoorAllNodes" :key="`is-${n.id}`" :label="n.name || String(n.id)" :value="n.id" />
      </el-select>
      <el-select v-model="indoorEndId" size="small" filterable clearable placeholder="室内终点" style="flex: 1">
        <el-option v-for="n in indoorAllNodes" :key="`ie-${n.id}`" :label="n.name || String(n.id)" :value="n.id" />
      </el-select>
      <el-button size="small" type="primary" :loading="loading" @click="planIndoor">室内规划</el-button>
    </div>
    <div ref="chartEl" v-loading="loading" class="chart" />
  </div>
</template>

<style scoped>
.map-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 420px;
}
.map-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.title {
  font-weight: 800;
}
.hint {
  font-size: 12px;
}
.indoor-tools,
.indoor-form {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.indoor-form {
  margin-bottom: 8px;
}
.chart {
  flex: 1;
  min-height: 380px;
  width: 100%;
}
</style>
