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
.panel {
  padding: 4px 2px;
}
.panel h3 {
  font-size: 18px;
  line-height: 1.35;
}
.panel p {
  margin: 8px 0;
  line-height: 1.55;
}
.indoor-tag {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  color: #6f4a16;
  background: rgba(182, 120, 45, 0.13);
  border: 1px solid rgba(182, 120, 45, 0.18);
  font-size: 13px;
  font-weight: 700;
}
</style>
