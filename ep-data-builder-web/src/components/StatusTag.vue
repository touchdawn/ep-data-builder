<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, default: '' },
  size: { type: String, default: 'default' }
})

const META = {
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  SKIPPED: { label: '跳过', type: 'info' },
  RUNNING: { label: '执行中', type: 'warning' }
}

const meta = computed(() => META[props.status] || { label: props.status || '-', type: 'info' })
</script>

<template>
  <el-tag :type="meta.type" :size="size" disable-transitions>{{ meta.label }}</el-tag>
</template>
