<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../stores/auth'
import { currentMasterType, submitMasterFields, submitSubFields, submitSubTypes, type FieldDefinition, type SubType } from '../../api/metadata'
import ActiveMetadataPanel from '../../components/metadata/ActiveMetadataPanel.vue'
import MetadataEditor from '../../components/metadata/MetadataEditor.vue'
import type { ApiError } from '../../types'

const props = withDefaults(defineProps<{ initialTab?: 'active' | 'submit' }>(), { initialTab: 'active' })
const { t } = useI18n()
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
  const message = value.message ?? t('metadata.department.loadFallback')
  return value.requestId ? t('common.apiError', { message, requestId: t('common.requestId', { id: value.requestId }) }) : message
}
async function loadAssignment(): Promise<void> {
  clearActive(); assignmentError.value = ''
  try { const assignment = await currentMasterType(); selectedMasterTypeId.value = assignment.id; assignmentName.value = `${assignment.name} (${assignment.code})` } catch (reason) { assignmentError.value = errorMessage(reason) }
}
onMounted(loadAssignment)
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>{{ t('metadata.department.title') }}</h1><p>{{ t('metadata.department.description') }}</p><p v-if="assignmentName">{{ t('metadata.department.currentAssignment', { name: assignmentName }) }}</p><p v-if="assignmentError" class="form-error" role="alert">{{ assignmentError }}</p></div></div>
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="t('metadata.department.currentActive')" name="active"><ActiveMetadataPanel :master-type-id="selectedMasterTypeId" @loaded="receiveActive" /></el-tab-pane>
      <el-tab-pane v-if="isEditor" :label="t('metadata.department.submitChanges')" name="submit" data-testid="submit-changes-tab">
        <MetadataEditor family="master-fields" :owner-id="selectedMasterTypeId" :active-items="activeFields" :on-submit="(items) => submitMasterFields(selectedMasterTypeId, items as never)" />
        <MetadataEditor family="sub-types" :owner-id="selectedMasterTypeId" :active-items="activeSubTypes" :on-submit="(items) => submitSubTypes(selectedMasterTypeId, items as never)" />
        <label>{{ t('metadata.department.subType') }} <select v-model.number="selectedSubTypeId" name="subTypeId"><option :value="0" disabled>{{ t('metadata.department.selectSubType') }}</option><option v-for="type in activeSubTypes" :key="type.id" :value="type.id">{{ type.name }} ({{ type.code }})</option></select></label>
        <MetadataEditor v-if="selectedSubTypeId" family="sub-fields" :owner-id="selectedSubTypeId" :active-items="activeSubFields[selectedSubTypeId] ?? []" :on-submit="(items) => submitSubFields(selectedSubTypeId, items as never)" />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
