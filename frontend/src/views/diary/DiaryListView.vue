<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  apiDiaryDelete,
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
const router = useRouter()
const loading = ref(false)

const listQuery = reactive({ page: 1, size: 50, sortBy: 'heat' })
const list = ref<Diary[]>([])
const diaryDestMap = ref<Record<number, number[]>>({})
const diaryCreatorNicknameMap = ref<Record<number, string>>({})
const scenicTagMap = ref<Record<number, string[]>>({})

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

function applySuggestion(item: SearchSuggestion) {
  suggestionOpen.value = false
  void router.push(`/diary/${item.diaryId}`)
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
  const map: Record<number, string[]> = {}
  for (const item of res.list || []) {
    map[item.id] = Array.isArray(item.tags) ? item.tags : []
  }
  scenicTagMap.value = map
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

function canManage(row: Diary) {
  return auth.isAuthed && auth.user?.id === row.userId
}

async function del(row: Diary, e: Event) {
  e.stopPropagation()
  await ElMessageBox.confirm('确认删除该日记？此操作不可恢复。', '警告', { type: 'warning' })
  await apiDiaryDelete(row.id)
  if (fromSearch.value) await runSearch()
  else await load()
}

onMounted(async () => {
  await loadInterestChips()
  await load()
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

      <div class="feed" v-loading="loading">
        <div
          v-for="row in displayItems"
          :key="row.id"
          class="diary-card"
          @click="$router.push(`/diary/${row.id}`)"
        >
          <div class="cover">
            <img v-if="firstImage(row)" :src="firstImage(row)!" alt="" />
            <div v-else class="cover-placeholder">无图</div>
          </div>
          <div class="card-title">
            <div class="card-title-row">
              <div class="card-title-text">{{ row.title }}</div>
              <div v-if="diaryCreatorNicknameMap[row.id]" class="card-nickname">{{ diaryCreatorNicknameMap[row.id] }}</div>
            </div>
          </div>
          <div v-if="canManage(row)" class="card-actions" @click.stop>
            <el-button size="small" @click="$router.push(`/diary/${row.id}/edit`)">编辑</el-button>
            <el-button size="small" type="danger" @click="del(row, $event)">删除</el-button>
          </div>
        </div>

        <div v-if="!displayItems.length && !loading" class="empty muted">暂无日记</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.diary-list-page {
  max-width: none !important;
  margin: 0 !important;
  padding: 0 18px 18px;
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
  border-radius: 14px;
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
  padding: 6px 10px;
  border-radius: 999px;
  color: var(--text-secondary, #756b59);
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
}

.tag-btn.active {
  background: var(--glass-subtle);
  color: var(--text-primary, #2d2618);
  font-weight: 700;
  backdrop-filter: blur(var(--glass-subtle-blur));
  -webkit-backdrop-filter: blur(var(--glass-subtle-blur));
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

.feed {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  max-width: none;
  margin: 0;
  min-height: 120px;
}

@media (max-width: 1200px) {
  .feed {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .feed {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .feed {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .feed {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }
}

.diary-card {
  cursor: pointer;
  border-radius: 16px;
  overflow: hidden;
  background: var(--glass-card);
  border: 1px solid var(--glass-border-soft);
  backdrop-filter: blur(var(--glass-card-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-card-blur)) saturate(var(--glass-saturate));
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.diary-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}

.cover {
  width: 100%;
  aspect-ratio: 3 / 4;
  background: rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.cover img {
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
  color: rgba(0, 0, 0, 0.35);
}

.card-title {
  padding: 14px 16px 12px;
  font-weight: 700;
  font-size: 16px;
  line-height: 1.45;
  color: var(--text-primary, #1a1a18);
}

.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.card-title-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-nickname {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 12px;
  font-weight: 400;
  color: rgba(58, 51, 40, 0.65);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 45%;
}

.card-actions {
  padding: 0 16px 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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
