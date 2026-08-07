<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DashboardRecentTask } from '../../api/dashboard'

const props = defineProps<{
  tasks: DashboardRecentTask[]
  canOpenApprovalDetails: boolean
}>()

const { t, te } = useI18n()

function taskTypeLabel(taskType: string): string {
  const key = `dashboard.taskTypes.${taskType}`
  return te(key) ? t(key) : taskType
}

const rows = computed(() => props.tasks.map((task) => ({
  ...task,
  detailTo: task.taskType === 'RECORD'
    ? { path: `/metadata/approvals/${task.id}`, query: { taskType: 'RECORD' } }
    : { path: `/metadata/approvals/${task.id}` }
})))
</script>

<template>
  <table class="records-table">
    <thead>
      <tr>
        <th>{{ t('dashboard.recent.task') }}</th>
        <th>{{ t('dashboard.recent.type') }}</th>
        <th>{{ t('dashboard.recent.entity') }}</th>
        <th>{{ t('common.status') }}</th>
        <th>{{ t('dashboard.recent.submitted') }}</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="task in rows" :key="task.id">
        <td>
          <router-link v-if="canOpenApprovalDetails" :to="task.detailTo">#{{ task.id }}</router-link>
          <span v-else>#{{ task.id }}</span>
        </td>
        <td>{{ taskTypeLabel(task.taskType) }}</td>
        <td>{{ task.entityId }}</td>
        <td>{{ t(`status.${task.status}`) }}</td>
        <td>{{ task.submittedAt }}</td>
      </tr>
    </tbody>
  </table>
</template>
