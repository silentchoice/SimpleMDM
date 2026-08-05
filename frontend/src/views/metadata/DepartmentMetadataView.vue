<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { submitMasterFields, submitSubFields, submitSubTypes, type FieldDefinition, type SubType } from '../../api/metadata'
import ActiveMetadataPanel from '../../components/metadata/ActiveMetadataPanel.vue'
import MetadataEditor from '../../components/metadata/MetadataEditor.vue'

const props = withDefaults(defineProps<{ masterTypeId?: number }>(), { masterTypeId: 0 })
const auth = useAuthStore()
const selectedMasterTypeId = ref(props.masterTypeId)
const isEditor = computed(() => auth.hasAnyRole(['DEPT_EDITOR']))
const activeFields = ref<FieldDefinition[]>([])
const activeSubTypes = ref<SubType[]>([])
const activeSubFields = ref<Record<number, FieldDefinition[]>>({})
const selectedSubTypeId = ref(0)
watch(() => props.masterTypeId, (value) => { selectedMasterTypeId.value = value })
function receiveActive(value: { fields: FieldDefinition[], subTypes: SubType[], subFields: Record<number, FieldDefinition[]> }): void {
  activeFields.value = value.fields
  activeSubTypes.value = value.subTypes
  activeSubFields.value = value.subFields
}
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>Department metadata</h1><p>Inspect approved definitions or submit an independent change for approval.</p></div><label>Master type ID <input v-model.number="selectedMasterTypeId" type="number" min="1" /></label></div>
    <el-tabs>
      <el-tab-pane label="Current active version"><ActiveMetadataPanel :master-type-id="selectedMasterTypeId" @loaded="receiveActive" /></el-tab-pane>
      <el-tab-pane v-if="isEditor" label="Submit changes" data-testid="submit-changes-tab">
        <MetadataEditor family="master-fields" :owner-id="selectedMasterTypeId" :active-items="activeFields" :on-submit="(items) => submitMasterFields(selectedMasterTypeId, items as never)" />
        <MetadataEditor family="sub-types" :owner-id="selectedMasterTypeId" :active-items="activeSubTypes" :on-submit="(items) => submitSubTypes(selectedMasterTypeId, items as never)" />
        <label>Sub-type ID <input v-model.number="selectedSubTypeId" type="number" min="1" /></label>
        <MetadataEditor v-if="selectedSubTypeId" family="sub-fields" :owner-id="selectedSubTypeId" :active-items="activeSubFields[selectedSubTypeId] ?? []" :on-submit="(items) => submitSubFields(selectedSubTypeId, items as never)" />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
