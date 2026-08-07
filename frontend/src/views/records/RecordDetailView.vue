<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { listMasterFields, listSubFields, listSubTypes, type FieldDefinition, type SubType } from '../../api/metadata'
import { createRecordDraft, getRecord, listRecordHistory, requestRecordDeletion, type HistorySnapshot, type RecordDraftCommand, type RecordDetail } from '../../api/records'
import { useAuthStore } from '../../stores/auth'
import RecordHistoryTable from '../../components/records/RecordHistoryTable.vue'
import RecordStatusTag from '../../components/records/RecordStatusTag.vue'
import RecordSnapshotTables from '../../components/records/RecordSnapshotTables.vue'
import type { ApiError } from '../../types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n()

const loading = ref(false)
const error = ref('')
const record = ref<RecordDetail | null>(null)
const history = ref<HistorySnapshot[]>([])
const fields = ref<FieldDefinition[]>([])
const subTypes = ref<SubType[]>([])
const subFields = ref<Record<number, FieldDefinition[]>>({})
const activeTab = ref<'current' | 'diff' | 'history'>('current')
const deleteReason = ref('')

const isEditor = computed(() => auth.hasAnyRole(['DEPT_EDITOR']))
const canMutate = computed(() => isEditor.value
  && record.value?.status === 'ACTIVE'
  && record.value.departmentId === auth.session?.department?.id)

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function errorMessage(reason: unknown): string {
  const value = reason as ApiError
  return value.requestId
    ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) })
    : value.message
}

const previousVersion = computed(() => history.value.find((item) => item.version !== record.value?.version) ?? null)
async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  record.value = null
  history.value = []
  try {
    const detail = await getRecord(Number(route.params.recordId))
    record.value = detail
    history.value = await listRecordHistory(detail.id)
    try {
      if (detail.departmentId === auth.session?.department?.id) {
        const [fieldDefinitions, types] = await Promise.all([
          listMasterFields(detail.masterTypeId), listSubTypes(detail.masterTypeId)
        ])
        fields.value = fieldDefinitions
        subTypes.value = types
        const children = await Promise.all(types.map(async (type) =>
          [type.id, await listSubFields(type.id)] as const))
        subFields.value = Object.fromEntries(children)
      } else {
        fields.value = []
        subTypes.value = []
        subFields.value = {}
      }
    } catch {
      fields.value = []
      subTypes.value = []
      subFields.value = {}
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function createEditDraft(): Promise<void> {
  if (!record.value) return
  error.value = ''
  const body: RecordDraftCommand = {
    recordId: record.value.id,
    masterTypeId: record.value.masterTypeId,
    baseVersion: record.value.version,
    action: 'UPDATE',
    masterValues: clone(record.value.masterValues),
    children: clone(record.value.children).map((group) => ({
      subTypeId: group.subTypeId,
      rows: group.rows.map((row) => ({ recordId: row.id, rowOrder: row.rowOrder, values: clone(row.values) }))
    })),
    deleteReason: null
  }
  try {
    const draft = await createRecordDraft(body)
    await router.push(`/records/drafts/${draft.id}`)
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

async function requestDelete(): Promise<void> {
  if (!record.value || !deleteReason.value.trim()) return
  error.value = ''
  try {
    const draft = await requestRecordDeletion(record.value.id, deleteReason.value.trim())
    await router.push(`/records/drafts/${draft.id}`)
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

onMounted(load)
</script>

<template>
  <section class="content-view">
    <div class="view-heading">
      <div>
        <h1>{{ t('record.detail.title') }}</h1>
        <p>{{ t('record.detail.description') }}</p>
      </div>
      <div v-if="record && canMutate" class="record-detail__actions">
        <el-button :data-testid="`record-edit-${record.id}`" type="primary" @click="createEditDraft">{{ t('record.detail.editDraft') }}</el-button>
      </div>
    </div>

    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-else-if="loading">{{ t('common.loading') }}</p>
    <template v-else-if="record">
      <div class="record-detail__summary">
        <h2>{{ record.recordCode }}</h2>
        <RecordStatusTag :status="record.status" />
      </div>

      <div v-if="canMutate" class="record-detail__delete">
        <label>
          <span>{{ t('record.detail.deleteReason') }}</span>
          <input name="deleteReason" :value="deleteReason" @input="deleteReason = ($event.target as HTMLInputElement).value">
        </label>
        <el-button :data-testid="`record-delete-${record.id}`" type="danger" :disabled="!deleteReason.trim()" @click="requestDelete">{{ t('record.detail.requestDelete') }}</el-button>
      </div>

      <div class="record-tabs">
        <button data-testid="detail-tab-current" type="button" @click="activeTab = 'current'">{{ t('record.detail.tabs.current') }}</button>
        <button data-testid="detail-tab-diff" type="button" @click="activeTab = 'diff'">{{ t('record.detail.tabs.diff') }}</button>
        <button data-testid="detail-tab-history" type="button" @click="activeTab = 'history'">{{ t('record.detail.tabs.history') }}</button>
      </div>

      <RecordSnapshotTables
        v-if="activeTab === 'current'"
        :snapshot="record"
        :master-fields="fields"
        :sub-types="subTypes"
        :sub-fields="subFields"
        testid-prefix="current"
      />

      <div v-else-if="activeTab === 'diff'" class="record-detail__diff">
        <RecordSnapshotTables
          v-if="previousVersion"
          :snapshot="previousVersion"
          :master-fields="fields"
          :sub-types="subTypes"
          :sub-fields="subFields"
          testid-prefix="diff-before"
        />
        <RecordSnapshotTables
          :snapshot="record"
          :master-fields="fields"
          :sub-types="subTypes"
          :sub-fields="subFields"
          testid-prefix="diff-after"
        />
      </div>

      <RecordHistoryTable v-else :snapshots="history" :fields="fields" :sub-types="subTypes" :sub-fields="subFields" />
    </template>
  </section>
</template>
