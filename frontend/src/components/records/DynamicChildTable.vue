<script setup lang="ts">
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

function stringValue(row: EditorChildRow, code: string): string {
  const value = row.values[code]
  return Array.isArray(value) ? '' : value == null ? '' : String(value)
}

function errorKey(clientId: string, code: string): string {
  return `${props.subtype.id}:${clientId}:${code}`
}
</script>

<template>
  <section class="child-table">
    <div class="child-table__header">
      <h3>{{ subtype.name }}</h3>
      <el-button v-if="!readonly" :data-testid="`child-${subtype.id}-add`" type="primary" plain @click="emit('add', subtype.id)">Add row</el-button>
    </div>
    <div v-for="(row, rowIndex) in rows" :key="row.clientId" class="child-table__row">
      <div v-for="field in fields" :key="field.id" class="dynamic-form__field">
        <label :for="`child-${subtype.id}-row-${rowIndex}-${field.code}`">{{ field.displayName }}</label>
        <input
          v-if="field.fieldType !== 'SELECT'"
          :id="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          :name="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          type="text"
          :readonly="readonly"
          :value="stringValue(row, field.code)"
          @input="emit('update', subtype.id, row.clientId, field.code, ($event.target as HTMLInputElement).value)"
        >
        <select
          v-else
          :id="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          :name="`child-${subtype.id}-row-${rowIndex}-${field.code}`"
          :disabled="readonly"
          :value="stringValue(row, field.code)"
          @change="emit('update', subtype.id, row.clientId, field.code, ($event.target as HTMLSelectElement).value)"
        >
          <option value=""></option>
          <option v-for="option in field.options" :key="option" :value="option">{{ option }}</option>
        </select>
        <p v-if="errors[errorKey(row.clientId, field.code)]" class="form-error">{{ errors[errorKey(row.clientId, field.code)] }}</p>
      </div>
      <div v-if="!readonly" class="child-table__actions">
        <button :data-testid="`child-${subtype.id}-row-${rowIndex}-up`" type="button" :disabled="rowIndex === 0" @click="emit('move', subtype.id, row.clientId, 'up')">Up</button>
        <button :data-testid="`child-${subtype.id}-row-${rowIndex}-down`" type="button" :disabled="rowIndex === rows.length - 1" @click="emit('move', subtype.id, row.clientId, 'down')">Down</button>
        <button :data-testid="`child-${subtype.id}-row-${rowIndex}-delete`" type="button" @click="emit('remove', subtype.id, row.clientId)">Delete</button>
      </div>
    </div>
  </section>
</template>
