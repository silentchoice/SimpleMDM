<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listApprovalTasks, type ApprovalStatus, type ApprovalTask } from '../../api/approval'
import type { ApiError } from '../../types'

const status = ref<ApprovalStatus>('PENDING')
const tasks = ref<ApprovalTask[]>([])
const loading = ref(false)
const error = ref('')
function message(value: ApiError): string { return value.requestId ? `${value.message} (Request ID: ${value.requestId})` : value.message }
async function load(): Promise<void> {
  loading.value = true; error.value = ''
  try { tasks.value = await listApprovalTasks(status.value) } catch (cause) { tasks.value = []; error.value = message(cause as ApiError) } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>Metadata approvals</h1><p>Review metadata changes for your department.</p></div></div>
    <label>Status <select v-model="status" name="status" @change="load"><option>PENDING</option><option>APPROVED</option><option>REJECTED</option></select></label>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    <p v-else-if="loading">Loading approvals…</p>
    <p v-else-if="tasks.length === 0">No metadata approval tasks match this status.</p>
    <table v-else><thead><tr><th>Task</th><th>Metadata kind</th><th>Entity</th><th>Status</th><th>Submitted</th></tr></thead><tbody>
      <tr v-for="task in tasks" :key="task.id"><td><router-link :to="`/metadata/approvals/${task.id}`">#{{ task.id }}</router-link></td><td>{{ task.entityKind }}</td><td>{{ task.entityId }}</td><td>{{ task.status }}</td><td>{{ task.submittedAt }}</td></tr>
    </tbody></table>
  </section>
</template>

<style scoped>
table { width: 100%; margin-top: 16px; border-collapse: collapse; }
th, td { padding: 9px; border-bottom: 1px solid #e5e7eb; text-align: left; }
</style>
