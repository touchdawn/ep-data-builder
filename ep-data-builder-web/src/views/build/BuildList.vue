<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createBuild, listBuilds } from '../../api/build'
import { listFactories, getFactory } from '../../api/factory'
import { listEnvironments } from '../../api/env'
import { useUserStore } from '../../stores/user'
import { formatDuration, formatTime } from '../../utils/format'
import ParamFillForm from '../../components/ParamFillForm.vue'
import StatusTag from '../../components/StatusTag.vue'

const router = useRouter()
const userStore = useUserStore()

// ------- 发起造数 -------
const factoryOptions = ref([])
const factoryLoading = ref(false)
const envs = ref([])

const launch = reactive({
  factoryId: null,
  envId: null,
  params: {}
})
const launchFactory = ref(null) // 选中工厂的完整定义（含参数 schema）
const launching = ref(false)
const paramFormRef = ref(null)

async function searchFactories(kw) {
  factoryLoading.value = true
  try {
    const data = await listFactories({ keyword: kw || '', page: 1, size: 50 })
    factoryOptions.value = (data.list || []).filter((f) => f.enabled)
  } finally {
    factoryLoading.value = false
  }
}

async function onFactoryChange(id) {
  launchFactory.value = null
  launch.params = {}
  if (id == null) return
  launchFactory.value = await getFactory(id)
}

async function handleLaunch() {
  if (launch.factoryId == null) {
    ElMessage.warning('请选择工厂')
    return
  }
  if (launch.envId == null) {
    ElMessage.warning('请选择环境')
    return
  }
  launching.value = true
  try {
    const params = paramFormRef.value ? paramFormRef.value.collect() : {}
    const data = await createBuild({
      factoryId: launch.factoryId,
      envId: launch.envId,
      params
    })
    ElMessage.success('造数执行完成，查看执行轨迹')
    router.push(`/builds/${data.id}`)
  } catch (e) {
    // 失败提示由拦截器处理；若返回 buildId 可去记录列表查轨迹
    await loadBuilds()
  } finally {
    launching.value = false
  }
}

// ------- 执行记录 -------
const loading = ref(false)
const filter = reactive({ factoryId: null, envId: null, status: '' })
const page = ref(1)
const size = ref(10)
const total = ref(0)
const builds = ref([])

async function loadBuilds() {
  loading.value = true
  try {
    const data = await listBuilds({
      factoryId: filter.factoryId ?? '',
      envId: filter.envId ?? '',
      status: filter.status || '',
      page: page.value,
      size: size.value
    })
    total.value = data.total || 0
    builds.value = data.list || []
  } finally {
    loading.value = false
  }
}

function handleFilter() {
  page.value = 1
  loadBuilds()
}

function handleResetFilter() {
  filter.factoryId = null
  filter.envId = null
  filter.status = ''
  page.value = 1
  loadBuilds()
}

function openDetail(row) {
  router.push(`/builds/${row.id}`)
}

onMounted(async () => {
  await Promise.all([
    searchFactories(''),
    listEnvironments().then((data) => {
      envs.value = data || []
    }),
    loadBuilds()
  ])
})
</script>

<template>
  <div class="page-container">
    <!-- 发起造数（VIEWER 只读隐藏） -->
    <el-card v-if="userStore.canWrite" shadow="never" class="launch-card">
      <template #header>
        <span class="card-title">发起造数</span>
        <span class="form-tip" style="margin-left: 10px">选工厂 → 选环境 → 覆盖参数（留空使用默认值）→ 执行</span>
      </template>
      <el-form label-width="160px">
        <el-form-item label="工厂" required>
          <el-select v-model="launch.factoryId" filterable remote clearable
            :remote-method="searchFactories" :loading="factoryLoading" placeholder="输入关键词搜索工厂"
            style="width: 420px" @change="onFactoryChange">
            <el-option v-for="f in factoryOptions" :key="f.id" :value="f.id"
              :label="`${f.name}（${f.code}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="环境" required>
          <el-select v-model="launch.envId" clearable placeholder="选择目标环境" style="width: 420px">
            <el-option v-for="e in envs" :key="e.id" :value="e.id" :label="`${e.name}（${e.code}）`" />
          </el-select>
        </el-form-item>
        <template v-if="launchFactory">
          <el-form-item label=" ">
            <div class="form-tip">{{ launchFactory.description }}</div>
          </el-form-item>
          <ParamFillForm ref="paramFormRef" v-model="launch.params"
            :params="launchFactory.params || []" />
        </template>
        <el-form-item label=" ">
          <el-button type="primary" :loading="launching" :disabled="launch.factoryId == null"
            @click="handleLaunch">
            {{ launching ? '执行中…' : '执行造数' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 执行记录 -->
    <el-card shadow="never">
      <template #header>
        <span class="card-title">执行记录</span>
      </template>
      <div class="toolbar">
        <el-select v-model="filter.factoryId" clearable filterable placeholder="按工厂筛选"
          style="width: 240px">
          <el-option v-for="f in factoryOptions" :key="f.id" :value="f.id"
            :label="`${f.name}（${f.code}）`" />
        </el-select>
        <el-select v-model="filter.envId" clearable placeholder="按环境筛选" style="width: 180px">
          <el-option v-for="e in envs" :key="e.id" :value="e.id" :label="`${e.name}（${e.code}）`" />
        </el-select>
        <el-select v-model="filter.status" clearable placeholder="按状态筛选" style="width: 140px">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <el-button type="primary" @click="handleFilter">查询</el-button>
        <el-button @click="handleResetFilter">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="builds" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="工厂" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.factoryName || '-' }}
            <span v-if="row.factoryCode" class="form-tip">（{{ row.factoryCode }}）</span>
          </template>
        </el-table-column>
        <el-table-column prop="envCode" label="环境" width="110" />
        <el-table-column prop="source" label="来源" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="info" disable-transitions>{{ row.source }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <StatusTag :status="row.status" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="发起时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无执行记录" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadBuilds" @size-change="handleFilter" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.launch-card {
  margin-bottom: 12px;
}

.card-title {
  font-weight: 600;
}
</style>
