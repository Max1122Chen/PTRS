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
        <h3>The Land of Serene Beauty</h3>
        <h1>travel</h1>
      </div>

      <div class="es-info-wrap">
        <p>
          Discover a smarter way to travel with our Personalized Travel Recommendation System. From scenic spots and local food to route planning and nearby facilities, every feature is designed to help you explore more easily and enjoy every journey. Start your next adventure, find places that match your interests, and let every trip become a memorable story.
        </p>
      </div>

      <div class="es-cta">
        <button type="button" @click="exploreMore">
          Explore More
          <i class="fa-solid fa-arrow-right" aria-hidden="true" />
        </button>
      </div>

      <div class="es-slider" aria-hidden="true">
        <i class="fa-solid fa-chevron-left" />
        <i class="fa-solid fa-chevron-right" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.es-content {
  isolation: isolate;
}
</style>
