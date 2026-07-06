<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFactories, createFactory, deleteFactory } from '../../api/factory'
import { useUserStore } from '../../stores/user'
import { formatTime } from '../../utils/format'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref([])

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ code: '', name: '', description: '', owner: '' })

const rules = {
  code: [{ required: true, message: '请输入工厂编码，如 user-center.user', trigger: 'blur' }],
  name: [{ required: true, message: '请输入工厂名称', trigger: 'blur' }],
  description: [{ required: true, message: '功能描述决定 LLM 检索命中率，必填', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const data = await listFactories({ keyword: keyword.value, page: page.value, size: size.value })
    total.value = data.total || 0
    list.value = data.list || []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  load()
}

function handleReset() {
  keyword.value = ''
  page.value = 1
  load()
}

function openCreate() {
  form.code = ''
  form.name = ''
  form.description = ''
  form.owner = ''
  dialogVisible.value = true
}

async function handleCreate() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    const data = await createFactory({
      code: form.code,
      name: form.name,
      description: form.description,
      owner: form.owner
    })
    const id = data && typeof data === 'object' ? data.id : data
    ElMessage.success('工厂创建成功，进入编辑器完善参数与步骤')
    dialogVisible.value = false
    if (id != null) {
      router.push(`/factories/${id}`)
    } else {
      await load()
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function openEditor(row) {
  router.push(`/factories/${row.id}`)
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除工厂「${row.name}（${row.code}）」吗？`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await deleteFactory(row.id)
      ElMessage.success('删除成功')
      await load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="按编码 / 名称 / 描述搜索" clearable style="width: 280px"
          @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div style="flex: 1"></div>
        <el-button v-if="userStore.canWrite" type="primary" @click="openCreate">新建工厂</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="code" label="编码" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openEditor(row)">{{ row.code }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="description" label="功能描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="owner" label="负责人" width="110">
          <template #default="{ row }">{{ row.owner || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small" disable-transitions>
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标记" width="90">
          <template #default="{ row }">
            <el-tooltip v-if="row.pureSql" content="纯 SQL 工厂：无 API 步骤，需自行承担表结构漂移风险" placement="top">
              <el-tag type="warning" size="small" disable-transitions>纯SQL</el-tag>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditor(row)">
              {{ userStore.canWrite ? '编辑器' : '查看' }}
            </el-button>
            <el-button v-if="userStore.canWrite" link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无工厂" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
          @current-change="load" @size-change="handleSearch" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建工厂" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" placeholder="{moduleCode}.{实体名}，如 user-center.user" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 用户（可指定注册时长）" />
        </el-form-item>
        <el-form-item label="功能描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4"
            placeholder="写清楚：造的是什么数据、默认产出什么状态、可以定制哪些关键维度。例：创建平台用户，默认当天注册的 NORMAL 个人用户；可指定注册时长（createdDaysAgo）造老用户" />
        </el-form-item>
        <el-form-item label="负责人" prop="owner">
          <el-input v-model="form.owner" placeholder="负责人姓名或账号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">创建并进入编辑器</el-button>
      </template>
    </el-dialog>
  </div>
</template>
