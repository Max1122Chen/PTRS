<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiScenicSearchByKeyword, type ScenicArea } from '../../lib/api'
import { useScenicHubStore, type PanelTab } from '../../stores/scenicHub'
import ScenicMapCanvas from './components/ScenicMapCanvas.vue'
import FacilityPanel from './components/panels/FacilityPanel.vue'
import FoodPanel from './components/panels/FoodPanel.vue'
import PoiDetailPanel from './components/panels/PoiDetailPanel.vue'
import RoutePanel from './components/panels/RoutePanel.vue'

const hub = useScenicHubStore()
const route = useRoute()
const router = useRouter()
const isMobile = useMediaQuery('(max-width: 900px)')
const sheetExpanded = ref(true)
const areaOpts = ref<ScenicArea[]>([])
const areaLoading = ref(false)
let areaSeq = 0

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

function onAreaChange(id: number | undefined) {
  const picked = areaOpts.value.find((a) => a.id === id)
  hub.setArea(id, picked?.name)
  router.replace({ query: { ...route.query, areaId: id != null ? String(id) : undefined } })
}

function onTabChange(tab: PanelTab) {
  hub.setPanelTab(tab)
  router.replace({ query: { ...route.query, tab } })
}

function onMapLoaded(candidates: any[], details: Record<number, any>) {
  hub.onMapLoaded(candidates, details)
}

function syncFromRoute() {
  const q = route.query
  if (typeof q.areaId === 'string' && q.areaId) {
    const id = Number(q.areaId)
    if (Number.isFinite(id) && hub.areaId !== id) {
      hub.setArea(id)
    }
  }
  const tab = q.tab as string
  if (tab === 'route' || tab === 'facility' || tab === 'food' || tab === 'poi') {
    hub.panelTab = tab
  }
}

onMounted(() => {
  syncFromRoute()
  if (hub.areaId != null) {
    areaOpts.value = [{ id: hub.areaId, name: hub.areaName || `景区 ${hub.areaId}` } as ScenicArea]
  }
})

watch(() => route.query, syncFromRoute)
</script>

<template>
  <div class="hub-page">
    <header class="hub-top glass">
      <el-select
        :model-value="hub.areaId"
        filterable
        remote
        clearable
        placeholder="选择景区（如：北邮沙河）"
        :remote-method="remoteArea"
        :loading="areaLoading"
        style="min-width: 240px; max-width: 360px"
        @update:model-value="onAreaChange"
      >
        <el-option v-for="o in areaOpts" :key="o.id" :label="`${o.name}（ID ${o.id}）`" :value="o.id" />
      </el-select>
      <span v-if="hub.focusPoiId != null" class="focus-chip">当前选中：{{ hub.focusPoiName }}</span>
    </header>

    <div :class="['hub-body', { mobile: isMobile }]">
      <section class="map-stage glass">
        <ScenicMapCanvas
          :area-id="hub.areaId"
          :focus-poi-id="hub.focusPoiId"
          :highlight-path="hub.routePath"
          :facility-highlights="hub.facilityHighlights"
          :hovered-facility-id="hub.hoveredFacilityId"
          :show-road-nodes="hub.showRoadNodes"
          @focus-select="(p) => hub.setFocusPoi(p.nodeId, p.name)"
          @map-loaded="onMapLoaded"
        />
      </section>

      <aside v-if="!isMobile" class="side-panel glass">
        <el-tabs :model-value="hub.panelTab" @update:model-value="(t: PanelTab) => onTabChange(t)">
          <el-tab-pane label="路线" name="route" />
          <el-tab-pane label="设施" name="facility" />
          <el-tab-pane label="美食" name="food" />
          <el-tab-pane label="详情" name="poi" />
        </el-tabs>
        <RoutePanel v-show="hub.panelTab === 'route'" />
        <FacilityPanel v-show="hub.panelTab === 'facility'" />
        <FoodPanel v-show="hub.panelTab === 'food'" />
        <PoiDetailPanel v-show="hub.panelTab === 'poi'" />
      </aside>

      <div v-else class="mobile-sheet glass" :class="{ collapsed: !sheetExpanded }">
        <button type="button" class="sheet-handle" @click="sheetExpanded = !sheetExpanded">
          {{ sheetExpanded ? '收起面板' : '展开面板' }}
        </button>
        <div v-show="sheetExpanded" class="sheet-body">
          <el-tabs :model-value="hub.panelTab" @update:model-value="(t: PanelTab) => onTabChange(t)">
            <el-tab-pane label="路线" name="route" />
            <el-tab-pane label="设施" name="facility" />
            <el-tab-pane label="美食" name="food" />
            <el-tab-pane label="详情" name="poi" />
          </el-tabs>
          <RoutePanel v-show="hub.panelTab === 'route'" />
          <FacilityPanel v-show="hub.panelTab === 'facility'" />
          <FoodPanel v-show="hub.panelTab === 'food'" />
          <PoiDetailPanel v-show="hub.panelTab === 'poi'" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hub-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: calc(100vh - 120px);
}
.hub-top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  flex-wrap: wrap;
}
.focus-chip {
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(80, 200, 255, 0.15);
}
.hub-body {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 14px;
  flex: 1;
  min-height: 0;
}
.hub-body.mobile {
  grid-template-columns: 1fr;
  grid-template-rows: 1fr auto;
}
.map-stage {
  padding: 12px;
  min-height: 480px;
}
.side-panel {
  padding: 12px 14px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.mobile-sheet {
  padding: 8px 12px 12px;
  max-height: 45vh;
  overflow: auto;
}
.mobile-sheet.collapsed {
  max-height: 48px;
  overflow: hidden;
}
.sheet-handle {
  width: 100%;
  border: none;
  background: rgba(255, 255, 255, 0.08);
  color: inherit;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 8px;
}
</style>
