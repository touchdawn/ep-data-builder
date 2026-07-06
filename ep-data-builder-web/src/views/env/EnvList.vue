<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listEnvironments,
  createEnvironment,
  updateEnvironment,
  deleteEnvironment
} from '../../api/env'
import { useUserStore } from '../../stores/user'
import { formatTime } from '../../utils/format'
import EndpointPanel from './EndpointPanel.vue'
import DatasourcePanel from './DatasourcePanel.vue'

const userStore = useUserStore()

const loading = ref(false)
const envs = ref([])

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, code: '', name: '', description: '' })

const rules = {
  code: [{ required: true, message: '请输入环境编码，如 test-1', trigger: 'blur' }],
  name: [{ required: true, message: '请输入环境名称', trigger: 'blur' }]
}

async function loadEnvs() {
  loading.value = true
  try {
    envs.value = (await listEnvironments()) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.id = null
  form.code = ''
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.code = row.code
  form.name = row.name
  form.description = row.description || ''
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
    const payload = { code: form.code, name: form.name, description: form.description }
    if (form.id == null) {
      await createEnvironment(payload)
      ElMessage.success('环境创建成功')
    } else {
      await updateEnvironment(form.id, payload)
      ElMessage.success('环境更新成功')
    }
    dialogVisible.value = false
    await loadEnvs()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除环境「${row.name}（${row.code}）」吗？`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await deleteEnvironment(row.id)
      ElMessage.success('删除成功')
      await loadEnvs()
    })
    .catch(() => {})
}

onMounted(loadEnvs)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button v-if="userStore.canWrite" type="primary" @click="openCreate">新建环境</el-button>
        <el-button @click="loadEnvs">刷新</el-button>
        <span class="form-tip">展开行可管理该环境下的模块端点与数据源</span>
      </div>

      <el-table v-loading="loading" :data="envs" row-key="id" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-body">
              <el-tabs>
                <el-tab-pane label="模块端点">
                  <EndpointPanel :env-id="row.id" :env-code="row.code" />
                </el-tab-pane>
                <el-tab-pane label="数据源">
                  <DatasourcePanel :env-id="row.id" :env-code="row.code" />
                </el-tab-pane>
              </el-tabs>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="code" label="环境编码" width="160" />
        <el-table-column prop="name" label="环境名称" width="200" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="userStore.canWrite" label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无环境，请先新建" />
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id == null ? '新建环境' : '编辑环境'" width="480px"
      destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="环境编码" prop="code">
          <el-input v-model="form.code" placeholder="如 test-1" :disabled="form.id != null" />
        </el-form-item>
        <el-form-item label="环境名称" prop="name">
          <el-input v-model="form.name" placeholder="如 测试一环境" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="环境用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.expand-body {
  padding: 4px 16px 12px;
  background: #fafafa;
}
</style>
