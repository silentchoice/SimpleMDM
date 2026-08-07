<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { FieldDefinition, SubType } from '../../api/metadata'

export interface EditorChildRow {
  clientId: string
  recordId: number | null
  rowOrder: number
  values: Record<string, unknown>
}

const props = defineProps<{
  subtype: SubType
  fields: FieldDefinition[]
  rows: EditorChildRow[]
  errors: Record<string, string>
  readonly?: boolean
}>()

const emit = defineEmits<{
  add: [subTypeId: number]
  remove: [subTypeId: number, clientId: string]
  move: [subTypeId: number, clientId: string, direction: 'up' | 'down']
  update: [subTypeId: number, clientId: string, code: string, value: unknown]
}>()
const { t } = useI18n()

function stringValue(row: EditorChildRow, code: string): string {
  const value = row.values[code]
  return Array.isArray(value) ? '' : value == null ? '' : String(value)
}

function multiValue(row: EditorChildRow, code: string): string[] {
  const value = row.values[code]
  return Array.isArray(value) ? value.map(String) : []
}

function boolValue(row: EditorChildRow, code: string): boolean {
  return Boolean(row.values[code])
}

function hasUnknownOption(row: EditorChildRow, field: FieldDefinition): boolean {
  const value = row.values[field.code]
  return typeof value === 'string' && value.length > 0 && !field.options.includes(value)
}

function updateMulti(subTypeId: number, clientId: string, code: string, event: Event): void {
  const target = event.target as HTMLSelectElement
  emit('update', subTypeId, clientId, code, Array.from(target.selectedOptions).map((option) => option.value))
}

function errorKey(clientId: string, code: string): string {
  return `${props.subtype.id}:${clientId}:${code}`
}
</script>

<template>
  <section class="child-table">
    <div class="child-table__header">
      <h3>{{ subtype.name }}</h3>
      <el-button v-if="!readonly" :data-testid="`child-${subtype.id}-add`" type="primary" plain @click="emit('add', subtype.id)">{{ t('record.child.addRow') }}</el-button>
    </div>
    <div v-for="(row, rowIndex) in rows" :key="row.clientId" class="child-table__row">
      <div v-for="field in fields" :key="field.id" class="dynamic-form__field">
        <label v-if="field.fieldType !== 'RADIO'" :for="`child-${subtype.id}-row-${rowIndex}-${field.code}`">{{ field.displayName }}</label>
        <input
          v-if="field.fieldType === 'TEXT'"
          :id="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          :name="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          type="text"
          :readonly="readonly"
          :value="stringValue(row, field.code)"
          @input="emit('update', subtype.id, row.clientId, field.code, ($event.target as HTMLInputElement).value)"
        >
        <input
          v-else-if="field.fieldType === 'NUMBER'"
          :id="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          :name="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          type="number"
          :readonly="readonly"
          :value="stringValue(row, field.code)"
          @input="emit('update', subtype.id, row.clientId, field.code, ($event.target as HTMLInputElement).value === '' ? null : Number(($event.target as HTMLInputElement).value))"
        >
        <input
          v-else-if="field.fieldType === 'DATE'"
          :id="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          :name="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          type="date"
          :readonly="readonly"
          :value="stringValue(row, field.code)"
          @input="emit('update', subtype.id, row.clientId, field.code, ($event.target as HTMLInputElement).value)"
        >
        <input
          v-else-if="field.fieldType === 'DATETIME'"
          :id="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          :name="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          type="datetime-local"
          :readonly="readonly"
          :value="stringValue(row, field.code)"
          @input="emit('update', subtype.id, row.clientId, field.code, ($event.target as HTMLInputElement).value)"
        >
        <select
          v-else-if="field.fieldType === 'SELECT'"
          :id="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          :name="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          :disabled="readonly"
          :value="stringValue(row, field.code)"
          @change="emit('update', subtype.id, row.clientId, field.code, ($event.target as HTMLSelectElement).value)"
        >
          <option value=""></option>
          <option v-if="hasUnknownOption(row, field)" :value="stringValue(row, field.code)">{{ stringValue(row, field.code) }}</option>
          <option v-for="option in field.options" :key="option" :value="option">{{ option }}</option>
        </select>
        <fieldset v-else-if="field.fieldType === 'RADIO'" :name="`child-${subtype.id}-row-${rowIndex}-${field.code}`" :disabled="readonly">
          <legend>{{ field.displayName }}</legend>
          <label v-if="hasUnknownOption(row, field)">
            <input
              type="radio"
              :name="`child-${subtype.id}-row-${rowIndex}-${field.code}-radio`"
              :value="stringValue(row, field.code)"
              :checked="stringValue(row, field.code) === stringValue(row, field.code)"
              @change="emit('update', subtype.id, row.clientId, field.code, ($event.target as HTMLInputElement).value)"
            >
            <span>{{ stringValue(row, field.code) }}</span>
          </label>
          <label v-for="option in field.options" :key="option">
            <input
              :data-testid="`child-${subtype.id}-row-${rowIndex}-${option.toLowerCase()}-radio`"
              type="radio"
              :name="`child-${subtype.id}-row-${rowIndex}-${field.code}-radio`"
              :value="option"
              :checked="stringValue(row, field.code) === option"
              @change="emit('update', subtype.id, row.clientId, field.code, ($event.target as HTMLInputElement).value)"
            >
            <span>{{ option }}</span>
          </label>
        </fieldset>
        <select
          v-else-if="field.fieldType === 'MULTISELECT'"
          :id="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          :name="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          multiple
          :disabled="readonly"
          @change="updateMulti(subtype.id, row.clientId, field.code, $event)"
        >
          <option v-for="option in field.options" :key="option" :value="option" :selected="multiValue(row, field.code).includes(option)">{{ option }}</option>
        </select>
        <label v-else class="dynamic-form__switch">
          <input
            :id="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
            :name="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
            type="checkbox"
            :disabled="readonly"
            :checked="boolValue(row, field.code)"
            @change="emit('update', subtype.id, row.clientId, field.code, ($event.target as HTMLInputElement).checked)"
          >
          <span>{{ field.displayName }}</span>
        </label>
        <p v-if="errors[errorKey(row.clientId, field.code)]" class="form-error">{{ errors[errorKey(row.clientId, field.code)] }}</p>
      </div>
      <div v-if="!readonly" class="child-table__actions">
        <button :data-testid="`child-${subtype.id}-row-${rowIndex}-up`" type="button" :disabled="rowIndex === 0" @click="emit('move', subtype.id, row.clientId, 'up')">{{ t('record.child.up') }}</button>
        <button :data-testid="`child-${subtype.id}-row-${rowIndex}-down`" type="button" :disabled="rowIndex === rows.length - 1" @click="emit('move', subtype.id, row.clientId, 'down')">{{ t('record.child.down') }}</button>
        <button :data-testid="`child-${subtype.id}-row-${rowIndex}-delete`" type="button" @click="emit('remove', subtype.id, row.clientId)">{{ t('record.child.delete') }}</button>
      </div>
    </div>
  </section>
</template>
