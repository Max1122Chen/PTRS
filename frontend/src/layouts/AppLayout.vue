<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import { Menu as MenuIcon } from '@element-plus/icons-vue'
import UserAvatar from '../components/UserAvatar.vue'
import {
  GALLERY_SECTION_TITLE,
  GALLERY_SUB_NAV,
  PRIMARY_NAV,
  isGallerySection,
  isSubNavActive,
} from '../config/nav'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isMobile = useMediaQuery('(max-width: 720px)')
const drawerOpen = ref(false)

const path = computed(() => route.path)
const isHome = computed(() => path.value === '/home')
const isProfile = computed(() => path.value === '/profile')
const showGallerySubNav = computed(() => isGallerySection(path.value))

const visibleSubNav = computed(() =>
  GALLERY_SUB_NAV.filter((item) => !item.adminOnly || auth.user?.role?.toUpperCase() === 'ADMIN'),
)

function goProfile() {
  if (auth.isAuthed) {
    router.push('/profile')
  } else {
    router.push('/login')
  }
}

function closeDrawer() {
  drawerOpen.value = false
}
</script>

<template>
  <div class="es-app">
    <header class="es-header">
      <template v-if="!isMobile">
        <nav class="es-nav" aria-label="主导航">
          <router-link
            v-for="item in PRIMARY_NAV"
            :key="item.to"
            :to="item.to"
            class="nav-items"
            :class="{ active: isSubNavActive(item, path) }"
          >
            {{ item.label }}
          </router-link>
          <div class="es-nav__user">
            <template v-if="auth.isAuthed">
              <button
                type="button"
                class="es-nav__avatar-btn"
                :class="{ 'is-active': isProfile }"
                aria-label="个人中心"
                @click="goProfile"
              >
                <UserAvatar :src="auth.user?.avatar" :size="36" />
              </button>
            </template>
            <template v-else>
              <el-button type="primary" size="small" @click="router.push('/login')">登录</el-button>
              <el-button size="small" @click="router.push('/register')">注册</el-button>
            </template>
          </div>
        </nav>
      </template>

      <template v-else>
        <nav class="es-nav es-nav--mobile" aria-label="主导航">
          <el-button text circle class="menuBtn" aria-label="打开菜单" @click="drawerOpen = true">
            <el-icon><MenuIcon /></el-icon>
          </el-button>
          <span v-if="showGallerySubNav" class="es-nav__section-title">{{ GALLERY_SECTION_TITLE }}</span>
          <div class="es-nav__user">
            <template v-if="auth.isAuthed">
              <button type="button" class="es-nav__avatar-btn" aria-label="个人中心" @click="goProfile">
                <UserAvatar :src="auth.user?.avatar" :size="34" />
              </button>
            </template>
            <template v-else>
              <el-button type="primary" size="small" @click="router.push('/login')">登录</el-button>
            </template>
          </div>
        </nav>

        <nav v-if="showGallerySubNav" class="es-subnav es-subnav--mobile" aria-label="游览功能">
          <router-link
            v-for="item in visibleSubNav"
            :key="item.to"
            :to="item.to"
            class="es-subnav__pill"
            :class="{ 'is-active': isSubNavActive(item, path) }"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <el-drawer v-model="drawerOpen" size="min(88vw, 320px)" direction="ltr" title="菜单">
          <div class="es-drawer-links">
            <router-link
              v-for="item in PRIMARY_NAV"
              :key="item.to"
              :to="item.to"
              class="drawer-link"
              @click="closeDrawer"
            >
              {{ item.label }}
            </router-link>

            <template v-if="showGallerySubNav">
              <div class="drawer-sep" />
              <div class="drawer-group-label">游览功能</div>
              <router-link
                v-for="item in visibleSubNav"
                :key="`sub-${item.to}`"
                :to="item.to"
                class="drawer-link drawer-link--sub"
                @click="closeDrawer"
              >
                {{ item.label }}
              </router-link>
            </template>

            <template v-if="auth.isAuthed">
              <div class="drawer-sep" />
              <router-link to="/profile" class="drawer-link" @click="closeDrawer">个人中心</router-link>
            </template>
            <template v-else>
              <el-button type="primary" style="margin-top: 12px" @click="router.push('/register'); closeDrawer()">
                注册
              </el-button>
            </template>
          </div>
        </el-drawer>
      </template>
    </header>

    <nav v-if="!isMobile && showGallerySubNav" class="es-subnav" aria-label="游览功能">
      <router-link
        v-for="item in visibleSubNav"
        :key="item.to"
        :to="item.to"
        class="es-subnav__pill"
        :class="{ 'is-active': isSubNavActive(item, path) }"
      >
        {{ item.label }}
      </router-link>
    </nav>

    <main :class="['es-main', { 'es-main--flush': isHome }]">
      <router-view v-slot="{ Component }">
        <template v-if="isHome">
          <component :is="Component" />
        </template>
        <div v-else class="es-main-inner es-panel-page">
          <component :is="Component" />
        </div>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.es-nav--mobile {
  width: 100%;
  justify-content: space-between;
}

.es-nav__section-title {
  flex: 1;
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  color: rgb(53, 53, 53);
}

.es-nav__avatar-btn {
  display: inline-flex;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 50%;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.es-nav__avatar-btn:hover {
  transform: scale(1.06);
}

.es-nav__avatar-btn.is-active {
  box-shadow: 0 0 0 2px #16423c;
}

.menuBtn {
  width: 42px;
  height: 42px;
  color: rgb(53, 53, 53);
  transition: background 0.2s ease, transform 0.15s ease;
}

.menuBtn:hover {
  transform: scale(1.04);
}

.es-drawer-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.drawer-group-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding: 4px 10px 2px;
  letter-spacing: 1px;
}

.drawer-link {
  padding: 12px 10px;
  border-radius: 12px;
  text-decoration: none;
  color: var(--el-text-color-primary);
  font-weight: 500;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    transform 0.15s ease;
}

.drawer-link--sub {
  padding-left: 18px;
  font-size: 14px;
}

.drawer-link:hover {
  background: var(--el-fill-color-light);
  color: var(--accent);
}

.drawer-link.router-link-active {
  background: var(--el-color-primary-light-9);
  color: var(--accent);
  font-weight: 600;
}

.drawer-sep {
  height: 1px;
  background: var(--el-border-color-lighter);
  margin: 8px 0;
}
</style>
