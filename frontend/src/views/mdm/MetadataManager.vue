<template>
  <el-card>
    <template #header>元数据对象</template>
    <el-alert title="当前后端仅开放元数据读取接口；字段写入功能待服务端接口提供后启用。" type="info" :closable="false" />
    <el-table :data="objectTypes">
      <el-table-column prop="code" label="对象编码" />
      <el-table-column prop="name" label="对象名称" />
      <el-table-column prop="id" label="ID" />
      <el-table-column label="字段"><template #default="{ row }">{{ (row.fields || []).map(field => field.field_name).join('、') || '—' }}</template></el-table-column>
    </el-table>
  </el-card>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { listObjectTypes } from '../../api/mdm'
const objectTypes = ref([])
onMounted(async () => { objectTypes.value = (await listObjectTypes()).data || [] })
</script>
