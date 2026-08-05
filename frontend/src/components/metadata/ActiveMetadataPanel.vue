<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { listMasterFields, listSubFields, listSubTypes, type FieldDefinition, type SubType } from '../../api/metadata'
import type { ApiError } from '../../types'

const props = withDefaults(defineProps<{ masterTypeId?: number }>(), { masterTypeId: 0 })
const emit = defineEmits<{ loaded: [value: { fields: FieldDefinition[], subTypes: SubType[], subFields: Record<number, FieldDefinition[]> }] }>()
const fields = ref<FieldDefinition[]>([])
const subTypes = ref<SubType[]>([])
const subFields = ref<Record<number, FieldDefinition[]>>({})
const loading = ref(false)
const error = ref('')
function message(reason: unknown): string { const value = reason as ApiError; return value.requestId ? `${value.message} (Request ID: ${value.requestId})` : value.message }
async function refresh(): Promise<void> {
  if (!props.masterTypeId) return
  loading.value = true; error.value = ''
  try {
    const [masterFields, types] = await Promise.all([listMasterFields(props.masterTypeId), listSubTypes(props.masterTypeId)])
    fields.value = masterFields
    subTypes.value = types
    const lists = await Promise.all(types.map(async (type) => [type.id, await listSubFields(type.id)] as const))
    subFields.value = Object.fromEntries(lists)
    emit('loaded', JSON.parse(JSON.stringify({ fields: fields.value, subTypes: subTypes.value, subFields: subFields.value })) as { fields: FieldDefinition[], subTypes: SubType[], subFields: Record<number, FieldDefinition[]> })
  } catch (reason) { error.value = message(reason) } finally { loading.value = false }
}
watch(() => props.masterTypeId, refresh)
onMounted(refresh)
</script>

<template>
  <section aria-label="Current active version">
    <div class="view-heading"><div><h2>Current active version</h2><p>Approved metadata is read-only.</p></div><el-button data-testid="refresh-active" :loading="loading" @click="refresh">Refresh</el-button></div>
    <p v-if="!masterTypeId">Enter a master type ID to inspect its department ACTIVE metadata.</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <template v-if="masterTypeId">
      <h3>Master fields</h3><ul><li v-for="field in fields" :key="field.id">{{ field.displayName }} ({{ field.code }})</li></ul>
      <section v-for="type in subTypes" :key="type.id"><h3>{{ type.name }} ({{ type.code }})</h3><ul><li v-for="field in subFields[type.id] ?? []" :key="field.id">{{ field.displayName }} ({{ field.code }})</li></ul></section>
    </template>
  </section>
</template>
