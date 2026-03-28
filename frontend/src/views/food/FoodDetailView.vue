<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiFoodDetailView, apiFoodRate, apiTrackEngagement, type FoodDetailVO } from '../../lib/api'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const food = ref<FoodDetailVO | null>(null)

const rate = reactive({
  rating: 5,
  comment: '',
})

async function load() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    food.value = await apiFoodDetailView(id)
  } finally {
    loading.value = false
  }
}

async function submitRate() {
  if (!food.value) return
  await apiFoodRate({ foodId: food.value.id, rating: rate.rating, comment: rate.comment || undefined })
  ElMessage.success('评分成功')
  await load()
}

async function engage(actionType: 'LIKE' | 'FAVORITE') {
  if (!auth.isAuthed) {
    ElMessage.warning('请先登录后再点赞或收藏')
    return
  }
  if (!food.value?.id) return
  await apiTrackEngagement({
    targetType: 'FOOD',
    targetId: food.value.id,
    actionType,
  })
  ElMessage.success(actionType === 'LIKE' ? '点赞成功，已学习你的偏好' : '收藏成功，已学习你的偏好')
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <el-card class="glass" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <div style="font-weight: 900">{{ food?.name || '美食详情' }}</div>
          <el-tag effect="plain">{{ food?.cuisine || '—' }}</el-tag>
        </div>
      </template>

      <div class="grid">
        <div class="glass block">
          <div class="k">价格</div>
          <div class="v">¥ {{ food?.price ?? 0 }}</div>
        </div>
        <div class="glass block">
          <div class="k">热度 / 评分</div>
          <div class="v">{{ food?.heat ?? 0 }} / {{ food?.rating ?? 0 }}</div>
        </div>
        <div class="glass block">
          <div class="k">所属景区</div>
          <div class="v">{{ food?.areaName || '—' }}</div>
        </div>
        <div class="glass block">
          <div class="k">餐厅信息</div>
          <div class="v">{{ food?.restaurantName || '—' }}</div>
        </div>
      </div>

      <div class="glass block" style="margin-top: 12px">
        <div class="k">描述</div>
        <div class="v2">{{ food?.description || '暂无描述' }}</div>
      </div>

      <div class="actionsBar">
        <el-button @click="engage('LIKE')">点赞美食</el-button>
        <el-button type="primary" plain @click="engage('FAVORITE')">收藏美食</el-button>
      </div>

      <el-divider />

      <div class="glass block">
        <div style="font-weight: 900">评分</div>
        <div class="muted" style="font-size: 12px; margin-top: 4px">
          后端接口：<code>/api/food/rate</code>（需要登录，携带 <code>Authorization: Bearer token</code>）
        </div>

        <div v-if="auth.isAuthed" class="rateBox">
          <el-rate v-model="rate.rating" />
          <el-input v-model="rate.comment" type="textarea" :rows="3" placeholder="写点评价（可选）" />
          <el-button type="primary" @click="submitRate">提交</el-button>
        </div>
        <div v-else class="muted" style="margin-top: 12px">
          请先
          <a style="cursor: pointer; color: var(--accent-main)" @click="$router.push('/login')">登录</a>
          后评分
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.block {
  padding: 14px;
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
.v2 {
  font-size: 13px;
  margin-top: 6px;
  line-height: 1.6;
}
.rateBox {
  margin-top: 12px;
  display: grid;
  gap: 12px;
}

.actionsBar {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>

