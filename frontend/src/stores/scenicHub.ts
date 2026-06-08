import { defineStore } from 'pinia'
import type { FacilityNearbyVO, Food, FoodRecommendVO, RoutePoiCandidate } from '../lib/api'

export type PanelTab = 'route' | 'facility' | 'food' | 'poi'

export type FacilityHighlight = {
  id: number
  name: string
  latitude?: number
  longitude?: number
}

export const useScenicHubStore = defineStore('scenicHub', {
  state: () => ({
    areaId: undefined as number | undefined,
    areaName: '' as string,
    focusPoiId: null as number | null,
    focusDisplayName: '' as string,
    panelTab: 'route' as PanelTab,
    poiCandidates: [] as RoutePoiCandidate[],
    poiLabelMap: {} as Record<number, string>,
    poiDetailMap: {} as Record<number, { name?: string; type?: string; location?: string; longitude?: number; latitude?: number; indoorAvailable?: boolean }>,

    routePath: null as number[] | null,
    facilityRadius: 500 as 200 | 500 | 1000,
    facilityType: '',
    facilityKeyword: '',
    facilityResults: [] as FacilityNearbyVO[],
    hoveredFacilityId: null as number | null,

    foodWeights: { wHeat: 0.3, wRating: 0.5, wDistance: 0.2 },
    foodCuisine: '',
    foodKeyword: '',
    foodRecList: [] as FoodRecommendVO[],
    foodSearchList: [] as Food[],
    foodDetailOpen: false,
    foodDetailId: null as number | null,
    showRoadNodes: false,
  }),

  getters: {
    focusPoiName(state): string {
      if (state.focusPoiId == null) return ''
      if (state.focusDisplayName) return state.focusDisplayName
      return state.poiLabelMap[state.focusPoiId] || `节点 ${state.focusPoiId}`
    },
    facilityHighlights(state): FacilityHighlight[] {
      const out: FacilityHighlight[] = []
      for (const row of state.facilityResults) {
        const f = row.facility
        if (!f?.id || f.latitude == null || f.longitude == null) continue
        out.push({
          id: f.id,
          name: f.name || `设施 ${f.id}`,
          latitude: f.latitude,
          longitude: f.longitude,
        })
      }
      return out
    },
  },

  actions: {
    setArea(id: number | undefined, name?: string) {
      this.areaId = id
      if (name) this.areaName = name
      this.focusPoiId = null
      this.focusDisplayName = ''
      this.routePath = null
      this.clearFacilityHighlights()
    },
    setFocusPoi(id: number | null, displayName?: string) {
      this.focusPoiId = id
      if (id == null) {
        this.focusDisplayName = ''
        return
      }
      if (displayName?.trim()) {
        this.focusDisplayName = displayName.trim()
        return
      }
      const fromFacility = this.facilityResults.find((r) => r.facility?.id === id)?.facility?.name
      this.focusDisplayName = fromFacility || this.poiLabelMap[id] || `节点 ${id}`
    },
    setPanelTab(tab: PanelTab) {
      this.panelTab = tab
    },
    onMapLoaded(candidates: RoutePoiCandidate[], details: Record<number, { name?: string; type?: string; location?: string; longitude?: number; latitude?: number; indoorAvailable?: boolean }>) {
      this.poiCandidates = candidates
      this.poiDetailMap = details
      const labels: Record<number, string> = {}
      candidates.forEach((c) => {
        labels[c.nodeId] = c.name || `节点${c.nodeId}`
      })
      this.poiLabelMap = labels
    },
    setFacilityResults(rows: FacilityNearbyVO[]) {
      this.facilityResults = rows
    },
    clearFacilityHighlights() {
      this.facilityResults = []
      this.hoveredFacilityId = null
    },
    setRoutePath(path: number[] | null) {
      this.routePath = path
    },
  },
})
