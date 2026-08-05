<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listRoles } from '../../api/system'
import type { ApiError, Role } from '../../types'

const roles = ref<Role[]>([])
const loading = ref(false)
const error = ref('')
async function load(): Promise<void> {
  loading.value = true
  try { roles.value = await listRoles() } catch (reason) { const apiError = reason as ApiError; error.value = apiError.requestId ? `${apiError.message} (Request ID: ${apiError.requestId})` : apiError.message } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>Roles</h1><p>Fixed roles available for user assignment.</p></div></div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <el-table :data="roles.map((role) => ({ role }))" v-loading="loading"><el-table-column prop="role" label="Role" /></el-table>
  </section>
</template>
