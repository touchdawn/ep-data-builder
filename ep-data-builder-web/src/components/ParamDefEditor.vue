<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * 参数定义可编辑表格。
 * props.params 为父组件持有的数组，本组件直接原地增删改（数组引用不变）。
 * 行结构：{ name, dataType, defaultValue, description, enums: [{value, meaning}] }
 * sortNo 由父组件保存时按行序生成。
 */
const props = defineProps({
  params: { type: Array, required: true },
  readonly: { type: Boolean, default: false }
})

const DATA_TYPES = ['string', 'int', 'long', 'boolean', 'date', 'datetime', 'enum']

const enumDialogVisible = ref(false)
const enumEditing = reactive({ row: null, rows: [] })

function addRow() {
  props.params.push({
    name: '',
    dataType: 'string',
    defaultValue: '',
    description: '',
    enums: []
  })
}

function removeRow(index) {
  props.params.splice(index, 1)
}

function moveRow(index, delta) {
  const target = index + delta
  if (target < 0 || target >= props.params.length) return
  const arr = props.params
  ;[arr[index], arr[target]] = [arr[target], arr[index]]
}

function openEnumEditor(row) {
  enumEditing.row = row
  enumEditing.rows = (row.enums || []).map((e) => ({ value: e.value, meaning: e.meaning }))
  enumDialogVisible.value = true
}

function addEnumRow() {
  enumEditing.rows.push({ value: '', meaning: '' })
}

function removeEnumRow(index) {
  enumEditing.rows.splice(index, 1)
}

function saveEnums() {
  const cleaned = enumEditing.rows.filter((e) => e.value !== '')
  const values = cleaned.map((e) => e.value)
  if (new Set(values).size !== values.length) {
    ElMessage.warning('枚举取值存在重复')
    return
  }
  enumEditing.row.enums = cleaned.map((e) => ({ value: e.value, meaning: e.meaning }))
  enumDialogVisible.value = false
}

function onTypeChange(row) {
  if (row.dataType !== 'enum') row.enums = []
}
</script>

<template>
  <div>
    <el-table :data="props.params" border size="small">
      <el-table-column label="#" type="index" width="50" />
      <el-table-column label="参数名" min-width="150">
        <template #default="{ row }">
          <el-input v-model="row.name" :disabled="readonly" placeholder="如 createdDaysAgo" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="130">
        <template #default="{ row }">
          <el-select v-model="row.dataType" :disabled="readonly" @change="onTypeChange(row)">
            <el-option v-for="t in DATA_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="默认值" min-width="180">
        <template #default="{ row }">
          <el-input v-model="row.defaultValue" :disabled="readonly"
            placeholder="支持模板函数，如 test_${randomStr(8)}" />
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="200">
        <template #default="{ row }">
          <el-input v-model="row.description" :disabled="readonly" placeholder="写给人也写给 LLM" />
        </template>
      </el-table-column>
      <el-table-column label="枚举" width="110">
        <template #default="{ row }">
          <el-button v-if="row.dataType === 'enum'" link type="primary"
            @click="openEnumEditor(row)">
            枚举({{ (row.enums || []).length }})
          </el-button>
          <span v-else class="form-tip">-</span>
        </template>
      </el-table-column>
      <el-table-column v-if="!readonly" label="操作" width="150" fixed="right">
        <template #default="{ $index }">
          <el-button link :disabled="$index === 0" @click="moveRow($index, -1)">上移</el-button>
          <el-button link :disabled="$index === props.params.length - 1"
            @click="moveRow($index, 1)">下移</el-button>
          <el-button link type="danger" @click="removeRow($index)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无参数，Builder 语义要求所有参数都有默认值" :image-size="60" />
      </template>
    </el-table>
    <div v-if="!readonly" style="margin-top: 10px">
      <el-button type="primary" plain @click="addRow">+ 添加参数</el-button>
    </div>

    <el-dialog v-model="enumDialogVisible" title="枚举取值编辑" width="520px" append-to-body>
      <el-table :data="enumEditing.rows" border size="small">
        <el-table-column label="取值 value" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.value" placeholder="如 PERSONAL" />
          </template>
        </el-table-column>
        <el-table-column label="含义 meaning" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.meaning" placeholder="如 个人用户" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeEnumRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 10px">
        <el-button plain size="small" @click="addEnumRow">+ 添加取值</el-button>
      </div>
      <template #footer>
        <el-button @click="enumDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEnums">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
