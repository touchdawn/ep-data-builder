<script setup>
import { STEP_TYPE_META } from '../utils/steps'
import { formatDuration, prettyJson } from '../utils/format'
import StatusTag from './StatusTag.vue'

/**
 * 执行轨迹时间线：成功绿 / 失败红 / 跳过灰。
 * steps: [{ sortNo, stepType, stepName, skipped, request, response, outputsJson, status, errorMsg, durationMs }]
 */
defineProps({
  steps: { type: Array, default: () => [] }
})

const COLOR = {
  SUCCESS: '#67c23a',
  FAILED: '#f56c6c',
  SKIPPED: '#909399'
}

function stepColor(step) {
  if (step.skipped) return COLOR.SKIPPED
  return COLOR[step.status] || '#909399'
}

function typeLabel(stepType) {
  return (STEP_TYPE_META[stepType] || { label: stepType }).label
}
</script>

<template>
  <div>
    <el-empty v-if="!steps.length" description="暂无步骤轨迹" />
    <el-timeline v-else>
      <el-timeline-item v-for="step in steps" :key="step.sortNo" :color="stepColor(step)">
        <div class="trace-step">
          <div class="trace-step-header">
            <span class="trace-step-no">步骤 {{ step.sortNo }}</span>
            <el-tag size="small" disable-transitions>{{ typeLabel(step.stepType) }}</el-tag>
            <span class="trace-step-name">{{ step.stepName || '未命名步骤' }}</span>
            <StatusTag :status="step.skipped ? 'SKIPPED' : step.status" size="small" />
            <span class="form-tip">{{ formatDuration(step.durationMs) }}</span>
          </div>

          <div v-if="step.errorMsg" class="trace-error">{{ step.errorMsg }}</div>

          <el-collapse v-if="!step.skipped" class="trace-collapse">
            <el-collapse-item v-if="step.request" title="请求（渲染后的报文 / SQL）">
              <pre class="json-view">{{ prettyJson(step.request) }}</pre>
            </el-collapse-item>
            <el-collapse-item v-if="step.response" title="响应（摘要 / 影响行数）">
              <pre class="json-view">{{ prettyJson(step.response) }}</pre>
            </el-collapse-item>
            <el-collapse-item v-if="step.outputsJson" title="输出变量">
              <pre class="json-view">{{ prettyJson(step.outputsJson) }}</pre>
            </el-collapse-item>
          </el-collapse>
          <div v-else class="form-tip">条件不满足，步骤被跳过</div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.trace-step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.trace-step-no {
  font-weight: 600;
}

.trace-step-name {
  font-weight: 600;
}

.trace-error {
  margin-top: 8px;
  padding: 8px 12px;
  background: #fef0f0;
  border: 1px solid #fbc4c4;
  border-radius: 4px;
  color: #f56c6c;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}

.trace-collapse {
  margin-top: 8px;
}
</style>
