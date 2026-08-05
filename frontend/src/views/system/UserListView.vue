<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { assignUserRoles, createUser, listDepartments, listUsers, setUserStatus, updateUser, type Department, type SystemUser } from '../../api/system'
import UserDrawer, { type UserDrawerInput } from '../../components/system/UserDrawer.vue'
import type { ApiError } from '../../types'

const users = ref<SystemUser[]>([])
const departments = ref<Department[]>([])
const selected = ref<SystemUser | null>(null)
const drawerOpen = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const { t } = useI18n()
function errorMessage(reason: unknown): string { const apiError = reason as ApiError; return apiError.requestId ? t('common.apiError', { message: apiError.message, requestId: t('common.requestId', { id: apiError.requestId }) }) : apiError.message }
function statusLabel(status?: 'ACTIVE' | 'DISABLED'): string { return status ? t(`status.${status}`) : '' }
async function load(): Promise<void> {
  loading.value = true
  try { [users.value, departments.value] = await Promise.all([listUsers(), listDepartments()]) } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
function create(): void { selected.value = null; error.value = ''; drawerOpen.value = true }
function edit(user: SystemUser): void { selected.value = user; error.value = ''; drawerOpen.value = true }
async function save(value: UserDrawerInput): Promise<void> {
  error.value = ''
  saving.value = true
  try {
    if (selected.value) {
      await updateUser(selected.value.id, { displayName: value.displayName, departmentId: value.departmentId })
      await assignUserRoles(selected.value.id, value.roles)
    } else if ('username' in value) await createUser(value)
    drawerOpen.value = false
    await load()
  } catch (reason) { error.value = errorMessage(reason) } finally { saving.value = false }
}
async function setStatus(user: SystemUser, status: 'ACTIVE' | 'DISABLED'): Promise<void> {
  if (!window.confirm(t(status === 'ACTIVE' ? 'system.users.activateConfirm' : 'system.users.deactivateConfirm', { username: user.username }))) return
  error.value = ''
  try { await setUserStatus(user.id, status); await load() } catch (reason) { error.value = errorMessage(reason) }
}
onMounted(load)
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>{{ t('system.users.title') }}</h1><p>{{ t('system.users.description') }}</p></div><el-button data-testid="user-create" type="primary" @click="create">{{ t('system.users.create') }}</el-button></div>
    <p v-if="error && !drawerOpen" class="form-error" role="alert">{{ error }}</p>
    <el-table :data="users" v-loading="loading">
      <el-table-column prop="username" :label="t('system.users.username')" />
      <el-table-column prop="displayName" :label="t('system.users.displayName')" />
      <el-table-column prop="departmentId" :label="t('system.users.departmentId')" />
      <el-table-column prop="roles" :label="t('system.users.roles')"><template #default="scope">{{ scope.row.roles.join(', ') }}</template></el-table-column>
      <el-table-column :label="t('common.status')"><template #default="scope">{{ statusLabel(scope.row?.status) }}</template></el-table-column>
      <el-table-column :label="t('common.actions')" width="220"><template #default="scope">
        <el-button text type="primary" @click="edit(scope.row)">{{ t('common.edit') }}</el-button>
        <el-button v-if="scope.row.status === 'ACTIVE'" text type="warning" @click="setStatus(scope.row, 'DISABLED')">{{ t('common.deactivate') }}</el-button>
        <el-button v-else text type="success" @click="setStatus(scope.row, 'ACTIVE')">{{ t('common.activate') }}</el-button>
      </template></el-table-column>
    </el-table>
    <UserDrawer :open="drawerOpen" :user="selected" :departments="departments" :saving="saving" :error="error" :on-saved="save" @close="drawerOpen = false" />
  </section>
</template>
