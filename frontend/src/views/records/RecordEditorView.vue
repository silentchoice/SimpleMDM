<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { acquireRecordLock, getRecord, getRecordDraft, listRecordHistory, releaseRecordLock, renewRecordLock, submitRecordDraft, updateRecordDraft, type EditLock, type HistorySnapshot, type RecordDetail, type RecordDraft, type RecordDraftCommand } from '../../api/records'
import { listMasterFields, listSubFields, listSubTypes, type FieldDefinition, type SubType } from '../../api/metadata'
import DynamicChildTable, { type EditorChildRow } from '../../components/records/DynamicChildTable.vue'
import DynamicMasterForm from '../../components/records/DynamicMasterForm.vue'
import RecordHistoryTable from '../../components/records/RecordHistoryTable.vue'
import RecordStatusTag from '../../components/records/RecordStatusTag.vue'
import type { ApiError } from '../../types'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const error = ref('')
const refreshGuidance = ref('')
const readOnlyConflict = ref(false)
const activeTab = ref<'current' | 'draft' | 'history'>('draft')
const currentRecord = ref<RecordDetail | null>(null)
const history = ref<HistorySnapshot[]>([])
const draft = ref<RecordDraft | null>(null)
const masterFields = ref<FieldDefinition[]>([])
const subTypes = ref<SubType[]>([])
const subFields = ref<Record<number, FieldDefinition[]>>({})
const masterValues = ref<Record<string, unknown>>({})
const childRows = ref<Record<number, EditorChildRow[]>>({})
const fieldErrors = ref<Record<string, string>>({})
const saving = ref(false)
const submitting = ref(false)
const lastSavedPayload = ref('')
const activeSubTypeId = ref(0)
const lock = ref<EditLock | null>(null)
const skipLeaveConfirm = ref(false)
let renewTimer: number | null = null
let nextClientRowId = 1

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function errorMessage(reason: unknown): string {
  const value = reason as ApiError
  return value.requestId
    ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) })
    : value.message
}

function rowKey(recordId: number | null): string {
  if (recordId != null) return `persisted-${recordId}`
  const value = `new-${nextClientRowId}`
  nextClientRowId += 1
  return value
}

function draftFieldValue(code: string): string {
  const value = draft.value?.deleteReason ?? ''
  return code === 'deleteReason' ? value : ''
}

function buildCommand(): RecordDraftCommand {
  return {
    recordId: draft.value?.recordId ?? null,
    masterTypeId: draft.value?.masterTypeId ?? 0,
    baseVersion: draft.value?.baseVersion ?? 0,
    action: draft.value?.action ?? 'CREATE',
    masterValues: clone(masterValues.value),
    children: subTypes.value.map((type) => ({
      subTypeId: type.id,
      rows: (childRows.value[type.id] ?? []).map((row, index) => ({
        recordId: row.recordId,
        rowOrder: index,
        values: clone(row.values)
      }))
    })),
    deleteReason: draft.value?.action === 'DELETE' ? (draft.value.deleteReason ?? '').trim() : null
  }
}

const isDirty = computed(() => lastSavedPayload.value !== JSON.stringify(buildCommand()))
const canSubmitDelete = computed(() => draft.value?.action !== 'DELETE' || Boolean((draft.value?.deleteReason ?? '').trim()))

function displayValue(values: Record<string, unknown>, code: string): string {
  const value = values[code]
  if (Array.isArray(value)) return value.join(', ')
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return value == null ? '—' : String(value)
}

function syncDraftState(loaded: RecordDraft): void {
  draft.value = clone(loaded)
  masterValues.value = clone(loaded.masterValues)
  childRows.value = Object.fromEntries(loaded.children.map((group) => [
    group.subTypeId,
    group.rows.map((row) => ({
      clientId: rowKey(row.recordId),
      recordId: row.recordId,
      rowOrder: row.rowOrder,
      values: clone(row.values)
    }))
  ]))
  lastSavedPayload.value = JSON.stringify(buildCommand())
  activeSubTypeId.value = subTypes.value[0]?.id ?? 0
}

function validate(): boolean {
  const nextErrors: Record<string, string> = {}
  for (const field of masterFields.value) {
    const value = masterValues.value[field.code]
    const empty = value == null || value === '' || (Array.isArray(value) && value.length === 0)
    if (field.required && empty) nextErrors[field.code] = `${field.displayName} ${t('record.editor.required')}`
  }
  for (const type of subTypes.value) {
    for (const row of childRows.value[type.id] ?? []) {
      for (const field of subFields.value[type.id] ?? []) {
        const value = row.values[field.code]
        const empty = value == null || value === ''
        if (field.required && empty) nextErrors[`${type.id}:${row.clientId}:${field.code}`] = `${field.displayName} ${t('record.editor.required')}`
      }
    }
  }
  if (draft.value?.action === 'DELETE' && !(draft.value.deleteReason ?? '').trim()) {
    nextErrors.deleteReason = t('record.editor.deleteReasonRequired')
  }
  fieldErrors.value = nextErrors
  return Object.keys(nextErrors).length === 0
}

function scheduleRenew(nextLock: EditLock): void {
  if (renewTimer != null) window.clearTimeout(renewTimer)
  const ttlMs = new Date(nextLock.expiresAt).getTime() - Date.now()
  if (ttlMs <= 1000 || !draft.value?.recordId) return
  renewTimer = window.setTimeout(async () => {
    if (!draft.value?.recordId || !lock.value?.token) return
    try {
      const renewed = await renewRecordLock(draft.value.recordId, lock.value.token)
      lock.value = renewed
      scheduleRenew(renewed)
    } catch (reason) {
      readOnlyConflict.value = true
      error.value = errorMessage(reason)
    }
  }, Math.max(1000, Math.floor(ttlMs / 2)))
}

async function bestEffortRelease(): Promise<void> {
  if (!draft.value?.recordId || !lock.value?.token) return
  const token = lock.value.token
  const recordId = draft.value.recordId
  lock.value = null
  if (renewTimer != null) window.clearTimeout(renewTimer)
  renewTimer = null
  try {
    await releaseRecordLock(recordId, token)
  } catch {
    // best effort
  }
}

async function acquireLockIfNeeded(): Promise<void> {
  if (!draft.value?.recordId) return
  try {
    const acquired = await acquireRecordLock(draft.value.recordId)
    lock.value = acquired
    scheduleRenew(acquired)
  } catch (reason) {
    readOnlyConflict.value = true
    error.value = errorMessage(reason)
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  refreshGuidance.value = ''
  readOnlyConflict.value = false
  try {
    const loadedDraft = await getRecordDraft(Number(route.params.draftId))
    const [fields, types] = await Promise.all([
      listMasterFields(loadedDraft.masterTypeId),
      listSubTypes(loadedDraft.masterTypeId)
    ])
    masterFields.value = fields
    subTypes.value = types
    const children = await Promise.all(types.map(async (type) => [type.id, await listSubFields(type.id)] as const))
    subFields.value = Object.fromEntries(children)
    syncDraftState(loadedDraft)
    if (loadedDraft.recordId) {
      const [detail, versions] = await Promise.all([
        getRecord(loadedDraft.recordId),
        listRecordHistory(loadedDraft.recordId)
      ])
      currentRecord.value = detail
      history.value = versions
      await acquireLockIfNeeded()
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function updateMasterValue(code: string, value: unknown): void {
  masterValues.value = { ...masterValues.value, [code]: value }
}

function updateChildValue(subTypeId: number, clientId: string, code: string, value: unknown): void {
  childRows.value = {
    ...childRows.value,
    [subTypeId]: (childRows.value[subTypeId] ?? []).map((row) => row.clientId === clientId
      ? { ...row, values: { ...row.values, [code]: value } }
      : row)
  }
}

function addChildRow(subTypeId: number): void {
  const values = Object.fromEntries((subFields.value[subTypeId] ?? []).map((field) => [field.code, '']))
  childRows.value = {
    ...childRows.value,
    [subTypeId]: [...(childRows.value[subTypeId] ?? []), { clientId: rowKey(null), recordId: null, rowOrder: (childRows.value[subTypeId] ?? []).length, values }]
  }
}

function removeChildRow(subTypeId: number, clientId: string): void {
  childRows.value = {
    ...childRows.value,
    [subTypeId]: (childRows.value[subTypeId] ?? []).filter((row) => row.clientId !== clientId)
  }
}

function moveChildRow(subTypeId: number, clientId: string, direction: 'up' | 'down'): void {
  const rows = [...(childRows.value[subTypeId] ?? [])]
  const index = rows.findIndex((row) => row.clientId === clientId)
  if (index < 0) return
  const swapIndex = direction === 'up' ? index - 1 : index + 1
  if (swapIndex < 0 || swapIndex >= rows.length) return
  ;[rows[index], rows[swapIndex]] = [rows[swapIndex], rows[index]]
  childRows.value = { ...childRows.value, [subTypeId]: rows }
}

async function saveDraft(): Promise<void> {
  if (!draft.value || saving.value || submitting.value || readOnlyConflict.value) return
  refreshGuidance.value = ''
  if (!validate()) return
  saving.value = true
  try {
    const saved = await updateRecordDraft(draft.value.id, buildCommand())
    syncDraftState(saved)
    error.value = ''
  } catch (reason) {
    error.value = errorMessage(reason)
    if ((reason as ApiError).status === 409) refreshGuidance.value = t('record.editor.refreshGuidance')
  } finally {
    saving.value = false
  }
}

async function submitDraft(): Promise<void> {
  if (!draft.value || saving.value || submitting.value || readOnlyConflict.value || !canSubmitDelete.value) return
  refreshGuidance.value = ''
  if (!validate()) return
  submitting.value = true
  try {
    await submitRecordDraft(draft.value.id)
    await bestEffortRelease()
    skipLeaveConfirm.value = true
    await router.push(draft.value.recordId ? `/records/${draft.value.recordId}` : '/records')
  } catch (reason) {
    error.value = errorMessage(reason)
    if ((reason as ApiError).status === 409) refreshGuidance.value = t('record.editor.refreshGuidance')
  } finally {
    submitting.value = false
    skipLeaveConfirm.value = false
  }
}

async function cancelEdit(): Promise<void> {
  if (isDirty.value && !window.confirm(t('record.editor.discardConfirm'))) return
  skipLeaveConfirm.value = true
  await bestEffortRelease()
  await router.push(draft.value?.recordId ? `/records/${draft.value.recordId}` : '/records')
  skipLeaveConfirm.value = false
}

onBeforeRouteLeave(() => {
  if (skipLeaveConfirm.value) return true
  if (isDirty.value && !window.confirm(t('record.editor.discardConfirm'))) return false
  void bestEffortRelease()
  return true
})

onMounted(() => {
  void load()
  window.addEventListener('beforeunload', beforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  void bestEffortRelease()
})

function beforeUnload(event: BeforeUnloadEvent): void {
  if (!isDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}
</script>

<template>
  <section class="content-view">
    <div class="view-heading">
      <div>
        <h1>{{ t('record.editor.title') }}</h1>
        <p>{{ t('record.editor.description') }}</p>
      </div>
      <RecordStatusTag v-if="draft" :status="draft.status" />
    </div>

    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="refreshGuidance" class="form-error">{{ refreshGuidance }}</p>
    <p v-if="loading">{{ t('common.loading') }}</p>

    <template v-else-if="draft">
      <div class="record-tabs">
        <button type="button" @click="activeTab = 'current'">{{ t('record.editor.tabs.current') }}</button>
        <button type="button" @click="activeTab = 'draft'">{{ t('record.editor.tabs.draft') }}</button>
        <button type="button" @click="activeTab = 'history'">{{ t('record.editor.tabs.history') }}</button>
      </div>

      <div v-if="activeTab === 'current' && currentRecord" class="record-detail__grid">
        <template v-for="field in masterFields" :key="field.id">
          <dt>{{ field.displayName }}</dt>
          <dd>{{ displayValue(currentRecord.masterValues, field.code) }}</dd>
        </template>
      </div>

      <div v-else-if="activeTab === 'history'">
        <RecordHistoryTable :snapshots="history" :fields="masterFields" />
      </div>

      <div v-else>
        <p v-if="readOnlyConflict">{{ t('record.editor.readOnly') }}</p>
        <label class="dynamic-form__field">
          <span>{{ t('record.list.recordCode') }}</span>
          <input name="recordCode" readonly :value="draft.recordCode">
        </label>
        <DynamicMasterForm :fields="masterFields" :values="masterValues" :errors="fieldErrors" :readonly="readOnlyConflict" @update="updateMasterValue" />

        <div class="record-editor__subtypes">
          <button
            v-for="type in subTypes"
            :key="type.id"
            type="button"
            :data-testid="`subtype-tab-${type.id}`"
            @click="activeSubTypeId = type.id"
          >
            {{ type.name }}
          </button>
        </div>

        <DynamicChildTable
          v-for="type in subTypes.filter((item) => item.id === activeSubTypeId)"
          :key="type.id"
          :subtype="type"
          :fields="subFields[type.id] ?? []"
          :rows="childRows[type.id] ?? []"
          :errors="fieldErrors"
          :readonly="readOnlyConflict"
          @add="addChildRow"
          @remove="removeChildRow"
          @move="moveChildRow"
          @update="updateChildValue"
        />

        <label v-if="draft.action === 'DELETE'" class="dynamic-form__field">
          <span>{{ t('record.detail.deleteReason') }}</span>
          <input name="deleteReason" :value="draftFieldValue('deleteReason')" :readonly="readOnlyConflict" @input="draft = draft ? { ...draft, deleteReason: ($event.target as HTMLInputElement).value } : null">
          <p v-if="fieldErrors.deleteReason" class="form-error">{{ fieldErrors.deleteReason }}</p>
        </label>

        <div v-if="!readOnlyConflict" class="record-editor__actions">
          <el-button data-testid="record-cancel" @click="cancelEdit">{{ t('common.cancel') }}</el-button>
          <el-button data-testid="record-save" type="primary" :disabled="saving || submitting" @click="saveDraft">{{ saving ? t('common.saving') : t('common.save') }}</el-button>
          <el-button data-testid="record-submit" type="success" :disabled="saving || submitting || !canSubmitDelete" @click="submitDraft">{{ t('record.editor.submit') }}</el-button>
        </div>
      </div>
    </template>
  </section>
</template>
