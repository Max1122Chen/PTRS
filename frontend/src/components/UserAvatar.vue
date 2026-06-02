<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    src?: string | null
    size?: number
    alt?: string
  }>(),
  {
    size: 36,
    alt: '用户头像',
  },
)

const hasImage = computed(() => Boolean(props.src?.trim()))
</script>

<template>
  <span
    class="user-avatar"
    :class="{ 'user-avatar--empty': !hasImage }"
    :style="{ width: `${size}px`, height: `${size}px` }"
    role="img"
    :aria-label="alt"
  >
    <img v-if="hasImage" :src="src!" :alt="alt" />
  </span>
</template>

<style scoped>
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  background: #ffffff;
  border: 1.5px solid rgba(255, 255, 255, 0.65);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.user-avatar--empty {
  background: #ffffff;
}
</style>
