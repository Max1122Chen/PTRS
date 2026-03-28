<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  apiRecommendationList,
  apiRecommendationPersonalized,
  type ScenicArea,
  type ScenicAreaRecommendVO,
} from '../lib/api'
import { interestLabelZh, normalizeInterestKey } from '../lib/interestTags'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const tab = ref<'recommend' | 'personalized'>('recommend')

const query = reactive({
  page: 1,
  size: 8,
  type: '' as string | '',
  tagKeyword: '' as string | '',
})

const loading = ref(false)
const list = ref<ScenicArea[]>([])
const total = ref(0)
const pList = ref<ScenicAreaRecommendVO[]>([])
const pTotal = ref(0)

const canPersonal = computed(() => auth.isAuthed)
const presetTagOptions = ['山岳', '古镇', '美食', '历史', '自然', '文化', '亲子', '徒步', '摄影']

const tagOptions = computed(() => {
  const set = new Set<string>(presetTagOptions.map((item) => normalizeInterestKey(item)))
  ;(auth.user?.interests ?? []).forEach((tag) => {
    const v = normalizeInterestKey(tag || '')
    if (v) set.add(v)
  })
  ;[...list.value, ...pList.value].forEach((item) => {
    ;(item.tags ?? []).forEach((tag) => {
      const v = normalizeInterestKey(tag || '')
      if (v) set.add(v)
    })
    if ((!item.tags || item.tags.length === 0) && item.type) {
      const t = normalizeInterestKey(item.type)
      if (t) set.add(t)
    }
  })
  return Array.from(set)
})

function displayTags(item: ScenicArea | ScenicAreaRecommendVO): string[] {
  const tags = item.tags ?? []
  if (tags.length > 0) {
    return tags.slice(0, 2).map((tag) => interestLabelZh(tag))
  }
  if (item.type) {
    return [interestLabelZh(item.type)]
  }
  return ['暂无标签']
}

async function load() {
  loading.value = true
  try {
    if (tab.value === 'personalized') {
      if (!canPersonal.value) {
        ElMessage.info('登录后可使用个性化推荐')
        tab.value = 'recommend'
        return
      }
      const data = await apiRecommendationPersonalized({
        page: query.page,
        size: query.size,
        type: query.type || undefined,
        tagKeyword: query.tagKeyword || undefined,
      })
      pList.value = data.list
      pTotal.value = data.total
      return
    }

    const data = await apiRecommendationList({
      page: query.page,
      size: query.size,
      sortBy: 'heat',
      type: query.type || undefined,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (canPersonal.value) {
    tab.value = 'personalized'
  }
  load()
})

watch(
  () => auth.isAuthed,
  (authed, prev) => {
    if (authed && !prev && tab.value !== 'personalized') {
      tab.value = 'personalized'
      query.page = 1
      load()
    }
  },
)

function formatScore(score?: number) {
  return typeof score === 'number' ? score.toFixed(2) : '0.00'
}
</script>

<template>
  <div class="page">
    <div class="hero glass">
      <div class="h1">探索你的下一段旅程</div>
      <div class="muted">
        热门景区、个性化推荐、路线规划与周边设施一站式体验。
      </div>
      <div class="filters">
        <el-segmented v-model="tab" :options="[
          { label: '热门列表', value: 'recommend' },
          { label: '智能推荐', value: 'personalized' },
        ]" />
        <el-select
          v-if="tab === 'personalized'"
          v-model="query.tagKeyword"
          placeholder="选择标签关键字（可选）"
          style="max-width: 220px"
          filterable
          clearable
        >
          <el-option
            v-for="tag in tagOptions"
            :key="tag"
            :label="interestLabelZh(tag)"
            :value="tag"
          />
        </el-select>
        <el-button type="primary" :loading="loading" @click="query.page = 1; load()">
          刷新
        </el-button>
      </div>
    </div>

    <div class="grid" v-loading="loading">
      <template v-if="tab !== 'personalized'">
        <el-card v-for="s in list" :key="s.id" class="card" shadow="never" @click="$router.push(`/scenic/${s.id}`)">
          <div class="card-title">{{ s.name }}</div>
          <div class="muted line">{{ s.location || '—' }}</div>
          <div class="muted line">{{ s.description || '暂无简介' }}</div>
          <div class="spacer" />
          <div class="meta">
            <div class="tags">
              <el-tag v-for="tag in displayTags(s)" :key="`${s.id}-${tag}`" effect="plain" size="small">{{ tag }}</el-tag>
            </div>
          </div>
        </el-card>
      </template>

      <template v-else>
        <el-card v-for="s in pList" :key="s.id" class="card" shadow="never" @click="$router.push(`/scenic/${s.id}`)">
          <div class="card-title">{{ s.name }}</div>
          <div class="muted line">{{ s.location || '—' }}</div>
          <div class="muted line">{{ s.description || '暂无简介' }}</div>
          <div class="recommend-badge">推荐分 {{ formatScore(s.score) }}</div>
          <div class="recommend-reason">{{ s.reason || '根据你的兴趣和景点质量综合推荐' }}</div>
          <div class="spacer" />
          <div class="meta">
            <div class="tags">
              <el-tag v-for="tag in displayTags(s)" :key="`${s.id}-${tag}`" effect="plain" size="small">{{ tag }}</el-tag>
            </div>
          </div>
        </el-card>
      </template>
    </div>

    <div class="pager">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :page-size="query.size"
        :current-page="query.page"
        :total="tab === 'personalized' ? pTotal : total"
        @current-change="(p:number)=>{query.page=p; load()}"
      />
    </div>
  </div>
</template>

<style scoped>
.hero {
  padding: 20px;
}
.h1 {
  font-size: 26px;
  font-weight: 860;
  margin-bottom: 8px;
  letter-spacing: 0.2px;
}
.filters {
  margin-top: 12px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}
.grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.card {
  cursor: pointer;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  height: 100%;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}
.card:hover {
  transform: translateY(-2px);
  border-color: var(--accent-ring);
  background: rgba(255, 255, 255, 0.035);
}
.card-title {
  font-weight: 820;
  font-size: 16px;
  margin-bottom: 6px;
}
.line {
  font-size: 12px;
  margin-bottom: 6px;
}
.recommend-badge {
  margin-top: 2px;
  margin-bottom: 6px;
  color: #0f7b6c;
  font-size: 12px;
  font-weight: 800;
}
.recommend-reason {
  color: var(--text-2);
  font-size: 12px;
  line-height: 1.4;
}
.spacer {
  flex: 1;
}
.meta {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 10px;
  margin-top: 0;
}
.tags {
  display: flex;
  gap: 6px;
  flex-wrap: nowrap;
  overflow: hidden;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: center;
}
@media (max-width: 1200px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>

