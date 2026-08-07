<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DashboardRecentTask } from '../../api/dashboard'

const props = defineProps<{
  tasks: DashboardRecentTask[]
}>()

const { t } = useI18n()

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
        <td><router-link :to="task.detailTo">#{{ task.id }}</router-link></td>
        <td>{{ task.taskType }}</td>
        <td>{{ task.entityId }}</td>
        <td>{{ t(`status.${task.status}`) }}</td>
        <td>{{ task.submittedAt }}</td>
      </tr>
    </tbody>
  </table>
</template>
