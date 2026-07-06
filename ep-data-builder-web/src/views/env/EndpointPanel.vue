<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listEndpoints, createEndpoint, updateEndpoint, deleteEndpoint } from '../../api/env'
import { useUserStore } from '../../stores/user'

const props = defineProps({
  envId: { type: [Number, String], required: true },
  envCode: { type: String, default: '' }
})

const userStore = useUserStore()

const loading = ref(false)
const endpoints = ref([])

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, moduleCode: '', baseUrl: '', headers: '' })

const validateHeaders = (rule, value, callback) => {
  if (!value) {
    callback()
    return
  }
  try {
    const parsed = JSON.parse(value)
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      callback(new Error('必须是 JSON 对象，如 {"X-Gateway-Token": "xxx"}'))
      return
    }
    callback()
  } catch (e) {
    callback(new Error('不是合法的 JSON'))
  }
}

const rules = {
  moduleCode: [{ required: true, message: '请输入模块编码（对齐契约平台）', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  headers: [{ validator: validateHeaders, trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    endpoints.value = (await listEndpoints(props.envId)) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.id = null
  form.moduleCode = ''
  form.baseUrl = ''
  form.headers = ''
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.moduleCode = row.moduleCode
  form.baseUrl = row.baseUrl
  form.headers = row.headers || ''
  dialogVisible.value = true
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    const payload = { moduleCode: form.moduleCode, baseUrl: form.baseUrl, headers: form.headers }
    if (form.id == null) {
      await createEndpoint(props.envId, payload)
      ElMessage.success('端点创建成功')
    } else {
      await updateEndpoint(form.id, payload)
      ElMessage.success('端点更新成功')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除模块「${row.moduleCode}」的端点吗？`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await deleteEndpoint(row.id)
      ElMessage.success('删除成功')
      await load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <el-button v-if="userStore.canWrite" type="primary" size="small" @click="openCreate">
        新增端点
      </el-button>
      <el-button size="small" @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="endpoints" size="small" border>
      <el-table-column prop="moduleCode" label="模块编码" width="200" />
      <el-table-column prop="baseUrl" label="Base URL" min-width="240" show-overflow-tooltip />
      <el-table-column prop="headers" label="公共 Header" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.headers || '-' }}</template>
      </el-table-column>
      <el-table-column v-if="userStore.canWrite" label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无模块端点" :image-size="60" />
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id == null ? '新增端点' : '编辑端点'" width="560px"
      destroy-on-close append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="模块编码" prop="moduleCode">
          <el-input v-model="form.moduleCode" placeholder="如 user-center（对齐契约平台 moduleCode）" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="如 http://test-1.user-center.example.com" />
        </el-form-item>
        <el-form-item label="公共 Header" prop="headers">
          <el-input v-model="form.headers" type="textarea" :rows="4"
            placeholder='JSON 对象，如 {"X-Gateway-Token": "xxx"}，可留空' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
