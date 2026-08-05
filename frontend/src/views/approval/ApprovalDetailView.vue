<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getApprovalTask, listApprovalTasks, type ApprovalTask } from '../../api/approval'
import { invalidateActiveMetadata } from '../../api/metadata'
import type { ApiError } from '../../types'
import ApprovalActionBar from '../../components/approval/ApprovalActionBar.vue'
import SnapshotDiff from '../../components/approval/SnapshotDiff.vue'

const route = useRoute()
const router = useRouter()
const task = ref<ApprovalTask | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
let loadGeneration = 0
function message(value: ApiError): string { return value.requestId ? `${value.message} (Request ID: ${value.requestId})` : value.message }
async function load(clearActionError = false): Promise<void> {
  const taskId = Number(route.params.taskId)
  const generation = ++loadGeneration
  task.value = null
  if (clearActionError) actionError.value = ''
  loading.value = true; error.value = ''
  if (!Number.isSafeInteger(taskId) || taskId <= 0) { task.value = null; error.value = 'Approval task not found'; loading.value = false; return }
  try { const loaded = await getApprovalTask(taskId); if (generation === loadGeneration) task.value = loaded } catch (cause) { if (generation === loadGeneration) { task.value = null; error.value = message(cause as ApiError) } } finally { if (generation === loadGeneration) loading.value = false }
}
async function approved(): Promise<void> { invalidateActiveMetadata(); await router.push({ name: 'approvals' }) }
async function rejected(): Promise<void> { await load(); await listApprovalTasks('PENDING').catch(() => undefined) }
async function conflict(apiError: ApiError): Promise<void> { actionError.value = message(apiError); await load() }
onMounted(() => load(true))
watch(() => route.params.taskId, () => load(true))
</script>

<template>
  <section class="content-view">
    <router-link to="/metadata/approvals">Back to approvals</router-link>
    <p v-if="loading && !task">Loading approval…</p>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    <p v-if="actionError" role="alert" class="form-error">{{ actionError }}</p>
    <template v-if="task">
      <h1>Metadata approval #{{ task.id }}</h1>
      <dl><dt>Kind</dt><dd>{{ task.entityKind }}</dd><dt>Entity</dt><dd>{{ task.entityId }}</dd><dt>Status</dt><dd>{{ task.status }}</dd><dt>Submitted by</dt><dd>{{ task.submittedBy }}</dd><dt>Submitted at</dt><dd>{{ task.submittedAt }}</dd><template v-if="task.reviewedBy !== null"><dt>Reviewed by</dt><dd>{{ task.reviewedBy }}</dd><dt>Reviewed at</dt><dd>{{ task.reviewedAt }}</dd><dt>Review comment</dt><dd>{{ task.reviewComment || '—' }}</dd></template></dl>
      <SnapshotDiff :before-snapshot="task.beforeSnapshot" :after-snapshot="task.afterSnapshot" :entity-kind="task.entityKind" :entity-id="task.entityId" />
      <ApprovalActionBar :key="task.id" :task-id="task.id" :status="task.status" @approved="approved" @rejected="rejected" @conflict="conflict" />
    </template>
  </section>
</template>

<style scoped>
dl { display: grid; grid-template-columns: max-content 1fr; gap: 6px 12px; } dt { font-weight: 700; } dd { margin: 0; }
</style>
