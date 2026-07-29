<template>
  <span>{{ displayValue }}</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  definition: { type: Object, required: true },
  value: { default: null },
})

const displayValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return '-'
  const options = props.definition.options || []
  const match = options.find(option => {
    const value = typeof option === 'object' ? option.value : option
    return value === props.value
  })
  if (match && typeof match === 'object') return match.label
  return String(props.value)
})
</script>
