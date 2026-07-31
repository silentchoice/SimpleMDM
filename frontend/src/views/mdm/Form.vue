<template>
  <el-card>
    <template #header>{{ mode === 'create' ? '新增主数据' : '编辑主数据' }}</template>
    <el-form label-width="120px">
      <el-form-item label="记录编码" required><el-input v-model="form.record_code" :disabled="mode !== 'create'" /></el-form-item>
      <el-form-item v-for="field in fields" :key="field.field_key" :label="field.field_name" :required="field.required">
        <TypedFieldInput v-model="form.data[field.field_key]" :field="field" :options="referenceOptions[field.reference_object_type_id] || []" />
      </el-form-item>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-button v-if="canEdit" data-test="save" :disabled="loading || unavailable || saving" :loading="saving" type="primary" @click="save">保存</el-button>
      <el-button @click="back">返回</el-button>
    </el-form>
  </el-card>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createRecord, listObjectTypes, listRecords, updateRecord } from '../../api/mdm'
import TypedFieldInput from '../../components/mdm/TypedFieldInput.vue'
const route = useRoute()
const router = useRouter()
const mode = computed(() => route.meta.mode || 'create')
const objectCode = computed(() => String(route.query.object || ''))
const departmentId = computed(() => Number(route.query.department))
const fields = ref([])
const error = ref('')
const loading = ref(true)
const saving = ref(false)
const unavailable = ref(false)
const referenceOptions = reactive({})
const form = reactive({ id: null, version: null, record_code: '', data: {} })
const permissions = JSON.parse(localStorage.getItem('permissions') || '[]')
const canEdit = computed(() => permissions.some(item =>
  (item.code === 'MDM_RECORD_EDIT' || item.permission_code === 'MDM_RECORD_EDIT') &&
  (Array.isArray(item.editable_department_ids)
    ? item.editable_department_ids.map(Number).includes(departmentId.value)
    : item.can_edit === true)
))
onMounted(async () => {
 try {
  const types = (await listObjectTypes()).data || []
  fields.value = types.find(item => item.code === objectCode.value)?.fields || []
  for (const field of fields.value) form.data[field.field_key] = null
  const byId = new Map(types.map(type => [Number(type.id), type.code]))
  const referenceIds = [...new Set(fields.value.filter(field => field.data_type === 'REFERENCE').map(field => Number(field.reference_object_type_id)).filter(Number.isFinite))]
  await Promise.all(referenceIds.map(async id => {
    const code = byId.get(id)
    referenceOptions[id] = code ? (await listRecords(code)).data || [] : []
  }))
  if (mode.value !== 'create') {
    const rows = (await listRecords(objectCode.value)).data || []
    const row = rows.find(item => Number(item.id) === Number(route.params.id))
    if (row) Object.assign(form, { id: row.id, version: row.version, record_code: row.record_code, data: { ...row.data } })
    else { unavailable.value = true; error.value = '记录不存在或无权访问' }
  }
 } catch (exception) {
  unavailable.value = true
  error.value = exception?.message || '加载记录失败'
 } finally {
  loading.value = false
 }
})
function serialize(field, value) {
  if (value == null || value === '') return value
  const type = String(field.data_type || 'STRING').toUpperCase()
  if (type === 'INTEGER' || type === 'REFERENCE') return Number.parseInt(value, 10)
  if (type === 'DECIMAL') return String(value)
  if (type === 'BOOLEAN') return value === true || value === 'true'
  return value
}
function validate() {
  if (!form.record_code.trim()) return '记录编码不能为空'
  const invalidDecimal = fields.value.find(field => {
    if (field.data_type !== 'DECIMAL' || form.data[field.field_key] == null || form.data[field.field_key] === '') return false
    const match = String(form.data[field.field_key]).match(/^-?(\d+)(?:\.(\d+))?$/)
    if (!match) return true
    const scale = field.scale_value ?? 0
    const precision = field.precision_value ?? 65
    const integerDigits = match[1].replace(/^0+/, '').length
    const fractionDigits = match[2]?.length || 0
    return fractionDigits > scale || integerDigits > precision - scale
  })
  if (invalidDecimal) return `${invalidDecimal.field_name}格式不符合精度要求`
  const missing = fields.value.find(field => field.required && (form.data[field.field_key] == null || form.data[field.field_key] === ''))
  return missing ? `${missing.field_name}不能为空` : ''
}
async function save() {
  if (!canEdit.value || unavailable.value || loading.value || saving.value) return
  error.value = validate()
  if (error.value) return
  const data = Object.fromEntries(fields.value.map(field => [field.field_key, serialize(field, form.data[field.field_key])]))
  saving.value = true
  try {
    if (mode.value === 'create') await createRecord(objectCode.value, { department_id: departmentId.value, record_code: form.record_code, data })
    else await updateRecord(objectCode.value, { id: form.id, version: form.version, data })
    back()
  } catch (exception) {
    error.value = exception?.message || '保存失败'
  } finally {
    saving.value = false
  }
}
function back() { router.push({ path: '/mdm', query: route.query }) }
defineExpose({ form, save })
</script>
