<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
function errorMessage(reason: unknown): string { const apiError = reason as ApiError; return apiError.requestId ? `${apiError.message} (Request ID: ${apiError.requestId})` : apiError.message }
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
  if (!window.confirm(`${status === 'ACTIVE' ? 'Activate' : 'Deactivate'} ${user.username}?`)) return
  error.value = ''
  try { await setUserStatus(user.id, status); await load() } catch (reason) { error.value = errorMessage(reason) }
}
onMounted(load)
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>Users</h1><p>Manage user accounts, departments, and fixed roles.</p></div><el-button data-testid="user-create" type="primary" @click="create">Create user</el-button></div>
    <p v-if="error && !drawerOpen" class="form-error" role="alert">{{ error }}</p>
    <el-table :data="users" v-loading="loading">
      <el-table-column prop="username" label="Username" />
      <el-table-column prop="displayName" label="Display name" />
      <el-table-column prop="departmentId" label="Department ID" />
      <el-table-column prop="roles" label="Roles"><template #default="scope">{{ scope.row.roles.join(', ') }}</template></el-table-column>
      <el-table-column prop="status" label="Status" />
      <el-table-column label="Actions" width="220"><template #default="scope">
        <el-button text type="primary" @click="edit(scope.row)">Edit</el-button>
        <el-button v-if="scope.row.status === 'ACTIVE'" text type="warning" @click="setStatus(scope.row, 'DISABLED')">Deactivate</el-button>
        <el-button v-else text type="success" @click="setStatus(scope.row, 'ACTIVE')">Activate</el-button>
      </template></el-table-column>
    </el-table>
    <UserDrawer :open="drawerOpen" :user="selected" :departments="departments" :saving="saving" :error="error" :on-saved="save" @close="drawerOpen = false" />
  </section>
</template>
