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
    <el-table :data="records" v-loading="loading">
      <el-table-column prop="record_code" label="编码" />
      <el-table-column v-for="field in fields" :key="field.field_key" :label="field.field_name">
        <template #default="{ row }"><TypedFieldValue :field="field" :value="row.data?.[field.field_key]" /></template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button v-if="canEdit" :data-test="`edit-${row.id}`" link @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listObjectTypes, listRecords } from '../../api/mdm'
import { useContextStore } from '../../stores/context'
import TypedFieldValue from '../../components/mdm/TypedFieldValue.vue'
const route = useRoute()
const router = useRouter()
const context = useContextStore()
const objectTypes = ref([])
const records = ref([])
const loading = ref(false)
const selectedObject = ref(route.query.object || '')
const selectedDepartment = ref(route.query.department ? Number(route.query.department) : null)
const permissions = JSON.parse(localStorage.getItem('permissions') || '[]')
const canEdit = computed(() => permissions.some(item => item.code === 'MDM_RECORD_EDIT' || item.permission_code === 'MDM_RECORD_EDIT' || (item.perm_type === 'EDIT' && item.can_edit !== false)))
const currentObject = computed(() => objectTypes.value.find(item => item.code === selectedObject.value))
const fields = computed(() => currentObject.value?.fields || [])
onMounted(async () => {
  await context.initialize(JSON.parse(localStorage.getItem('user') || '{}'), route.query)
  selectedDepartment.value = context.departmentId
  objectTypes.value = (await listObjectTypes()).data || []
  if (!selectedObject.value) selectedObject.value = objectTypes.value[0]?.code || ''
  await syncQuery()
  await load()
})
async function load() {
  if (!selectedObject.value) return
  loading.value = true
  try {
    const response = await listRecords(selectedObject.value)
    records.value = (response.data || []).filter(row => selectedDepartment.value == null || Number(row.department_id) === Number(selectedDepartment.value))
  } finally { loading.value = false }
}
async function syncQuery() {
  context.select({ object: selectedObject.value, department: selectedDepartment.value })
  await router.replace({ query: context.query })
}
async function changeObject() { await syncQuery(); await load() }
async function changeDepartment() { await syncQuery(); await load() }
function openCreate() { router.push({ path: '/mdm/create', query: context.query }) }
function openEdit(row) { router.push({ path: `/mdm/${row.id}/edit`, query: context.query }) }
</script>
<style scoped>
.toolbar { display: flex; justify-content: space-between; gap: 12px; }
.toolbar > div { display: flex; gap: 12px; }
</style>
