<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { currentMasterType, listMasterFields, type FieldDefinition } from '../../api/metadata'
import { copyRecordDraft, listRecordDrafts, listRecords, type RecordDraft, type RecordSummary } from '../../api/records'
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
const myDrafts = ref<RecordDraft[]>([])
const pageNumber = ref(0)
const pageSize = ref(20)
const totalPages = ref(0)
const filters = ref<RecordFilterModel>({ recordCode: '', keyword: '', status: '', includeDeleted: false })
const latestRequest = ref(0)

const isEditor = computed(() => auth.hasAnyRole(['DEPT_EDITOR']))
const hasForeignRecords = computed(() => items.value.some((item) => !isOwnRecord(item)))

function errorMessage(reason: unknown): string {
  const value = reason as ApiError
  return value.requestId
    ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) })
    : value.message
}

function formatValue(value: unknown): string {
  if (Array.isArray(value)) return value.join(', ')
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return value == null ? '—' : String(value)
}

function displayValue(item: RecordSummary, code: string): string {
  return formatValue(item.masterValues[code])
}

function isOwnRecord(item: RecordSummary): boolean {
  return item.departmentId === auth.session?.department?.id
}

function sourceFields(item: RecordSummary): { code: string; value: unknown }[] {
  return Object.entries(item.masterValues)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([code, value]) => ({ code, value }))
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
    myDrafts.value = isEditor.value ? await listRecordDrafts() : []
    await loadRecords(0)
  } catch (reason) {
    loading.value = false
    error.value = errorMessage(reason)
  }
}

async function createDraft(): Promise<void> {
  if (!masterTypeId.value) return
  error.value = ''
  await router.push('/records/new')
}

async function copyRejected(draftId: number): Promise<void> {
  error.value = ''
  try {
    const copy = await copyRecordDraft(draftId)
    await router.push(`/records/drafts/${copy.id}`)
  } catch (reason) {
    error.value = errorMessage(reason)
  }
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

    <section v-if="isEditor && myDrafts.length" class="record-drafts">
      <h2>{{ t('record.list.myDrafts') }}</h2>
      <table class="records-table">
        <thead><tr><th>{{ t('record.list.recordCode') }}</th><th>{{ t('common.status') }}</th><th>{{ t('common.actions') }}</th></tr></thead>
        <tbody>
          <tr v-for="item in myDrafts" :key="item.id">
            <td>{{ item.recordCode }}</td>
            <td><RecordStatusTag :status="item.status" /></td>
            <td class="records-table__actions">
              <router-link :to="`/records/drafts/${item.id}`" :data-testid="`draft-resume-${item.id}`">{{ t('record.list.resumeDraft') }}</router-link>
              <button v-if="item.status === 'REJECTED'" :data-testid="`draft-copy-${item.id}`" type="button" @click="copyRejected(item.id)">{{ t('record.editor.copyRejected') }}</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <RecordFilters v-model="filters" @search="loadRecords(0)" />

    <p v-if="loading">{{ t('common.loading') }}</p>
    <p v-else-if="!items.length">{{ t('common.empty') }}</p>

    <table v-else class="records-table">
      <thead>
        <tr>
          <th>{{ t('record.list.recordCode') }}</th>
          <th v-for="field in fields" :key="field.id">{{ field.displayName }}</th>
          <th v-if="hasForeignRecords">{{ t('record.list.sourceFields') }}</th>
          <th>{{ t('common.status') }}</th>
          <th>{{ t('common.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id" :data-testid="`record-row-${item.id}`">
          <td>{{ item.recordCode }}</td>
          <td v-for="field in fields" :key="field.id" :data-testid="`record-field-${item.id}-${field.code}`">
            {{ isOwnRecord(item) ? displayValue(item, field.code) : '—' }}
          </td>
          <td v-if="hasForeignRecords">
            <dl v-if="!isOwnRecord(item)" :data-testid="`record-source-fields-${item.id}`">
              <template v-for="sourceField in sourceFields(item)" :key="sourceField.code">
                <dt><code>{{ sourceField.code }}</code></dt>
                <dd>{{ formatValue(sourceField.value) }}</dd>
              </template>
              <template v-if="!sourceFields(item).length">—</template>
            </dl>
            <span v-else>—</span>
          </td>
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
