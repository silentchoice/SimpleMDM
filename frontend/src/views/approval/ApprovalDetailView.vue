<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { getApprovalTask, listApprovalTasks, type ApprovalTask } from '../../api/approval'
import { invalidateActiveMetadata } from '../../api/metadata'
import type { ApiError } from '../../types'
import ApprovalActionBar from '../../components/approval/ApprovalActionBar.vue'
import SnapshotDiff from '../../components/approval/SnapshotDiff.vue'

const route = useRoute()
const { t } = useI18n()
const router = useRouter()
const task = ref<ApprovalTask | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
let loadGeneration = 0
function message(value: ApiError): string { return value.requestId ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) }) : value.message }
async function load(clearActionError = false): Promise<void> {
  const taskId = Number(route.params.taskId)
  const generation = ++loadGeneration
  task.value = null
  if (clearActionError) actionError.value = ''
  loading.value = true; error.value = ''
  if (!Number.isSafeInteger(taskId) || taskId <= 0) { task.value = null; error.value = t('approval.detail.notFound'); loading.value = false; return }
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
    <router-link to="/metadata/approvals">{{ t('approval.detail.back') }}</router-link>
    <p v-if="loading && !task">{{ t('approval.detail.loading') }}</p>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    <p v-if="actionError" role="alert" class="form-error">{{ actionError }}</p>
    <template v-if="task">
      <h1>{{ t('approval.detail.title', { id: task.id }) }}</h1>
      <dl><dt>{{ t('approval.detail.kind') }}</dt><dd>{{ t(`approval.entityKinds.${task.entityKind}`) }}</dd><dt>{{ t('approval.detail.entity') }}</dt><dd>{{ task.entityId }}</dd><dt>{{ t('common.status') }}</dt><dd>{{ t(`status.${task.status}`) }}</dd><dt>{{ t('approval.detail.submittedBy') }}</dt><dd>{{ task.submittedBy }}</dd><dt>{{ t('approval.detail.submittedAt') }}</dt><dd>{{ task.submittedAt }}</dd><template v-if="task.reviewedBy !== null"><dt>{{ t('approval.detail.reviewedBy') }}</dt><dd>{{ task.reviewedBy }}</dd><dt>{{ t('approval.detail.reviewedAt') }}</dt><dd>{{ task.reviewedAt }}</dd><dt>{{ t('approval.detail.reviewComment') }}</dt><dd>{{ task.reviewComment || '—' }}</dd></template></dl>
      <SnapshotDiff :before-snapshot="task.beforeSnapshot" :after-snapshot="task.afterSnapshot" :entity-kind="task.entityKind" :entity-id="task.entityId" />
      <ApprovalActionBar :key="task.id" :task-id="task.id" :status="task.status" @approved="approved" @rejected="rejected" @conflict="conflict" />
    </template>
  </section>
</template>

<style scoped>
dl { display: grid; grid-template-columns: max-content 1fr; gap: 6px 12px; } dt { font-weight: 700; } dd { margin: 0; }
</style>
