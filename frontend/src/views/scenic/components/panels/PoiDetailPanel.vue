<script setup lang="ts">
import { computed } from 'vue'
import { useScenicHubStore } from '../../../../stores/scenicHub'

const hub = useScenicHubStore()

const detail = computed(() => {
  if (hub.focusPoiId == null) return null
  return hub.poiDetailMap[hub.focusPoiId] || { name: hub.focusPoiName }
})
</script>

<template>
  <div class="panel">
    <template v-if="hub.focusPoiId != null && detail">
      <h3 style="margin: 0 0 8px">{{ detail.name || hub.focusPoiName }}</h3>
      <p class="muted">节点 ID：{{ hub.focusPoiId }}</p>
      <p class="muted">类型：{{ detail.type || '未分类' }}</p>
      <p class="muted">位置：{{ detail.location || '-' }}</p>
      <p v-if="detail.latitude != null && detail.longitude != null" class="muted">
        经纬度：{{ detail.longitude?.toFixed(6) }}, {{ detail.latitude?.toFixed(6) }}
      </p>
      <p v-if="detail.indoorAvailable" class="indoor-tag">支持室内导航 — 在地图上点击该 POI 可进入室内图</p>
    </template>
    <p v-else class="muted">请先在地图上点击选择一个 POI</p>
  </div>
</template>

<style scoped>
.indoor-tag {
  margin-top: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 200, 80, 0.15);
  font-size: 13px;
}
</style>
