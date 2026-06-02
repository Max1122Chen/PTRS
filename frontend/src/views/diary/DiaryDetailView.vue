<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  apiDiaryAnimationCancel,
  apiDiaryAnimationGenerate,
  apiDiaryAnimationJob,
  apiDiaryDelete,
  apiDiaryDetail,
  apiDiaryRate,
  apiScenicDetail,
  type AnimationGeneratePayload,
  type AnimationGenerationParamsVO,
  type DiaryAnimationJobStatus,
  type DiaryDetailVO,
} from '../../lib/api'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const diary = ref<DiaryDetailVO | null>(null)
const destinationLine = ref('')

const rate = reactive({ rating: 5 })

const animLoading = ref(false)
const animHint = ref('')
/** 多行：阶段 / 通道 / 任务号 / 轮询次数 / 后端最新说明 */
const animProgressDetail = ref('')
const animJobId = ref<string | null>(null)
const animLastStatus = ref<DiaryAnimationJobStatus | null>(null)
/** 与轮询同步，任务结束后仍保留便于查看即梦堆栈 */
const animEventLog = ref<string[]>([])
/** POST /generate 返回的快照，便于未轮询前展示本次参数（选项 4.B） */
const animSubmittedGenParams = ref<AnimationGenerationParamsVO | null>(null)
/** 默认展开「完整事件日志」，即梦阶段可立刻看到进度行 */
const animCollapseActive = ref<string[]>(['log'])
const animOpts = reactive({
  aspectRatio: '16:9',
  style: 'documentary',
  durationSec: 8,
  extraPrompt: '',
})

/** 用户点击「停止任务」后尽快结束轮询 */
const animAbortPoll = ref(false)

function goDiaryList() {
  // 与列表页 replace+push 配合：返回上一历史项即带筛选/搜索状态的 /diary?...
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/diary')
}

async function cancelAnimationTask() {
  const id = animJobId.value
  if (!id) return
  animAbortPoll.value = true
  try {
    await apiDiaryAnimationCancel(id)
    ElMessage.success('已请求停止')
    try {
      const st = await apiDiaryAnimationJob(id)
      animLastStatus.value = st
      animEventLog.value = st.eventLog ?? []
      animProgressDetail.value = formatAnimProgress(st)
    } catch {
      /* ignore */
    }
  } catch {
    /* http 拦截器 */
  } finally {
    animLoading.value = false
  }
}

function formatAnimProgress(st: DiaryAnimationJobStatus) {
  const lines: string[] = []
  if (st.stage) lines.push(`阶段：${st.stage}`)
  if (st.provider) lines.push(`通道：${st.provider}`)
  if (st.externalRef) lines.push(`云端任务标识：${st.externalRef}`)
  if (st.jimengPollCount != null && st.jimengPollCount > 0)
    lines.push(`即梦进度查询：已第 ${st.jimengPollCount} 次`)
  if (st.libtvPollCount != null && st.libtvPollCount > 0)
    lines.push(`LibTV 会话轮询：已第 ${st.libtvPollCount} 次`)
  if (st.message) lines.push(`说明：${st.message}`)
  return lines.join('\n')
}

function buildAnimPayload(): AnimationGeneratePayload {
  const p: AnimationGeneratePayload = {
    aspectRatio: animOpts.aspectRatio,
    style: animOpts.style,
    durationSec: animOpts.durationSec,
  }
  const ex = animOpts.extraPrompt?.trim()
  if (ex) p.extraPrompt = ex
  return p
}

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

function isDiaryOwner() {
  return auth.isAuthed && auth.user?.id != null && diary.value?.userId === auth.user.id
}

async function deleteDiary() {
  if (!diary.value?.id) return
  await ElMessageBox.confirm('确认删除该日记？此操作不可恢复。', '警告', { type: 'warning' })
  await apiDiaryDelete(diary.value.id)
  ElMessage.success('已删除')
  goDiaryList()
}

async function generateAnimation() {
  if (!diary.value?.id) return
  animLoading.value = true
  animHint.value = '已提交，后端正在连接服务商…'
  animProgressDetail.value = ''
  animJobId.value = null
  animLastStatus.value = null
  animEventLog.value = []
  animSubmittedGenParams.value = null
  animCollapseActive.value = ['log']
  animAbortPoll.value = false
  try {
    const data = await apiDiaryAnimationGenerate(diary.value.id, buildAnimPayload())
    const jobId = data.jobId
    animSubmittedGenParams.value = data.generationParams ?? null
    animJobId.value = jobId
    animHint.value = '生成耗时取决于云端队列，可浏览其他页面；稍后回到本页刷新即可查看成片。'
    const deadline = Date.now() + 25 * 60 * 1000
    let delayMs = 600
    while (Date.now() < deadline) {
      if (animAbortPoll.value) {
        animAbortPoll.value = false
        animHint.value = ''
        animJobId.value = null
        return
      }
      await new Promise((r) => setTimeout(r, delayMs))
      delayMs = Math.min(4500, delayMs + 400)
      const st = await apiDiaryAnimationJob(jobId)
      animLastStatus.value = st
      animEventLog.value = st.eventLog ?? []
      animProgressDetail.value = formatAnimProgress(st)
      if (st.status === 'SUCCEEDED') {
        ElMessage.success('动画已生成')
        animHint.value = ''
        animProgressDetail.value = ''
        animJobId.value = null
        await load()
        return
      }
      if (st.status === 'FAILED') {
        ElMessage.error(st.message || '生成失败')
        animHint.value = ''
        animProgressDetail.value = ''
        animJobId.value = null
        return
      }
      if (st.status === 'CANCELLED') {
        ElMessage.info('任务已取消')
        animHint.value = ''
        animJobId.value = null
        return
      }
    }
    ElMessage.warning('本页等待超时；若后端仍在跑，可稍后刷新本页查看是否已出现「旅游动画」区块。')
    animHint.value = ''
    animProgressDetail.value = ''
  } catch {
    animHint.value = ''
    animProgressDetail.value = ''
    animJobId.value = null
  } finally {
    animLoading.value = false
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
        <div class="detail-header">
          <el-button link type="primary" class="back-nav" @click="goDiaryList">← 日记列表</el-button>
          <div class="detail-header-main">
            <div style="font-weight: 900">{{ diary?.title || '日记详情' }}</div>
            <div class="muted" style="font-size: 13px">
              热度 {{ diary?.heat ?? 0 }} · 评分 {{ diary?.rating ?? 0 }}
            </div>
          </div>
          <div v-if="isDiaryOwner()" class="detail-header-actions">
            <el-button size="small" @click="router.push(`/diary/${diary?.id}/edit`)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteDiary">删除</el-button>
          </div>
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

        <div v-if="diary?.animationUrl" class="gallery" style="flex-direction: column; align-items: stretch">
          <div class="muted" style="margin-bottom: 6px">旅游动画（AIGC）</div>
          <video :src="diary.animationUrl" controls class="video-player" />
          <a :href="diary.animationUrl" target="_blank" class="link">{{ diary.animationUrl }}</a>
        </div>

        <div v-if="isDiaryOwner()" style="margin-top: 14px">
          <div class="anim-opts">
            <span class="muted" style="font-size: 13px">生成参数（留空项由后端默认）</span>
            <div class="anim-opts-grid">
              <div class="anim-field">
                <span class="lbl">比例</span>
                <el-select v-model="animOpts.aspectRatio" style="width: 140px">
                  <el-option label="横屏 16:9" value="16:9" />
                  <el-option label="竖屏 9:16" value="9:16" />
                  <el-option label="方形 1:1" value="1:1" />
                </el-select>
              </div>
              <div class="anim-field">
                <span class="lbl">风格</span>
                <el-select v-model="animOpts.style" style="width: 140px">
                  <el-option label="写实纪实" value="documentary" />
                  <el-option label="电影感" value="cinematic" />
                  <el-option label="清新治愈" value="fresh" />
                  <el-option label="动漫" value="anime" />
                </el-select>
              </div>
              <div class="anim-field">
                <span class="lbl">时长(秒)</span>
                <el-input-number v-model="animOpts.durationSec" :min="3" :max="120" :step="1" />
              </div>
              <div class="anim-field anim-span2">
                <span class="lbl">额外提示</span>
                <el-input v-model="animOpts.extraPrompt" type="textarea" :rows="2" placeholder="可选；例如镜头慢一点、突出夜景" />
              </div>
            </div>
          </div>
          <div v-if="animLoading && animSubmittedGenParams" class="anim-submitted-params glass" style="margin-top: 12px">
            <div class="muted" style="margin-bottom: 8px; font-size: 13px">本次任务参数（提交快照）</div>
            <div style="font-size: 13px; line-height: 1.6">
              <div>比例 {{ animSubmittedGenParams.aspectRatio }} · 风格 {{ animSubmittedGenParams.styleLabel }}</div>
              <div>时长约 {{ animSubmittedGenParams.durationSec }} 秒</div>
              <div v-if="animSubmittedGenParams.extraPrompt?.trim()" class="muted">
                额外：{{ animSubmittedGenParams.extraPrompt }}
              </div>
            </div>
          </div>
          <el-button type="primary" plain :loading="animLoading" @click="generateAnimation">
            生成 / 重新生成旅游动画
          </el-button>
          <el-button
            v-if="animLoading && animJobId"
            type="danger"
            plain
            @click="cancelAnimationTask"
          >
            停止任务
          </el-button>
          <span v-if="animHint" class="muted" style="margin-left: 10px; font-size: 13px">{{ animHint }}</span>
          <el-alert
            v-if="animLoading && animProgressDetail"
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 12px; white-space: pre-wrap; text-align: left"
            :title="'服务商进度（约每几秒更新）'"
            :description="animProgressDetail"
          />
          <el-collapse v-if="animEventLog.length > 0" v-model="animCollapseActive" style="margin-top: 12px">
            <el-collapse-item title="完整事件日志" name="log">
              <pre class="anim-event-log">{{ animEventLog.join('\n') }}</pre>
            </el-collapse-item>
          </el-collapse>
          <div class="muted" style="margin-top: 8px; font-size: 12px">
            由后端调用云端生成并保存到本站；耗时取决于服务商队列。
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
.detail-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}
.detail-header-main {
  flex: 1;
  min-width: 0;
}
.detail-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.back-nav {
  flex-shrink: 0;
  padding: 0 4px 0 0;
}
.content {
  padding: 6px 2px;
}
.anim-event-log {
  margin: 0;
  max-height: 360px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
  background: rgba(0, 0, 0, 0.04);
  padding: 10px;
  border-radius: 8px;
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

.anim-opts {
  margin-bottom: 12px;
}
.anim-opts-grid {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px 14px;
  align-items: center;
}
.anim-field {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.anim-field .lbl {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 52px;
}
.anim-span2 {
  grid-column: 1 / -1;
}
.anim-span2 .el-input {
  flex: 1;
}
.anim-chat-wrap {
  margin-top: 12px;
}
.anim-transcript {
  max-height: 280px;
  overflow: auto;
  padding: 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.04);
  margin-bottom: 10px;
}
.anim-bubble {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
}
.anim-bubble.asst {
  background: rgba(64, 158, 255, 0.12);
}
.anim-bubble.usr {
  background: rgba(103, 194, 58, 0.12);
}
.anim-role {
  font-size: 11px;
  opacity: 0.75;
  display: block;
  margin-bottom: 4px;
}
.anim-content {
  white-space: pre-wrap;
  word-break: break-word;
}
.anim-reply-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.anim-reply-row .el-button {
  flex-shrink: 0;
}
</style>

