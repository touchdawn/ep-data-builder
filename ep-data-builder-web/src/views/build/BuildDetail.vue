<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBuild } from '../../api/build'
import { formatDuration, formatTime, prettyJson } from '../../utils/format'
import BuildTraceView from '../../components/BuildTraceView.vue'
import StatusTag from '../../components/StatusTag.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const build = ref(null)

async function load() {
  loading.value = true
  try {
    build.value = await getBuild(route.params.id)
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/builds')
}

onMounted(load)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-card shadow="never" class="info-card">
      <template #header>
        <div class="detail-header">
          <div>
            <el-button link @click="goBack">← 返回记录列表</el-button>
            <span class="detail-title">执行详情 #{{ route.params.id }}</span>
            <StatusTag v-if="build" :status="build.status" />
          </div>
          <el-button @click="load">刷新</el-button>
        </div>
      </template>

      <el-descriptions v-if="build" :column="3" border>
        <el-descriptions-item label="工厂">
          {{ build.factoryName || '-' }}
          <span v-if="build.factoryCode" class="form-tip">（{{ build.factoryCode }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="环境">{{ build.envCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ build.source || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag :status="build.status" size="small" />
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ formatDuration(build.durationMs) }}</el-descriptions-item>
        <el-descriptions-item label="发起时间">{{ formatTime(build.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="build && build.errorMsg" class="error-box">{{ build.errorMsg }}</div>

      <el-row v-if="build" :gutter="12" class="json-row">
        <el-col :span="12">
          <div class="json-block-title">入参（合并后的最终参数）</div>
          <pre class="json-view">{{ prettyJson(build.paramsJson) || '（空）' }}</pre>
        </el-col>
        <el-col :span="12">
          <div class="json-block-title">输出（对调用方）</div>
          <pre class="json-view">{{ prettyJson(build.outputsJson) || '（空）' }}</pre>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span style="font-weight: 600">逐步执行轨迹</span>
        <span class="form-tip" style="margin-left: 10px">成功绿 / 失败红 / 跳过灰；展开看渲染后的报文与响应</span>
      </template>
      <BuildTraceView :steps="(build && build.steps) || []" />
    </el-card>
  </div>
</template>

<style scoped>
.info-card {
  margin-bottom: 12px;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.detail-title {
  font-size: 16px;
  font-weight: 700;
  margin: 0 10px;
}

.error-box {
  margin-top: 12px;
  padding: 10px 14px;
  background: #fef0f0;
  border: 1px solid #fbc4c4;
  border-radius: 4px;
  color: #f56c6c;
  white-space: pre-wrap;
  word-break: break-all;
}

.json-row {
  margin-top: 12px;
}

.json-block-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}
</style>
