<script setup>
import { onMounted, ref } from 'vue'
import { listLogs } from '../../api/system'
import { formatTime } from '../../utils/format'

const BIZ_TYPES = ['ENV', 'DATASOURCE', 'FACTORY', 'RECIPE', 'POOL', 'TOKEN', 'USER']

const loading = ref(false)
const bizType = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const logs = ref([])

async function load() {
  loading.value = true
  try {
    const data = await listLogs({ bizType: bizType.value || '', page: page.value, size: size.value })
    total.value = data.total || 0
    logs.value = data.list || []
  } finally {
    loading.value = false
  }
}

function handleFilter() {
  page.value = 1
  load()
}

function handleReset() {
  bizType.value = ''
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="bizType" clearable placeholder="按业务类型筛选" style="width: 200px">
          <el-option v-for="t in BIZ_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
        <el-button type="primary" @click="handleFilter">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="logs" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="bizType" label="业务类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="info" disable-transitions>{{ row.bizType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizId" label="业务 ID" width="90" />
        <el-table-column prop="action" label="动作" width="130" />
        <el-table-column prop="detail" label="详情" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">{{ row.detail || '-' }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无操作日志" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
          @current-change="load" @size-change="handleFilter" />
      </div>
    </el-card>
  </div>
</template>
