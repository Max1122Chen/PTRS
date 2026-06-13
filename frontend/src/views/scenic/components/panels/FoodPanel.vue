<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import {
  apiFoodDetailView,
  apiFoodRecommendation,
  apiFoodSearch,
  type FoodDetailVO,
  type FoodRecommendVO,
} from '../../../../lib/api'
import { useScenicHubStore } from '../../../../stores/scenicHub'

const hub = useScenicHubStore()
const loading = ref(false)
const tab = ref<'recommend' | 'search'>('recommend')
const detail = ref<FoodDetailVO | null>(null)

const searchForm = reactive({
  keyword: '',
  cuisine: '',
  page: 1,
  size: 10,
})

async function loadRecommend() {
  if (hub.areaId == null) {
    ElMessage.warning('请先选择景区')
    return
  }
  loading.value = true
  try {
    hub.foodRecList = await apiFoodRecommendation({
      areaId: hub.areaId,
      anchorPoiId: hub.focusPoiId ?? undefined,
      ...hub.foodWeights,
      page: 1,
      size: 10,
    })
  } finally {
    loading.value = false
  }
}

async function loadSearch() {
  if (hub.areaId == null) {
    ElMessage.warning('请先选择景区')
    return
  }
  loading.value = true
  try {
    hub.foodSearchList = await apiFoodSearch({
      keyword: searchForm.keyword || undefined,
      cuisine: searchForm.cuisine || undefined,
      areaId: hub.areaId,
      page: searchForm.page,
      size: searchForm.size,
    })
  } finally {
    loading.value = false
  }
}

async function openFoodDetail(foodId: number) {
  hub.foodDetailId = foodId
  detail.value = await apiFoodDetailView(foodId)
  hub.foodDetailOpen = true
}

function formatDist(row: FoodRecommendVO) {
  if (row.distance == null) return '-'
  return `${Number(row.distance).toFixed(0)} m`
}
</script>

<template>
  <div class="panel">
    <el-tabs v-model="tab">
      <el-tab-pane label="推荐 Top10" name="recommend" />
      <el-tab-pane label="搜索" name="search" />
    </el-tabs>

    <div class="anchor muted">
      锚点：<strong>{{ hub.focusPoiId != null ? hub.focusPoiName : '景区中心（未选 POI）' }}</strong>
    </div>

    <template v-if="tab === 'recommend'">
      <el-form label-position="top" size="small">
        <el-form-item label="热度权重">
          <el-slider v-model="hub.foodWeights.wHeat" :min="0" :max="1" :step="0.1" show-input />
        </el-form-item>
        <el-form-item label="评价权重">
          <el-slider v-model="hub.foodWeights.wRating" :min="0" :max="1" :step="0.1" show-input />
        </el-form-item>
        <el-form-item label="距离权重">
          <el-slider v-model="hub.foodWeights.wDistance" :min="0" :max="1" :step="0.1" show-input />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="loadRecommend">获取推荐</el-button>
      </el-form>
      <el-table v-if="hub.foodRecList.length" :data="hub.foodRecList" size="small" @row-click="(r: FoodRecommendVO) => r.id && openFoodDetail(r.id)">
        <el-table-column prop="name" label="美食" min-width="100" />
        <el-table-column prop="restaurantName" label="餐厅" min-width="90" />
        <el-table-column label="距离" width="72">
          <template #default="{ row }">{{ formatDist(row) }}</template>
        </el-table-column>
        <el-table-column prop="rating" label="评分" width="56" />
      </el-table>
    </template>

    <template v-else>
      <el-form label-position="top" size="small">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" clearable />
        </el-form-item>
        <el-form-item label="菜系">
          <el-input v-model="searchForm.cuisine" clearable />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="loadSearch">搜索</el-button>
      </el-form>
      <el-table v-if="hub.foodSearchList.length" :data="hub.foodSearchList" size="small" @row-click="(r: { id: number }) => openFoodDetail(r.id)">
        <el-table-column prop="name" label="美食" />
        <el-table-column prop="cuisine" label="菜系" width="80" />
        <el-table-column prop="rating" label="评分" width="56" />
      </el-table>
    </template>

    <el-drawer v-model="hub.foodDetailOpen" title="美食详情" size="380px">
      <template v-if="detail">
        <p><b>{{ detail.name }}</b></p>
        <p class="muted">菜系：{{ detail.cuisine || '-' }}</p>
        <p class="muted">餐厅：{{ detail.restaurantName || '-' }}</p>
        <p class="muted">景区：{{ detail.areaName || '-' }}</p>
        <p class="muted">评分：{{ detail.rating ?? '-' }}</p>
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
.panel :deep(.el-tabs__header) {
  margin-bottom: 2px;
}
.panel :deep(.el-form-item) {
  margin-bottom: 12px;
}
.panel :deep(.el-slider__runway) {
  margin-right: 4px;
}
.anchor {
  font-size: 13px;
  line-height: 1.5;
  padding: 9px 10px;
  border-radius: 8px;
  background: rgba(182, 120, 45, 0.1);
  border: 1px solid rgba(182, 120, 45, 0.16);
}
</style>
