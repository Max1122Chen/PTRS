<script setup lang="ts">
import type { CommentVO } from '../lib/api'

defineProps<{
  comments?: CommentVO[]
  emptyText?: string
}>()

function formatTime(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div class="comment-section">
    <div class="comment-title">评论（{{ comments?.length ?? 0 }}）</div>

    <div v-if="!comments?.length" class="muted empty">
      {{ emptyText || '暂无评论，快来抢沙发吧' }}
    </div>

    <div v-else class="comment-list">
      <div v-for="item in comments" :key="item.id" class="comment-item glass">
        <div class="comment-head">
          <span class="nickname">{{ item.userNickname || '游客' }}</span>
          <el-rate
            v-if="item.rating != null"
            :model-value="item.rating"
            disabled
            show-score
            score-template="{value}"
            size="small"
          />
          <span class="time muted">{{ formatTime(item.createTime) }}</span>
        </div>
        <div v-if="item.content?.trim()" class="comment-body">{{ item.content }}</div>
        <div v-else class="comment-body muted">未填写文字评价</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment-section {
  margin-top: 4px;
}
.comment-title {
  font-weight: 900;
  margin-bottom: 12px;
}
.empty {
  font-size: 13px;
}
.comment-list {
  display: grid;
  gap: 10px;
}
.comment-item {
  padding: 12px 14px;
  border-radius: 12px;
}
.comment-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.nickname {
  font-weight: 700;
  font-size: 13px;
}
.time {
  margin-left: auto;
  font-size: 12px;
}
.comment-body {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
