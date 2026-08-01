<template>
  <el-card v-loading="loading">
    <template #header>审批中心</template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="items" empty-text="暂无审批记录">
      <el-table-column prop="id" label="审批编号" />
      <el-table-column prop="record_code" label="记录编码" />
      <el-table-column label="操作类型"><template #default="{ row }">{{ operationLabel(row.operation) }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
      <el-table-column label="操作"><template #default="{ row }"><el-button link @click="$router.push('/workflow/approvals/' + row.id)">查看详情</el-button></template></el-table-column>
    </el-table>
  </el-card>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { listApprovals } from '../../api/workflow'
import { chineseError, operationLabel, statusLabel } from '../../utils/labels'
const items = ref([]), loading = ref(false), error = ref('')
onMounted(async () => {
  loading.value = true
  try { items.value = (await listApprovals()).data || [] }
  catch (exception) { error.value = chineseError(exception, '加载审批列表失败') }
  finally { loading.value = false }
})
</script>
