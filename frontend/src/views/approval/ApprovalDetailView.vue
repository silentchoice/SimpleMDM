<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { getApprovalTask, listApprovalTasks, type ApprovalTask, type ApprovalTaskType } from '../../api/approval'
import { invalidateActiveMetadata } from '../../api/metadata'
import { useAuthStore } from '../../stores/auth'
import type { ApiError } from '../../types'
import ApprovalActionBar from '../../components/approval/ApprovalActionBar.vue'
import RecordSnapshotDiff from '../../components/approval/RecordSnapshotDiff.vue'
import SnapshotDiff from '../../components/approval/SnapshotDiff.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n()
const task = ref<ApprovalTask | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
let loadGeneration = 0

const taskType = computed<ApprovalTaskType>(() => route.query.taskType === 'RECORD' ? 'RECORD' : 'METADATA')
const selfApprovalError = computed(() => task.value?.taskType === 'RECORD' && task.value.submittedBy === auth.session?.user.id
  ? t('approval.actions.selfApprovalRecord')
  : '')

function message(value: ApiError): string {
  return value.requestId
    ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) })
    : value.message
}

async function load(clearActionError = false): Promise<void> {
  const taskId = Number(route.params.taskId)
  const currentTaskType = taskType.value
  const generation = ++loadGeneration
  task.value = null
  if (clearActionError) actionError.value = ''
  loading.value = true
  error.value = ''
  if (!Number.isSafeInteger(taskId) || taskId <= 0) {
    task.value = null
    error.value = t('approval.detail.notFound')
    loading.value = false
    return
  }
  try {
    const loaded = currentTaskType === 'RECORD'
      ? await getApprovalTask(taskId, 'RECORD')
      : await getApprovalTask(taskId)
    if (generation === loadGeneration) task.value = loaded
  } catch (cause) {
    if (generation === loadGeneration) {
      task.value = null
      error.value = message(cause as ApiError)
    }
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function approved(): Promise<void> {
  invalidateActiveMetadata()
  await router.push(taskType.value === 'RECORD' ? { name: 'approvals', query: { taskType: 'RECORD' } } : { name: 'approvals' })
}

async function rejected(): Promise<void> {
  await load()
  await listApprovalTasks('PENDING', taskType.value).catch(() => undefined)
}

async function conflict(apiError: ApiError): Promise<void> {
  actionError.value = message(apiError)
  await load()
}

onMounted(() => load(true))
watch(() => [route.params.taskId, route.query.taskType], () => load(true))
</script>

<template>
  <section class="content-view">
    <router-link :to="taskType === 'RECORD' ? { path: '/metadata/approvals', query: { taskType: 'RECORD' } } : '/metadata/approvals'">
      {{ t('approval.detail.back') }}
    </router-link>
    <p v-if="loading && !task">{{ t('approval.detail.loading') }}</p>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    <p v-if="actionError" role="alert" class="form-error">{{ actionError }}</p>
    <template v-if="task">
      <h1>{{ t(task.taskType === 'RECORD' ? 'approval.detail.recordTitle' : 'approval.detail.title', { id: task.id }) }}</h1>
      <div class="approval-audit-row">
        <span>{{ t(`approval.entityKinds.${task.entityKind}`) }}</span>
        <span>{{ task.entityId }}</span>
        <span>{{ t(`status.${task.status}`) }}</span>
        <span>{{ t('approval.detail.submittedBy') }} {{ task.submittedBy }}</span>
        <span>{{ t('approval.detail.submittedAt') }} {{ task.submittedAt }}</span>
        <span v-if="task.reviewedBy !== null">{{ t('approval.detail.reviewedBy') }} {{ task.reviewedBy }}</span>
        <span v-if="task.reviewedAt">{{ t('approval.detail.reviewedAt') }} {{ task.reviewedAt }}</span>
        <span v-if="task.reviewComment">{{ t('approval.detail.reviewComment') }} {{ task.reviewComment }}</span>
      </div>

      <RecordSnapshotDiff
        v-if="task.taskType === 'RECORD'"
        :before-snapshot="task.beforeSnapshot"
        :after-snapshot="task.afterSnapshot"
      />
      <SnapshotDiff
        v-else
        :before-snapshot="task.beforeSnapshot ?? ''"
        :after-snapshot="task.afterSnapshot"
        :entity-kind="task.entityKind"
        :entity-id="task.entityId"
      />

      <p v-if="selfApprovalError" role="alert" class="form-error">{{ selfApprovalError }}</p>
      <ApprovalActionBar
        v-else
        :key="`${task.id}-${task.taskType}`"
        :task-id="task.id"
        :task-type="task.taskType"
        :status="task.status"
        @approved="approved"
        @rejected="rejected"
        @conflict="conflict"
      />
    </template>
  </section>
</template>
