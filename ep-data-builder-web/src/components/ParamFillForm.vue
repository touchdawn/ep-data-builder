<script setup>
import { watch } from 'vue'

/**
 * 按工厂参数 schema 动态渲染的填参表单（造数控制台等场景共用）。
 * props.modelValue：{ 参数名: 值 }，仅收录用户显式填写的参数；
 * 留空 = 使用工厂默认值（Builder 语义），提交前请用 collect() 取有效覆盖参数。
 */
const props = defineProps({
  params: { type: Array, default: () => [] },
  modelValue: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue'])

watch(
  () => props.params,
  () => {
    // 工厂切换后清掉旧参数值
    emit('update:modelValue', {})
  }
)

function setValue(name, value) {
  const next = { ...props.modelValue }
  if (value === '' || value === null || value === undefined) {
    delete next[name]
  } else {
    next[name] = value
  }
  emit('update:modelValue', next)
}

/** 收集有效的覆盖参数（去掉空值） */
function collect() {
  const result = {}
  Object.keys(props.modelValue).forEach((k) => {
    const v = props.modelValue[k]
    if (v !== '' && v !== null && v !== undefined) result[k] = v
  })
  return result
}

defineExpose({ collect })
</script>

<template>
  <el-form label-width="160px" class="param-fill-form">
    <el-empty v-if="!params.length" description="该工厂没有参数，空参即可造出默认数据" :image-size="60" />
    <el-form-item v-for="p in params" :key="p.name" :label="p.name">
      <!-- 数值 -->
      <el-input-number v-if="p.dataType === 'int' || p.dataType === 'long'"
        :model-value="modelValue[p.name]" :controls="false" style="width: 220px"
        :placeholder="p.defaultValue != null && p.defaultValue !== '' ? `默认 ${p.defaultValue}` : '留空使用默认值'"
        @update:model-value="(v) => setValue(p.name, v)" />
      <!-- 布尔：留空=默认 -->
      <el-select v-else-if="p.dataType === 'boolean'" :model-value="modelValue[p.name]" clearable
        style="width: 220px" placeholder="留空使用默认值"
        @update:model-value="(v) => setValue(p.name, v)">
        <el-option label="true" :value="true" />
        <el-option label="false" :value="false" />
      </el-select>
      <!-- 日期 -->
      <el-date-picker v-else-if="p.dataType === 'date'" :model-value="modelValue[p.name]"
        type="date" value-format="YYYY-MM-DD" style="width: 220px" placeholder="留空使用默认值"
        @update:model-value="(v) => setValue(p.name, v)" />
      <!-- 日期时间 -->
      <el-date-picker v-else-if="p.dataType === 'datetime'" :model-value="modelValue[p.name]"
        type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 220px"
        placeholder="留空使用默认值" @update:model-value="(v) => setValue(p.name, v)" />
      <!-- 枚举 -->
      <el-select v-else-if="p.dataType === 'enum'" :model-value="modelValue[p.name]" clearable
        style="width: 220px" placeholder="留空使用默认值"
        @update:model-value="(v) => setValue(p.name, v)">
        <el-option v-for="e in p.enums || []" :key="e.value"
          :label="e.meaning ? `${e.value}（${e.meaning}）` : e.value" :value="e.value" />
      </el-select>
      <!-- 字符串 -->
      <el-input v-else :model-value="modelValue[p.name]" style="width: 320px"
        :placeholder="p.defaultValue != null && p.defaultValue !== '' ? `默认 ${p.defaultValue}` : '留空使用默认值'"
        clearable @update:model-value="(v) => setValue(p.name, v)" />
      <div v-if="p.description" class="form-tip param-tip">{{ p.description }}</div>
    </el-form-item>
  </el-form>
</template>

<style scoped>
.param-tip {
  width: 100%;
  margin-top: 2px;
}
</style>
