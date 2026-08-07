<script setup lang="ts">
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

function update<K extends keyof RecordFilterModel>(key: K, value: RecordFilterModel[K]): void {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>

<template>
  <form class="record-filters" @submit.prevent="emit('search')">
    <label>
      <span>Record code</span>
      <input name="recordCode" :value="modelValue.recordCode" @input="update('recordCode', ($event.target as HTMLInputElement).value)">
    </label>
    <label>
      <span>Keyword</span>
      <input name="keyword" :value="modelValue.keyword" @input="update('keyword', ($event.target as HTMLInputElement).value)">
    </label>
    <label>
      <span>Status</span>
      <select name="status" :value="modelValue.status" @change="update('status', ($event.target as HTMLSelectElement).value)">
        <option value="">All</option>
        <option value="ACTIVE">ACTIVE</option>
        <option value="DELETED">DELETED</option>
      </select>
    </label>
    <label class="record-filters__toggle">
      <input name="includeDeleted" type="checkbox" :checked="modelValue.includeDeleted" @change="update('includeDeleted', ($event.target as HTMLInputElement).checked)">
      <span>Include deleted</span>
    </label>
    <el-button native-type="submit" type="primary">Search</el-button>
  </form>
</template>
