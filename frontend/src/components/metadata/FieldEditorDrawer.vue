<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FieldDefinition, FieldType, SubType } from '../../api/metadata'

type EditorFamily = 'master-fields' | 'sub-types' | 'sub-fields'
type Draft = FieldDefinition | SubType

const props = defineProps<{ open: boolean, family: EditorFamily, draft: Draft | null }>()
const emit = defineEmits<{ close: [], save: [value: Draft] }>()
const { t } = useI18n()

const form = ref<Draft | null>(null)
const error = ref('')
const isField = computed(() => props.family !== 'sub-types')
const supportsOptions = computed(() => isField.value && ['SELECT', 'RADIO', 'MULTISELECT'].includes((form.value as FieldDefinition | null)?.fieldType ?? ''))

function clone(value: Draft | null): Draft | null { return value ? JSON.parse(JSON.stringify(value)) as Draft : null }
watch(() => [props.open, props.draft] as const, () => { form.value = clone(props.draft); error.value = '' }, { immediate: true, deep: true })

function validCode(code: string): boolean { return /^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(code) }

function submit(): void {
  const value = form.value
  error.value = ''
  if (!value) return
  if (!validCode(value.code)) { error.value = t('metadata.fieldEditor.invalidCode'); return }
  if ('displayName' in value) {
    if (!value.displayName.trim()) { error.value = t('metadata.fieldEditor.nameRequired'); return }
    if (!value.fieldType) { error.value = t('metadata.fieldEditor.fieldTypeRequired'); return }
    if (['SELECT', 'RADIO', 'MULTISELECT'].includes(value.fieldType)) {
      const options = value.options.map((option) => option.trim()).filter(Boolean)
      if (!options.length) { error.value = t('metadata.fieldEditor.optionsRequired'); return }
      if (new Set(options.map((option) => option.toLowerCase())).size !== options.length) { error.value = t('metadata.fieldEditor.optionsUnique'); return }
      value.options = options
    } else value.options = []
  } else if (!value.name.trim()) { error.value = t('metadata.fieldEditor.nameRequired'); return }
  emit('save', JSON.parse(JSON.stringify(value)) as Draft)
}

const fieldTypes: FieldType[] = ['TEXT', 'NUMBER', 'DATE', 'DATETIME', 'SELECT', 'RADIO', 'MULTISELECT', 'SWITCH']
</script>

<template>
  <aside v-if="open && form" class="system-drawer" role="dialog" :aria-label="t('metadata.fieldEditor.ariaLabel')">
    <h2>{{ 'displayName' in form ? t('metadata.fieldEditor.field') : t('metadata.fieldEditor.subType') }}</h2>
    <form @submit.prevent="submit">
      <label>{{ t('metadata.fieldEditor.code') }} <input v-model="form.code" name="code" /></label>
      <template v-if="'displayName' in form">
        <label>{{ t('metadata.fieldEditor.name') }} <input v-model="form.displayName" name="displayName" /></label>
        <label>{{ t('metadata.fieldEditor.type') }}
          <select v-model="form.fieldType" name="fieldType"><option value="">{{ t('metadata.fieldEditor.selectType') }}</option><option v-for="type in fieldTypes" :key="type" :value="type">{{ t(`metadata.fieldTypes.${type}`) }}</option></select>
        </label>
        <label><input v-model="form.required" type="checkbox" name="required" /> {{ t('metadata.fieldEditor.required') }}</label>
        <label v-if="isField"><input v-model="form.shared" type="checkbox" name="shared" /> {{ t('metadata.fieldEditor.shared') }}</label>
        <label v-if="supportsOptions">{{ t('metadata.fieldEditor.options') }}<textarea :value="form.options.join(', ')" name="options" @input="form.options = ($event.target as HTMLTextAreaElement).value.split(',')" /></label>
      </template>
      <label v-else>{{ t('metadata.fieldEditor.name') }} <input v-model="form.name" name="name" /></label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="drawer-actions"><el-button native-type="button" @click="emit('close')">{{ t('common.cancel') }}</el-button><el-button native-type="submit" type="primary">{{ t('metadata.fieldEditor.saveDraft') }}</el-button></div>
    </form>
  </aside>
</template>
