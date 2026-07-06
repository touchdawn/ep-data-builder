<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDatasources,
  createDatasource,
  updateDatasource,
  deleteDatasource,
  testDatasourceConnection
} from '../../api/env'
import { useUserStore } from '../../stores/user'

const props = defineProps({
  envId: { type: [Number, String], required: true },
  envCode: { type: String, default: '' }
})

const userStore = useUserStore()

const loading = ref(false)
const datasources = ref([])
const testingId = ref(null)

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null,
  schemaCode: '',
  dbType: 'MYSQL',
  channel: 'HUNTER',
  jdbcUrl: '',
  username: '',
  password: ''
})

const dbTypes = ['MYSQL', 'DM', 'ORACLE', 'POSTGRESQL', 'SQLSERVER']

const rules = {
  schemaCode: [
    { required: true, message: '请输入 schemaCode，如 mysql.TMy27MAIN-user_center', trigger: 'blur' }
  ],
  dbType: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  channel: [{ required: true, message: '请选择通道', trigger: 'change' }],
  jdbcUrl: [
    {
      validator: (rule, value, callback) => {
        if (form.channel === 'DIRECT' && !value) callback(new Error('DIRECT 通道必须填写 JDBC URL'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  username: [
    {
      validator: (rule, value, callback) => {
        if (form.channel === 'DIRECT' && !value) callback(new Error('DIRECT 通道必须填写账号'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  password: [
    {
      validator: (rule, value, callback) => {
        // 新建的 DIRECT 数据源必须填密码；编辑时留空表示不修改
        if (form.channel === 'DIRECT' && form.id == null && !value) {
          callback(new Error('DIRECT 通道必须填写密码'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function load() {
  loading.value = true
  try {
    datasources.value = (await listDatasources(props.envId)) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.id = null
  form.schemaCode = ''
  form.dbType = 'MYSQL'
  form.channel = 'HUNTER'
  form.jdbcUrl = ''
  form.username = ''
  form.password = ''
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.schemaCode = row.schemaCode
  form.dbType = row.dbType
  form.channel = row.channel || 'HUNTER'
  form.jdbcUrl = row.jdbcUrl || ''
  form.username = row.username || ''
  form.password = '' // 列表不回显密码；留空 = 不修改
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
    const payload = {
      schemaCode: form.schemaCode,
      dbType: form.dbType,
      channel: form.channel,
      jdbcUrl: form.channel === 'DIRECT' ? form.jdbcUrl : null,
      username: form.channel === 'DIRECT' ? form.username : null,
      password: form.channel === 'DIRECT' ? form.password : null
    }
    if (form.id == null) {
      await createDatasource(props.envId, payload)
      ElMessage.success('数据源创建成功')
    } else {
      await updateDatasource(form.id, payload)
      ElMessage.success('数据源更新成功')
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
  ElMessageBox.confirm(`确定删除数据源「${row.schemaCode}」吗？`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await deleteDatasource(row.id)
      ElMessage.success('删除成功')
      await load()
    })
    .catch(() => {})
}

async function handleTest(row) {
  testingId.value = row.id
  try {
    await testDatasourceConnection(row.id)
    ElMessage.success(`数据源「${row.schemaCode}」连接成功`)
  } catch (e) {
    // 失败提示由拦截器统一处理
  } finally {
    testingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <el-button v-if="userStore.canWrite" type="primary" size="small" @click="openCreate">
        新增数据源
      </el-button>
      <el-button size="small" @click="load">刷新</el-button>
      <span class="form-tip">HUNTER 通道免凭证；DIRECT 兜底通道需 JDBC 连接信息</span>
    </div>
    <el-table v-loading="loading" :data="datasources" size="small" border>
      <el-table-column prop="schemaCode" label="schemaCode" min-width="220" show-overflow-tooltip />
      <el-table-column prop="dbType" label="数据库类型" width="110" />
      <el-table-column label="通道" width="100">
        <template #default="{ row }">
          <el-tag :type="row.channel === 'DIRECT' ? 'warning' : 'success'" size="small">
            {{ row.channel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="jdbcUrl" label="JDBC URL" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.jdbcUrl || '-' }}</template>
      </el-table-column>
      <el-table-column prop="username" label="账号" width="120">
        <template #default="{ row }">{{ row.username || '-' }}</template>
      </el-table-column>
      <el-table-column v-if="userStore.canWrite" label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" size="small" :loading="testingId === row.id"
            @click="handleTest(row)">
            测试连接
          </el-button>
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据源" :image-size="60" />
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id == null ? '新增数据源' : '编辑数据源'" width="600px"
      destroy-on-close append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="schemaCode" prop="schemaCode">
          <el-input v-model="form.schemaCode" placeholder="如 mysql.TMy27MAIN-user_center（对齐猎户）" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="dbType">
          <el-select v-model="form.dbType" style="width: 100%">
            <el-option v-for="t in dbTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="通道" prop="channel">
          <el-radio-group v-model="form.channel">
            <el-radio value="HUNTER">HUNTER（主通道，免凭证）</el-radio>
            <el-radio value="DIRECT">DIRECT（直连兜底）</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.channel === 'DIRECT'">
          <el-form-item label="JDBC URL" prop="jdbcUrl">
            <el-input v-model="form.jdbcUrl" placeholder="如 jdbc:mysql://10.0.0.1:3306/user_center" />
          </el-form-item>
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="数据库账号" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password
              :placeholder="form.id == null ? '数据库密码' : '留空表示不修改密码'" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
