<template>
  <el-tag v-if="type === 'BOOLEAN'" :type="value ? 'success' : 'info'">{{ value ? '是' : '否' }}</el-tag>
  <span v-else-if="type === 'REFERENCE'">{{ referenceLabel }}</span>
  <span v-else>{{ display }}</span>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ field: { type: Object, required: true }, value: { default: null }, options: { type: Array, default: () => [] } })
const type = computed(() => String(props.field.data_type || 'STRING').toUpperCase())
const display = computed(() => props.value == null || props.value === '' ? '—' : String(props.value))
const referenceLabel = computed(() => {
  const option = props.options.find(item => Number(item.id) === Number(props.value))
  return option?.label || option?.record_code || display.value
})
</script>
