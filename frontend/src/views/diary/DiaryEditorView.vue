<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  apiDiaryCreate,
  apiDiaryDetail,
  apiDiaryUploadAttachment,
  apiDiaryUpdate,
  apiScenicDetail,
  apiScenicSearchByKeyword,
  type DiaryAttachmentUploadResult,
  type ScenicArea,
} from '../../lib/api'
import type { UploadRequestOptions } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)

const isEdit = ref(false)
const diaryId = ref<number | null>(null)

const form = reactive({
  title: '',
  content: '',
})

const imageUrls = ref<string[]>([])
const videoUrls = ref<string[]>([])
const uploadPending = ref(0)
const destinationIds = ref<number[]>([])
const destinationOptions = ref<Array<{ id: number; name: string; location?: string }>>([])
const destinationLoading = ref(false)
let searchSeq = 0

const attachments = computed(() => {
  const images = imageUrls.value.map((url) => ({ url, mediaType: 'image' as const }))
  const videos = videoUrls.value.map((url) => ({ url, mediaType: 'video' as const }))
  return [...images, ...videos]
})

function parseJsonList(raw?: string) {
  if (!raw) return [] as string[]
  try {
    const list = JSON.parse(raw)
    if (!Array.isArray(list)) return []
    return list.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
  } catch {
    return []
  }
}

function pushAttachment(url: string, mediaType: DiaryAttachmentUploadResult['mediaType']) {
  if (!url) return
  const list = mediaType === 'video' ? videoUrls.value : imageUrls.value
  if (!list.includes(url)) {
    list.push(url)
  }
}

function removeAttachment(url: string, mediaType: 'image' | 'video') {
  if (mediaType === 'video') {
    videoUrls.value = videoUrls.value.filter((x) => x !== url)
    return
  }
  imageUrls.value = imageUrls.value.filter((x) => x !== url)
}

function beforeUpload(file: File) {
  const lowerName = file.name.toLowerCase()
  const imageOk = /\.(jpg|jpeg|png)$/.test(lowerName)
  const videoOk = /\.mp4$/.test(lowerName)
  if (!imageOk && !videoOk) {
    ElMessage.warning('仅支持 JPG/PNG/MP4 格式')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('单文件大小不能超过10MB')
    return false
  }
  return true
}

async function customUpload(options: UploadRequestOptions) {
  uploadPending.value += 1
  try {
    const file = options.file as File
    const data = await apiDiaryUploadAttachment(file)
    pushAttachment(data.url, data.mediaType)
    ElMessage.success(`${file.name} 上传成功`)
    options.onSuccess?.(data)
  } catch (err) {
    options.onError?.(err as any)
  } finally {
    uploadPending.value -= 1
  }
}

function upsertDestinationOption(item: { id: number; name: string; location?: string }) {
  if (!item.id || !item.name) return
  const idx = destinationOptions.value.findIndex((x) => x.id === item.id)
  if (idx >= 0) {
    destinationOptions.value[idx] = item
  } else {
    destinationOptions.value.push(item)
  }
}

function keepSelectedDestinationOptions() {
  if (destinationIds.value.length === 0) {
    destinationOptions.value = []
    return
  }
  const selectedSet = new Set(destinationIds.value)
  destinationOptions.value = destinationOptions.value.filter((x) => selectedSet.has(x.id))
}

function toDestinationOption(item: ScenicArea) {
  return {
    id: item.id,
    name: item.name,
    location: item.location,
  }
}

async function ensureDestinationOptionsByIds(ids: number[]) {
  const missed = ids.filter((id) => !destinationOptions.value.some((x) => x.id === id))
  if (missed.length === 0) return
  for (const id of missed) {
    try {
      const detail = await apiScenicDetail(id)
      upsertDestinationOption(toDestinationOption(detail))
    } catch {
      // 忽略单个目的地加载失败，避免影响整体编辑。
    }
  }
}

async function remoteSearchDestination(keyword: string) {
  const q = keyword.trim()
  if (!q) {
    searchSeq++
    destinationLoading.value = false
    keepSelectedDestinationOptions()
    return
  }
  const currentSeq = ++searchSeq
  destinationLoading.value = true
  try {
    keepSelectedDestinationOptions()
    const list = await apiScenicSearchByKeyword({ keyword: q, limit: 50 })
    if (currentSeq !== searchSeq) return
    list.forEach((item) => upsertDestinationOption(toDestinationOption(item)))
  } finally {
    if (currentSeq === searchSeq) destinationLoading.value = false
  }
}

async function loadForEdit(id: number) {
  loading.value = true
  try {
    const d = await apiDiaryDetail(id)
    form.title = d.title
    form.content = d.content
    imageUrls.value = parseJsonList(d.images)
    videoUrls.value = parseJsonList(d.videos)
    destinationIds.value = [...((d.destinations || []) as number[])]
    await ensureDestinationOptionsByIds(destinationIds.value)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.title || !form.content) {
    ElMessage.warning('请填写标题与正文')
    return
  }
  if (uploadPending.value > 0) {
    ElMessage.warning('附件仍在上传中，请稍后再提交')
    return
  }
  loading.value = true
  try {
    const images = [...imageUrls.value]
    const videos = [...videoUrls.value]
    const destinations = [...destinationIds.value]
    if (isEdit.value && diaryId.value) {
      await apiDiaryUpdate(diaryId.value, { title: form.title, content: form.content, images, videos, destinations })
      ElMessage.success('更新成功')
      router.push(`/diary/${diaryId.value}`)
      return
    }
    if (destinations.length === 0) {
      ElMessage.warning('请至少选择一个目的地')
      return
    }
    const data = await apiDiaryCreate({ title: form.title, content: form.content, images, videos, destinations })
    ElMessage.success('创建成功')
    router.push(`/diary/${data.diary_id}`)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const id = route.params.id ? Number(route.params.id) : null
  if (id) {
    isEdit.value = true
    diaryId.value = id
    loadForEdit(id)
  }
})
</script>

<template>
  <div class="page" v-loading="loading">
    <el-card class="editor-card" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <div style="font-weight: 900">{{ isEdit ? '编辑日记' : '新建日记' }}</div>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="写下你的旅途故事..." />
        </el-form-item>

        <div class="grid">
          <el-form-item label="目的地名称（必填，可多选）">
            <el-select
              v-model="destinationIds"
              multiple
              filterable
              remote
              clearable
              collapse-tags
              collapse-tags-tooltip
              :reserve-keyword="false"
              :remote-method="remoteSearchDestination"
              :loading="destinationLoading"
              @change="keepSelectedDestinationOptions"
              placeholder="输入景区名称关键字"
              style="width: 100%"
            >
              <el-option
                v-for="item in destinationOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              >
                <div style="display: flex; justify-content: space-between; gap: 10px">
                  <span>{{ item.name }}</span>
                  <span class="muted">{{ item.location || '—' }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
        </div>

        <div class="grid">
          <el-form-item label="附件上传（单区拖拽，支持 JPG/PNG/MP4，单文件≤10MB）">
            <el-upload
              drag
              multiple
              :show-file-list="false"
              :auto-upload="true"
              accept=".jpg,.jpeg,.png,.mp4"
              :before-upload="beforeUpload"
              :http-request="customUpload"
              style="width: 100%"
            >
              <el-icon style="font-size: 28px; margin-bottom: 8px"><UploadFilled /></el-icon>
              <div style="font-size: 14px; font-weight: 700">将附件拖到这里，或点击上传</div>
              <div class="muted" style="font-size: 12px; margin-top: 4px">
                上传后会自动识别为图片或视频，并转换为可回传 URL
              </div>
            </el-upload>

            <div v-if="uploadPending > 0" class="muted" style="margin-top: 8px">
              正在上传 {{ uploadPending }} 个文件...
            </div>

            <div v-if="attachments.length" class="attachment-wall">
              <div v-for="item in attachments" :key="item.url" class="attachment-item">
                <div class="attachment-head">
                  <el-tag size="small" :type="item.mediaType === 'video' ? 'warning' : 'success'">
                    {{ item.mediaType === 'video' ? '视频' : '图片' }}
                  </el-tag>
                  <el-button link type="danger" @click="removeAttachment(item.url, item.mediaType)">
                    移除
                  </el-button>
                </div>

                <el-image
                  v-if="item.mediaType === 'image'"
                  :src="item.url"
                  fit="cover"
                  style="width: 100%; height: 160px; border-radius: 10px"
                  :preview-src-list="[item.url]"
                  preview-teleported
                />

                <video v-else :src="item.url" controls style="width: 100%; max-height: 200px; border-radius: 10px" />

                <a :href="item.url" target="_blank" class="link">{{ item.url }}</a>
              </div>
            </div>
          </el-form-item>
        </div>

        <el-button type="primary" size="large" :loading="loading" @click="submit">
          {{ isEdit ? '保存修改' : '发布日记' }}
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.editor-card {
  background: transparent;
  border: none;
}

.editor-card :deep(.el-card__header),
.editor-card :deep(.el-card__body) {
  background: transparent;
}

.grid {
  display: block;
}

.attachment-wall {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.attachment-item {
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(6px);
}

.attachment-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.link {
  display: block;
  margin-top: 8px;
  color: var(--accent-main);
  text-decoration: none;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 860px) {
  .attachment-wall {
    grid-template-columns: 1fr;
  }
}
</style>

