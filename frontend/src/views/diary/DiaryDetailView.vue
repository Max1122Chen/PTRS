<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiDiaryDetail, apiDiaryRate, apiScenicDetail, type DiaryDetailVO } from '../../lib/api'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const diary = ref<DiaryDetailVO | null>(null)
const destinationLine = ref('')

const rate = reactive({ rating: 5 })

function parseJsonList(s?: string | string[]) {
  if (!s) return []
  if (Array.isArray(s)) return s
  try {
    const v = JSON.parse(s)
    return Array.isArray(v) ? v : []
  } catch {
    return []
  }
}

async function load() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    diary.value = await apiDiaryDetail(id)
    destinationLine.value = ''
    const ids = diary.value.destinations ?? []
    if (ids.length === 0) return
    const parts: string[] = []
    for (const did of ids) {
      try {
        const s = await apiScenicDetail(did)
        parts.push(s.name || `#${did}`)
      } catch {
        parts.push(`#${did}`)
      }
    }
    destinationLine.value = parts.join('、')
  } finally {
    loading.value = false
  }
}

async function submitRate() {
  if (!diary.value?.id) return
  try {
    const r = Number(rate.rating)
    if (r < 1 || r > 5 || Number.isNaN(r)) {
      ElMessage.warning('请选择 1～5 星')
      return
    }
    await apiDiaryRate({ diaryId: diary.value.id, rating: r })
    ElMessage.success('评分成功')
    await load()
  } catch {
    // http 拦截器已提示
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <el-card class="glass" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center; gap: 12px">
          <div style="font-weight: 900">{{ diary?.title || '日记详情' }}</div>
          <div class="muted">热度 {{ diary?.heat ?? 0 }} · 评分 {{ diary?.rating ?? 0 }}</div>
        </div>
      </template>

      <div class="content">
        <div v-if="destinationLine" class="muted" style="margin-bottom: 10px; font-size: 13px">
          目的地：{{ destinationLine }}
        </div>
        <div class="text">{{ diary?.content }}</div>

        <div v-if="parseJsonList(diary?.images).length" class="gallery">
          <div class="muted" style="margin-bottom: 6px">图片</div>
          <el-image
            v-for="(u, idx) in parseJsonList(diary?.images)"
            :key="idx"
            :src="u"
            fit="cover"
            style="width: 160px; height: 110px; border-radius: 12px"
          />
        </div>

        <div v-if="parseJsonList(diary?.videos).length" class="gallery">
          <div class="muted" style="margin-bottom: 6px">视频</div>
          <div v-for="(u, idx) in parseJsonList(diary?.videos)" :key="idx" class="video-card">
            <video :src="u" controls class="video-player" />
            <a :href="u" target="_blank" class="link">{{ u }}</a>
          </div>
        </div>
      </div>

      <el-divider />

      <div class="glass rateBox">
        <div style="font-weight: 900">评分</div>


        <div v-if="auth.isAuthed" class="rateRow">
          <el-rate v-model="rate.rating" />
          <el-button type="primary" @click="submitRate">提交评分</el-button>
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
.content {
  padding: 6px 2px;
}
.text {
  white-space: pre-wrap;
  line-height: 1.75;
}
.gallery {
  margin-top: 14px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}
.link {
  color: var(--accent-main);
  text-decoration: none;
  font-size: 12px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.video-card {
  width: min(420px, 100%);
}

.video-player {
  width: 100%;
  max-height: 260px;
  border-radius: 10px;
  background: #000;
}
.rateBox {
  padding: 14px;
}
.rateRow {
  margin-top: 12px;
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
</style>

