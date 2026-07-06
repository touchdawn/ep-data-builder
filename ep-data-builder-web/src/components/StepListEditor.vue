<script setup>
import { nextTick, reactive } from 'vue'
import { ElMessageBox } from 'element-plus'
import { newEditableStep, cloneEditableStep, STEP_TYPE_META } from '../utils/steps'

/**
 * 步骤编排编辑器：垂直卡片列表。
 * props.steps 为父组件持有的可编辑步骤数组（见 utils/steps.js 的编辑模型），原地增删改。
 * props.paramNames 为工厂参数名列表，与前序步骤 outputs 一起构成"可用变量"。
 */
const props = defineProps({
  steps: { type: Array, required: true },
  paramNames: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false }
})

const OPS = ['==', '!=', '>', '<', '>=', '<=']
const METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

const expanded = reactive({})
const taRefs = {}

function setTaRef(key, el) {
  if (el) taRefs[key] = el
  else delete taRefs[key]
}

function toggle(step) {
  expanded[step._key] = !expanded[step._key]
}

function addStep(stepType) {
  const step = newEditableStep(stepType)
  props.steps.push(step)
  expanded[step._key] = true
}

function moveStep(index, delta) {
  const target = index + delta
  if (target < 0 || target >= props.steps.length) return
  const arr = props.steps
  ;[arr[index], arr[target]] = [arr[target], arr[index]]
}

function copyStep(index) {
  const copy = cloneEditableStep(props.steps[index])
  copy.name = copy.name ? `${copy.name}-副本` : ''
  props.steps.splice(index + 1, 0, copy)
  expanded[copy._key] = true
}

function removeStep(index) {
  const step = props.steps[index]
  ElMessageBox.confirm(`确定删除步骤 ${index + 1}「${step.name || '未命名'}」吗？`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      delete expanded[step._key]
      props.steps.splice(index, 1)
    })
    .catch(() => {})
}

/** 某步骤可用变量 = 工厂参数 + 前序步骤 outputs 的 var */
function availableVars(index) {
  const vars = [...props.paramNames.filter(Boolean)]
  for (let i = 0; i < index; i++) {
    ;(props.steps[i].outputs || []).forEach((o) => {
      if (o.var && !vars.includes(o.var)) vars.push(o.var)
    })
  }
  return vars
}

function getTemplateText(step) {
  return step.stepType === 'API_CALL' ? step.api.bodyTemplate : step.sqlc.sql
}

function setTemplateText(step, text) {
  if (step.stepType === 'API_CALL') step.api.bodyTemplate = text
  else step.sqlc.sql = text
}

/** 在模板 textarea 光标处插入 ${varName} */
function insertVariable(step, varName) {
  if (props.readonly) return
  const snippet = '${' + varName + '}'
  const current = getTemplateText(step) || ''
  const comp = taRefs[step._key]
  const ta =
    comp && (comp.textarea || (comp.$el && comp.$el.querySelector && comp.$el.querySelector('textarea')))
  if (ta && typeof ta.selectionStart === 'number') {
    const start = ta.selectionStart
    const end = ta.selectionEnd
    setTemplateText(step, current.slice(0, start) + snippet + current.slice(end))
    nextTick(() => {
      ta.focus()
      ta.selectionStart = ta.selectionEnd = start + snippet.length
    })
  } else {
    setTemplateText(step, current + snippet)
  }
}

function addOutput(step) {
  step.outputs.push({ var: '', expr: '' })
}

function removeOutput(step, index) {
  step.outputs.splice(index, 1)
}

function addAssert(step) {
  step.api.asserts.push({ expr: '', op: '==', value: '' })
}

function removeAssert(step, index) {
  step.api.asserts.splice(index, 1)
}

function addKvRow(rows) {
  rows.push({ key: '', value: '' })
}

function removeKvRow(rows, index) {
  rows.splice(index, 1)
}

function typeMeta(stepType) {
  return STEP_TYPE_META[stepType] || { label: stepType, tagType: 'info' }
}
</script>

<template>
  <div class="step-list-editor">
    <el-empty v-if="!steps.length" description='暂无步骤。推荐"API 先行、SQL 补刀"：先 API_CALL 造合法数据，再 SQL_EXEC 精准修改个别字段' />

    <el-card v-for="(step, index) in steps" :key="step._key" class="step-card" shadow="never">
      <template #header>
        <div class="step-header">
          <div class="step-header-left" @click="toggle(step)">
            <span class="step-index">{{ index + 1 }}</span>
            <el-tag :type="typeMeta(step.stepType).tagType" size="small" disable-transitions>
              {{ typeMeta(step.stepType).label }}
            </el-tag>
            <span class="step-name">{{ step.name || '未命名步骤' }}</span>
            <el-tag v-if="step.conditionExpr" size="small" type="info" disable-transitions>
              条件: {{ step.conditionExpr }}
            </el-tag>
          </div>
          <div v-if="!readonly" class="step-header-actions">
            <el-button link :disabled="index === 0" @click="moveStep(index, -1)">上移</el-button>
            <el-button link :disabled="index === steps.length - 1" @click="moveStep(index, 1)">
              下移
            </el-button>
            <el-button link @click="copyStep(index)">复制</el-button>
            <el-button link type="danger" @click="removeStep(index)">删除</el-button>
            <el-button link type="primary" @click="toggle(step)">
              {{ expanded[step._key] ? '收起' : '展开' }}
            </el-button>
          </div>
          <div v-else class="step-header-actions">
            <el-button link type="primary" @click="toggle(step)">
              {{ expanded[step._key] ? '收起' : '展开' }}
            </el-button>
          </div>
        </div>
      </template>

      <div v-show="expanded[step._key]">
        <el-form label-width="130px" size="default" :disabled="readonly">
          <el-form-item label="步骤名">
            <el-input v-model="step.name" placeholder="如 创建用户 / 修改注册时间" style="max-width: 420px" />
          </el-form-item>
          <el-form-item label="条件表达式">
            <el-input v-model="step.conditionExpr"
              placeholder="留空=总是执行；仅支持 变量 op 常量 与 and/or，如 createdDaysAgo > 0"
              style="max-width: 420px" />
          </el-form-item>

          <!-- API_CALL 专属表单 -->
          <template v-if="step.stepType === 'API_CALL'">
            <el-form-item label="apiCode">
              <el-input v-model="step.api.apiCode" placeholder="契约平台接口编码，如 user-center.createUser"
                style="max-width: 420px" />
            </el-form-item>
            <el-form-item label="Method 覆盖">
              <el-select v-model="step.api.overrideMethod" clearable placeholder="留空以契约为准（脱契约兜底用）"
                style="width: 220px">
                <el-option v-for="m in METHODS" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item label="Path 覆盖">
              <el-input v-model="step.api.overridePath" placeholder="留空以契约为准，如 /api/user/create"
                style="max-width: 420px" />
            </el-form-item>

            <el-form-item label="Header 模板">
              <div class="kv-editor">
                <div v-for="(row, ri) in step.api.headerRows" :key="ri" class="kv-row">
                  <el-input v-model="row.key" placeholder="Header 名" class="kv-key" />
                  <el-input v-model="row.value" placeholder="值，支持 ${变量}" class="kv-value" />
                  <el-button v-if="!readonly" link type="danger"
                    @click="removeKvRow(step.api.headerRows, ri)">删除</el-button>
                </div>
                <el-button v-if="!readonly" plain size="small"
                  @click="addKvRow(step.api.headerRows)">+ 添加 Header</el-button>
              </div>
            </el-form-item>
            <el-form-item label="Query 模板">
              <div class="kv-editor">
                <div v-for="(row, ri) in step.api.queryRows" :key="ri" class="kv-row">
                  <el-input v-model="row.key" placeholder="参数名" class="kv-key" />
                  <el-input v-model="row.value" placeholder="值，支持 ${变量}" class="kv-value" />
                  <el-button v-if="!readonly" link type="danger"
                    @click="removeKvRow(step.api.queryRows, ri)">删除</el-button>
                </div>
                <el-button v-if="!readonly" plain size="small"
                  @click="addKvRow(step.api.queryRows)">+ 添加参数</el-button>
              </div>
            </el-form-item>

            <el-form-item label="Body 模板">
              <div class="template-editor">
                <el-input :ref="(el) => setTaRef(step._key, el)" v-model="step.api.bodyTemplate"
                  type="textarea" :rows="8"
                  placeholder='请求体 JSON 模板，如 { "userAccount": { "userName": "${userName}" } }' />
                <div class="var-panel">
                  <div class="var-panel-title">可用变量（点击插入）</div>
                  <template v-if="availableVars(index).length">
                    <el-tag v-for="v in availableVars(index)" :key="v" class="var-tag" size="small"
                      @click="insertVariable(step, v)">${{ '{' }}{{ v }}}</el-tag>
                  </template>
                  <div v-else class="form-tip">暂无：先定义参数或在前序步骤配置输出变量</div>
                  <div class="form-tip var-fn-tip">
                    内置函数：now() / daysAgo(n) / hoursAgo(n) / randomStr(n) / randomInt(a,b) / uuid() / seq()
                  </div>
                </div>
              </div>
            </el-form-item>

            <el-form-item label="期望状态码">
              <el-input-number v-model="step.api.expectHttpStatus" :min="100" :max="599"
                :controls="false" style="width: 140px" />
            </el-form-item>

            <el-form-item label="响应断言">
              <div class="sub-table-wrap">
                <el-table :data="step.api.asserts" size="small" border>
                  <el-table-column label="JSONPath 表达式" min-width="200">
                    <template #default="{ row }">
                      <el-input v-model="row.expr" :disabled="readonly" placeholder="如 $.code" />
                    </template>
                  </el-table-column>
                  <el-table-column label="比较符" width="110">
                    <template #default="{ row }">
                      <el-select v-model="row.op" :disabled="readonly">
                        <el-option v-for="op in OPS" :key="op" :label="op" :value="op" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="期望值" min-width="140">
                    <template #default="{ row }">
                      <el-input v-model="row.value" :disabled="readonly" placeholder='如 "0"' />
                    </template>
                  </el-table-column>
                  <el-table-column v-if="!readonly" label="操作" width="80">
                    <template #default="{ $index }">
                      <el-button link type="danger" @click="removeAssert(step, $index)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-button v-if="!readonly" plain size="small" style="margin-top: 8px"
                  @click="addAssert(step)">+ 添加断言</el-button>
              </div>
            </el-form-item>
          </template>

          <!-- SQL_EXEC 专属表单 -->
          <template v-else-if="step.stepType === 'SQL_EXEC'">
            <el-form-item label="schemaCode">
              <el-input v-model="step.sqlc.schemaCode"
                placeholder="目标数据源，如 mysql.TMy27MAIN-user_center" style="max-width: 420px" />
            </el-form-item>
            <el-form-item label="SQL 模板">
              <div class="template-editor">
                <el-input :ref="(el) => setTaRef(step._key, el)" v-model="step.sqlc.sql"
                  type="textarea" :rows="8"
                  placeholder="如 UPDATE t_user SET created_time = ${daysAgo(createdDaysAgo)} WHERE id = ${userId}" />
                <div class="var-panel">
                  <div class="var-panel-title">可用变量（点击插入）</div>
                  <template v-if="availableVars(index).length">
                    <el-tag v-for="v in availableVars(index)" :key="v" class="var-tag" size="small"
                      @click="insertVariable(step, v)">${{ '{' }}{{ v }}}</el-tag>
                  </template>
                  <div v-else class="form-tip">暂无：先定义参数或在前序步骤配置输出变量</div>
                  <div class="form-tip var-fn-tip">
                    内置函数：now() / daysAgo(n) / hoursAgo(n) / randomStr(n) / randomInt(a,b) / uuid() / seq()
                  </div>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="期望影响行数">
              <el-input-number v-model="step.sqlc.expectAffectedRows" :min="0" :controls="false"
                style="width: 140px" placeholder="留空不校验" />
              <span class="form-tip" style="margin-left: 8px">留空表示不校验影响行数</span>
            </el-form-item>
          </template>

          <!-- 输出变量 -->
          <el-form-item label="输出变量">
            <div class="sub-table-wrap">
              <el-table :data="step.outputs" size="small" border>
                <el-table-column label="变量名 var" min-width="140">
                  <template #default="{ row }">
                    <el-input v-model="row.var" :disabled="readonly" placeholder="如 userId" />
                  </template>
                </el-table-column>
                <el-table-column label="提取表达式 expr" min-width="220">
                  <template #default="{ row }">
                    <el-input v-model="row.expr" :disabled="readonly"
                      :placeholder="step.stepType === 'API_CALL' ? 'JSONPath，如 $.data.userId' : '提取表达式'" />
                  </template>
                </el-table-column>
                <el-table-column v-if="!readonly" label="操作" width="80">
                  <template #default="{ $index }">
                    <el-button link type="danger" @click="removeOutput(step, $index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button v-if="!readonly" plain size="small" style="margin-top: 8px"
                @click="addOutput(step)">+ 添加输出</el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <div v-if="!readonly" class="add-step-bar">
      <el-dropdown trigger="click" @command="addStep">
        <el-button type="primary" plain>+ 新增步骤</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="API_CALL">API_CALL — 调业务接口</el-dropdown-item>
            <el-dropdown-item command="SQL_EXEC">SQL_EXEC — 执行 SQL</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.step-card {
  margin-bottom: 12px;
}

.step-card :deep(.el-card__header) {
  padding: 10px 16px;
}

.step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.step-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex: 1;
  min-width: 240px;
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 12px;
  flex-shrink: 0;
}

.step-name {
  font-weight: 600;
}

.step-header-actions {
  display: flex;
  align-items: center;
}

.template-editor {
  display: flex;
  gap: 12px;
  width: 100%;
  align-items: flex-start;
}

.template-editor > .el-textarea,
.template-editor > :first-child {
  flex: 1;
}

.var-panel {
  width: 260px;
  flex-shrink: 0;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  padding: 8px 10px;
  background: #fafafa;
}

.var-panel-title {
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
  font-weight: 600;
}

.var-tag {
  margin: 0 6px 6px 0;
  cursor: pointer;
}

.var-fn-tip {
  margin-top: 6px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 6px;
}

.kv-editor {
  width: 100%;
}

.kv-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.kv-key {
  width: 220px;
}

.kv-value {
  flex: 1;
  max-width: 420px;
}

.sub-table-wrap {
  width: 100%;
  max-width: 720px;
}

.add-step-bar {
  margin-top: 4px;
}
</style>
