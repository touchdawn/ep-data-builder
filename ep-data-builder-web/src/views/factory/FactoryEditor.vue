<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFactory, updateFactory } from '../../api/factory'
import { useUserStore } from '../../stores/user'
import { toEditableStep, toServerStep } from '../../utils/steps'
import ParamDefEditor from '../../components/ParamDefEditor.vue'
import StepListEditor from '../../components/StepListEditor.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const factoryId = route.params.id
const loading = ref(false)
const saving = ref(false)
const activeTab = ref('basic')

const basic = reactive({
  id: null,
  code: '',
  name: '',
  description: '',
  owner: '',
  enabled: true,
  lockVersion: 0
})

// 参数与步骤：持有数组引用不变，子组件原地编辑
const params = reactive([])
const steps = reactive([])

const paramNames = computed(() => params.map((p) => p.name).filter(Boolean))

const isPureSql = computed(
  () => steps.length > 0 && steps.every((s) => s.stepType !== 'API_CALL')
)

async function load() {
  loading.value = true
  try {
    const data = await getFactory(factoryId)
    basic.id = data.id
    basic.code = data.code
    basic.name = data.name
    basic.description = data.description || ''
    basic.owner = data.owner || ''
    basic.enabled = !!data.enabled
    basic.lockVersion = data.lockVersion || 0

    params.splice(0, params.length)
    ;(data.params || [])
      .slice()
      .sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0))
      .forEach((p) =>
        params.push({
          name: p.name || '',
          dataType: p.dataType || 'string',
          defaultValue: p.defaultValue == null ? '' : p.defaultValue,
          description: p.description || '',
          enums: (p.enums || []).map((e) => ({ value: e.value, meaning: e.meaning }))
        })
      )

    steps.splice(0, steps.length)
    ;(data.steps || [])
      .slice()
      .sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0))
      .forEach((s) => steps.push(toEditableStep(s)))
  } finally {
    loading.value = false
  }
}

function validateBeforeSave() {
  if (!basic.name) {
    ElMessage.warning('工厂名称不能为空')
    activeTab.value = 'basic'
    return false
  }
  if (!basic.description) {
    ElMessage.warning('功能描述不能为空：它决定 LLM 造数推理的检索命中率')
    activeTab.value = 'basic'
    return false
  }
  for (let i = 0; i < params.length; i++) {
    const p = params[i]
    if (!p.name) {
      ElMessage.warning(`第 ${i + 1} 个参数缺少参数名`)
      activeTab.value = 'params'
      return false
    }
    if (p.dataType === 'enum' && !(p.enums || []).length) {
      ElMessage.warning(`枚举参数「${p.name}」尚未配置枚举取值`)
      activeTab.value = 'params'
      return false
    }
  }
  const names = params.map((p) => p.name)
  if (new Set(names).size !== names.length) {
    ElMessage.warning('参数名存在重复')
    activeTab.value = 'params'
    return false
  }
  for (let i = 0; i < steps.length; i++) {
    const s = steps[i]
    const label = `步骤 ${i + 1}「${s.name || '未命名'}」`
    if (s.stepType === 'API_CALL') {
      if (!s.api.apiCode && !(s.api.overrideMethod && s.api.overridePath)) {
        ElMessage.warning(`${label}：请填写 apiCode，或同时填写 Method/Path 覆盖（脱契约兜底）`)
        activeTab.value = 'steps'
        return false
      }
    } else if (s.stepType === 'SQL_EXEC') {
      if (!s.sqlc.schemaCode) {
        ElMessage.warning(`${label}：请填写 schemaCode`)
        activeTab.value = 'steps'
        return false
      }
      if (!s.sqlc.sql) {
        ElMessage.warning(`${label}：请填写 SQL 模板`)
        activeTab.value = 'steps'
        return false
      }
    }
  }
  return true
}

async function doSave() {
  const payload = {
    id: basic.id,
    code: basic.code,
    name: basic.name,
    description: basic.description,
    owner: basic.owner,
    enabled: basic.enabled,
    lockVersion: basic.lockVersion,
    params: params.map((p, i) => ({
      name: p.name,
      dataType: p.dataType,
      defaultValue: p.defaultValue,
      description: p.description,
      enums: p.dataType === 'enum' ? p.enums : [],
      sortNo: i + 1
    })),
    steps: steps.map((s, i) => toServerStep(s, i + 1))
  }
  saving.value = true
  try {
    await updateFactory(factoryId, payload)
    ElMessage.success('保存成功')
    await load() // 重新加载以获取新的 lockVersion
  } catch (e) {
    // 拦截器已提示（含乐观锁冲突）
  } finally {
    saving.value = false
  }
}

async function handleSave() {
  if (!validateBeforeSave()) return
  // 参数说明缺失：告警但允许强行保存
  const missingDesc = params.filter((p) => !p.description).map((p) => p.name)
  // 纯 SQL 工厂：强提醒表结构漂移风险
  const warnings = []
  if (missingDesc.length) {
    warnings.push(`以下参数缺少说明：${missingDesc.join('、')}（影响 LLM 检索与调用方理解）`)
  }
  if (isPureSql.value) {
    warnings.push('当前为纯 SQL 工厂（无 API_CALL 步骤），需自行承担表结构漂移风险，列表页将标记「纯SQL」')
  }
  if (warnings.length) {
    try {
      await ElMessageBox.confirm(warnings.join('\n\n'), '保存前请确认', {
        confirmButtonText: '仍要保存',
        cancelButtonText: '返回修改',
        type: 'warning'
      })
    } catch (e) {
      return
    }
  }
  await doSave()
}

function goBack() {
  router.push('/factories')
}

onMounted(load)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-card shadow="never" class="editor-header-card">
      <div class="editor-header">
        <div class="editor-title">
          <el-button link @click="goBack">← 返回列表</el-button>
          <span class="factory-name">{{ basic.name || '（未命名工厂）' }}</span>
          <el-tag size="small" type="info" disable-transitions>{{ basic.code }}</el-tag>
          <el-tag v-if="isPureSql" size="small" type="warning" disable-transitions>纯SQL</el-tag>
          <el-tag :type="basic.enabled ? 'success' : 'info'" size="small" disable-transitions>
            {{ basic.enabled ? '启用' : '停用' }}
          </el-tag>
        </div>
        <div v-if="userStore.canWrite">
          <el-button type="primary" :loading="saving" @click="handleSave">保 存</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-form label-width="100px" style="max-width: 720px" :disabled="!userStore.canWrite">
            <el-form-item label="编码">
              <el-input v-model="basic.code" disabled />
              <div class="form-tip">编码是工厂的全局唯一标识（{moduleCode}.{实体名}），创建后不可修改</div>
            </el-form-item>
            <el-form-item label="名称" required>
              <el-input v-model="basic.name" placeholder="如 用户（可指定注册时长）" />
            </el-form-item>
            <el-form-item label="功能描述" required>
              <el-input v-model="basic.description" type="textarea" :rows="5"
                placeholder="写清楚：造的是什么数据、默认产出什么状态、可以定制哪些关键维度。例：创建平台用户，默认当天注册的 NORMAL 个人用户；可指定注册时长（createdDaysAgo）造老用户" />
              <div class="form-tip">描述写给人也写给 LLM——它是"工厂检索/造数推理"的匹配依据，质量决定命中率</div>
            </el-form-item>
            <el-form-item label="负责人">
              <el-input v-model="basic.owner" placeholder="负责人姓名或账号" style="max-width: 300px" />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="basic.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane name="params">
          <template #label>参数定义（{{ params.length }}）</template>
          <div class="form-tip" style="margin-bottom: 10px">
            Builder 语义：所有参数都应有默认值，空参即可造出"能用的默认数据"；默认值支持模板函数（now/daysAgo/randomStr/randomInt/uuid/seq）
          </div>
          <ParamDefEditor :params="params" :readonly="!userStore.canWrite" />
        </el-tab-pane>

        <el-tab-pane name="steps">
          <template #label>步骤编排（{{ steps.length }}）</template>
          <div class="form-tip" style="margin-bottom: 10px">
            推荐"API 先行、SQL 补刀"：先 API_CALL 保证数据合理合法，再 SQL_EXEC 精准修改个别字段（如把创建时间改到 5 年前）
          </div>
          <StepListEditor :steps="steps" :param-names="paramNames" :readonly="!userStore.canWrite" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.editor-header-card {
  margin-bottom: 12px;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.editor-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.factory-name {
  font-size: 17px;
  font-weight: 700;
}
</style>
