<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { currentMasterType, listMasterFields, type FieldDefinition } from '../../api/metadata'
import { createRecordDraft, listRecords, type RecordDraftCommand, type RecordSummary } from '../../api/records'
import { useAuthStore } from '../../stores/auth'
import RecordFilters, { type RecordFilterModel } from '../../components/records/RecordFilters.vue'
import RecordStatusTag from '../../components/records/RecordStatusTag.vue'
import type { ApiError } from '../../types'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const error = ref('')
const masterTypeId = ref<number | null>(null)
const fields = ref<FieldDefinition[]>([])
const items = ref<RecordSummary[]>([])
const pageNumber = ref(0)
const pageSize = ref(20)
const totalPages = ref(0)
const filters = ref<RecordFilterModel>({ recordCode: '', keyword: '', status: '', includeDeleted: false })
const latestRequest = ref(0)

const isEditor = computed(() => auth.hasAnyRole(['DEPT_EDITOR']))

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function errorMessage(reason: unknown): string {
  const value = reason as ApiError
  return value.requestId
    ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) })
    : value.message
}

function displayValue(item: RecordSummary, code: string): string {
  const value = item.masterValues[code]
  if (Array.isArray(value)) return value.join(', ')
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return value == null ? '—' : String(value)
}

function createEmptyValues(): Record<string, unknown> {
  return Object.fromEntries(fields.value.map((field) => [field.code, field.fieldType === 'MULTISELECT' ? [] : field.fieldType === 'SWITCH' ? false : '']))
}

async function loadRecords(page = 0): Promise<void> {
  if (!masterTypeId.value) return
  loading.value = true
  error.value = ''
  pageNumber.value = page
  const requestId = latestRequest.value + 1
  latestRequest.value = requestId
  try {
    const result = await listRecords({
      masterTypeId: masterTypeId.value,
      recordCode: filters.value.recordCode || undefined,
      keyword: filters.value.keyword || undefined,
      status: filters.value.status || undefined,
      includeDeleted: filters.value.includeDeleted,
      page,
      size: pageSize.value,
      sortBy: 'updatedAt',
      sortDirection: 'desc'
    })
    if (latestRequest.value !== requestId) return
    items.value = result.content
    totalPages.value = result.totalPages
  } catch (reason) {
    if (latestRequest.value !== requestId) return
    items.value = []
    totalPages.value = 0
    error.value = errorMessage(reason)
  } finally {
    if (latestRequest.value === requestId) loading.value = false
  }
}

async function loadMetadataAndRecords(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const assignment = await currentMasterType()
    masterTypeId.value = assignment.id
    fields.value = await listMasterFields(assignment.id)
    await loadRecords(0)
  } catch (reason) {
    loading.value = false
    error.value = errorMessage(reason)
  }
}

async function createDraft(): Promise<void> {
  if (!masterTypeId.value) return
  const body: RecordDraftCommand = {
    recordId: null,
    masterTypeId: masterTypeId.value,
    baseVersion: 0,
    action: 'CREATE',
    masterValues: createEmptyValues(),
    children: [],
    deleteReason: null
  }
  const draft = await createRecordDraft(clone(body))
  await router.push(`/records/drafts/${draft.id}`)
}

onMounted(loadMetadataAndRecords)
</script>

<template>
  <section class="content-view">
    <div class="view-heading">
      <div>
        <h1>{{ t('record.list.title') }}</h1>
        <p>{{ t('record.list.description') }}</p>
      </div>
      <el-button v-if="isEditor" data-testid="record-create" type="primary" @click="createDraft">{{ t('record.list.createDraft') }}</el-button>
    </div>

    <p v-if="error" class="form-error" role="alert">{{ error }}</p>

    <RecordFilters v-model="filters" @search="loadRecords(0)" />

    <p v-if="loading">{{ t('common.loading') }}</p>
    <p v-else-if="!items.length">{{ t('common.empty') }}</p>

    <table v-else class="records-table">
      <thead>
        <tr>
          <th>{{ t('record.list.recordCode') }}</th>
          <th v-for="field in fields" :key="field.id">{{ field.displayName }}</th>
          <th>{{ t('common.status') }}</th>
          <th>{{ t('common.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td>{{ item.recordCode }}</td>
          <td v-for="field in fields" :key="field.id">{{ displayValue(item, field.code) }}</td>
          <td><RecordStatusTag :status="item.status" /></td>
          <td class="records-table__actions">
            <router-link :to="`/records/${item.id}`" :data-testid="`record-view-${item.id}`">{{ t('record.list.view') }}</router-link>
          </td>
        </tr>
      </tbody>
    </table>

    <div class="records-pagination">
      <button data-testid="page-next" type="button" :disabled="pageNumber + 1 >= totalPages" @click="loadRecords(pageNumber + 1)">{{ t('record.list.nextPage') }}</button>
    </div>
  </section>
</template>
