<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listTokens, createToken, updateToken } from '../../api/system'
import { formatTime } from '../../utils/format'

const loading = ref(false)
const tokens = ref([])

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref(null)
const createForm = reactive({ name: '', qpsLimit: 5 })
const createRules = {
  name: [{ required: true, message: '请输入令牌名称（标识调用方）', trigger: 'blur' }]
}

const plainVisible = ref(false)
const plainToken = ref('')

const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({ id: null, name: '', enabled: true, qpsLimit: 5 })

async function load() {
  loading.value = true
  try {
    tokens.value = (await listTokens()) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.name = ''
  createForm.qpsLimit = 5
  createVisible.value = true
}

async function handleCreate() {
  try {
    await createFormRef.value.validate()
  } catch (e) {
    return
  }
  creating.value = true
  try {
    const data = await createToken({ name: createForm.name, qpsLimit: createForm.qpsLimit })
    createVisible.value = false
    plainToken.value = data.token
    plainVisible.value = true
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    creating.value = false
  }
}

async function copyToken() {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(plainToken.value)
    } else {
      const ta = document.createElement('textarea')
      ta.value = plainToken.value
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

function openEdit(row) {
  editForm.id = row.id
  editForm.name = row.name
  editForm.enabled = !!row.enabled
  editForm.qpsLimit = row.qpsLimit
  editVisible.value = true
}

async function handleEditSave() {
  editSaving.value = true
  try {
    await updateToken(editForm.id, { enabled: editForm.enabled, qpsLimit: editForm.qpsLimit })
    ElMessage.success('更新成功')
    editVisible.value = false
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    editSaving.value = false
  }
}

async function toggleEnabled(row) {
  try {
    await updateToken(row.id, { enabled: row.enabled, qpsLimit: row.qpsLimit })
    ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch (e) {
    row.enabled = !row.enabled // 失败回滚
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">新建令牌</el-button>
        <el-button @click="load">刷新</el-button>
        <span class="form-tip">开放 API 调用方使用 Header X-Builder-Token 携带令牌；明文仅创建时展示一次</span>
      </div>

      <el-table v-loading="loading" :data="tokens" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="toggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="qpsLimit" label="QPS 限制" width="100" />
        <el-table-column label="最近使用" width="180">
          <template #default="{ row }">{{ formatTime(row.lastUsedAt) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无令牌" />
        </template>
      </el-table>
    </el-card>

    <!-- 新建 -->
    <el-dialog v-model="createVisible" title="新建令牌" width="460px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="createForm.name" placeholder="标识调用方，如 ep-test-platform" />
        </el-form-item>
        <el-form-item label="QPS 限制" prop="qpsLimit">
          <el-input-number v-model="createForm.qpsLimit" :min="1" :max="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 明文展示（仅一次） -->
    <el-dialog v-model="plainVisible" title="令牌创建成功" width="560px"
      :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon
        title="令牌明文仅此一次展示，请立即复制保存；关闭后无法再次查看" style="margin-bottom: 12px" />
      <el-input :model-value="plainToken" readonly>
        <template #append>
          <el-button @click="copyToken">复制</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="plainVisible = false">我已保存，关闭</el-button>
      </template>
    </el-dialog>

    <!-- 编辑 -->
    <el-dialog v-model="editVisible" :title="`编辑令牌「${editForm.name}」`" width="420px"
      destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="启用">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
        <el-form-item label="QPS 限制">
          <el-input-number v-model="editForm.qpsLimit" :min="1" :max="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleEditSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
