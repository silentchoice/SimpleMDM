<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { listApprovalTasks, type ApprovalStatus, type ApprovalTask } from '../../api/approval'
import type { ApiError } from '../../types'

const status = ref<ApprovalStatus>('PENDING')
const { t } = useI18n()
const tasks = ref<ApprovalTask[]>([])
const loading = ref(false)
const error = ref('')
let loadGeneration = 0
function message(value: ApiError): string { return value.requestId ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) }) : value.message }
async function load(): Promise<void> {
  const requestedStatus = status.value
  const generation = ++loadGeneration
  loading.value = true; error.value = ''
  try { const loaded = await listApprovalTasks(requestedStatus); if (generation === loadGeneration) tasks.value = loaded } catch (cause) { if (generation === loadGeneration) { tasks.value = []; error.value = message(cause as ApiError) } } finally { if (generation === loadGeneration) loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>{{ t('approval.list.title') }}</h1><p>{{ t('approval.list.description') }}</p></div></div>
    <label>{{ t('approval.list.filterStatus') }} <select v-model="status" name="status" @change="load"><option value="PENDING">{{ t('status.PENDING') }}</option><option value="APPROVED">{{ t('status.APPROVED') }}</option><option value="REJECTED">{{ t('status.REJECTED') }}</option></select></label>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    <p v-else-if="loading">{{ t('approval.list.loading') }}</p>
    <p v-else-if="tasks.length === 0">{{ t('approval.list.empty') }}</p>
    <table v-else><thead><tr><th>{{ t('approval.list.task') }}</th><th>{{ t('approval.list.metadataKind') }}</th><th>{{ t('approval.list.entity') }}</th><th>{{ t('common.status') }}</th><th>{{ t('approval.list.submitted') }}</th></tr></thead><tbody>
      <tr v-for="task in tasks" :key="task.id"><td><router-link :to="`/metadata/approvals/${task.id}`">#{{ task.id }}</router-link></td><td>{{ t(`approval.entityKinds.${task.entityKind}`) }}</td><td>{{ task.entityId }}</td><td>{{ t(`status.${task.status}`) }}</td><td>{{ task.submittedAt }}</td></tr>
    </tbody></table>
  </section>
</template>

<style scoped>
table { width: 100%; margin-top: 16px; border-collapse: collapse; }
th, td { padding: 9px; border-bottom: 1px solid #e5e7eb; text-align: left; }
</style>
