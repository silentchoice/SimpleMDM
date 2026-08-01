<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <div>
          <el-select v-model="selectedObject" placeholder="选择主数据对象" @change="changeObject">
            <el-option v-for="item in objectTypes" :key="item.id" :label="item.name" :value="item.code" />
          </el-select>
          <el-tree-select v-model="selectedDepartment" :data="context.departments" node-key="id"
            :props="{ label: 'name', children: 'children' }" check-strictly @change="changeDepartment" />
        </div>
        <el-button v-if="canEdit" data-test="create" type="primary" @click="openCreate">新增</el-button>
      </div>
    </template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" :closable="false" />
    <el-table :data="records" v-loading="loading">
      <el-table-column prop="record_code" label="编码" />
      <el-table-column v-for="field in fields" :key="field.field_key" :label="field.field_name">
        <template #default="{ row }"><TypedFieldValue :field="field" :value="row.data?.[field.field_key]" :options="referenceOptions[field.reference_object_type_id] || []" /></template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button :data-test="`detail-${row.id}`" link @click="openDetail(row)">查看</el-button>
          <el-button v-if="canEdit" :data-test="`edit-${row.id}`" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.can_distribute === true" :data-test="`distribute-${row.id}`" link type="primary" :loading="distributingId === row.id" @click="distribute(row)">分发最新快照</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listObjectTypes, listRecords } from '../../api/mdm'
import { distributeRecord } from '../../api/integration'
import { useContextStore } from '../../stores/context'
import { chineseError } from '../../utils/labels'
import TypedFieldValue from '../../components/mdm/TypedFieldValue.vue'
const route = useRoute()
const router = useRouter()
const context = useContextStore()
const objectTypes = ref([])
const records = ref([])
const loading = ref(false)
const distributingId = ref(null)
const error = ref('')
const notice = ref('')
const referenceOptions = ref({})
const selectedObject = ref(route.query.object || '')
const selectedDepartment = ref(route.query.department ? Number(route.query.department) : null)
const permissions = JSON.parse(localStorage.getItem('permissions') || '[]')
const canEdit = computed(() => permissions.some(item =>
  (item.code === 'MDM_RECORD_EDIT' || item.permission_code === 'MDM_RECORD_EDIT') &&
  (Array.isArray(item.editable_department_ids)
    ? item.editable_department_ids.map(Number).includes(Number(selectedDepartment.value))
    : item.can_edit === true)
))
const currentObject = computed(() => objectTypes.value.find(item => item.code === selectedObject.value))
const projectedKeys = computed(() => new Set(records.value.flatMap(row => Object.keys(row.data || {}))))
const fields = computed(() => (currentObject.value?.fields || []).filter(field => projectedKeys.value.has(field.field_key)))
onMounted(async () => {
  loading.value = true
  try {
    await context.initialize(JSON.parse(localStorage.getItem('user') || '{}'), route.query)
    selectedDepartment.value = context.departmentId
    objectTypes.value = (await listObjectTypes()).data || []
    if (!selectedObject.value) selectedObject.value = objectTypes.value[0]?.code || ''
    await syncQuery()
    await load()
    await loadReferenceOptions()
  } catch (exception) {
    records.value = []
    error.value = chineseError(exception, '初始化主数据列表失败')
  } finally { loading.value = false }
})
async function loadReferenceOptions() {
  const byId = new Map(objectTypes.value.map(type => [Number(type.id), type.code]))
  const ids = [...new Set(fields.value.filter(field => field.data_type === 'REFERENCE').map(field => Number(field.reference_object_type_id)).filter(Number.isFinite))]
  const pairs = await Promise.all(ids.map(async id => [id, (await listRecords(byId.get(id))).data || []]))
  referenceOptions.value = Object.fromEntries(pairs)
}
async function load() {
  if (!selectedObject.value) return
  loading.value = true
  error.value = ''
  try {
    const response = await listRecords(selectedObject.value)
    records.value = (response.data || []).filter(row => selectedDepartment.value == null || Number(row.department_id) === Number(selectedDepartment.value))
  } catch (exception) { error.value = chineseError(exception, '加载主数据失败') }
  finally { loading.value = false }
}
async function syncQuery() {
  context.select({ object: selectedObject.value, department: selectedDepartment.value })
  await router.replace({ query: context.query })
}
async function changeObject() { await syncQuery(); await load(); await loadReferenceOptions() }
async function changeDepartment() { await syncQuery(); await load() }
function openCreate() { router.push({ path: '/mdm/create', query: context.query }) }
function openDetail(row) { router.push({ path: `/mdm/${row.id}`, query: context.query }) }
function openEdit(row) { router.push({ path: `/mdm/${row.id}/edit`, query: context.query }) }
async function distribute(row) {
  if (row.can_distribute !== true || distributingId.value != null) return
  distributingId.value = row.id; error.value = ''; notice.value = ''
  try { await distributeRecord(row.id, { reason: '管理界面手动分发最新快照' }); notice.value = '已创建分发任务' }
  catch (exception) { error.value = chineseError(exception, '创建分发任务失败') }
  finally { distributingId.value = null }
}
</script>
<style scoped>
.toolbar { display: flex; justify-content: space-between; gap: 12px; }
.toolbar > div { display: flex; gap: 12px; }
</style>
