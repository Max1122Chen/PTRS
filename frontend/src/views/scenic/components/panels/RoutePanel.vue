<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { apiPlanRoute, apiPlanRouteMulti } from '../../../../lib/api'
import { storeToRefs } from 'pinia'
import { useScenicHubStore } from '../../../../stores/scenicHub'

const hub = useScenicHubStore()
const { showRoadNodes } = storeToRefs(hub)
const loading = ref(false)

const form = reactive({
  startId: null as number | null,
  endId: null as number | null,
  vehicle: '' as string,
  strategy: '' as '' | 'distance' | 'time',
  multiPointIds: [] as number[],
  returnToStart: false,
})

const result = ref<{ path: number[]; distance: number; time: number } | null>(null)

const nodeOptions = computed(() => hub.poiCandidates)

const selectedMultiPointLabels = computed(() =>
  form.multiPointIds.map((id) => ({
    id,
    label: hub.poiLabelMap[id] || `节点${id}`,
  })),
)

function useFocusAsStart() {
  if (hub.focusPoiId != null) form.startId = hub.focusPoiId
}

function useFocusAsEnd() {
  if (hub.focusPoiId != null) form.endId = hub.focusPoiId
}

async function plan() {
  if (!form.startId || !form.endId) {
    ElMessage.warning('请选择起点和终点')
    return
  }
  if (hub.areaId == null) {
    ElMessage.warning('请先选择景区')
    return
  }
  loading.value = true
  try {
    result.value = await apiPlanRoute({
      areaId: hub.areaId,
      startId: Number(form.startId),
      endId: Number(form.endId),
      vehicle: form.vehicle || undefined,
      strategy: form.strategy || undefined,
    })
    hub.setRoutePath(result.value.path)
  } catch {
    hub.setRoutePath(null)
  } finally {
    loading.value = false
  }
}

async function planMulti() {
  const points = form.multiPointIds.map((n) => Number(n)).filter((n) => Number.isFinite(n))
  if (points.length < 2) {
    ElMessage.warning('多点规划至少需要 2 个地点')
    return
  }
  if (hub.areaId == null) {
    ElMessage.warning('请先选择景区')
    return
  }
  loading.value = true
  try {
    result.value = await apiPlanRouteMulti({
      areaId: hub.areaId,
      points,
      returnToStart: form.returnToStart,
      vehicle: form.vehicle || undefined,
      strategy: form.strategy || undefined,
    })
    hub.setRoutePath(result.value.path)
  } catch {
    hub.setRoutePath(null)
  } finally {
    loading.value = false
  }
}

function moveMultiPoint(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= form.multiPointIds.length) return
  const copied = [...form.multiPointIds]
  const temp = copied[index]
  copied[index] = copied[target]
  copied[target] = temp
  form.multiPointIds = copied
}

defineExpose({ form, result })
</script>

<template>
  <div class="panel">
    <el-form label-position="top" size="small">
      <el-form-item label="交通工具">
        <el-segmented
          v-model="form.vehicle"
          :options="[
            { label: '步行', value: 'walk' },
            { label: '自行车', value: 'bike' },
            { label: '电瓶车', value: 'shuttle' },
          ]"
        />
      </el-form-item>
      <el-form-item label="策略">
        <el-radio-group v-model="form.strategy">
          <el-radio-button label="distance">最短距离</el-radio-button>
          <el-radio-button label="time">最短时间</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="路网调试">
        <el-switch v-model="showRoadNodes" active-text="显示道路节点" inactive-text="隐藏" />
      </el-form-item>

      <div class="row">
        <el-form-item label="起点" style="flex: 1">
          <el-select v-model="form.startId" filterable clearable placeholder="选起点" style="width: 100%">
            <el-option v-for="n in nodeOptions" :key="`s-${n.nodeId}`" :label="`${n.name}（${n.nodeId}）`" :value="n.nodeId" />
          </el-select>
          <el-button size="small" text @click="useFocusAsStart">使用当前选中点</el-button>
        </el-form-item>
        <el-form-item label="终点" style="flex: 1">
          <el-select v-model="form.endId" filterable clearable placeholder="选终点" style="width: 100%">
            <el-option v-for="n in nodeOptions" :key="`e-${n.nodeId}`" :label="`${n.name}（${n.nodeId}）`" :value="n.nodeId" />
          </el-select>
          <el-button size="small" text @click="useFocusAsEnd">使用当前选中点</el-button>
        </el-form-item>
      </div>
      <el-button type="primary" :loading="loading" @click="plan">两点规划</el-button>

      <el-divider />
      <el-form-item label="多点规划">
        <el-select v-model="form.multiPointIds" multiple filterable clearable collapse-tags placeholder="至少 2 个点" style="width: 100%">
          <el-option v-for="n in nodeOptions" :key="`m-${n.nodeId}`" :label="`${n.name}（${n.nodeId}）`" :value="n.nodeId" />
        </el-select>
      </el-form-item>
      <el-form-item label="闭环">
        <el-switch v-model="form.returnToStart" active-text="回到起点" inactive-text="止于末点" />
      </el-form-item>
      <el-form-item v-if="selectedMultiPointLabels.length" label="访问顺序">
        <div v-for="(item, idx) in selectedMultiPointLabels" :key="item.id" class="order-row">
          <span>{{ idx + 1 }}. {{ item.label }}</span>
          <span>
            <el-button size="small" text :disabled="idx === 0" @click="moveMultiPoint(idx, -1)">上</el-button>
            <el-button size="small" text :disabled="idx === selectedMultiPointLabels.length - 1" @click="moveMultiPoint(idx, 1)">下</el-button>
          </span>
        </div>
      </el-form-item>
      <el-button type="primary" plain :loading="loading" @click="planMulti">多点规划</el-button>

      <div v-if="result" class="result glass">
        <div class="muted">path：{{ result.path.join(' → ') }}</div>
        <div class="muted">distance：{{ result.distance.toFixed(1) }} m</div>
        <div class="muted">time：{{ result.time.toFixed(1) }} s</div>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.panel {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding-right: 2px;
}
.panel :deep(.el-form-item) {
  margin-bottom: 13px;
}
.panel :deep(.el-segmented) {
  width: 100%;
}
.panel :deep(.el-radio-group) {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.panel :deep(.el-radio-button__inner) {
  width: 100%;
  text-align: center;
}
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.result {
  margin-top: 12px;
  padding: 12px;
  font-size: 12px;
  border-radius: 8px;
  background: rgba(22, 66, 60, 0.08) !important;
  border-color: rgba(22, 66, 60, 0.12) !important;
}
.order-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.34);
}
@media (max-width: 520px) {
  .row {
    grid-template-columns: 1fr;
  }
}
</style>
