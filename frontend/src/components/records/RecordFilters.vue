<script setup lang="ts">
import { useI18n } from 'vue-i18n'

export interface RecordFilterModel {
  recordCode: string
  keyword: string
  status: string
  includeDeleted: boolean
}

const props = defineProps<{ modelValue: RecordFilterModel }>()
const emit = defineEmits<{
  'update:modelValue': [RecordFilterModel]
  search: []
}>()
const { t } = useI18n()

function update<K extends keyof RecordFilterModel>(key: K, value: RecordFilterModel[K]): void {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>

<template>
  <form class="record-filters" @submit.prevent="emit('search')">
    <label>
      <span>{{ t('record.filters.recordCode') }}</span>
      <input name="recordCode" :value="modelValue.recordCode" @input="update('recordCode', ($event.target as HTMLInputElement).value)">
    </label>
    <label>
      <span>{{ t('record.filters.keyword') }}</span>
      <input name="keyword" :value="modelValue.keyword" @input="update('keyword', ($event.target as HTMLInputElement).value)">
    </label>
    <label>
      <span>{{ t('record.filters.status') }}</span>
      <select name="status" :value="modelValue.status" @change="update('status', ($event.target as HTMLSelectElement).value)">
        <option value="">{{ t('record.filters.all') }}</option>
        <option value="ACTIVE">{{ t('record.status.ACTIVE') }}</option>
        <option value="DELETED">{{ t('record.status.DELETED') }}</option>
      </select>
    </label>
    <label class="record-filters__toggle">
      <input name="includeDeleted" type="checkbox" :checked="modelValue.includeDeleted" @change="update('includeDeleted', ($event.target as HTMLInputElement).checked)">
      <span>{{ t('record.filters.includeDeleted') }}</span>
    </label>
    <el-button native-type="submit" type="primary">{{ t('record.filters.search') }}</el-button>
  </form>
</template>
