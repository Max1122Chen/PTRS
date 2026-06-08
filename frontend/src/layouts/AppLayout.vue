<script setup lang="ts">

import { computed, ref } from 'vue'

import { useRoute, useRouter } from 'vue-router'

import { useMediaQuery } from '@vueuse/core'

import { Menu as MenuIcon } from '@element-plus/icons-vue'

import UserAvatar from '../components/UserAvatar.vue'

import { useAuthStore } from '../stores/auth'



const route = useRoute()

const router = useRouter()

const auth = useAuthStore()



const isMobile = useMediaQuery('(max-width: 720px)')

const drawerOpen = ref(false)



const isHome = computed(() => route.path === '/home')

const isGallerySection = computed(() => mainNavActive('/recommend'))

const isProfile = computed(() => route.path === '/profile')



function mainNavActive(path: string): boolean {

  const p = route.path

  if (path === '/home') return p === '/home'

  if (path === '/about') return p === '/about'

  if (path === '/diary') return p.startsWith('/diary')

  if (path === '/recommend') {

    return (

      p === '/recommend' ||

      p === '/scenic' ||

      p.startsWith('/scenic/') ||

      p.startsWith('/admin')

    )

  }

  return false

}



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

        <nav class="es-nav" aria-label="Primary">

          <router-link to="/home" class="nav-items" :class="{ active: mainNavActive('/home') }">Home</router-link>

          <router-link to="/about" class="nav-items" :class="{ active: mainNavActive('/about') }">About</router-link>

          <router-link to="/diary" class="nav-items" :class="{ active: mainNavActive('/diary') }">Reviews</router-link>

          <router-link to="/recommend" class="nav-items" :class="{ active: mainNavActive('/recommend') }">Gallery</router-link>

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

        <nav class="es-nav" style="width: 100%; justify-content: space-between">

          <el-button text circle class="menuBtn" aria-label="Menu" @click="drawerOpen = true">

            <el-icon><MenuIcon /></el-icon>

          </el-button>

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

        <el-drawer v-model="drawerOpen" size="min(88vw, 320px)" direction="ltr" title="Menu">

          <div class="es-drawer-links">

            <router-link to="/home" class="drawer-link" @click="closeDrawer">Home</router-link>

            <router-link to="/about" class="drawer-link" @click="closeDrawer">About</router-link>

            <router-link to="/diary" class="drawer-link" @click="closeDrawer">Reviews</router-link>

            <router-link to="/recommend" class="drawer-link" @click="closeDrawer">Gallery</router-link>

            <template v-if="isGallerySection">

              <div class="drawer-sep" />

              <router-link to="/scenic" class="drawer-link" @click="closeDrawer">景区</router-link>

              <router-link v-if="auth.user?.role?.toUpperCase() === 'ADMIN'" to="/admin" class="drawer-link" @click="closeDrawer">管理</router-link>

            </template>

            <template v-if="auth.isAuthed">

              <router-link to="/profile" class="drawer-link" @click="closeDrawer">个人中心</router-link>

            </template>

            <template v-else>

              <el-button type="primary" style="margin-top: 12px" @click="router.push('/register'); closeDrawer()">注册</el-button>

            </template>

          </div>

        </el-drawer>

      </template>

    </header>



    <nav v-if="!isMobile && isGallerySection" class="es-subnav" aria-label="App features">

      <router-link to="/recommend" class="es-subnav__link">推荐</router-link>

      <span style="color: rgba(255,255,255,0.25)">·</span>

      <router-link to="/scenic" class="es-subnav__link">景区</router-link>

      <template v-if="auth.user?.role?.toUpperCase() === 'ADMIN'">

        <span style="color: rgba(255,255,255,0.25)">·</span>

        <router-link to="/admin" class="es-subnav__link">管理</router-link>

      </template>

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


