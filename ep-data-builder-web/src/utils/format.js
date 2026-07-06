/** 时间格式化：兼容 epoch 毫秒（数字或数字字符串）与 ISO 字符串 */
export function formatTime(value) {
  if (value === null || value === undefined || value === '') return '-'
  let date
  if (typeof value === 'number') {
    date = new Date(value)
  } else if (/^\d+$/.test(String(value))) {
    date = new Date(Number(value))
  } else {
    date = new Date(value)
  }
  if (isNaN(date.getTime())) return String(value)
  const p = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())} ${p(
    date.getHours()
  )}:${p(date.getMinutes())}:${p(date.getSeconds())}`
}

/** JSON 美化：入参可为对象或 JSON 字符串，解析失败时原样返回 */
export function prettyJson(value) {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'object') {
    return JSON.stringify(value, null, 2)
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (e) {
    return String(value)
  }
}

/** 耗时展示 */
export function formatDuration(ms) {
  if (ms === null || ms === undefined || ms === '') return '-'
  const n = Number(ms)
  if (isNaN(n)) return String(ms)
  if (n < 1000) return `${n} ms`
  return `${(n / 1000).toFixed(2)} s`
}
