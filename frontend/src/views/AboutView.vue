<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

type ChatRole = 'user' | 'assistant'

type ChatMessage = {
  id: number
  role: ChatRole
  content: string
  time: string
}

type LocalChatResponse = {
  code: number
  message: string
  data?: {
    content?: string
    endpointUsed?: string
  }
}

const endpoint = ref('https://api.openai.com/v1/chat/completions')
const model = ref('gpt-4o-mini')
const apiKey = ref('')
const input = ref('')
const loading = ref(false)
const errorMsg = ref('')
const listEl = ref<HTMLElement | null>(null)
const messages = ref<ChatMessage[]>([
  {
    id: Date.now(),
    role: 'assistant',
    content: '你好，我是你的旅游助手。你可以问我行程规划、景点推荐、美食安排、预算建议等问题。',
    time: formatTime(new Date()),
  },
])

const canSend = computed(() => {
  return !loading.value && input.value.trim().length > 0 && apiKey.value.trim().length > 0
})

function formatTime(date: Date) {
  const hh = `${date.getHours()}`.padStart(2, '0')
  const mm = `${date.getMinutes()}`.padStart(2, '0')
  return `${hh}:${mm}`
}

function addMessage(role: ChatRole, content: string) {
  messages.value.push({
    id: Date.now() + Math.floor(Math.random() * 1000),
    role,
    content,
    time: formatTime(new Date()),
  })
  nextTick(() => {
    if (listEl.value) {
      listEl.value.scrollTop = listEl.value.scrollHeight
    }
  })
}

function clearChat() {
  messages.value = [
    {
      id: Date.now(),
      role: 'assistant',
      content: '会话已清空。你可以继续问我新的旅游问题。',
      time: formatTime(new Date()),
    },
  ]
  errorMsg.value = ''
}

async function sendMessage() {
  if (!canSend.value) return
  const userText = input.value.trim()
  input.value = ''
  errorMsg.value = ''
  addMessage('user', userText)
  loading.value = true
  try {
    const response = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        endpoint: endpoint.value.trim(),
        apiKey: apiKey.value.trim(),
        model: model.value.trim() || 'gpt-4o-mini',
        temperature: 0.7,
        messages: messages.value.map((item) => ({
          role: item.role,
          content: item.content,
        })),
      }),
    })

    const data = (await response.json()) as LocalChatResponse
    if (!response.ok || data.code !== 200) {
      throw new Error(data.message || `请求失败，状态码 ${response.status}`)
    }
    const answer = data.data?.content?.trim()
    addMessage('assistant', answer || '我暂时没有生成有效回复，请稍后重试。')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '请求失败，请检查网络或接口配置。'
    errorMsg.value = msg
    addMessage('assistant', '我暂时无法连接到模型服务，请检查 API Key、接口地址或网络后重试。')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page about">
    <h1 class="h1">About ExploreScape</h1>
    <p class="lead muted">
      旅游助手 Agent（会话不保存，仅当前页面有效）
    </p>
    <div class="chat-card">
      <div class="config-row">
        <input v-model="endpoint" class="cfg-input" placeholder="模型接口地址" />
        <input v-model="model" class="cfg-input model-input" placeholder="模型名称，如 gpt-4o-mini" />
      </div>
      <div class="config-row">
        <input
          v-model="apiKey"
          type="password"
          class="cfg-input"
          placeholder="输入 API Key（仅本页内存使用）"
        />
        <button class="ghost-btn" type="button" @click="clearChat">清空会话</button>
      </div>

      <div ref="listEl" class="chat-list">
        <div v-for="msg in messages" :key="msg.id" class="chat-item" :class="msg.role">
          <div class="avatar">{{ msg.role === 'user' ? '我' : '旅' }}</div>
          <div class="bubble-wrap">
            <div class="bubble">{{ msg.content }}</div>
            <div class="time">{{ msg.time }}</div>
          </div>
        </div>
        <div v-if="loading" class="typing">旅游助手正在思考中...</div>
      </div>

      <div class="input-row">
        <textarea
          v-model="input"
          class="chat-input"
          rows="3"
          placeholder="例如：帮我规划一个北京三日游，预算3000元"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <button class="send-btn" type="button" :disabled="!canSend" @click="sendMessage">
          发送
        </button>
      </div>
      <p v-if="errorMsg" class="error-text">{{ errorMsg }}</p>
    </div>
  </div>
</template>

<style scoped>
.about {
  max-width: 900px;
}
.h1 {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.5px;
  margin: 0 0 12px;
  color: #16423c;
}
.lead {
  font-size: 15px;
  line-height: 1.75;
  margin: 0 0 14px;
}

.chat-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-radius: 16px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.52);
  backdrop-filter: blur(12px);
}

.config-row {
  display: flex;
  gap: 8px;
}

.cfg-input {
  flex: 1;
  border: 1px solid rgba(22, 66, 60, 0.2);
  border-radius: 10px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.78);
  color: #1f2d2b;
  outline: none;
}

.model-input {
  max-width: 240px;
}

.ghost-btn {
  border: 1px solid rgba(22, 66, 60, 0.3);
  border-radius: 10px;
  padding: 0 14px;
  background: rgba(255, 255, 255, 0.7);
  color: #16423c;
  cursor: pointer;
}

.chat-list {
  height: 420px;
  overflow-y: auto;
  border-radius: 12px;
  padding: 10px;
  background: rgba(240, 245, 244, 0.9);
}

.chat-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}

.chat-item.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 12px;
  background: #16423c;
  flex-shrink: 0;
}

.chat-item.user .avatar {
  background: #3b8a4d;
}

.bubble-wrap {
  max-width: 75%;
}

.bubble {
  border-radius: 8px;
  padding: 10px 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fff;
  color: #1f2d2b;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
}

.chat-item.user .bubble {
  background: #95ec69;
}

.time {
  margin-top: 4px;
  font-size: 12px;
  color: #7f8d8b;
}

.typing {
  color: #5d6d6b;
  font-size: 13px;
  padding: 4px;
}

.input-row {
  display: flex;
  gap: 10px;
}

.chat-input {
  flex: 1;
  border: 1px solid rgba(22, 66, 60, 0.2);
  border-radius: 10px;
  padding: 8px 10px;
  resize: vertical;
  min-height: 72px;
  background: rgba(255, 255, 255, 0.85);
  outline: none;
}

.send-btn {
  width: 88px;
  border: none;
  border-radius: 10px;
  background: #16423c;
  color: #fff;
  cursor: pointer;
}

.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.error-text {
  margin: 0;
  color: #c0392b;
  font-size: 13px;
}
</style>
