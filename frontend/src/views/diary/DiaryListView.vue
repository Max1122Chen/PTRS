<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  apiDiaryDetail,
  apiDiaryList,
  apiDiarySearch,
  apiGetInterest,
  apiRecommendationList,
  apiScenicSearchByKeyword,
  type Diary,
} from '../../lib/api'
import { interestLabelZh, isExcludedTagPickerKey, normalizeInterestKey } from '../../lib/interestTags'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)

const listQuery = reactive({ page: 1, size: 50, sortBy: 'heat' })
const list = ref<Diary[]>([])
const diaryDestMap = ref<Record<number, number[]>>({})
const diaryCreatorNicknameMap = ref<Record<number, string>>({})
const scenicTagMap = ref<Record<number, string[]>>({})
const scenicNameMap = ref<Record<number, string>>({})

/** 用户兴趣（规范键 + 权重），用于顶部标签顺序与内容 */
const interestRows = ref<{ type: string; weight: number }[]>([])

const RECOMMEND_CHIP_ID = '__recommend__'
const activeChipId = ref(RECOMMEND_CHIP_ID)

const diaryFilterChips = computed(() => {
  const chips: { id: string; label: string }[] = [{ id: RECOMMEND_CHIP_ID, label: '推荐' }]
  const seen = new Set<string>()
  const sorted = [...interestRows.value].sort((a, b) => b.weight - a.weight)
  for (const row of sorted) {
    if (!row.type || seen.has(row.type)) continue
    seen.add(row.type)
    chips.push({ id: row.type, label: interestLabelZh(row.type) })
  }
  return chips
})

watch(diaryFilterChips, (chips) => {
  const ids = new Set(chips.map((c) => c.id))
  if (!ids.has(activeChipId.value)) {
    activeChipId.value = RECOMMEND_CHIP_ID
  }
})

const searchQuery = reactive({ keyword: '', destination: undefined as number | undefined, page: 1, size: 50 })
const searchList = ref<Diary[]>([])

const fromSearch = ref(false)
const sortBy = ref<'heat' | 'rating'>('heat')
const searchInput = ref('')
const confirmedSearchLabel = ref('')

type SearchSuggestion = {
  kind: 'title' | 'content' | 'destination'
  id: string
  diaryId: number
  label: string
  subLabel: string
  keyword?: string
  destinationId?: number
  destinationName?: string
}

const suggestionLoading = ref(false)
const suggestionOpen = ref(false)
const suggestionList = ref<SearchSuggestion[]>([])
const selectedDestination = ref<{ id: number; name: string } | null>(null)
let suggestSeq = 0
let suggestTimer: ReturnType<typeof setTimeout> | null = null

function uniqueByDiaryId(rows: SearchSuggestion[]) {
  const seen = new Set<number>()
  const out: SearchSuggestion[] = []
  for (const row of rows) {
    if (seen.has(row.diaryId)) continue
    seen.add(row.diaryId)
    out.push(row)
  }
  return out
}

function includesIgnoreCase(raw: string | null | undefined, keyword: string) {
  if (!raw) return false
  return raw.toLowerCase().includes(keyword.toLowerCase())
}

function contentSnippet(content: string | null | undefined, keyword: string) {
  if (!content) return ''
  const source = content.replace(/\s+/g, ' ').trim()
  if (!source) return ''
  const idx = source.toLowerCase().indexOf(keyword.toLowerCase())
  if (idx < 0) {
    return source.length > 42 ? `${source.slice(0, 42)}...` : source
  }
  const start = Math.max(0, idx - 10)
  const end = Math.min(source.length, idx + keyword.length + 24)
  const snippet = source.slice(start, end)
  return `${start > 0 ? '...' : ''}${snippet}${end < source.length ? '...' : ''}`
}

function buildDiarySuggestions(rows: Diary[], keyword: string) {
  const suggestions: SearchSuggestion[] = []
  for (const row of rows) {
    if (suggestions.length >= 8) break
    const titleMatch = includesIgnoreCase(row.title, keyword)
    if (titleMatch) {
      suggestions.push({
        kind: 'title',
        id: `title-${row.id}`,
        diaryId: row.id,
        label: row.title || '(无标题)',
        subLabel: '标题匹配',
        keyword,
      })
      continue
    }
    const snippet = contentSnippet(row.content, keyword)
    const contentMatch = includesIgnoreCase(row.content, keyword)
    suggestions.push({
      kind: contentMatch ? 'content' : 'title',
      id: `${contentMatch ? 'content' : 'title'}-${row.id}`,
      diaryId: row.id,
      label: row.title || '(无标题)',
      subLabel: contentMatch
        ? (snippet ? `正文匹配：${snippet}` : '正文匹配')
        : '标题匹配',
      keyword,
    })
  }
  return suggestions
}

async function buildDestinationDiarySuggestions(keyword: string) {
  const scenicRows = await apiScenicSearchByKeyword({ keyword, limit: 4 })
  if (!scenicRows.length) return [] as SearchSuggestion[]

  const groups = await Promise.all(
    scenicRows.map(async (scenic) => {
      const diaries = await apiDiarySearch({ destination: scenic.id, page: 1, size: 4 })
      return diaries.map((row) => ({
        kind: 'destination' as const,
        id: `destination-${scenic.id}-${row.id}`,
        diaryId: row.id,
        label: row.title || '(无标题)',
        subLabel: `目的地匹配：${scenic.name}`,
        destinationId: scenic.id,
        destinationName: scenic.name,
      }))
    }),
  )

  return uniqueByDiaryId(groups.flat())
}

async function fetchSearchSuggestions(keyword: string, seq: number) {
  suggestionLoading.value = true
  try {
    const [diaryRows, destinationDiaryRows] = await Promise.all([
      apiDiarySearch({ keyword, page: 1, size: 8 }),
      buildDestinationDiarySuggestions(keyword),
    ])

    if (seq !== suggestSeq) return

    const diarySuggestions = buildDiarySuggestions(diaryRows || [], keyword)
    suggestionList.value = uniqueByDiaryId([...diarySuggestions, ...destinationDiaryRows]).slice(0, 12)
    suggestionOpen.value = true
  } finally {
    if (seq === suggestSeq) suggestionLoading.value = false
  }
}

function onSearchFocus() {
  if (searchInput.value.trim()) {
    suggestionOpen.value = true
  }
}

function onSearchBlur() {
  window.setTimeout(() => {
    suggestionOpen.value = false
  }, 120)
}

function onSearchClear() {
  selectedDestination.value = null
  suggestionList.value = []
  suggestionOpen.value = false
  suggestionLoading.value = false
}

async function applySuggestion(item: SearchSuggestion) {
  suggestionOpen.value = false
  const q: Record<string, string> = {
    lm: 'b',
    chip: activeChipId.value,
    sort: sortBy.value,
  }
  const inp = searchInput.value.trim()
  if (inp) q.inp = inp
  await router.replace({ path: '/diary', query: q })
  await router.push(`/diary/${item.diaryId}`)
}

function buildDiaryListReturnQuery(): Record<string, string> {
  const q: Record<string, string> = {
    chip: activeChipId.value,
    sort: sortBy.value,
    inp: searchInput.value.trim(),
  }
  if (fromSearch.value) {
    q.lm = 's'
    if (searchQuery.keyword.trim()) q.kw = searchQuery.keyword.trim()
    if (searchQuery.destination != null) q.dst = String(searchQuery.destination)
    if (confirmedSearchLabel.value) q.lbl = confirmedSearchLabel.value
  } else {
    q.lm = 'b'
  }
  return q
}

async function openDiaryDetail(row: Diary) {
  const q = buildDiaryListReturnQuery()
  await router.replace({ path: '/diary', query: q })
  await router.push(`/diary/${row.id}`)
}

async function confirmSearch() {
  const q = searchInput.value.trim()
  if (!q) {
    await showAll()
    return
  }

  const exactDestination = suggestionList.value.find(
    (item) => item.kind === 'destination' && item.destinationName === q && item.destinationId,
  )

  if (selectedDestination.value && selectedDestination.value.name === q) {
    searchQuery.destination = selectedDestination.value.id
    searchQuery.keyword = ''
    confirmedSearchLabel.value = `目的地：${q}`
  } else if (exactDestination) {
    searchQuery.destination = exactDestination.destinationId
    searchQuery.keyword = ''
    confirmedSearchLabel.value = `目的地：${q}`
  } else {
    searchQuery.keyword = q
    searchQuery.destination = undefined
    selectedDestination.value = null
    confirmedSearchLabel.value = `关键词：${q}`
  }

  await runSearch()
  suggestionOpen.value = false
}

async function load() {
  loading.value = true
  try {
    await ensureScenicTagMap()
    list.value = await apiDiaryList(listQuery)
    await hydrateDiaryDestinations(list.value)
    fromSearch.value = false
  } finally {
    loading.value = false
  }
}

async function runSearch() {
  loading.value = true
  try {
    await ensureScenicTagMap()
    searchList.value = await apiDiarySearch({
      keyword: searchQuery.keyword.trim() || undefined,
      destination: searchQuery.destination,
      page: searchQuery.page,
      size: searchQuery.size,
    })
    await hydrateDiaryDestinations(searchList.value)
    fromSearch.value = true
  } finally {
    loading.value = false
  }
}

const baseItems = computed(() => (fromSearch.value ? searchList.value : list.value))

const filteredItems = computed(() => {
  if (activeChipId.value === RECOMMEND_CHIP_ID) return baseItems.value
  const key = activeChipId.value
  return baseItems.value.filter((row) => {
    const destIds = diaryDestMap.value[row.id] || []
    if (!destIds.length) return false
    return destIds.some((destId) => {
      const tags = scenicTagMap.value[destId] || []
      return tags.some((t) => normalizeInterestKey(t) === key)
    })
  })
})

function scoreOf(row: Diary, key: 'heat' | 'rating') {
  const value = key === 'rating' ? row.rating : row.heat
  const num = Number(value ?? 0)
  return Number.isFinite(num) ? num : 0
}

const displayItems = computed(() => {
  const rows = [...filteredItems.value]
  rows.sort((a, b) => scoreOf(b, sortBy.value) - scoreOf(a, sortBy.value))
  return rows
})

const sortLabel = computed(() => (sortBy.value === 'rating' ? '评分排序' : '热度排序'))

function onSortCommand(command: string | number | object) {
  if (command === 'rating') {
    sortBy.value = 'rating'
    return
  }
  sortBy.value = 'heat'
}

function firstImage(d: Diary): string | null {
  const raw = d.images
  if (!raw) return null
  if (Array.isArray(raw)) {
    const r = raw as unknown as string[]
    return r[0] && typeof r[0] === 'string' ? r[0] : null
  }
  try {
    const v = JSON.parse(raw) as string[]
    return Array.isArray(v) && v[0] ? String(v[0]) : null
  } catch {
    return null
  }
}

/** 关联景区 tags（去重，中文展示） */
function diaryScenicTags(row: Diary): string[] {
  const destIds = diaryDestMap.value[row.id] || []
  const seen = new Set<string>()
  const out: string[] = []
  for (const destId of destIds) {
    for (const tag of scenicTagMap.value[destId] || []) {
      const label = interestLabelZh(normalizeInterestKey(tag || ''))
      if (!label || seen.has(label)) continue
      seen.add(label)
      out.push(label)
    }
  }
  return out
}

function diaryScenicLabel(row: Diary): string {
  const destIds = diaryDestMap.value[row.id] || []
  const names = destIds
    .map((id) => scenicNameMap.value[id])
    .filter((n): n is string => Boolean(n))
  if (names.length) return names.join(' · ')
  return diaryCreatorNicknameMap.value[row.id] || '—'
}

function formatDiaryDate(row: Diary): string {
  const raw = row.createTime
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw.slice(0, 7).replace(/-/g, '.')
  const y = d.getFullYear()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  return `${y}.${m}`
}

function diaryHeatBadge(row: Diary): string {
  if (sortBy.value === 'rating' && row.rating != null) {
    return `★${Number(row.rating).toFixed(1)}`
  }
  const heat = Number(row.heat ?? 0)
  return `热 ${Number.isFinite(heat) ? heat : 0}`
}

async function showAll() {
  fromSearch.value = false
  searchQuery.keyword = ''
  searchQuery.destination = undefined
  confirmedSearchLabel.value = ''
  searchInput.value = ''
  selectedDestination.value = null
  suggestionList.value = []
  suggestionOpen.value = false
  suggestionLoading.value = false
  await load()
}

async function ensureScenicTagMap() {
  if (Object.keys(scenicTagMap.value).length > 0) return
  const res = await apiRecommendationList({ page: 1, size: 300, sortBy: 'heat' })
  const tagMap: Record<number, string[]> = {}
  const nameMap: Record<number, string> = {}
  for (const item of res.list || []) {
    tagMap[item.id] = Array.isArray(item.tags) ? item.tags : []
    if (item.name) nameMap[item.id] = item.name
  }
  scenicTagMap.value = tagMap
  scenicNameMap.value = nameMap
}

async function hydrateDiaryDestinations(diaries: Diary[]) {
  const missIds = diaries.map((d) => d.id).filter((id) => !(id in diaryDestMap.value))
  if (!missIds.length) return
  const entries = await Promise.all(
    missIds.map(async (id) => {
      try {
        const detail = await apiDiaryDetail(id)
        return {
          id,
          destinations: detail.destinations ?? [],
          nickname: detail.creatorNickname ?? '',
        } as const
      } catch {
        return {
          id,
          destinations: [] as number[],
          nickname: '',
        } as const
      }
    }),
  )
  diaryDestMap.value = {
    ...diaryDestMap.value,
    ...Object.fromEntries(entries.map((x) => [x.id, x.destinations])),
  }
  diaryCreatorNicknameMap.value = {
    ...diaryCreatorNicknameMap.value,
    ...Object.fromEntries(entries.map((x) => [x.id, x.nickname])),
  }
}

async function selectChip(chipId: string) {
  activeChipId.value = chipId
  if (chipId === RECOMMEND_CHIP_ID) {
    if (!fromSearch.value && !list.value.length) await load()
    return
  }
  if (!baseItems.value.length) await load()
}

async function loadInterestChips() {
  if (!auth.isAuthed) {
    interestRows.value = []
    return
  }
  try {
    const items = await apiGetInterest()
    interestRows.value = (items ?? [])
      .map((i) => ({
        type: normalizeInterestKey(i.type || ''),
        weight: Number(i.weight ?? 1),
      }))
      .filter((x) => x.type && !isExcludedTagPickerKey(x.type) && Number.isFinite(x.weight))
  } catch {
    interestRows.value = (auth.user?.interests ?? [])
      .map((t) => ({ type: normalizeInterestKey(t || ''), weight: 1 }))
      .filter((x) => x.type && !isExcludedTagPickerKey(x.type))
  }
}

onMounted(async () => {
  await loadInterestChips()
  const rq = route.query
  if (rq.lm === 's') {
    activeChipId.value =
      typeof rq.chip === 'string' && rq.chip ? rq.chip : RECOMMEND_CHIP_ID
    sortBy.value = rq.sort === 'rating' ? 'rating' : 'heat'
    searchQuery.keyword = typeof rq.kw === 'string' ? rq.kw : ''
    if (typeof rq.dst === 'string' && rq.dst) {
      const d = Number(rq.dst)
      searchQuery.destination = Number.isFinite(d) ? d : undefined
    } else {
      searchQuery.destination = undefined
    }
    confirmedSearchLabel.value = typeof rq.lbl === 'string' ? rq.lbl : ''
    searchInput.value =
      typeof rq.inp === 'string' && rq.inp
        ? rq.inp
        : searchQuery.keyword || (confirmedSearchLabel.value.startsWith('目的地：') ? confirmedSearchLabel.value.slice(5) : '')
    fromSearch.value = true
    await runSearch()
  } else if (rq.lm === 'b' || typeof rq.chip === 'string' || rq.sort === 'rating' || rq.sort === 'heat') {
    if (typeof rq.chip === 'string' && rq.chip) activeChipId.value = rq.chip
    sortBy.value = rq.sort === 'rating' ? 'rating' : 'heat'
    await load()
  } else {
    await load()
  }
  if (Object.keys(rq).length) {
    await router.replace({ path: '/diary' })
  }
})

watch(
  () => auth.isAuthed,
  async (authed) => {
    await loadInterestChips()
    if (!authed) {
      activeChipId.value = RECOMMEND_CHIP_ID
    }
  },
)

watch(
  () => auth.user?.interests,
  () => {
    void loadInterestChips()
  },
  { deep: true },
)

watch(searchInput, (value) => {
  const q = value.trim()

  if (selectedDestination.value && q !== selectedDestination.value.name) {
    selectedDestination.value = null
  }

  if (suggestTimer) {
    clearTimeout(suggestTimer)
    suggestTimer = null
  }

  if (!q) {
    suggestionList.value = []
    suggestionOpen.value = false
    suggestionLoading.value = false
    return
  }

  const seq = ++suggestSeq
  suggestTimer = setTimeout(() => {
    void fetchSearchSuggestions(q, seq)
  }, 180)
})
</script>

<template>
  <div class="page diary-list-page">
    <div class="diary-feed-shell">
      <div class="hdr">
        <div class="tag-row">
          <button
            v-for="chip in diaryFilterChips"
            :key="chip.id"
            class="tag-btn"
            :class="{ active: activeChipId === chip.id }"
            type="button"
            @click="selectChip(chip.id)"
          >
            {{ chip.label }}
          </button>
        </div>
        <div class="hdr-actions">
          <el-dropdown trigger="click" @command="onSortCommand">
            <el-button type="primary" plain class="hdr-btn sort-btn">{{ sortLabel }}</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="heat">热度排序</el-dropdown-item>
                <el-dropdown-item command="rating">评分排序</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="primary" class="hdr-btn plus-btn" :disabled="!auth.isAuthed" @click="$router.push('/diary/new')">
            +
          </el-button>
        </div>
      </div>

      <div class="search-engine-wrap">
        <div class="search-input-row">
          <el-input
            v-model="searchInput"
            clearable
            class="search-input"
            placeholder="搜索标题、正文或目的地（统一输入）"
            @focus="onSearchFocus"
            @blur="onSearchBlur"
            @keyup.enter="confirmSearch"
            @clear="onSearchClear"
          />
          <el-button type="primary" plain class="hdr-btn search-btn" @click="confirmSearch">确认搜索</el-button>
        </div>

        <div v-if="suggestionOpen" class="suggestion-panel">
          <div v-if="suggestionLoading" class="suggestion-status muted">正在检索...</div>

          <template v-else-if="suggestionList.length">
            <div class="suggestion-status muted">点击任意结果可直接进入日记详情</div>
            <button
              v-for="item in suggestionList"
              :key="item.id"
              class="suggestion-item"
              type="button"
              @mousedown.prevent="applySuggestion(item)"
            >
              <div class="suggestion-title">{{ item.label }}</div>
              <div class="suggestion-meta">{{ item.subLabel }}</div>
            </button>
          </template>

          <div v-else class="suggestion-status muted">暂无匹配项，按回车可直接按关键词搜索</div>
        </div>
      </div>

      <div v-if="fromSearch" class="search-hint">
        <span class="muted">当前结果：{{ confirmedSearchLabel || '搜索结果' }}</span>
        <el-button text type="primary" @click="showAll">查看全部</el-button>
      </div>

      <div class="feed lookbook-grid" v-loading="loading">
        <article
          v-for="(row, index) in displayItems"
          :key="row.id"
          class="lookbook-card diary-card ui-interactive-card animate-fade-in-up"
          :style="{ animationDelay: `${index * 0.08}s` }"
          @click="openDiaryDetail(row)"
        >
          <div class="lookbook-media ui-card-media">
            <img v-if="firstImage(row)" :src="firstImage(row)!" :alt="row.title" class="lookbook-img" />
            <div v-else class="cover-placeholder">无图</div>
            <div class="lookbook-overlay" aria-hidden="true" />
            <div class="lookbook-caption">
              <span class="lookbook-title">{{ row.title }}</span>
              <span class="lookbook-badge">{{ diaryHeatBadge(row) }}</span>
            </div>
          </div>
          <div class="lookbook-body">
            <div class="lookbook-meta">
              <span class="lookbook-dest">{{ diaryScenicLabel(row) }}</span>
              <span v-if="formatDiaryDate(row)" class="lookbook-date">{{ formatDiaryDate(row) }}</span>
            </div>
            <div v-if="diaryScenicTags(row).length" class="lookbook-tags">
              <span v-for="tag in diaryScenicTags(row)" :key="`${row.id}-${tag}`" class="lookbook-tag">
                {{ tag }}
              </span>
            </div>
          </div>
        </article>

        <div v-if="!displayItems.length && !loading" class="empty muted">暂无日记</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.diary-list-page {
  max-width: none !important;
  margin: 0 !important;
  padding: 0 10px 18px;
}

.hdr {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  position: sticky;
  top: 0;
  z-index: 20;
  margin: -4px -8px 14px;
  padding: 12px 8px;
  background: var(--glass-sticky);
  border: 1px solid var(--glass-border-faint);
  border-radius: 8px;
  backdrop-filter: blur(var(--glass-sticky-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-sticky-blur)) saturate(var(--glass-saturate));
}

.diary-feed-shell {
  background: transparent;
}

.tag-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.tag-row::-webkit-scrollbar {
  display: none;
}

.tag-btn {
  border: none;
  background: transparent;
  padding: 6px 12px;
  border-radius: 999px;
  color: var(--text-secondary, #756b59);
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    transform 0.15s ease;
}

.tag-btn:hover:not(.active) {
  background: var(--glass-subtle);
}

.tag-btn.active {
  background: var(--accent);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 2px 10px rgba(22, 66, 60, 0.25);
}

.hdr-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.hdr-btn {
  min-width: 62px;
  height: 34px;
}

.search-btn {
  min-width: 84px;
}

.sort-btn {
  min-width: 70px;
}

.plus-btn {
  min-width: 34px;
  padding: 0 10px;
  font-size: 18px;
  font-weight: 700;
}

.search-engine-wrap {
  position: relative;
  margin-bottom: 12px;
}

.search-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-input {
  flex: 1;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 999px;
  height: 36px;
}

.suggestion-panel {
  position: absolute;
  left: 0;
  right: 94px;
  top: calc(100% + 8px);
  z-index: 40;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--glass-border-soft);
  border-radius: 12px;
  background: var(--glass-card);
  backdrop-filter: blur(14px) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(14px) saturate(var(--glass-saturate));
  box-shadow: var(--shadow-sm);
}

.suggestion-item {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.suggestion-item:last-child {
  border-bottom: none;
}

.suggestion-item:hover {
  background: rgba(255, 255, 255, 0.45);
}

.suggestion-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #2d2618);
}

.suggestion-meta {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(58, 51, 40, 0.65);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.suggestion-status {
  padding: 10px 12px;
  font-size: 13px;
}

@media (max-width: 860px) {
  .search-input-row {
    gap: 8px;
  }

  .suggestion-panel {
    right: 0;
    top: calc(100% + 6px);
  }
}

.search-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 13px;
}

.feed.lookbook-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  max-width: none;
  margin: 0;
  min-height: 120px;
}

@media (max-width: 1120px) {
  .feed.lookbook-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .feed.lookbook-grid {
    grid-template-columns: 1fr;
  }
}

.lookbook-card.diary-card {
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  background: var(--glass-card);
  border: 1px solid var(--glass-border-soft);
  backdrop-filter: blur(var(--glass-card-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-card-blur)) saturate(var(--glass-saturate));
}

.lookbook-media {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: rgba(0, 0, 0, 0.18);
  overflow: hidden;
}

.lookbook-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.55);
  background: rgba(22, 66, 60, 0.25);
}

.lookbook-overlay {
  position: absolute;
  inset: auto 0 0;
  height: 5rem;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.38), transparent);
  pointer-events: none;
}

.lookbook-caption {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
  z-index: 1;
}

.lookbook-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  line-height: 1.35;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.35);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.lookbook-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 700;
  color: var(--text-primary, #1a1a18);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  padding: 4px 8px;
  border-radius: 999px;
}

.lookbook-body {
  padding: 12px 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.lookbook-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
}

.lookbook-dest {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  color: var(--text-primary, #1a1a18);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lookbook-date {
  flex-shrink: 0;
  color: var(--text-secondary, rgba(58, 51, 40, 0.65));
  font-size: 11px;
}

.lookbook-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.lookbook-tag {
  font-size: 10px;
  line-height: 1.2;
  color: var(--text-secondary, #6b6860);
  background: rgba(22, 66, 60, 0.08);
  padding: 3px 8px;
  border-radius: 999px;
}

.empty {
  text-align: center;
  padding: 32px 12px;
  font-size: 14px;
}

.muted {
  color: rgba(58, 51, 40, 0.65);
}
</style>
