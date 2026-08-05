<script setup lang="ts">
import { ref, watch } from 'vue'
import type { CreateUserInput, Department, SystemUser, UpdateUserInput } from '../../api/system'
import type { Role } from '../../types'

export type UserDrawerInput = CreateUserInput | (UpdateUserInput & { roles: Role[] })
const props = withDefaults(defineProps<{
  open: boolean
  user: SystemUser | null
  departments: Department[]
  saving: boolean
  error?: string
  onSaved?: (value: UserDrawerInput) => Promise<void> | void
}>(), { error: '', onSaved: undefined })
const emit = defineEmits<{ close: [] }>()
const roles: Role[] = ['SUPER_ADMIN', 'DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER']
const username = ref('')
const password = ref('')
const displayName = ref('')
const departmentId = ref('')
const selectedRoles = ref<Role[]>([])
const validationError = ref('')
const submitting = ref(false)

function reset(): void {
  username.value = props.user?.username ?? ''
  password.value = ''
  displayName.value = props.user?.displayName ?? ''
  departmentId.value = props.user?.departmentId?.toString() ?? ''
  selectedRoles.value = [...(props.user?.roles ?? [])]
  validationError.value = ''
}

watch(() => props.open, () => reset(), { immediate: true })
watch(() => props.user, () => { if (props.open) reset() })

async function submit(): Promise<void> {
  validationError.value = ''
  if (!displayName.value.trim() || (!props.user && (!username.value.trim() || !password.value))) {
    validationError.value = props.user ? 'Display name is required' : 'Username, password, and display name are required'
    return
  }
  if (submitting.value || props.saving) return
  const department = departmentId.value ? Number(departmentId.value) : null
  if (department !== null && !props.departments.some((item) => item.id === department && item.status === 'ACTIVE')) {
    validationError.value = 'Select an active department before saving'
    return
  }
  const value: UserDrawerInput = props.user
    ? { displayName: displayName.value.trim(), departmentId: department, roles: [...selectedRoles.value] }
    : { username: username.value.trim(), password: password.value, displayName: displayName.value.trim(), departmentId: department, roles: [...selectedRoles.value] }
  submitting.value = true
  try {
    await props.onSaved?.(value)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <aside v-if="open" class="system-drawer" role="dialog" :aria-label="user ? 'Edit user' : 'Create user'">
    <h2>{{ user ? 'Edit user' : 'Create user' }}</h2>
    <form @submit.prevent="submit">
      <template v-if="!user">
        <label for="user-username">Username</label>
        <input id="user-username" v-model="username" name="username" autocomplete="username" :disabled="saving || submitting" />
        <label for="user-password">Password</label>
        <input id="user-password" v-model="password" name="password" type="password" autocomplete="new-password" :disabled="saving || submitting" />
      </template>
      <label for="user-display-name">Display name</label>
      <input id="user-display-name" v-model="displayName" name="displayName" :disabled="saving || submitting" />
      <label for="user-department">Department</label>
      <select id="user-department" v-model="departmentId" name="departmentId" :disabled="saving || submitting">
        <option value="">Global</option>
        <option v-for="department in departments.filter((item) => item.status === 'ACTIVE')" :key="department.id" :value="department.id.toString()">{{ department.name }}</option>
      </select>
      <label for="user-roles">Roles</label>
      <select id="user-roles" v-model="selectedRoles" name="roles" multiple :disabled="saving || submitting">
        <option v-for="role in roles" :key="role" :value="role">{{ role }}</option>
      </select>
      <p v-if="validationError || error" class="form-error" role="alert">{{ validationError || error }}</p>
      <div class="drawer-actions">
        <el-button data-testid="user-cancel" native-type="button" @click="emit('close')">Cancel</el-button>
        <el-button native-type="submit" type="primary" :loading="saving || submitting">{{ saving || submitting ? 'Saving…' : 'Save' }}</el-button>
      </div>
    </form>
  </aside>
</template>
