<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { currentMasterType, submitMasterFields, submitSubFields, submitSubTypes, type FieldDefinition, type SubType } from '../../api/metadata'
import ActiveMetadataPanel from '../../components/metadata/ActiveMetadataPanel.vue'
import MetadataEditor from '../../components/metadata/MetadataEditor.vue'
import type { ApiError } from '../../types'

const props = withDefaults(defineProps<{ initialTab?: 'active' | 'submit' }>(), { initialTab: 'active' })
const auth = useAuthStore()
const selectedMasterTypeId = ref(0)
const activeTab = ref<'active' | 'submit'>(props.initialTab)
const assignmentName = ref('')
const assignmentError = ref('')
const isEditor = computed(() => auth.hasAnyRole(['DEPT_EDITOR']))
const activeFields = ref<FieldDefinition[]>([])
const activeSubTypes = ref<SubType[]>([])
const activeSubFields = ref<Record<number, FieldDefinition[]>>({})
const selectedSubTypeId = ref(0)
function clearActive(): void { activeFields.value = []; activeSubTypes.value = []; activeSubFields.value = {}; selectedSubTypeId.value = 0 }
watch(selectedMasterTypeId, clearActive)
watch(() => props.initialTab, (value) => { activeTab.value = value })
function receiveActive(value: { masterTypeId: number, fields: FieldDefinition[], subTypes: SubType[], subFields: Record<number, FieldDefinition[]> }): void {
  if (value.masterTypeId !== selectedMasterTypeId.value) return
  activeFields.value = value.fields
  activeSubTypes.value = value.subTypes
  activeSubFields.value = value.subFields
  if (!activeSubFields.value[selectedSubTypeId.value]) selectedSubTypeId.value = value.subTypes[0]?.id ?? 0
}
function errorMessage(reason: unknown): string {
  const value = reason as ApiError
  const message = value.message ?? 'Unable to load current master type'
  return value.requestId ? `${message} (Request ID: ${value.requestId})` : message
}
async function loadAssignment(): Promise<void> {
  clearActive(); assignmentError.value = ''
  try { const assignment = await currentMasterType(); selectedMasterTypeId.value = assignment.id; assignmentName.value = `${assignment.name} (${assignment.code})` } catch (reason) { assignmentError.value = errorMessage(reason) }
}
onMounted(loadAssignment)
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>Department metadata</h1><p>Inspect approved definitions or submit an independent change for approval.</p><p v-if="assignmentName">Current assignment: {{ assignmentName }}</p><p v-if="assignmentError" class="form-error" role="alert">{{ assignmentError }}</p></div></div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="Current active version" name="active"><ActiveMetadataPanel :master-type-id="selectedMasterTypeId" @loaded="receiveActive" /></el-tab-pane>
      <el-tab-pane v-if="isEditor" label="Submit changes" name="submit" data-testid="submit-changes-tab">
        <MetadataEditor family="master-fields" :owner-id="selectedMasterTypeId" :active-items="activeFields" :on-submit="(items) => submitMasterFields(selectedMasterTypeId, items as never)" />
        <MetadataEditor family="sub-types" :owner-id="selectedMasterTypeId" :active-items="activeSubTypes" :on-submit="(items) => submitSubTypes(selectedMasterTypeId, items as never)" />
        <label>Sub-type <select v-model.number="selectedSubTypeId" name="subTypeId"><option :value="0" disabled>Select a sub-type</option><option v-for="type in activeSubTypes" :key="type.id" :value="type.id">{{ type.name }} ({{ type.code }})</option></select></label>
        <MetadataEditor v-if="selectedSubTypeId" family="sub-fields" :owner-id="selectedSubTypeId" :active-items="activeSubFields[selectedSubTypeId] ?? []" :on-submit="(items) => submitSubFields(selectedSubTypeId, items as never)" />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
