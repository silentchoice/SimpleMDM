<template>
  <el-card v-loading="loading">
    <template #header><div class="header"><span>主数据详情</span><el-button @click="$router.back()">返回</el-button></div></template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="record">
      <el-descriptions border :column="2">
        <el-descriptions-item label="编码">{{ record.record_code }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ record.status }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ record.department_id }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ record.version }}</el-descriptions-item>
      </el-descriptions>
      <h3>主表字段</h3>
      <el-descriptions border :column="2">
        <el-descriptions-item v-for="(value,key) in record.data" :key="key" :label="key">{{ display(value) }}</el-descriptions-item>
      </el-descriptions>
    </template>
  </el-card>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getRecord } from '../../api/mdm'
import { chineseError } from '../../utils/labels'
const route=useRoute(), record=ref(null), loading=ref(false), error=ref('')
const display=value=>value==null||value===''?'—':typeof value==='boolean'?(value?'是':'否'):String(value)
onMounted(async()=>{loading.value=true;try{record.value=(await getRecord(route.query.object,route.params.id)).data}catch(e){error.value=chineseError(e,'加载主数据详情失败')}finally{loading.value=false}})
</script>
<style scoped>.header{display:flex;justify-content:space-between;align-items:center}h3{margin:22px 0 12px}</style>
