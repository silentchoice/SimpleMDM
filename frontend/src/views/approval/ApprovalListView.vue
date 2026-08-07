<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { listApprovalTasks, type ApprovalStatus, type ApprovalTask, type ApprovalTaskType } from '../../api/approval'
import type { ApiError } from '../../types'

const route = useRoute()
const router = useRouter()
const status = ref<ApprovalStatus>('PENDING')
const { t } = useI18n()
const tasks = ref<ApprovalTask[]>([])
const loading = ref(false)
const error = ref('')
let loadGeneration = 0

const taskType = computed<ApprovalTaskType>(() => route.query.taskType === 'RECORD' ? 'RECORD' : 'METADATA')

function message(value: ApiError): string {
  return value.requestId
    ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) })
    : value.message
}

async function load(): Promise<void> {
  const requestedStatus = status.value
  const requestedTaskType = taskType.value
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  try {
    const loaded = await listApprovalTasks(requestedStatus, requestedTaskType)
    if (generation === loadGeneration) tasks.value = loaded
  } catch (cause) {
    if (generation === loadGeneration) {
      tasks.value = []
      error.value = message(cause as ApiError)
    }
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function selectTaskType(next: ApprovalTaskType): Promise<void> {
  await router.push({ query: next === 'RECORD' ? { taskType: 'RECORD' } : {} })
}

onMounted(load)
watch(() => route.query.taskType, load)
</script>

<template>
  <section class="content-view">
    <div class="view-heading">
      <div>
        <h1>{{ t('approval.list.title') }}</h1>
        <p>{{ t('approval.list.description') }}</p>
      </div>
    </div>

    <div class="approval-task-tabs">
      <button data-testid="approval-task-type-metadata" type="button" :class="{ active: taskType === 'METADATA' }" @click="selectTaskType('METADATA')">
        {{ t('approval.list.tabs.metadata') }}
      </button>
      <button data-testid="approval-task-type-record" type="button" :class="{ active: taskType === 'RECORD' }" @click="selectTaskType('RECORD')">
        {{ t('approval.list.tabs.record') }}
      </button>
    </div>

    <label>
      {{ t('approval.list.filterStatus') }}
      <select v-model="status" name="status" @change="load">
        <option value="PENDING">{{ t('status.PENDING') }}</option>
        <option value="APPROVED">{{ t('status.APPROVED') }}</option>
        <option value="REJECTED">{{ t('status.REJECTED') }}</option>
      </select>
    </label>

    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    <p v-else-if="loading">{{ t('approval.list.loading') }}</p>
    <p v-else-if="tasks.length === 0">{{ t('approval.list.empty') }}</p>
    <table v-else class="records-table">
      <thead>
        <tr>
          <th>{{ t('approval.list.task') }}</th>
          <th>{{ t('approval.list.metadataKind') }}</th>
          <th>{{ t('approval.list.entity') }}</th>
          <th>{{ t('common.status') }}</th>
          <th>{{ t('approval.list.submitted') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="task in tasks" :key="task.id">
          <td>
            <router-link :to="task.taskType === 'RECORD' ? { path: `/metadata/approvals/${task.id}`, query: { taskType: 'RECORD' } } : `/metadata/approvals/${task.id}`">
              #{{ task.id }}
            </router-link>
          </td>
          <td>{{ t(`approval.entityKinds.${task.entityKind}`) }}</td>
          <td>{{ task.entityId }}</td>
          <td>{{ t(`status.${task.status}`) }}</td>
          <td>{{ task.submittedAt }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
