<script setup lang="ts">
import { Compass, ForkSpoon, Guide, Location, MapLocation, Refresh, Search, Star, TrendCharts } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  apiRecommendationList,
  apiRecommendationPersonalized,
  apiTagsList,
  type ScenicArea,
  type ScenicAreaRecommendVO,
} from '../lib/api'
import {
  COMMON_INTEREST_KEYS,
  interestLabelZh,
  isExcludedTagPickerKey,
  normalizeInterestKey,
} from '../lib/interestTags'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const base = import.meta.env.BASE_URL

const tab = ref<'recommend' | 'personalized'>('recommend')
const query = reactive({
  page: 1,
  size: 8,
  type: '' as string,
  tagKeyword: '' as string,
})

const loading = ref(false)
const list = ref<ScenicArea[]>([])
const total = ref(0)
const pList = ref<ScenicAreaRecommendVO[]>([])
const pTotal = ref(0)
const catalogTagKeys = ref<string[]>([])

const canPersonal = computed(() => auth.isAuthed)
const activeList = computed<(ScenicArea | ScenicAreaRecommendVO)[]>(() =>
  tab.value === 'personalized' ? pList.value : list.value,
)
const activeTotal = computed(() => (tab.value === 'personalized' ? pTotal.value : total.value))
const featuredScenic = computed(() => activeList.value[0])

const tagOptions = computed(() => {
  const raw = catalogTagKeys.value.length > 0 ? catalogTagKeys.value : [...COMMON_INTEREST_KEYS]
  const set = new Set(raw.filter((k) => !isExcludedTagPickerKey(k)))
  ;(auth.user?.interests ?? []).forEach((tag) => {
    const value = normalizeInterestKey(tag || '')
    if (value && !isExcludedTagPickerKey(value)) set.add(value)
  })
  ;[...list.value, ...pList.value].forEach((item) => {
    ;(item.tags ?? []).forEach((tag) => {
      const value = normalizeInterestKey(tag || '')
      if (value && !isExcludedTagPickerKey(value)) set.add(value)
    })
    if ((!item.tags || item.tags.length === 0) && item.type) {
      const value = normalizeInterestKey(item.type)
      if (value && !isExcludedTagPickerKey(value)) set.add(value)
    }
  })
  return Array.from(set)
})

const heroStats = computed(() => [
  { label: '可探索景区', value: activeTotal.value || activeList.value.length },
  { label: '兴趣标签', value: tagOptions.value.length },
  { label: '当前展示', value: activeList.value.length },
])

const quickThemes = [
  { key: 'nature', label: '自然风光', hint: '轻徒步与开阔视野' },
  { key: 'culture', label: '人文建筑', hint: '展馆、校园与历史空间' },
  { key: 'food', label: '美食周边', hint: '边逛边吃的路线灵感' },
]

const featureEntrances = [
  { tab: 'route' as const, label: '规划路线', icon: Guide, desc: '室外到室内一体导航' },
  { tab: 'facility' as const, label: '查找设施', icon: MapLocation, desc: '洗手间、服务点、出入口' },
  { tab: 'food' as const, label: '发现美食', icon: ForkSpoon, desc: '按距离、热度、评分推荐' },
]

const scenicVisuals = [
  `${base}explorescape/bac-1.png`,
  `${base}explorescape/bac-2-2.png`,
  `${base}explorescape/bac-2.png`,
  `${base}explorescape/bac-3.png`,
]
const destinationImage = `${base}explorescape/bac-1.png`

async function loadTagCatalog() {
  const fallback = [...COMMON_INTEREST_KEYS].filter((k) => !isExcludedTagPickerKey(k))
  try {
    const rows = await apiTagsList()
    const keys = rows
      .map((t) => normalizeInterestKey(t.name || ''))
      .filter((k): k is string => Boolean(k))
      .filter((k) => !isExcludedTagPickerKey(k))
    catalogTagKeys.value = keys.length > 0 ? keys : fallback
  } catch {
    catalogTagKeys.value = fallback
  }
}

function displayTags(item: ScenicArea | ScenicAreaRecommendVO): string[] {
  const tags = item.tags ?? []
  if (tags.length > 0) return tags.slice(0, 2).map((tag) => interestLabelZh(tag))
  if (item.type) return [interestLabelZh(item.type)]
  return ['暂无标签']
}

function scenicVisual(index: number) {
  return scenicVisuals[index % scenicVisuals.length]
}

function formatRating(value?: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(1) : '4.8'
}

function formatHeat(value?: number) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '探索中'
  if (value >= 10000) return `${(value / 10000).toFixed(1)}万热度`
  return `${Math.round(value)} 热度`
}

function formatScore(score?: number) {
  return typeof score === 'number' ? score.toFixed(2) : '0.00'
}

function recommendScore(item: ScenicArea | ScenicAreaRecommendVO) {
  return 'score' in item ? item.score : undefined
}

function recommendReason(item: ScenicArea | ScenicAreaRecommendVO) {
  return 'reason' in item ? item.reason : undefined
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
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  applyRecommendQueryFromRoute()
  if (!route.query.rt && canPersonal.value) {
    tab.value = 'personalized'
  }
  await loadTagCatalog()
  await load()
  if (route.query.rt) {
    await router.replace({ path: '/recommend' })
  }
})

watch(
  () => auth.isAuthed,
  (authed, prev) => {
    if (authed && !prev && tab.value !== 'personalized') {
      tab.value = 'personalized'
      query.page = 1
      void load()
    }
  },
)

function recommendReturnQuery(): Record<string, string> {
  const out: Record<string, string> = {
    rt: tab.value === 'recommend' ? 'r' : 'p',
    rp: String(query.page),
  }
  if (tab.value === 'personalized' && query.tagKeyword) {
    out.rk = query.tagKeyword
  }
  return out
}

async function openScenic(item: ScenicArea | ScenicAreaRecommendVO) {
  const q = recommendReturnQuery()
  await router.replace({ path: '/recommend', query: q })
  await router.push({ path: '/scenic', query: { areaId: String(item.id) } })
}

async function openScenicTab(item: ScenicArea | ScenicAreaRecommendVO | undefined, scenicTab: 'route' | 'facility' | 'food') {
  if (!item) return
  const q = recommendReturnQuery()
  await router.replace({ path: '/recommend', query: q })
  await router.push({ path: '/scenic', query: { areaId: String(item.id), tab: scenicTab } })
}

function selectTheme(key: string) {
  query.page = 1
  if (canPersonal.value) {
    tab.value = 'personalized'
    query.tagKeyword = key
  } else {
    tab.value = 'recommend'
    query.type = key
  }
  void load()
}

function applyRecommendQueryFromRoute() {
  const rq = route.query
  if (rq.rt === 'r') {
    tab.value = 'recommend'
    query.page = rq.rp ? Number(rq.rp) || 1 : 1
    query.tagKeyword = ''
  } else if (rq.rt === 'p') {
    tab.value = 'personalized'
    query.page = rq.rp ? Number(rq.rp) || 1 : 1
    query.tagKeyword = typeof rq.rk === 'string' ? rq.rk : ''
  }
}

function onRecommendTabChange() {
  query.page = 1
  query.type = ''
  query.tagKeyword = ''
  void load()
}
</script>

<template>
  <div class="page recommend-page">
    <section class="travel-hero animate-fade-in-up">
      <div class="hero-copy">
        <div class="eyebrow">
          <el-icon><Compass /></el-icon>
          智能旅行辅助
        </div>
        <h1>把下一段旅程，变成一张可探索的地图</h1>
        <p>从兴趣推荐进入景区工作台，继续完成路线规划、设施查询、美食发现和室内导航。</p>
        <div class="hero-actions">
          <el-segmented
            v-model="tab"
            :options="[
              { label: '热门目的地', value: 'recommend' },
              { label: '为你推荐', value: 'personalized' },
            ]"
            @change="onRecommendTabChange"
          />
          <el-button type="primary" :loading="loading" :icon="Refresh" @click="query.page = 1; load()">
            刷新灵感
          </el-button>
        </div>
        <div v-if="tab === 'personalized'" class="theme-row">
          <button v-for="theme in quickThemes" :key="theme.key" type="button" class="theme-chip" @click="selectTheme(theme.key)">
            <span>{{ theme.label }}</span>
            <small>{{ theme.hint }}</small>
          </button>
        </div>
      </div>

      <div class="hero-visual" aria-hidden="true">
        <img :src="scenicVisual(0)" alt="" />
        <div class="visual-card visual-card--top">
          <el-icon><TrendCharts /></el-icon>
          <span>{{ heroStats[0].value }} 个目的地</span>
        </div>
        <div class="visual-card visual-card--bottom">
          <el-icon><Location /></el-icon>
          <span>{{ featuredScenic?.name || '选择景区开始探索' }}</span>
        </div>
      </div>
    </section>

    <section v-if="tab === 'personalized'" class="planner-strip glass animate-fade-in-up delay-200">
      <div class="filter-panel">
        <div class="filter-title">
          <el-icon><Search /></el-icon>
          细化你的偏好
        </div>
        <el-select
          v-if="tab === 'personalized'"
          v-model="query.tagKeyword"
          placeholder="选择兴趣标签"
          filterable
          clearable
        >
          <el-option v-for="tag in tagOptions" :key="tag" :label="interestLabelZh(tag)" :value="tag" />
        </el-select>
        <el-select v-else v-model="query.type" placeholder="选择景区类型" filterable clearable>
          <el-option v-for="tag in tagOptions" :key="tag" :label="interestLabelZh(tag)" :value="tag" />
        </el-select>
        <el-button :icon="Search" @click="query.page = 1; load()">查找</el-button>
      </div>

      <div class="hero-stats">
        <div v-for="stat in heroStats" :key="stat.label" class="stat-pill">
          <strong>{{ stat.value }}</strong>
          <span>{{ stat.label }}</span>
        </div>
      </div>
    </section>

    <section v-if="tab === 'personalized' && featuredScenic" class="featured glass animate-fade-in-up delay-200">
      <div class="featured-media">
        <img :src="scenicVisual(1)" alt="" />
      </div>
      <div class="featured-body">
        <span class="section-kicker">本页精选</span>
        <h2>{{ featuredScenic.name }}</h2>
        <p>{{ featuredScenic.description || '从景区地图进入路线、设施和美食模块，快速完成一次完整的出行准备。' }}</p>
        <div class="featured-meta">
          <span><el-icon><Location /></el-icon>{{ featuredScenic.location || '位置待探索' }}</span>
          <span><el-icon><Star /></el-icon>{{ formatRating(featuredScenic.rating) }}</span>
          <span>{{ formatHeat(featuredScenic.heat) }}</span>
        </div>
        <div class="feature-entrances">
          <button
            v-for="entry in featureEntrances"
            :key="entry.tab"
            type="button"
            class="feature-entry"
            @click="openScenicTab(featuredScenic, entry.tab)"
          >
            <el-icon><component :is="entry.icon" /></el-icon>
            <span>{{ entry.label }}</span>
            <small>{{ entry.desc }}</small>
          </button>
        </div>
      </div>
    </section>

    <div class="section-head">
      <div>
        <span class="section-kicker">目的地列表</span>
        <h2>{{ tab === 'personalized' ? '根据兴趣挑选' : '当前热门景区' }}</h2>
      </div>
      <span class="list-count">共 {{ activeTotal }} 条</span>
    </div>

    <div class="grid animate-fade-in-up delay-200" v-loading="loading">
      <el-card
        v-for="s in activeList"
        :key="s.id"
        class="card destination-card ui-interactive-card"
        shadow="never"
        @click="openScenic(s)"
      >
        <div class="card-media">
          <img :src="destinationImage" alt="" />
          <div class="card-score">
            <el-icon><Star /></el-icon>
            {{ formatRating(s.rating) }}
          </div>
        </div>
        <div class="card-body">
          <div class="card-title">{{ s.name }}</div>
          <div class="location-line">
            <el-icon><Location /></el-icon>
            <span>{{ s.location || '位置待探索' }}</span>
          </div>
          <div class="muted line">{{ s.description || '暂无简介，可进入景区工作台查看路线、设施与美食信息。' }}</div>
          <div v-if="tab === 'personalized'" class="recommend-badge">推荐分 {{ formatScore(recommendScore(s)) }}</div>
          <div v-if="tab === 'personalized'" class="recommend-reason">
            {{ recommendReason(s) || '根据你的兴趣和景点质量综合推荐' }}
          </div>
          <div class="spacer" />
          <div class="meta">
            <div class="tags">
              <el-tag v-for="tag in displayTags(s)" :key="`${s.id}-${tag}`" effect="plain" size="small">{{ tag }}</el-tag>
            </div>
            <span class="enter-hint">进入景区</span>
          </div>
        </div>
      </el-card>
    </div>

    <div class="pager">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :page-size="query.size"
        :current-page="query.page"
        :total="activeTotal"
        @current-change="(p:number)=>{query.page=p; load()}"
      />
    </div>
  </div>
</template>

<style scoped>
.recommend-page {
  max-width: min(1240px, 100%) !important;
}

.travel-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(340px, 0.95fr);
  gap: 22px;
  min-height: 430px;
  overflow: hidden;
  border-radius: 8px;
  padding: clamp(22px, 4vw, 42px);
  color: #fff;
  background:
    linear-gradient(105deg, rgba(8, 22, 20, 0.92), rgba(18, 55, 48, 0.72) 50%, rgba(182, 120, 45, 0.22)),
    url('/explorescape/bac-4.png') center 20% / cover;
  box-shadow: var(--shadow-lg);
}

.travel-hero::after {
  content: '';
  position: absolute;
  inset: auto 0 0;
  height: 36%;
  pointer-events: none;
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.35), transparent);
}

.hero-copy,
.hero-visual {
  position: relative;
  z-index: 1;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.eyebrow,
.section-kicker {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #d7c49f;
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0;
}

.travel-hero h1 {
  margin: 12px 0;
  max-width: 720px;
  font-size: clamp(34px, 6vw, 64px);
  line-height: 1.04;
  letter-spacing: 0;
}

.travel-hero p {
  max-width: 610px;
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 16px;
  line-height: 1.8;
}

.hero-actions,
.theme-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 22px;
}

.travel-hero :deep(.el-segmented) {
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
}

.theme-chip {
  display: grid;
  gap: 4px;
  min-width: 150px;
  padding: 12px 14px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  color: #fff;
  text-align: left;
  background: rgba(255, 255, 255, 0.1);
  cursor: pointer;
  backdrop-filter: blur(12px);
  transition: transform 0.2s ease, background 0.2s ease, border-color 0.2s ease;
}

.theme-chip:hover {
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.36);
}

.theme-chip span {
  font-weight: 850;
}

.theme-chip small {
  color: rgba(255, 255, 255, 0.66);
}

.hero-visual {
  min-height: 320px;
  border-radius: 8px;
  overflow: hidden;
  align-self: center;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.hero-visual img {
  width: 100%;
  height: 100%;
  min-height: 320px;
  object-fit: cover;
  transform: scale(1.05);
}

.visual-card {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: min(78%, 300px);
  padding: 10px 12px;
  border-radius: 8px;
  color: #15201c;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.42);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(14px);
  font-weight: 850;
}

.visual-card--top {
  top: 18px;
  right: 18px;
}

.visual-card--bottom {
  left: 18px;
  bottom: 18px;
}

.planner-strip {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-top: 16px;
  padding: 14px;
}

.filter-panel,
.hero-stats,
.featured-meta,
.feature-entrances {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-weight: 850;
  color: var(--text-primary);
}

.filter-panel :deep(.el-select) {
  width: min(260px, 68vw);
}

.stat-pill {
  display: grid;
  gap: 2px;
  min-width: 92px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(22, 66, 60, 0.08);
  border: 1px solid rgba(22, 66, 60, 0.12);
}

.stat-pill strong {
  font-size: 19px;
  color: var(--accent);
}

.stat-pill span {
  color: var(--text-secondary);
  font-size: 12px;
}

.featured {
  display: grid;
  grid-template-columns: minmax(260px, 0.65fr) minmax(0, 1fr);
  gap: 18px;
  margin-top: 16px;
  padding: 14px;
  overflow: hidden;
}

.featured-media {
  min-height: 230px;
  border-radius: 8px;
  overflow: hidden;
}

.featured-media img,
.card-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.featured-body {
  align-self: center;
  min-width: 0;
}

.featured h2,
.section-head h2 {
  margin: 6px 0 8px;
  color: var(--text-primary);
  font-size: clamp(22px, 3vw, 34px);
  line-height: 1.15;
  letter-spacing: 0;
}

.featured p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.featured-meta {
  margin: 14px 0;
  color: var(--text-secondary);
}

.featured-meta span,
.location-line {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.feature-entry {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  column-gap: 9px;
  min-width: 178px;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgba(22, 66, 60, 0.12);
  background: rgba(255, 255, 255, 0.45);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.feature-entry:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.feature-entry .el-icon {
  grid-row: span 2;
  margin-top: 2px;
  color: var(--accent);
}

.feature-entry span {
  font-weight: 850;
}

.feature-entry small {
  color: var(--text-secondary);
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin: 22px 2px 12px;
  color: var(--text-primary);
}

.section-head h2 {
  margin-bottom: 0;
}

.list-count {
  color: rgba(255, 255, 255, 0.72);
  font-weight: 750;
}

.grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.card {
  cursor: pointer;
  display: flex;
  flex-direction: column;
  min-height: 360px;
  height: 100%;
  overflow: hidden;
}

.destination-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0 !important;
}

.card-media {
  position: relative;
  height: 138px;
  overflow: hidden;
}

.card-score {
  position: absolute;
  top: 10px;
  right: 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  border-radius: 999px;
  color: #14211d;
  background: rgba(255, 255, 255, 0.78);
  font-weight: 850;
  font-size: 12px;
  backdrop-filter: blur(10px);
}

.card-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 15px;
}

.card-title {
  font-weight: 850;
  font-size: 17px;
  line-height: 1.35;
  margin-bottom: 8px;
}

.location-line {
  color: var(--text-secondary);
  font-size: 12px;
  margin-bottom: 8px;
}

.line {
  font-size: 13px;
  line-height: 1.58;
  margin-bottom: 8px;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.recommend-badge {
  display: inline-flex;
  align-self: flex-start;
  margin-top: 2px;
  margin-bottom: 6px;
  color: var(--accent);
  background: rgba(22, 66, 60, 0.08);
  border: 1px solid rgba(22, 66, 60, 0.12);
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 850;
}

.recommend-reason {
  color: var(--text-2);
  font-size: 12px;
  line-height: 1.5;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.spacer {
  flex: 1;
}

.meta {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 10px;
  margin-top: 14px;
}

.tags {
  display: flex;
  gap: 6px;
  flex-wrap: nowrap;
  overflow: hidden;
}

.enter-hint {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 800;
  color: var(--accent);
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

@media (max-width: 1180px) {
  .travel-hero,
  .featured {
    grid-template-columns: 1fr;
  }

  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .travel-hero {
    min-height: auto;
    padding: 20px;
  }

  .hero-visual {
    min-height: 220px;
  }

  .hero-visual img {
    min-height: 220px;
  }

  .hero-actions,
  .planner-strip,
  .filter-panel,
  .hero-stats,
  .feature-entrances {
    align-items: stretch;
    flex-direction: column;
  }

  .theme-chip,
  .feature-entry,
  .filter-panel :deep(.el-select),
  .filter-panel .el-button,
  .hero-actions :deep(.el-segmented),
  .hero-actions .el-button {
    width: 100%;
  }

  .grid {
    grid-template-columns: 1fr;
  }

  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
