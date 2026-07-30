<template>
  <el-table :data="rows" border size="small" style="width: 100%;">
    <el-table-column prop="field" label="字段" width="120" />
    <el-table-column prop="old" label="变更前">
      <template #default="{ row }">
        <span :style="row.changed ? 'color: #f56c6c; text-decoration: line-through;' : ''">{{ row.old }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="new" label="变更后">
      <template #default="{ row }">
        <span :style="row.changed ? 'color: #67c23a; font-weight: 600;' : ''">{{ row.new }}</span>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  changeData: {
    type: Object,
    default: () => ({}),
  },
  definitions: {
    type: Array,
    default: () => [],
  },
})

const rows = computed(() => {
  const labels = new Map(props.definitions.map(definition =>
    [definition.field_key, definition.field_name]
  ))
  labels.set('owner_dept', '所属部门')
  return Object.entries(props.changeData).map(([field, vals]) => ({
    field: labels.get(field) || field,
    old: vals.old ?? '(空)',
    new: vals.new ?? '(空)',
    changed: vals.old !== vals.new,
  }))
})
</script>
