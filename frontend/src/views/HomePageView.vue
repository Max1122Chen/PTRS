<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
const base = import.meta.env.BASE_URL

/** public/explorescape 下的分层图，构建期与运行时均用 BASE_URL 拼接，避免打包器把绝对路径当模块 */
const layers = {
  b3: `${base}explorescape/bac-3.png`,
  b2: `${base}explorescape/bac-2.png`,
  b22: `${base}explorescape/bac-2-2.png`,
  b1: `${base}explorescape/bac-1.png`,
}

function exploreMore() {
  router.push('/recommend')
}
</script>

<template>
  <div class="es-home">
    <div class="es-content">
      <!-- 与 ExploreScape index.html 顺序一致：bac 3 → bac 2 → bac 2.2 → bac 1（bac 4 仅在壳层背景） -->
      <img :src="layers.b3" alt="" class="es-parallax-img back-3" />
      <img :src="layers.b2" alt="" class="es-parallax-img back-2mid" />
      <img :src="layers.b22" alt="" class="es-parallax-img back-2" />
      <img :src="layers.b1" alt="" class="es-parallax-img back-1" />

      <div class="es-title">
        <h1>travel</h1>
      </div>

      <div class="es-info-wrap">
        <p>
          智能推荐景区与路线，一站式查询周边设施与美食，支持室外—室内一体导航。发现与你兴趣匹配的旅程，让每一次出行都成为值得记录的故事。
        </p>
      </div>

      <div class="es-cta">
        <button type="button" @click="exploreMore">
          开始游览
          <i class="fa-solid fa-arrow-right" aria-hidden="true" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.es-content {
  isolation: isolate;
}
</style>
