<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldEditorDrawer from './FieldEditorDrawer.vue'
import type { ApprovalSubmission, FieldDefinition, FieldSubmission, FieldType, SubType, SubTypeSubmission } from '../../api/metadata'
import type { ApiError } from '../../types'

export type MetadataFamily = 'master-fields' | 'sub-types' | 'sub-fields'
type MetadataItem = FieldDefinition | SubType

const props = defineProps<{
  family: MetadataFamily
  ownerId: number
  activeItems: MetadataItem[]
  onSubmit: (items: FieldSubmission[] | SubTypeSubmission[]) => Promise<ApprovalSubmission>
}>()
const { t } = useI18n()

const drafts = ref<MetadataItem[]>([])
const editIndex = ref<number | null>(null)
const taskId = ref<number | null>(null)
const error = ref('')
const saving = ref(false)
const drawerOpen = computed(() => editIndex.value !== null)
const editing = computed(() => editIndex.value === null ? null : drafts.value[editIndex.value] ?? null)
const isFieldFamily = computed(() => props.family !== 'sub-types')

function deepCopy(items: MetadataItem[]): MetadataItem[] { return JSON.parse(JSON.stringify(items)) as MetadataItem[] }
function renumber(): void { drafts.value.forEach((item, index) => { if ('sortOrder' in item) item.sortOrder = index }) }
watch([() => props.activeItems, () => props.ownerId], ([items]) => { drafts.value = deepCopy(items); taskId.value = null; error.value = '' }, { immediate: true, deep: true })

function add(): void {
  taskId.value = null
  if (isFieldFamily.value) {
    drafts.value.push({ id: 0, ownerTypeId: props.ownerId, code: '', displayName: '', fieldType: '' as FieldType, required: false, options: [], shared: false, sortOrder: drafts.value.length, status: 'ACTIVE' })
  } else drafts.value.push({ id: 0, masterTypeId: props.ownerId, code: '', name: '', status: 'ACTIVE' })
  editIndex.value = drafts.value.length - 1
}

function edit(index: number): void { error.value = ''; editIndex.value = index }
function discard(): void {
  if (editIndex.value !== null && ('code' in drafts.value[editIndex.value]) && !drafts.value[editIndex.value].code) drafts.value.splice(editIndex.value, 1)
  editIndex.value = null
}
function saveDraft(value: MetadataItem): void {
  if (editIndex.value === null) return
  const duplicate = drafts.value.some((item, index) => index !== editIndex.value && item.code.toLowerCase() === value.code.toLowerCase())
  if (duplicate) { error.value = t('metadata.editor.duplicateCode'); return }
  drafts.value.splice(editIndex.value, 1, value)
  renumber()
  editIndex.value = null
  error.value = ''
}
function move(index: number, direction: -1 | 1): void {
  const target = index + direction
  if (target < 0 || target >= drafts.value.length) return
  const [item] = drafts.value.splice(index, 1)
  drafts.value.splice(target, 0, item)
  renumber()
}
function remove(index: number): void {
  if (saving.value) return
  drafts.value.splice(index, 1)
  renumber()
  taskId.value = null
}
function validateBeforeSubmit(): string {
  const codes = new Set<string>()
  const orders = new Set<number>()
  for (const item of drafts.value) {
    const code = item.code.toLowerCase()
    if (codes.has(code)) return t('metadata.editor.duplicateCode')
    codes.add(code)
    if ('sortOrder' in item) {
      if (orders.has(item.sortOrder)) return t('metadata.editor.duplicateSortOrder')
      orders.add(item.sortOrder)
    }
  }
  return ''
}
function fieldSubmission(item: FieldDefinition): FieldSubmission {
  return { code: item.code, displayName: item.displayName, fieldType: item.fieldType, required: item.required, options: [...item.options], shared: item.shared, sortOrder: item.sortOrder }
}
function errorMessage(reason: unknown): string {
  const apiError = reason as ApiError
  const message = apiError.message ?? t('metadata.editor.unableSubmit')
  return apiError.requestId ? t('common.apiError', { message, requestId: t('common.requestId', { id: apiError.requestId }) }) : message
}
async function submit(): Promise<void> {
  if (saving.value) return
  error.value = validateBeforeSubmit()
  if (error.value) return
  saving.value = true
  try {
    const body = isFieldFamily.value ? (drafts.value as FieldDefinition[]).map(fieldSubmission) : (drafts.value as SubType[]).map(({ code, name }) => ({ code, name }))
    taskId.value = (await props.onSubmit(body)).approvalTaskId
  } catch (reason) { error.value = errorMessage(reason) } finally { saving.value = false }
}
</script>

<template>
  <section class="metadata-editor">
    <div class="view-heading"><h2>{{ t(`metadata.editor.${family === 'master-fields' ? 'masterFields' : family === 'sub-types' ? 'subTypes' : 'subFields'}`) }}</h2><el-button data-testid="add-item" type="primary" :disabled="saving" @click="add">{{ t('metadata.editor.add') }}</el-button></div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="taskId" role="status">{{ t('metadata.editor.taskSubmitted', { id: taskId }) }}</p>
    <ol>
      <li v-for="(item, index) in drafts" :key="`${item.id}-${index}`">
        <span>{{ item.code }} — {{ 'displayName' in item ? item.displayName : item.name }}</span>
        <template v-if="'displayName' in item">
          <span :data-testid="`field-type-${index}`">{{ item.fieldType ? t(`metadata.fieldTypes.${item.fieldType}`) : '' }}</span>
          <span :data-testid="`field-shared-${index}`">{{ item.shared ? t('metadata.fieldEditor.shared') : '-' }}</span>
        </template>
        <el-button :data-testid="`edit-${index}`" text :disabled="saving" @click="edit(index)">{{ t('common.edit') }}</el-button>
        <el-button :data-testid="`remove-${index}`" text type="danger" :disabled="saving" @click="remove(index)">{{ t('metadata.editor.remove') }}</el-button>
        <el-button :data-testid="`move-up-${index}`" text :disabled="saving || index === 0" @click="move(index, -1)">{{ t('metadata.editor.up') }}</el-button>
        <el-button :data-testid="`move-down-${index}`" text :disabled="saving || index === drafts.length - 1" @click="move(index, 1)">{{ t('metadata.editor.down') }}</el-button>
      </li>
    </ol>
    <el-button :data-testid="`submit-${family}`" type="primary" :loading="saving" :disabled="saving" @click="submit">{{ t('metadata.editor.submit', { family: t(`metadata.editor.${family === 'master-fields' ? 'masterFields' : family === 'sub-types' ? 'subTypes' : 'subFields'}`) }) }}</el-button>
    <FieldEditorDrawer :open="drawerOpen" :family="family" :draft="editing" @close="discard" @save="saveDraft" />
  </section>
</template>
