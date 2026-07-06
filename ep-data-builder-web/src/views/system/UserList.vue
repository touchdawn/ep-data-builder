<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listUsers, createUser, updateUser } from '../../api/system'
import { formatTime } from '../../utils/format'

const ROLES = [
  { value: 'ADMIN', label: 'ADMIN（管理员）' },
  { value: 'EDITOR', label: 'EDITOR（编辑者）' },
  { value: 'VIEWER', label: 'VIEWER（只读）' }
]

const roleTagType = { ADMIN: 'danger', EDITOR: 'primary', VIEWER: 'info' }

const loading = ref(false)
const users = ref([])

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref(null)
const createForm = reactive({ username: '', password: '', displayName: '', role: 'EDITOR' })
const createRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const editVisible = ref(false)
const editSaving = ref(false)
const editFormRef = ref(null)
const editForm = reactive({
  id: null,
  username: '',
  displayName: '',
  role: 'EDITOR',
  enabled: true,
  password: ''
})
const editRules = {
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ min: 6, message: '密码至少 6 位', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    users.value = (await listUsers()) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.username = ''
  createForm.password = ''
  createForm.displayName = ''
  createForm.role = 'EDITOR'
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
    await createUser({
      username: createForm.username,
      password: createForm.password,
      displayName: createForm.displayName,
      role: createForm.role
    })
    ElMessage.success('用户创建成功')
    createVisible.value = false
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    creating.value = false
  }
}

function openEdit(row) {
  editForm.id = row.id
  editForm.username = row.username
  editForm.displayName = row.displayName || ''
  editForm.role = row.role
  editForm.enabled = !!row.enabled
  editForm.password = ''
  editVisible.value = true
}

async function handleEditSave() {
  try {
    await editFormRef.value.validate()
  } catch (e) {
    return
  }
  editSaving.value = true
  try {
    const payload = {
      displayName: editForm.displayName,
      role: editForm.role,
      enabled: editForm.enabled
    }
    if (editForm.password) payload.password = editForm.password
    await updateUser(editForm.id, payload)
    ElMessage.success('用户更新成功')
    editVisible.value = false
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    editSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">新建用户</el-button>
        <el-button @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="users" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="显示名" min-width="140">
          <template #default="{ row }">{{ row.displayName || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleTagType[row.role] || 'info'" size="small" disable-transitions>
              {{ row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small" disable-transitions>
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
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
          <el-empty description="暂无用户" />
        </template>
      </el-table>
    </el-card>

    <!-- 新建用户 -->
    <el-dialog v-model="createVisible" title="新建用户" width="480px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="createForm.displayName" placeholder="如 张三" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option v-for="r in ROLES" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户 -->
    <el-dialog v-model="editVisible" :title="`编辑用户「${editForm.username}」`" width="480px"
      destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="editForm.displayName" placeholder="如 张三" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="editForm.role" style="width: 100%">
            <el-option v-for="r in ROLES" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
        <el-form-item label="重置密码" prop="password">
          <el-input v-model="editForm.password" type="password" show-password
            placeholder="留空表示不修改密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleEditSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
