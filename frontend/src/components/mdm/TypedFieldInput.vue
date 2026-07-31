<template>
  <el-select v-if="type === 'BOOLEAN'" :model-value="modelValue" clearable @update:model-value="update">
    <el-option label="是" :value="true" /><el-option label="否" :value="false" />
  </el-select>
  <el-input-number v-else-if="type === 'INTEGER'" :model-value="numberValue" :precision="0" @update:model-value="update" />
  <el-input-number v-else-if="type === 'DECIMAL'" :model-value="numberValue" :precision="field.scale_value ?? 2" @update:model-value="update" />
  <el-date-picker v-else-if="type === 'DATE'" :model-value="modelValue" type="date" value-format="YYYY-MM-DD" @update:model-value="update" />
  <el-select v-else-if="type === 'REFERENCE'" :model-value="modelValue" filterable clearable @update:model-value="update">
    <el-option v-for="option in options" :key="option.id" :label="option.label || option.record_code || option.id" :value="option.id" />
  </el-select>
  <el-input v-else :model-value="modelValue" :type="type === 'TEXT' ? 'textarea' : 'text'" :maxlength="field.max_length || undefined" @update:model-value="update" />
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ field: { type: Object, required: true }, modelValue: { default: null }, options: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue'])
const type = computed(() => String(props.field.data_type || 'STRING').toUpperCase())
const numberValue = computed(() => props.modelValue === '' || props.modelValue == null ? null : Number(props.modelValue))
function update(value) { emit('update:modelValue', value) }
</script>
