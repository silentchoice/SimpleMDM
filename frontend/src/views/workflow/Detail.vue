<template>
  <el-card v-loading="loading">
    <template #header>审批详情</template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="item">
      <el-descriptions border :column="2">
        <el-descriptions-item label="审批编号">{{ item.id }}</el-descriptions-item>
        <el-descriptions-item label="记录编码">{{ item.record_code || '—' }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ operationLabel(item.operation) }}</el-descriptions-item>
        <el-descriptions-item label="审批状态">{{ statusLabel(item.status) }}</el-descriptions-item>
      </el-descriptions>

      <h3>主表变更</h3>
      <el-table :data="item.changes || []" empty-text="主表无变更">
        <el-table-column label="字段"><template #default="{ row }">{{ row.field_name || row.field_key || '—' }}</template></el-table-column>
        <el-table-column label="变更前"><template #default="{ row }">{{ displayValue(row.old_value) }}</template></el-table-column>
        <el-table-column label="变更后"><template #default="{ row }">{{ displayValue(row.new_value) }}</template></el-table-column>
      </el-table>

      <h3>子表变更</h3>
      <el-card v-for="group in item.child_changes || []" :key="group.change_key" shadow="never" class="child-change">
        <template #header>{{ group.child_type_name || '未知子表' }} · {{ operationLabel(group.operation) }}<span v-if="group.child_record_id"> · 子记录标识：{{ group.child_record_id }}</span></template>
        <el-table :data="group.values || []" empty-text="无可见变更">
          <el-table-column label="字段"><template #default="{ row }">{{ row.field_name || fieldLabel(row.field_key) }}</template></el-table-column>
          <el-table-column label="变更前"><template #default="{ row }">{{ displayValue(row.old_value) }}</template></el-table-column>
          <el-table-column label="变更后"><template #default="{ row }">{{ displayValue(row.new_value) }}</template></el-table-column>
        </el-table>
      </el-card>
      <el-empty v-if="!(item.child_changes || []).length" description="子表无变更" :image-size="60" />
      <el-button v-if="canApprove" data-test="approve" type="primary" :loading="approving" @click="doApprove">通过审批</el-button>
      <template v-if="canApprove">
        <el-input v-model="rejectComment" data-test="reject-comment" maxlength="2048" show-word-limit placeholder="请输入驳回意见" />
        <el-button data-test="reject" type="danger" :loading="rejecting" @click="doReject">驳回</el-button>
      </template>
    </template>
  </el-card>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { approve, getApproval, reject } from '../../api/workflow'
import { chineseError, fieldLabel, operationLabel, statusLabel } from '../../utils/labels'
const route = useRoute(), item = ref(null), loading = ref(false), approving = ref(false), error = ref('')
const canApprove = computed(() => item.value?.can_approve === true)
const rejecting = ref(false), rejectComment = ref('')
function displayValue(value) { return value == null || value === '' ? '—' : typeof value === 'boolean' ? (value ? '是' : '否') : String(value) }
async function load() {
  loading.value = true; error.value = ''
  try { item.value = (await getApproval(route.params.id)).data }
  catch (exception) { item.value = null; error.value = chineseError(exception, '加载审批详情失败') }
  finally { loading.value = false }
}
async function doApprove() {
  if (!canApprove.value || approving.value) return
  approving.value = true; error.value = ''
  try { await approve(route.params.id); await load() }
  catch (exception) { error.value = chineseError(exception, '审批失败') }
  finally { approving.value = false }
}
async function doReject() {
  if (!canApprove.value || rejecting.value) return
  rejecting.value = true; error.value = ''
  try { await reject(route.params.id, rejectComment.value); await load() }
  catch (exception) { error.value = chineseError(exception, '驳回失败') }
  finally { rejecting.value = false }
}
onMounted(load)
</script>
<style scoped>h3{margin:22px 0 12px}.child-change{margin-bottom:12px}</style>
