<template>
  <el-card v-loading="loading">
    <template #header>分发队列与记录</template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" :closable="false" />
    <el-select v-model="statusFilter" data-test="status-filter" clearable placeholder="全部状态">
      <el-option v-for="status in statuses" :key="status" :label="statusLabel(status)" :value="status" />
    </el-select>
    <h3>待分发队列</h3>
    <el-table :data="queuedItems" empty-text="暂无待分发任务">
      <el-table-column prop="event_id" label="事件编号" />
      <el-table-column prop="record_id" label="记录标识" />
      <el-table-column label="状态"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
      <el-table-column label="触发方式"><template #default="{ row }">{{ triggerLabel(row.trigger_type) }}</template></el-table-column>
      <el-table-column prop="retry_count" label="重试次数" />
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button v-if="row.can_retry === true" :data-test="`retry-${row.id}`" link type="primary" :loading="retryingId === row.id" @click="retry(row)">重试</el-button>
          <el-button v-if="row.can_cancel === true" :data-test="`cancel-${row.id}`" link type="danger" :loading="cancellingId === row.id" @click="cancel(row)">取消</el-button>
          <el-button v-if="isAdmin" :data-test="`detail-${row.id}`" link :loading="detailLoading && detailLogId === row.id" @click="showDetail(row)">查看快照</el-button>
        </template>
      </el-table-column>
    </el-table>
    <h3>已分发记录</h3>
    <el-table :data="historyItems" empty-text="暂无已分发记录">
      <el-table-column prop="event_id" label="事件编号" />
      <el-table-column prop="record_id" label="记录标识" />
      <el-table-column label="状态"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
      <el-table-column label="触发方式"><template #default="{ row }">{{ triggerLabel(row.trigger_type) }}</template></el-table-column>
      <el-table-column label="取消原因"><template #default="{ row }">{{ row.cancellation_reason || '—' }}</template></el-table-column>
      <el-table-column label="操作"><template #default="{ row }"><el-button v-if="row.can_retry === true" :data-test="`retry-${row.id}`" link type="primary" :loading="retryingId === row.id" @click="retry(row)">重试</el-button><el-button v-if="isAdmin" :data-test="`detail-${row.id}`" link @click="showDetail(row)">查看快照</el-button></template></el-table-column>
    </el-table>
    <el-card v-if="detail" shadow="never" class="snapshot">
      <template #header>分发快照详情 · 日志 #{{ detail.id }}</template>
      <h4>请求快照</h4><pre>{{ detail.request_snapshot || '—' }}</pre>
      <h4>响应快照</h4><pre>{{ detail.response_snapshot || '—' }}</pre>
    </el-card>
  </el-card>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { cancelPushLog, getPushLog, listPushLogs, retryPushLog } from '../../api/integration'
import { chineseError, statusLabel, triggerLabel } from '../../utils/labels'
const items = ref([]), detail = ref(null), loading = ref(false), retryingId = ref(null), cancellingId = ref(null), error = ref(''), notice = ref(''), statusFilter = ref('')
const statuses = ['PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED']
const filteredItems = computed(() => statusFilter.value ? items.value.filter(item => item.status === statusFilter.value) : items.value)
const queuedItems = computed(() => filteredItems.value.filter(item => ['PENDING', 'RUNNING'].includes(item.status)))
const historyItems = computed(() => filteredItems.value.filter(item => !['PENDING', 'RUNNING'].includes(item.status)))
const detailLoading = ref(false), detailLogId = ref(null)
const user = JSON.parse(localStorage.getItem('user') || '{}')
const isAdmin = computed(() => user.is_admin === true)
async function load() {
  loading.value = true; error.value = ''
  try { items.value = (await listPushLogs()).data || [] }
  catch (exception) { error.value = chineseError(exception, '加载分发日志失败') }
  finally { loading.value = false }
}
async function retry(row) {
  if (row.can_retry !== true || retryingId.value != null) return
  retryingId.value = row.id; error.value = ''; notice.value = ''
  try { await retryPushLog(row.id, { reason: '管理界面失败重试' }); notice.value = '已创建重试任务'; await load() }
  catch (exception) { error.value = chineseError(exception, '创建重试任务失败') }
  finally { retryingId.value = null }
}
async function cancel(row) {
  if (row.can_cancel !== true || cancellingId.value != null) return
  cancellingId.value = row.id; error.value = ''; notice.value = ''
  try { await cancelPushLog(row.id, { reason: '管理界面取消任务' }); notice.value = '已取消待分发任务'; await load() }
  catch (exception) { error.value = chineseError(exception, '取消待分发任务失败') }
  finally { cancellingId.value = null }
}
async function showDetail(row) {
  if (!isAdmin.value || detailLoading.value) return
  detail.value = null; detailLoading.value = true; detailLogId.value = row.id; error.value = ''
  try { detail.value = (await getPushLog(row.id)).data }
  catch (exception) { error.value = chineseError(exception, '加载分发快照失败') }
  finally { detailLoading.value = false }
}
onMounted(load)
</script>
<style scoped>h3{margin:20px 0 10px}.snapshot{margin-top:18px}pre{white-space:pre-wrap;word-break:break-all;background:#f5f7fa;padding:12px;border-radius:4px}</style>
