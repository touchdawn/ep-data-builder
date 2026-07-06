/**
 * 步骤编辑模型与服务端结构的互转。
 *
 * 服务端 step 结构：
 *   { sortNo, stepType, name, conditionExpr, config, outputs: [{var, expr}] }
 *   API_CALL config: { apiCode, overrideMethod, overridePath, bodyTemplate,
 *                      headerTemplate: {}, queryTemplate: {}, expectHttpStatus,
 *                      asserts: [{expr, op, value}] }
 *   SQL_EXEC config: { schemaCode, sql, expectAffectedRows }
 *
 * 编辑模型把 headerTemplate/queryTemplate 的对象展开为 [{key, value}] 行，方便表格编辑。
 */

let keySeed = 0

export function nextKey() {
  keySeed += 1
  return `step-${keySeed}`
}

function objectToRows(obj) {
  if (!obj || typeof obj !== 'object') return []
  return Object.keys(obj).map((k) => ({ key: k, value: obj[k] == null ? '' : String(obj[k]) }))
}

function rowsToObject(rows) {
  const obj = {}
  ;(rows || []).forEach((r) => {
    if (r.key) obj[r.key] = r.value == null ? '' : r.value
  })
  return obj
}

/** 新建一个空的可编辑步骤 */
export function newEditableStep(stepType) {
  const step = {
    _key: nextKey(),
    stepType,
    name: '',
    conditionExpr: '',
    outputs: []
  }
  if (stepType === 'API_CALL') {
    step.api = {
      apiCode: '',
      overrideMethod: '',
      overridePath: '',
      bodyTemplate: '',
      headerRows: [],
      queryRows: [],
      expectHttpStatus: 200,
      asserts: []
    }
  } else if (stepType === 'SQL_EXEC') {
    step.sqlc = {
      schemaCode: '',
      sql: '',
      expectAffectedRows: 1
    }
  }
  return step
}

/** 服务端 step → 可编辑步骤 */
export function toEditableStep(serverStep) {
  let cfg = serverStep.config
  if (typeof cfg === 'string') {
    try {
      cfg = JSON.parse(cfg)
    } catch (e) {
      cfg = {}
    }
  }
  cfg = cfg || {}

  const step = {
    _key: nextKey(),
    stepType: serverStep.stepType,
    name: serverStep.name || '',
    conditionExpr: serverStep.conditionExpr || '',
    outputs: (serverStep.outputs || []).map((o) => ({ var: o.var || '', expr: o.expr || '' }))
  }

  if (serverStep.stepType === 'API_CALL') {
    step.api = {
      apiCode: cfg.apiCode || '',
      overrideMethod: cfg.overrideMethod || '',
      overridePath: cfg.overridePath || '',
      bodyTemplate: cfg.bodyTemplate || '',
      headerRows: objectToRows(cfg.headerTemplate),
      queryRows: objectToRows(cfg.queryTemplate),
      expectHttpStatus: cfg.expectHttpStatus == null ? 200 : cfg.expectHttpStatus,
      asserts: (cfg.asserts || []).map((a) => ({
        expr: a.expr || '',
        op: a.op || '==',
        value: a.value == null ? '' : String(a.value)
      }))
    }
  } else if (serverStep.stepType === 'SQL_EXEC') {
    step.sqlc = {
      schemaCode: cfg.schemaCode || '',
      sql: cfg.sql || '',
      expectAffectedRows: cfg.expectAffectedRows == null ? null : cfg.expectAffectedRows
    }
  }
  return step
}

/** 可编辑步骤 → 服务端 step */
export function toServerStep(step, sortNo) {
  const out = {
    sortNo,
    stepType: step.stepType,
    name: step.name || '',
    conditionExpr: step.conditionExpr || '',
    outputs: (step.outputs || [])
      .filter((o) => o.var)
      .map((o) => ({ var: o.var, expr: o.expr || '' }))
  }
  if (step.stepType === 'API_CALL') {
    const a = step.api
    out.config = {
      apiCode: a.apiCode || '',
      overrideMethod: a.overrideMethod || null,
      overridePath: a.overridePath || null,
      bodyTemplate: a.bodyTemplate || '',
      headerTemplate: rowsToObject(a.headerRows),
      queryTemplate: rowsToObject(a.queryRows),
      expectHttpStatus: a.expectHttpStatus == null ? 200 : a.expectHttpStatus,
      asserts: (a.asserts || [])
        .filter((x) => x.expr)
        .map((x) => ({ expr: x.expr, op: x.op || '==', value: x.value == null ? '' : x.value }))
    }
  } else if (step.stepType === 'SQL_EXEC') {
    out.config = {
      schemaCode: step.sqlc.schemaCode || '',
      sql: step.sqlc.sql || '',
      expectAffectedRows:
        step.sqlc.expectAffectedRows === '' || step.sqlc.expectAffectedRows == null
          ? null
          : step.sqlc.expectAffectedRows
    }
  }
  return out
}

/** 深拷贝一个可编辑步骤（复制功能用），生成新 _key */
export function cloneEditableStep(step) {
  const plain = JSON.parse(JSON.stringify({ ...step, _key: undefined }))
  plain._key = nextKey()
  return plain
}

export const STEP_TYPE_META = {
  API_CALL: { label: 'API 调用', tagType: 'primary' },
  SQL_EXEC: { label: 'SQL 执行', tagType: 'warning' }
}
