<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getDashboardSummary, type DashboardSummary } from '../api/dashboard'
import SummaryMetrics from '../components/dashboard/SummaryMetrics.vue'
import RecentTasksTable from '../components/dashboard/RecentTasksTable.vue'
import { useAuthStore } from '../stores/auth'
import type { ApiError, Role } from '../types'

const auth = useAuthStore()
const { t } = useI18n()
const summary = ref<DashboardSummary | null>(null)
const loading = ref(true)
const error = ref('')

function message(value: ApiError): string {
  return value.requestId
    ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) })
    : value.message
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    summary.value = await getDashboardSummary()
  } catch (reason) {
    summary.value = null
    error.value = message(reason as ApiError)
  } finally {
    loading.value = false
  }
}

function hasRole(role: Role): boolean {
  return auth.session?.roles.includes(role) ?? false
}

const metrics = computed(() => {
  const current = summary.value
  if (!current) return []
  return [
    { key: 'formal', label: t('dashboard.metrics.formalCount'), value: current.formalCount },
    { key: 'drafts', label: t('dashboard.metrics.myDraftCount'), value: current.myDraftCount },
    { key: 'pending', label: t('dashboard.metrics.pendingApprovalCount'), value: current.pendingApprovalCount },
    { key: 'activated', label: t('dashboard.metrics.activatedThisMonth'), value: current.activatedThisMonth }
  ]
})

const shortcuts = computed(() => {
  if (hasRole('SUPER_ADMIN')) {
    return [
      { key: 'templates', label: t('menu.masterTypeTemplates'), to: '/metadata/templates' },
      { key: 'users', label: t('menu.users'), to: '/system/users' },
      { key: 'departments', label: t('menu.departments'), to: '/system/departments' },
      { key: 'roles', label: t('menu.roles'), to: '/system/roles' }
    ]
  }
  const items = [
    { key: 'records', label: t('menu.records'), to: '/records' },
    { key: 'active', label: t('menu.activeMetadata'), to: '/metadata/active' }
  ]
  if (hasRole('DEPT_EDITOR')) items.push({ key: 'submit', label: t('menu.submitChange'), to: '/metadata/changes/new' })
  if (hasRole('DEPT_APPROVER')) items.push({ key: 'approvals', label: t('menu.approvals'), to: '/metadata/approvals' })
  return items
})

onMounted(load)
</script>

<template>
  <section class="content-view dashboard-view">
    <div class="view-heading">
      <div>
        <h1>{{ t('dashboard.title') }}</h1>
        <p>{{ t('dashboard.description') }}</p>
      </div>
      <button data-testid="dashboard-refresh" type="button" class="dashboard-refresh" :disabled="loading" @click="load">
        {{ t('dashboard.refresh') }}
      </button>
    </div>

    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-else-if="loading">{{ t('common.loading') }}</p>
    <template v-else-if="summary">
      <SummaryMetrics :metrics="metrics" />

      <section class="dashboard-shortcuts">
        <h2>{{ t('dashboard.shortcuts.title') }}</h2>
        <div class="dashboard-shortcuts__grid">
          <router-link v-for="shortcut in shortcuts" :key="shortcut.key" class="dashboard-shortcut" :to="shortcut.to">
            {{ shortcut.label }}
          </router-link>
        </div>
      </section>

      <section class="dashboard-recent">
        <h2>{{ t('dashboard.recent.title') }}</h2>
        <p v-if="summary.recentTasks.length === 0">{{ t('dashboard.recent.empty') }}</p>
        <RecentTasksTable v-else :tasks="summary.recentTasks" />
      </section>
    </template>
  </section>
</template>
