<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiScenicDetail, apiTrackEngagement, type ScenicArea } from '../../lib/api'
import ScenicNearbyDialog from '../../components/map/ScenicNearbyDialog.vue'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const scenic = ref<ScenicArea | null>(null)
const nearbyVisible = ref(false)

const hasLocationDisplay = computed(() => Boolean(scenic.value?.location))

async function load() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    scenic.value = await apiScenicDetail(id)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openNearbyDialog() {
  if (!hasLocationDisplay.value) return
  nearbyVisible.value = true
}

async function engage(actionType: 'LIKE' | 'FAVORITE') {
  if (!auth.isAuthed) {
    ElMessage.warning('请先登录后再点赞或收藏')
    return
  }
  if (!scenic.value?.id) return
  await apiTrackEngagement({
    targetType: 'SCENIC',
    targetId: scenic.value.id,
    actionType,
  })
  ElMessage.success(actionType === 'LIKE' ? '点赞成功，已学习你的偏好' : '收藏成功，已学习你的偏好')
}
</script>

<template>
  <div class="page" v-loading="loading">
    <el-card class="glass" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center; gap: 12px">
          <div style="font-weight: 900; font-size: 18px">{{ scenic?.name || '景区详情' }}</div>
          <div class="tagWrap">
            <div class="muted tagLabel">标签</div>
            <el-tag effect="plain">{{ scenic?.type || '—' }}</el-tag>
          </div>
        </div>
      </template>

      <div class="grid">
        <div
          class="glass block locationBlock"
          :class="{ clickable: hasLocationDisplay }"
          role="button"
          tabindex="0"
          @click="openNearbyDialog"
          @keydown.enter.prevent="openNearbyDialog"
          @keydown.space.prevent="openNearbyDialog"
        >
          <div class="k">位置</div>
          <div class="v">
            {{ scenic?.location || '—' }}
            <span v-if="hasLocationDisplay" class="tip">点击查看地图</span>
          </div>
        </div>
        <div class="glass block">
          <div class="k">开放时间</div>
          <div class="v">{{ scenic?.openTime || '—' }}</div>
        </div>
        <div class="glass block">
          <div class="k">票价</div>
          <div class="v">{{ scenic?.ticketPrice || '—' }}</div>
        </div>
        <div class="glass block">
          <div class="k">热度 / 评分</div>
          <div class="v">{{ scenic?.heat ?? 0 }} / {{ scenic?.rating ?? 0 }}</div>
        </div>
      </div>

      <div class="actionsBar">
        <el-button @click="engage('LIKE')">点赞景点</el-button>
        <el-button type="primary" plain @click="engage('FAVORITE')">收藏景点</el-button>
      </div>

      <div class="glass block" style="margin-top: 14px">
        <div class="k">简介</div>
        <div class="v2">{{ scenic?.description || '暂无简介' }}</div>
      </div>

      <div class="glass block" style="margin-top: 14px">
        <div class="k">经纬度</div>
        <div class="v2">
          {{ scenic?.latitude ?? '—' }}, {{ scenic?.longitude ?? '—' }}
        </div>
      </div>
    </el-card>

    <ScenicNearbyDialog
      v-model="nearbyVisible"
      :scenic-name="scenic?.name"
      :location="scenic?.location"
      :latitude="scenic?.latitude"
      :longitude="scenic?.longitude"
    />
  </div>
</template>

<style scoped>
.tagWrap {
  display: flex;
  align-items: center;
  gap: 10px;
}
.tagLabel {
  font-size: 12px;
  margin-top: 1px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.block {
  padding: 14px;
}
.locationBlock {
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease;
}
.locationBlock.clickable {
  cursor: pointer;
}
.locationBlock.clickable:hover {
  border-color: var(--accent, #7d66d9);
}
.locationBlock.clickable:focus-visible {
  outline: 2px solid var(--accent, #7d66d9);
  outline-offset: 2px;
}
.k {
  font-size: 12px;
  color: var(--text-2);
}
.v {
  font-size: 14px;
  font-weight: 800;
  margin-top: 6px;
}
.tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-2);
  font-weight: 500;
}
.v2 {
  font-size: 13px;
  margin-top: 6px;
  line-height: 1.6;
}

.actionsBar {
  margin-top: 14px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 820px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>

