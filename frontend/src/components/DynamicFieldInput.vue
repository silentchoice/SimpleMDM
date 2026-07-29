<template>
  <el-input-number
    v-if="definition.field_type === 'number'"
    :model-value="modelValue"
    @update:model-value="emitValue"
    controls-position="right"
    style="width: 100%;"
  />
  <el-date-picker
    v-else-if="definition.field_type === 'date'"
    :model-value="modelValue"
    @update:model-value="emitValue"
    type="date"
    value-format="YYYY-MM-DD"
    style="width: 100%;"
  />
  <el-select
    v-else-if="definition.field_type === 'select'"
    :model-value="modelValue"
    @update:model-value="emitValue"
    clearable
    style="width: 100%;"
  >
    <el-option
      v-for="option in definition.options || []"
      :key="optionValue(option)"
      :label="optionLabel(option)"
      :value="optionValue(option)"
    />
  </el-select>
  <el-radio-group
    v-else-if="definition.field_type === 'radio'"
    :model-value="modelValue"
    @update:model-value="emitValue"
  >
    <el-radio
      v-for="option in definition.options || []"
      :key="optionValue(option)"
      :value="optionValue(option)"
    >
      {{ optionLabel(option) }}
    </el-radio>
  </el-radio-group>
  <el-input
    v-else
    :model-value="modelValue"
    @update:model-value="emitValue"
    clearable
  />
</template>

<script setup>
defineProps({
  definition: { type: Object, required: true },
  modelValue: { default: '' },
})

const emit = defineEmits(['update:modelValue'])

function emitValue(value) {
  emit('update:modelValue', value)
}

function optionValue(option) {
  return typeof option === 'object' ? option.value : option
}

function optionLabel(option) {
  return typeof option === 'object' ? option.label : option
}
</script>
