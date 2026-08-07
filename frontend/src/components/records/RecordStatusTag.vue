<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ status: string }>()
const { t } = useI18n()

const type = computed(() => {
  switch (props.status) {
    case 'ACTIVE':
    case 'APPROVED':
      return 'success'
    case 'DELETED':
    case 'REJECTED':
      return 'danger'
    case 'PENDING':
      return 'warning'
    default:
      return 'info'
  }
})

const label = computed(() => {
  const key = `record.status.${props.status}`
  return t(key)
})
</script>

<template>
  <el-tag :type="type" effect="plain">{{ label }}</el-tag>
</template>
