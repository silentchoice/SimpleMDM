<script setup lang="ts">
import type { FieldDefinition } from '../../api/metadata'

const props = defineProps<{
  fields: FieldDefinition[]
  values: Record<string, unknown>
  errors: Record<string, string>
  readonly?: boolean
}>()

const emit = defineEmits<{ update: [code: string, value: unknown] }>()

function stringValue(code: string): string {
  const value = props.values[code]
  return Array.isArray(value) ? '' : value == null ? '' : String(value)
}

function multiValue(code: string): string[] {
  const value = props.values[code]
  return Array.isArray(value) ? value.map(String) : []
}

function boolValue(code: string): boolean {
  return Boolean(props.values[code])
}

function hasUnknownOption(field: FieldDefinition): boolean {
  const value = props.values[field.code]
  if (typeof value === 'string') return value.length > 0 && !field.options.includes(value)
  return false
}

function updateMulti(code: string, event: Event): void {
  const target = event.target as HTMLSelectElement
  emit('update', code, Array.from(target.selectedOptions).map((option) => option.value))
}
</script>

<template>
  <div class="dynamic-form">
    <div v-for="field in fields" :key="field.id" class="dynamic-form__field">
      <label v-if="field.fieldType !== 'RADIO'" :for="`field-${field.code}`">{{ field.displayName }}</label>

      <input
        v-if="field.fieldType === 'TEXT'"
        :id="`field-${field.code}`"
        :name="`field-${field.code}`"
        type="text"
        :readonly="readonly"
        :value="stringValue(field.code)"
        @input="emit('update', field.code, ($event.target as HTMLInputElement).value)"
      >

      <input
        v-else-if="field.fieldType === 'NUMBER'"
        :id="`field-${field.code}`"
        :name="`field-${field.code}`"
        type="number"
        :readonly="readonly"
        :value="stringValue(field.code)"
        @input="emit('update', field.code, ($event.target as HTMLInputElement).value === '' ? null : Number(($event.target as HTMLInputElement).value))"
      >

      <input
        v-else-if="field.fieldType === 'DATE'"
        :id="`field-${field.code}`"
        :name="`field-${field.code}`"
        type="date"
        :readonly="readonly"
        :value="stringValue(field.code)"
        @input="emit('update', field.code, ($event.target as HTMLInputElement).value)"
      >

      <input
        v-else-if="field.fieldType === 'DATETIME'"
        :id="`field-${field.code}`"
        :name="`field-${field.code}`"
        type="datetime-local"
        :readonly="readonly"
        :value="stringValue(field.code)"
        @input="emit('update', field.code, ($event.target as HTMLInputElement).value)"
      >

      <select
        v-else-if="field.fieldType === 'SELECT'"
        :id="`field-${field.code}`"
        :name="`field-${field.code}`"
        :disabled="readonly"
        :value="stringValue(field.code)"
        @change="emit('update', field.code, ($event.target as HTMLSelectElement).value)"
      >
        <option value=""></option>
        <option v-if="hasUnknownOption(field)" :value="stringValue(field.code)">{{ stringValue(field.code) }}</option>
        <option v-for="option in field.options" :key="option" :value="option">{{ option }}</option>
      </select>

      <fieldset v-else-if="field.fieldType === 'RADIO'" :name="`field-${field.code}`" :disabled="readonly">
        <legend>{{ field.displayName }}</legend>
        <label v-if="hasUnknownOption(field)">
          <input
            type="radio"
            :name="`${field.code}-radio`"
            :value="stringValue(field.code)"
            :checked="stringValue(field.code) === stringValue(field.code)"
            @change="emit('update', field.code, ($event.target as HTMLInputElement).value)"
          >
          <span>{{ stringValue(field.code) }}</span>
        </label>
        <label v-for="option in field.options" :key="option">
          <input
            type="radio"
            :name="`${field.code}-radio`"
            :value="option"
            :checked="stringValue(field.code) === option"
            @change="emit('update', field.code, ($event.target as HTMLInputElement).value)"
          >
          <span>{{ option }}</span>
        </label>
      </fieldset>

      <select
        v-else-if="field.fieldType === 'MULTISELECT'"
        :id="`field-${field.code}`"
        :name="`field-${field.code}`"
        multiple
        :disabled="readonly"
        @change="updateMulti(field.code, $event)"
      >
        <option v-for="option in field.options" :key="option" :value="option" :selected="multiValue(field.code).includes(option)">{{ option }}</option>
      </select>

      <label v-else class="dynamic-form__switch">
        <input
          :id="`field-${field.code}`"
          :name="`field-${field.code}`"
          type="checkbox"
          :disabled="readonly"
          :checked="boolValue(field.code)"
          @change="emit('update', field.code, ($event.target as HTMLInputElement).checked)"
        >
        <span>{{ field.displayName }}</span>
      </label>

      <p v-if="errors[field.code]" class="form-error">{{ errors[field.code] }}</p>
    </div>
  </div>
</template>
