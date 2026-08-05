<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { listRoles } from '../../api/system'
import type { ApiError, Role } from '../../types'

const roles = ref<Role[]>([])
const loading = ref(false)
const error = ref('')
const { t } = useI18n()
async function load(): Promise<void> {
  loading.value = true
  try { roles.value = await listRoles() } catch (reason) { const apiError = reason as ApiError; error.value = apiError.requestId ? t('common.apiError', { message: apiError.message, requestId: t('common.requestId', { id: apiError.requestId }) }) : apiError.message } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>{{ t('system.roles.title') }}</h1><p>{{ t('system.roles.description') }}</p></div></div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <el-table :data="roles.map((role) => ({ role }))" v-loading="loading"><el-table-column prop="role" :label="t('system.roles.role')" /></el-table>
  </section>
</template>
