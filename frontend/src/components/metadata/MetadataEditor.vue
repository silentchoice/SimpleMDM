<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

const drafts = ref<MetadataItem[]>([])
const editIndex = ref<number | null>(null)
const taskId = ref<number | null>(null)
const error = ref('')
const drawerOpen = computed(() => editIndex.value !== null)
const editing = computed(() => editIndex.value === null ? null : drafts.value[editIndex.value] ?? null)
const isFieldFamily = computed(() => props.family !== 'sub-types')

function deepCopy(items: MetadataItem[]): MetadataItem[] { return JSON.parse(JSON.stringify(items)) as MetadataItem[] }
function renumber(): void { drafts.value.forEach((item, index) => { if ('sortOrder' in item) item.sortOrder = index }) }
watch(() => props.activeItems, (items) => { drafts.value = deepCopy(items); taskId.value = null; error.value = '' }, { immediate: true, deep: true })

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
  if (duplicate) { error.value = 'Duplicate code'; return }
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
function validateBeforeSubmit(): string {
  const codes = new Set<string>()
  const orders = new Set<number>()
  for (const item of drafts.value) {
    const code = item.code.toLowerCase()
    if (codes.has(code)) return 'Duplicate code'
    codes.add(code)
    if ('sortOrder' in item) {
      if (orders.has(item.sortOrder)) return 'Duplicate sort order'
      orders.add(item.sortOrder)
    }
  }
  return ''
}
function fieldSubmission(item: FieldDefinition): FieldSubmission {
  return { code: item.code, displayName: item.displayName, fieldType: item.fieldType, required: item.required, options: [...item.options], shared: props.family === 'sub-fields' && item.shared, sortOrder: item.sortOrder }
}
function errorMessage(reason: unknown): string {
  const apiError = reason as ApiError
  return apiError.requestId ? `${apiError.message} (Request ID: ${apiError.requestId})` : apiError.message ?? 'Unable to submit changes'
}
async function submit(): Promise<void> {
  error.value = validateBeforeSubmit()
  if (error.value) return
  try {
    const body = isFieldFamily.value ? (drafts.value as FieldDefinition[]).map(fieldSubmission) : (drafts.value as SubType[]).map(({ code, name }) => ({ code, name }))
    taskId.value = (await props.onSubmit(body)).approvalTaskId
  } catch (reason) { error.value = errorMessage(reason) }
}
</script>

<template>
  <section class="metadata-editor">
    <div class="view-heading"><h2>{{ family === 'master-fields' ? 'Master fields' : family === 'sub-types' ? 'Sub-types' : 'Sub-fields' }}</h2><el-button data-testid="add-item" type="primary" @click="add">Add</el-button></div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="taskId" role="status">Approval task #{{ taskId }} submitted. ACTIVE metadata is unchanged.</p>
    <ol>
      <li v-for="(item, index) in drafts" :key="`${item.id}-${index}`"><span>{{ item.code }} — {{ 'displayName' in item ? item.displayName : item.name }}</span><el-button :data-testid="`edit-${index}`" text @click="edit(index)">Edit</el-button><el-button :data-testid="`move-up-${index}`" text :disabled="index === 0" @click="move(index, -1)">Up</el-button><el-button :data-testid="`move-down-${index}`" text :disabled="index === drafts.length - 1" @click="move(index, 1)">Down</el-button></li>
    </ol>
    <el-button :data-testid="`submit-${family}`" type="primary" @click="submit">Submit {{ family }}</el-button>
    <FieldEditorDrawer :open="drawerOpen" :family="family" :draft="editing" @close="discard" @save="saveDraft" />
  </section>
</template>
