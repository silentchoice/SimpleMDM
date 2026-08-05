<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ACTIVE_METADATA_INVALIDATED_EVENT, listMasterFields, listSubFields, listSubTypes, type FieldDefinition, type SubType } from '../../api/metadata'
import type { ApiError } from '../../types'

const props = withDefaults(defineProps<{ masterTypeId?: number }>(), { masterTypeId: 0 })
const { t } = useI18n()
const emit = defineEmits<{ loaded: [value: { masterTypeId: number, fields: FieldDefinition[], subTypes: SubType[], subFields: Record<number, FieldDefinition[]> }] }>()
const fields = ref<FieldDefinition[]>([])
const subTypes = ref<SubType[]>([])
const subFields = ref<Record<number, FieldDefinition[]>>({})
const loading = ref(false)
const error = ref('')
let generation = 0
function message(reason: unknown): string { const value = reason as ApiError; return value.requestId ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) }) : value.message }
function clear(): void { fields.value = []; subTypes.value = []; subFields.value = {}; error.value = '' }
async function refresh(): Promise<void> {
  const ownerId = props.masterTypeId
  const requestGeneration = ++generation
  clear()
  if (!ownerId) return
  loading.value = true; error.value = ''
  try {
    const [masterFields, types] = await Promise.all([listMasterFields(ownerId), listSubTypes(ownerId)])
    const lists = await Promise.all(types.map(async (type) => [type.id, await listSubFields(type.id)] as const))
    if (requestGeneration !== generation || ownerId !== props.masterTypeId) return
    fields.value = masterFields
    subTypes.value = types
    subFields.value = Object.fromEntries(lists)
    emit('loaded', JSON.parse(JSON.stringify({ masterTypeId: ownerId, fields: fields.value, subTypes: subTypes.value, subFields: subFields.value })) as { masterTypeId: number, fields: FieldDefinition[], subTypes: SubType[], subFields: Record<number, FieldDefinition[]> })
  } catch (reason) {
    if (requestGeneration === generation && ownerId === props.masterTypeId) error.value = message(reason)
  } finally { if (requestGeneration === generation) loading.value = false }
}
watch(() => props.masterTypeId, refresh)
onMounted(() => { window.addEventListener(ACTIVE_METADATA_INVALIDATED_EVENT, refresh); refresh() })
onBeforeUnmount(() => window.removeEventListener(ACTIVE_METADATA_INVALIDATED_EVENT, refresh))
</script>

<template>
  <section :aria-label="t('metadata.active.ariaLabel')">
    <div class="view-heading"><div><h2>{{ t('metadata.active.title') }}</h2><p>{{ t('metadata.active.description') }}</p></div><el-button data-testid="refresh-active" :loading="loading" @click="refresh">{{ t('metadata.active.refresh') }}</el-button></div>
    <p v-if="!masterTypeId">{{ t('metadata.active.noAssignment') }}</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <template v-if="masterTypeId">
      <h3>{{ t('metadata.active.masterFields') }}</h3><ul><li v-for="field in fields" :key="field.id">{{ field.displayName }} ({{ field.code }})</li></ul>
      <section v-for="type in subTypes" :key="type.id"><h3>{{ type.name }} ({{ type.code }})</h3><ul><li v-for="field in subFields[type.id] ?? []" :key="field.id">{{ field.displayName }} ({{ field.code }})</li></ul></section>
    </template>
  </section>
</template>
