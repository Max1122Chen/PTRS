<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { apiGetInterest, apiRefresh, apiUpdateInterest } from '../../lib/api'
import { COMMON_INTEREST_KEYS, interestLabelZh, normalizeInterestKey, roundTwo } from '../../lib/interestTags'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const interestItems = ref<InterestInput[]>([])
const chartEl = ref<HTMLDivElement | null>(null)
let interestChart: echarts.ECharts | null = null
const commonTags = COMMON_INTEREST_KEYS
const addTag = ref('')

type InterestInput = { type: string; weight?: number }

const chartStat = reactive({
  total: 0,
  dominant: '-',
})

function normalizeInterests(items: InterestInput[]): InterestInput[] {
  const cleaned: InterestInput[] = []
  for (const item of items) {
    const type = normalizeInterestKey(item.type || '')
    if (!type) continue
    const weight = roundTwo(Number(item.weight ?? 1))
    if (!Number.isFinite(weight) || weight <= 0 || weight > 5) {
      throw new Error(`兴趣权重非法（${type}:${item.weight}），请将权重设置在 (0,5]`)
    }
    cleaned.push({ type, weight })
  }
  return cleaned
}

async function saveInterest() {
  loading.value = true
  try {
    const payload = normalizeInterests(interestItems.value)
    if (!payload.length) {
      ElMessage.warning('请至少保留一个兴趣项')
      return
    }
    await apiUpdateInterest({
      interests: payload,
    })
    ElMessage.success('兴趣已更新')
    if (auth.user) auth.user.interests = payload.map((i) => i.type)
    interestItems.value = payload.map((i) => ({
      type: interestLabelZh(i.type),
      weight: i.weight,
    }))
    await renderInterestChart(interestItems.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '兴趣保存失败')
  } finally {
    loading.value = false
  }
}

async function refreshToken() {
  if (!auth.token) return
  const data = await apiRefresh(auth.token)
  auth.setAuth(data.token, auth.user!)
  ElMessage.success('令牌已刷新')
}

async function loadInterests() {
  if (!auth.isAuthed) return
  try {
    const items = await apiGetInterest()
    interestItems.value = (items ?? []).map((item) => ({
      type: interestLabelZh(item.type),
      weight: roundTwo(item.weight),
    }))
    await renderInterestChart(interestItems.value)
    if (auth.user) {
      auth.user.interests = (items ?? []).map((item) => item.type)
    }
  } catch {
    // 回显失败不阻塞页面渲染，保留本地已有展示
  }
}

function addInterest(type = '') {
  interestItems.value.push({ type, weight: 1.0 })
}

function removeInterest(index: number) {
  if (interestItems.value.length <= 1) {
    ElMessage.warning('至少保留一个兴趣项')
    return
  }
  interestItems.value.splice(index, 1)
}

function quickAddTag(tag: string) {
  const target = normalizeInterestKey(tag)
  const exists = interestItems.value.some((item) => normalizeInterestKey(item.type) === target)
  if (exists) {
    ElMessage.info(`${interestLabelZh(tag)} 已在兴趣列表中`)
    return
  }
  addInterest(interestLabelZh(tag))
}

function addCustomTag() {
  const tag = addTag.value.trim()
  if (!tag) return
  quickAddTag(tag)
  addTag.value = ''
}

async function renderInterestChart(items: InterestInput[]) {
  await nextTick()
  if (!chartEl.value) return

  const normalized = normalizeInterests(items)
  if (!interestChart) {
    interestChart = echarts.init(chartEl.value)
  }

  const total = normalized.reduce((sum, item) => sum + Number(item.weight ?? 0), 0)
  chartStat.total = total
  chartStat.dominant = normalized.length
    ? interestLabelZh([...normalized].sort((a, b) => Number(b.weight ?? 0) - Number(a.weight ?? 0))[0].type)
    : '-'

  interestChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: 'var(--text-2)' } },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: true,
        label: { show: true, formatter: '{b}\n{d}%' },
        data: normalized.map((item) => ({
          name: interestLabelZh(item.type),
          value: Number(item.weight ?? 0),
        })),
      },
    ],
  })
}

watch(
  interestItems,
  async (val) => {
    if (!val.length) return
    try {
      await renderInterestChart(val)
    } catch {
      // ignore live rendering failures caused by temporary invalid edits
    }
  },
  { deep: true },
)

onBeforeUnmount(() => {
  if (interestChart) {
    interestChart.dispose()
    interestChart = null
  }
})

onMounted(loadInterests)
</script>

<template>
  <div class="page">
    <el-card class="glass" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <div style="font-weight: 800">个人中心</div>
          <el-tag effect="plain">{{ auth.user?.role || 'USER' }}</el-tag>
        </div>
      </template>

      <div class="grid">
        <div class="glass block">
          <div class="k">用户名</div>
          <div class="v">{{ auth.user?.username }}</div>
        </div>
        <div class="glass block">
          <div class="k">用户ID</div>
          <div class="v">{{ auth.user?.id }}</div>
        </div>
      </div>

      <div class="glass block" style="margin-top: 12px">
        <div class="k">兴趣权重配置</div>

        <div class="interestRows">
          <div v-for="(item, index) in interestItems" :key="`${index}-${item.type}`" class="interestRow">
            <el-input v-model="item.type" placeholder="兴趣标签（如：nature）" style="max-width: 220px" />
            <el-slider v-model="item.weight" :min="0.1" :max="5" :step="0.01" style="flex: 1; min-width: 180px" />
            <el-input-number v-model="item.weight" :min="0.1" :max="5" :step="0.01" :precision="2" :controls="false" />
            <el-button text type="danger" @click="removeInterest(index)">删除</el-button>
          </div>
        </div>

        <div class="addActions">
          <el-select v-model="addTag" filterable clearable placeholder="快速添加常用标签" style="max-width: 220px">
            <el-option v-for="tag in commonTags" :key="tag" :label="interestLabelZh(tag)" :value="tag" />
          </el-select>
          <el-button @click="addCustomTag">添加标签</el-button>
          <el-button @click="addInterest()">新增空白兴趣</el-button>
        </div>

        <div class="chartBox">
          <div class="chartHeader">
            <span>兴趣分布图</span>
            <span class="muted">总权重 {{ chartStat.total.toFixed(2) }}，主兴趣 {{ chartStat.dominant }}</span>
          </div>
          <div ref="chartEl" class="pieChart" />
        </div>

        <div class="actions">
          <el-button type="primary" :loading="loading" @click="saveInterest">保存兴趣</el-button>
          <el-button @click="refreshToken">刷新 Token</el-button>
        </div>
        <div class="hint muted">
          建议：按你常看的内容配置 3-6 个兴趣，权重越高代表偏好越强，范围 <code>(0,5]</code>。
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.block {
  padding: 14px;
}
.k {
  font-size: 12px;
  color: var(--text-2);
}
.v {
  font-size: 16px;
  font-weight: 800;
  margin-top: 6px;
}
.actions {
  display: flex;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.interestRows {
  margin-top: 10px;
  display: grid;
  gap: 10px;
}

.interestRow {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.addActions {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.chartBox {
  margin-top: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 10px;
}

.chartHeader {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.pieChart {
  margin-top: 8px;
  height: 280px;
}

.hint {
  margin-top: 8px;
  font-size: 12px;
}
@media (max-width: 720px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>

