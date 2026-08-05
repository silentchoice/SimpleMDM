<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createDepartment, deleteDepartment, listDepartments, setDepartmentStatus, updateDepartment, type Department, type DepartmentInput } from '../../api/system'
import DepartmentDrawer from '../../components/system/DepartmentDrawer.vue'
import type { ApiError } from '../../types'

const departments = ref<Department[]>([])
const selected = ref<Department | null>(null)
const drawerOpen = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const { t } = useI18n()

function errorMessage(reason: unknown): string {
  const apiError = reason as ApiError
  return apiError.requestId ? t('common.apiError', { message: apiError.message, requestId: t('common.requestId', { id: apiError.requestId }) }) : apiError.message
}
function statusLabel(status?: 'ACTIVE' | 'DISABLED'): string { return status ? t(`status.${status}`) : '' }
async function load(): Promise<void> {
  loading.value = true
  try { departments.value = await listDepartments() } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
function create(): void { selected.value = null; error.value = ''; drawerOpen.value = true }
function edit(department: Department): void { selected.value = department; error.value = ''; drawerOpen.value = true }
async function save(body: DepartmentInput): Promise<void> {
  error.value = ''
  saving.value = true
  try {
    if (selected.value) await updateDepartment(selected.value.id, body)
    else await createDepartment(body)
    drawerOpen.value = false
    await load()
  } catch (reason) { error.value = errorMessage(reason) } finally { saving.value = false }
}
async function setStatus(department: Department, status: 'ACTIVE' | 'DISABLED'): Promise<void> {
  if (!window.confirm(t(status === 'ACTIVE' ? 'system.departments.activateConfirm' : 'system.departments.deactivateConfirm', { name: department.name }))) return
  error.value = ''
  try { await setDepartmentStatus(department.id, status); await load() } catch (reason) { error.value = errorMessage(reason) }
}
async function disable(department: Department): Promise<void> {
  if (!window.confirm(t('system.departments.disableConfirm', { name: department.name }))) return
  error.value = ''
  try { await deleteDepartment(department.id); await load() } catch (reason) { error.value = errorMessage(reason) }
}
onMounted(load)
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>{{ t('system.departments.title') }}</h1><p>{{ t('system.departments.description') }}</p></div><el-button data-testid="department-create" type="primary" @click="create">{{ t('system.departments.create') }}</el-button></div>
    <p v-if="error && !drawerOpen" class="form-error" role="alert">{{ error }}</p>
    <el-table :data="departments" v-loading="loading">
      <el-table-column prop="code" :label="t('system.departments.code')" />
      <el-table-column prop="name" :label="t('system.departments.name')" />
      <el-table-column :label="t('common.status')"><template #default="scope">{{ statusLabel(scope.row?.status) }}</template></el-table-column>
      <el-table-column :label="t('common.actions')" width="280">
        <template #default="scope">
          <el-button text type="primary" @click="edit(scope.row)">{{ t('common.edit') }}</el-button>
          <el-button v-if="scope.row.status === 'ACTIVE'" text type="warning" @click="setStatus(scope.row, 'DISABLED')">{{ t('common.deactivate') }}</el-button>
          <el-button v-else text type="success" @click="setStatus(scope.row, 'ACTIVE')">{{ t('common.activate') }}</el-button>
          <el-button v-if="scope.row.status === 'ACTIVE'" text type="danger" @click="disable(scope.row)">{{ t('common.disable') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <DepartmentDrawer :open="drawerOpen" :department="selected" :saving="saving" :error="error" :on-saved="save" @close="drawerOpen = false" />
  </section>
</template>
