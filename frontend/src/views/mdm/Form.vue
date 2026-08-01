<template>
  <el-card>
    <template #header>{{ mode === 'create' ? '新增主数据' : '编辑主数据' }}</template>
    <el-form label-width="120px">
      <h3>主表信息</h3>
      <el-form-item label="记录编码" required><el-input v-model="form.record_code" :disabled="mode !== 'create' || submitted" placeholder="请输入记录编码" /></el-form-item>
      <el-form-item v-for="field in fields" :key="field.field_key" :label="field.field_name" :required="field.required">
        <TypedFieldInput v-model="form.data[field.field_key]" :field="field" :disabled="submitted" :options="referenceOptions[field.reference_object_type_id] || []" />
      </el-form-item>

      <el-divider content-position="left">子表信息</el-divider>
      <div v-if="canModify && availableChildTypes.length" class="child-picker">
        <el-select v-model="selectedChildCode" data-test="child-type-select" placeholder="请选择子表类型">
          <el-option v-for="type in availableChildTypes" :key="type.code" :label="type.name || type.code" :value="type.code" />
        </el-select>
        <el-button :disabled="!selectedChildCode || loadingChildCode != null" :loading="loadingChildCode != null" @click="addChildGroup">添加子表</el-button>
      </div>
      <el-alert v-if="!loading && !error && childMetadataMissing" title="服务端未返回可用子表元数据，暂时无法编辑子表。" type="warning" :closable="false" />
      <el-empty v-if="!childGroups.length" description="暂未添加子表" :image-size="60" />
      <el-card v-for="group in childGroups" :key="group.child_code" class="child-group" shadow="never">
        <template #header><div class="group-title"><b>{{ group.name }}</b><el-button v-if="canModify" link type="primary" @click="addChildRow(group)">添加一行</el-button></div></template>
        <div v-for="(row, rowIndex) in group.rows" :key="row.key" class="child-row">
          <template v-if="row.operation !== 'DELETE'">
            <div class="row-title"><span>第 {{ visibleRowNumber(group, rowIndex) }} 行</span><el-button v-if="canModify" link type="danger" @click="removeChildRow(group, rowIndex)">删除此行</el-button></div>
            <el-form-item v-for="field in group.fields" :key="field.field_key" :label="field.field_name" :required="field.required">
              <TypedFieldInput v-model="row.data[field.field_key]" :field="field" :disabled="submitted" :options="referenceOptions[field.reference_object_type_id] || []" />
            </el-form-item>
          </template>
          <el-alert v-else :title="`已标记删除子表记录 ${row.id}`" type="warning" :closable="false" />
        </div>
      </el-card>

      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="submission" :title="submission" type="success" :closable="false" show-icon />
      <div class="actions">
        <el-button v-if="canEdit" data-test="save" :disabled="loading || unavailable || saving || submitted" :loading="saving" type="primary" @click="save">提交审批</el-button>
        <el-button @click="back">返回</el-button>
      </div>
    </el-form>
  </el-card>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createRecord, listChildRecords, listObjectTypes, listRecords, updateRecord } from '../../api/mdm'
import { chineseError, permissionAllowsDepartment } from '../../utils/labels'
import TypedFieldInput from '../../components/mdm/TypedFieldInput.vue'

const route = useRoute(), router = useRouter()
const mode = computed(() => route.meta.mode || 'create')
const objectCode = computed(() => String(route.query.object || ''))
const departmentId = computed(() => Number(route.query.department))
const fields = ref([]), childTypes = ref([]), childGroups = ref([])
const selectedChildCode = ref(''), error = ref(''), submission = ref('')
const loading = ref(true), saving = ref(false), unavailable = ref(false), submitted = ref(false)
const childMetadataMissing = ref(false), loadingChildCode = ref(null)
const referenceOptions = reactive({})
const form = reactive({ id: null, version: null, record_code: '', data: {} })
const originalMasterKeys = ref(new Set())
const currentUser = JSON.parse(localStorage.getItem('user') || '{}')
const canEdit = computed(() => currentUser.is_admin === true || permissionAllowsDepartment('MDM_RECORD_EDIT', departmentId.value))
const canModify = computed(() => canEdit.value && !submitted.value)
const availableChildTypes = computed(() => childTypes.value.filter(type => !childGroups.value.some(group => group.child_code === type.code)))
let rowSequence = 0

function active(items = []) { return items.filter(item => item.status == null || String(item.status).toLowerCase() === 'active') }
function emptyData(definitions) { return Object.fromEntries(definitions.map(field => [field.field_key, field.default_value ?? null])) }

onMounted(async () => {
  try {
    const types = (await listObjectTypes()).data || []
    const current = types.find(item => item.code === objectCode.value)
    if (!current) throw new Error()
    fields.value = active(current.fields)
    childMetadataMissing.value = !Object.prototype.hasOwnProperty.call(current, 'child_types') || !Array.isArray(current.child_types)
    childTypes.value = childMetadataMissing.value ? [] : active(current.child_types).map(type => ({ ...type, fields: active(type.fields) }))
    for (const field of fields.value) form.data[field.field_key] = field.default_value ?? null
    await loadReferences(types)
    if (mode.value !== 'create') {
      const rows = (await listRecords(objectCode.value)).data || []
      const row = rows.find(item => Number(item.id) === Number(route.params.id))
      if (!row) { unavailable.value = true; error.value = '记录不存在或无权访问'; return }
      originalMasterKeys.value = new Set(Object.keys(row.data || {}))
      Object.assign(form, { id: row.id, version: row.version, record_code: row.record_code, data: { ...row.data } })
    }
  } catch (exception) {
    unavailable.value = true
    error.value = chineseError(exception, '加载记录失败')
  } finally { loading.value = false }
})

async function loadReferences(types) {
  const allFields = [...fields.value, ...childTypes.value.flatMap(type => type.fields)]
  const byId = new Map(types.map(type => [Number(type.id), type.code]))
  const ids = [...new Set(allFields.filter(field => String(field.data_type).toUpperCase() === 'REFERENCE').map(field => Number(field.reference_object_type_id)).filter(Number.isFinite))]
  await Promise.all(ids.map(async id => {
    const code = byId.get(id)
    referenceOptions[id] = code ? (await listRecords(code)).data || [] : []
  }))
}

async function addChildGroup() {
  if (!canModify.value || loadingChildCode.value != null) return
  const type = childTypes.value.find(item => item.code === selectedChildCode.value)
  if (!type || childGroups.value.some(group => group.child_code === type.code)) return
  loadingChildCode.value = type.code
  let rows = []
  try { rows = mode.value === 'create' ? [] : (await listChildRecords(form.id, type.code)).data || [] }
  catch (exception) { error.value = chineseError(exception, '加载子表失败'); return }
  finally { loadingChildCode.value = null }
  const group = { child_code: type.code, name: type.name || type.code, fields: type.fields, rows: rows.map(row => ({
    key: `existing-${row.id}`, operation: 'UPDATE', id: row.id, expected_version: row.version,
    originalData: { ...row.data }, data: { ...emptyData(type.fields), ...row.data },
  })) }
  childGroups.value.push(group)
  if (!group.rows.length) addChildRow(group)
  selectedChildCode.value = ''
}

function addChildRow(group) {
  if (!canModify.value) return
  group.rows.push({ key: `new-${++rowSequence}`, operation: 'CREATE', data: emptyData(group.fields) })
}
function removeChildRow(group, index) {
  if (!canModify.value) return
  const row = group.rows[index]
  if (row.id == null) group.rows.splice(index, 1)
  else row.operation = 'DELETE'
}
function visibleRowNumber(group, index) { return group.rows.slice(0, index + 1).filter(row => row.operation !== 'DELETE').length }

function serialize(field, value) {
  if (value == null || value === '') return value
  const type = String(field.data_type || 'STRING').toUpperCase()
  if (type === 'INTEGER' || type === 'REFERENCE') return Number.parseInt(value, 10)
  if (type === 'DECIMAL') return String(value)
  if (type === 'BOOLEAN') return value === true || value === 'true'
  return value
}
function serializedData(definitions, data) { return Object.fromEntries(definitions.map(field => [field.field_key, serialize(field, data[field.field_key])])) }
function compatibleUpdateData(definitions, data, originalKeys) {
  return Object.fromEntries(definitions
    .filter(field => originalKeys.has(field.field_key) || (data[field.field_key] != null && data[field.field_key] !== ''))
    .map(field => [field.field_key, serialize(field, data[field.field_key])]))
}
function sameData(left, right) { return JSON.stringify(left) === JSON.stringify(right) }

function validateFields(definitions, data, originalKeys = null) {
  const missing = definitions.find(field => field.required
    && (originalKeys == null || originalKeys.has(field.field_key) || (data[field.field_key] != null && data[field.field_key] !== ''))
    && (data[field.field_key] == null || data[field.field_key] === ''))
  if (missing) return `${missing.field_name}不能为空`
  const invalidDecimal = definitions.find(field => {
    if (String(field.data_type).toUpperCase() !== 'DECIMAL' || data[field.field_key] == null || data[field.field_key] === '') return false
    const match = String(data[field.field_key]).match(/^-?(\d+)(?:\.(\d+))?$/)
    if (!match) return true
    const scale = field.scale_value ?? 0, precision = field.precision_value ?? 65
    return (match[2]?.length || 0) > scale || match[1].replace(/^0+/, '').length > precision - scale
  })
  return invalidDecimal ? `${invalidDecimal.field_name}格式不符合精度要求` : ''
}
function validate() {
  if (!form.record_code.trim()) return '记录编码不能为空'
  const masterError = validateFields(fields.value, form.data,
    mode.value === 'create' ? null : originalMasterKeys.value)
  if (masterError) return masterError
  for (const group of childGroups.value) for (const row of group.rows) {
    if (row.operation !== 'DELETE') {
      const childError = validateFields(group.fields, row.data,
        row.operation === 'CREATE' ? null : new Set(Object.keys(row.originalData || {})))
      if (childError) return `${group.name}：${childError}`
    }
  }
  return ''
}
function serializeChildren() {
  return childGroups.value.map(group => ({ child_code: group.child_code, rows: group.rows.flatMap(row => {
    if (row.operation === 'DELETE') return [{ operation: 'DELETE', id: row.id, expected_version: row.expected_version }]
    const data = row.operation === 'CREATE' ? serializedData(group.fields, row.data)
      : compatibleUpdateData(group.fields, row.data, new Set(Object.keys(row.originalData || {})))
    if (row.operation === 'CREATE') return [{ operation: 'CREATE', data }]
    if (sameData(data, serializedData(group.fields, row.originalData))) return []
    return [{ operation: 'UPDATE', id: row.id, expected_version: row.expected_version, data }]
  }) })).filter(group => group.rows.length)
}

async function save() {
  if (!canEdit.value || unavailable.value || loading.value || saving.value || submitted.value) return
  error.value = validate(); submission.value = ''
  if (error.value) return
  const payload = {
    operation: mode.value === 'create' ? 'CREATE' : 'UPDATE', object_code: objectCode.value,
    record_id: mode.value === 'create' ? null : form.id,
    expected_version: mode.value === 'create' ? null : form.version,
    record_code: form.record_code, department_id: departmentId.value,
    data: mode.value === 'create' ? serializedData(fields.value, form.data)
      : compatibleUpdateData(fields.value, form.data, originalMasterKeys.value),
    children: serializeChildren(),
  }
  saving.value = true
  try {
    const response = mode.value === 'create' ? await createRecord(objectCode.value, payload) : await updateRecord(objectCode.value, payload)
    if (response?.data?.status === 'PENDING') { submission.value = '已提交审批，审批通过后才会生效。'; submitted.value = true }
    else submission.value = '请求已提交，请在审批中心查看状态。'
  } catch (exception) { error.value = chineseError(exception, '提交审批失败') }
  finally { saving.value = false }
}
function back() { router.push({ path: '/mdm', query: route.query }) }
defineExpose({ form, selectedChildCode, childGroups, submitted, addChildGroup, addChildRow, removeChildRow, save })
</script>
<style scoped>
h3{margin-top:0}.child-picker,.group-title,.row-title,.actions{display:flex;align-items:center;gap:12px}.child-picker{margin-bottom:16px}.group-title,.row-title{justify-content:space-between}.child-group{margin-bottom:16px}.child-row{padding:12px 0;border-bottom:1px solid #ebeef5}.child-row:last-child{border-bottom:0}.actions{margin-top:18px}
</style>
