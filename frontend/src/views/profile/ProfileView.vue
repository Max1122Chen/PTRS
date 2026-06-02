<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance, UploadRequestOptions } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { apiGetInterest, apiGetProfile, apiRefresh, apiTagsList, apiUpdateInterest, apiUploadAvatar } from '../../lib/api'
import { useAiConfigStore } from '../../stores/aiConfig'
import UserAvatar from '../../components/UserAvatar.vue'
import {
  COMMON_INTEREST_KEYS,
  interestLabelZh,
  isExcludedTagPickerKey,
  normalizeInterestKey,
  roundTwo,
} from '../../lib/interestTags'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const aiConfig = useAiConfigStore()
const router = useRouter()
const loading = ref(false)
const avatarUploading = ref(false)
const aiSaving = ref(false)
const avatarUploadRef = ref<UploadInstance>()

const interestItems = ref<InterestInput[]>([])
const chartEl = ref<HTMLDivElement | null>(null)
let interestChart: echarts.ECharts | null = null
/** 来自 GET /api/tags，与首页一致；失败时用 COMMON_INTEREST_KEYS 并排除「普通景区」等 */
const catalogTagKeys = ref<string[]>([...COMMON_INTEREST_KEYS].filter((k) => !isExcludedTagPickerKey(k)))
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

function beforeAvatarUpload(file: File) {
  const lower = file.name.toLowerCase()
  if (!/\.(jpg|jpeg|png)$/.test(lower)) {
    ElMessage.warning('头像仅支持 JPG/PNG')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像大小不能超过 2MB')
    return false
  }
  return true
}

async function customAvatarUpload(options: UploadRequestOptions) {
  avatarUploading.value = true
  try {
    const file = options.file as File
    const user = await apiUploadAvatar(file)
    auth.patchUser({ avatar: user.avatar })
    ElMessage.success('头像已更新')
    options.onSuccess?.(user)
  } catch (err) {
    options.onError?.(err as any)
  } finally {
    avatarUploading.value = false
  }
}

async function loadProfile() {
  if (!auth.isAuthed) return
  try {
    const user = await apiGetProfile()
    if (user) auth.patchUser(user)
  } catch {
    // 使用本地缓存即可
  }
}

function saveAiForm() {
  if (!auth.user?.id) {
    ElMessage.warning('请先登录')
    return
  }
  if (!aiConfig.isComplete) {
    ElMessage.warning('请填写完整的 API 信息')
    return
  }
  aiSaving.value = true
  try {
    aiConfig.persistForUser(auth.user.id)
    ElMessage.success('API 配置已保存，About 页旅游助手将自动使用')
  } finally {
    aiSaving.value = false
  }
}

async function logout() {
  await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  aiConfig.resetSession()
  auth.clear()
  router.push('/home')
}

function triggerAvatarUpload() {
  const input = avatarUploadRef.value?.$el.querySelector('input[type="file"]') as HTMLInputElement | null
  input?.click()
}

function onAvatarMenu(command: string | number | object) {
  if (command === 'upload') {
    triggerAvatarUpload()
    return
  }
  if (command === 'logout') {
    void logout()
  }
}

watch(
  () => auth.user?.id,
  (id) => {
    if (id) aiConfig.ensureLoaded(id)
  },
  { immediate: true },
)

onMounted(() => {
  void loadProfile()
  loadInterests()
  void loadTagCatalog()
})
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

      <div class="avatar-section glass block">
        <div class="avatar-row">
          <UserAvatar :src="auth.user?.avatar" :size="88" />
          <div class="avatar-info">
            <p class="avatar-name">{{ auth.user?.username }}</p>
            <p class="avatar-id muted">ID {{ auth.user?.id ?? '—' }}</p>
          </div>
          <div class="avatar-actions">
            <el-dropdown trigger="click" @command="onAvatarMenu">
              <button type="button" class="settings-btn" aria-label="设置">
                <el-icon :size="20"><Setting /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="upload" :disabled="avatarUploading">
                    {{ avatarUploading ? '上传中…' : '上传头像' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
        <el-upload
          ref="avatarUploadRef"
          class="avatar-upload-hidden"
          :show-file-list="false"
          accept=".jpg,.jpeg,.png"
          :auto-upload="true"
          :before-upload="beforeAvatarUpload"
          :http-request="customAvatarUpload"
          :disabled="avatarUploading"
        >
          <span />
        </el-upload>
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
            <el-option v-for="tag in catalogTagKeys" :key="tag" :label="interestLabelZh(tag)" :value="tag" />
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

      <div class="glass block api-section">
        <div class="k">API 信息录入</div>
        <p class="muted api-hint">用于 About 页旅游助手对话，保存在本浏览器（按账号区分），无需每次重新填写。</p>
        <div class="api-row">
          <el-input v-model="aiConfig.endpoint" placeholder="模型接口地址（自行填写）" clearable />
          <el-input v-model="aiConfig.model" class="api-model" placeholder="模型名称（自行填写）" clearable />
        </div>
        <div class="api-row api-row--single">
          <el-input
            v-model="aiConfig.apiKey"
            type="password"
            show-password
            placeholder="输入 API Key"
            clearable
          />
        </div>
        <div class="actions">
          <el-button type="primary" :loading="aiSaving" @click="saveAiForm">保存 API 配置</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.avatar-section {
  margin-bottom: 12px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: nowrap;
}

.avatar-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.avatar-actions {
  flex-shrink: 0;
  margin-left: auto;
}

.settings-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 1px solid rgba(22, 66, 60, 0.22);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  color: #16423c;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    transform 0.15s ease,
    box-shadow 0.2s ease;
}

.settings-btn:hover {
  background: rgba(22, 66, 60, 0.08);
  border-color: rgba(22, 66, 60, 0.38);
  transform: rotate(18deg);
}

.settings-btn:focus-visible {
  outline: 2px solid rgba(22, 66, 60, 0.35);
  outline-offset: 2px;
}

.avatar-upload-hidden {
  display: none;
}

.avatar-name {
  font-size: 18px;
  font-weight: 800;
  margin: 0;
}

.avatar-id {
  margin: 4px 0 0;
  font-size: 13px;
}

.block {
  padding: 14px;
}
.k {
  font-size: 12px;
  color: var(--text-2);
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

.api-section {
  margin-top: 12px;
}

.api-hint {
  margin: 8px 0 12px;
  font-size: 12px;
  line-height: 1.5;
}

.api-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.api-row .el-input {
  flex: 1;
  min-width: 200px;
}

.api-model {
  max-width: 280px;
}

@media (max-width: 720px) {
  .api-model {
    max-width: none;
  }
}
</style>

