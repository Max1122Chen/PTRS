<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiRegister } from '../../lib/api'

const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  password2: '',
  email: '',
  nickname: '',
})

async function submit() {
  if (!form.username || !form.password || !form.email || !form.nickname) {
    ElMessage.warning('请完善：用户名、密码、邮箱、昵称')
    return
  }
  if (form.password !== form.password2) {
    ElMessage.warning('两次密码不一致')
    return
  }
  loading.value = true
  try {
    await apiRegister({
      username: form.username,
      password: form.password,
      email: form.email,
      nickname: form.nickname,
    })
    ElMessage.success('注册成功，请登录')
    location.href = '/login'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="es-auth-page">
    <div class="panel glass animate-fade-in-up">
      <div class="hero">
        <div class="h1">创建账号</div>
        <div class="muted">
          注册后可体验路线规划、设施查询、美食推荐、热门/个性化景区推荐，以及日记发布与评分。
        </div>
      </div>

      <el-form label-position="top" class="form" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="例如：alice" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input
            v-model="form.email"
            type="email"
            placeholder="例如：alice@example.com"
            autocomplete="email"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="form.password2" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="例如：Alice" autocomplete="nickname" />
        </el-form-item>

        <el-button type="primary" size="large" :loading="loading" style="width: 100%" @click="submit">
          注册
        </el-button>
        <div class="muted links">
          已有账号？
          <a @click.prevent="$router.push('/login')">去登录</a>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.es-auth-page {
  width: 100%;
}
.panel {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
  padding: 16px;
}
.hero {
  padding: 16px;
  border-radius: 16px;
  background:
    radial-gradient(420px 220px at 18% 15%, var(--accent-pink-2), transparent 60%),
    radial-gradient(360px 240px at 88% 26%, var(--accent-main-2), transparent 62%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.12), rgba(255, 255, 255, 0.04));
  border: 1px solid var(--glass-border-soft);
  backdrop-filter: blur(16px) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(16px) saturate(var(--glass-saturate));
  box-shadow: var(--shadow-md);
}
.h1 {
  font-size: 28px;
  font-weight: 820;
  margin-bottom: 8px;
}
.form {
  padding: 14px;
}
.links {
  margin-top: 12px;
  font-size: 13px;
}
.links a {
  color: var(--accent-main);
  cursor: pointer;
  text-decoration: none;
}
@media (max-width: 860px) {
  .panel {
    grid-template-columns: 1fr;
  }
}
</style>

