<template>
  <div>
    <div class="page-header"><h2>推送日志</h2></div>

    <el-card shadow="hover" style="margin-bottom: 16px;">
      <el-form :inline="true">
        <el-form-item label="目标系统">
          <el-select v-model="query.target_system" placeholder="全部" clearable style="width: 160px;" @change="fetchData">
            <el-option label="CRM" value="CRM" />
            <el-option label="MES" value="MES" />
            <el-option label="HR" value="HR" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px;" @change="fetchData">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 12px 24px;">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="请求内容" :span="2">
                  <pre style="max-height: 200px; overflow: auto; font-size: 12px; background: #f5f7fa; padding: 8px; border-radius: 4px;">{{ formatJson(row.request_body) }}</pre>
                </el-descriptions-item>
                <el-descriptions-item label="响应内容" :span="2">
                  <pre style="max-height: 200px; overflow: auto; font-size: 12px; background: #f5f7fa; padding: 8px; border-radius: 4px;">{{ formatJson(row.response_body) }}</pre>
                </el-descriptions-item>
                <el-descriptions-item v-if="row.error_message" label="错误信息" :span="2">
                  <span style="color: #f56c6c;">{{ row.error_message }}</span>
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="personnel_name" label="人员" width="100" />
        <el-table-column prop="target_system" label="目标系统" width="100">
          <template #default="{ row }">
            <el-tag :type="row.target_system === 'CRM' ? '' : row.target_system === 'MES' ? 'success' : 'warning'" size="small">
              {{ row.target_system }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : row.status === 'failed' ? 'danger' : 'info'" size="small">
              {{ row.status === 'success' ? '成功' : row.status === 'failed' ? '失败' : '待推送' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="response_code" label="响应码" width="80" />
        <el-table-column prop="retry_count" label="重试次数" width="80" />
        <el-table-column prop="pushed_at" label="推送时间" min-width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'failed' && userStore.isOperator()"
              size="small" text type="primary"
              @click="handleRetry(row)"
            >重试</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.page_size"
          :total="total"
          :page-sizes="[10, 20]"
          layout="total, sizes, prev, pager, next"
          @change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { listPushLogs, retryPush } from '../../api/pushLog'
import { useUserStore } from '../../stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ target_system: '', status: '', page: 1, page_size: 10 })

function formatJson(str) {
  if (!str) return '(空)'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listPushLogs({
      target_system: query.target_system,
      status: query.status,
      page: query.page,
      page_size: query.page_size,
    })
    tableData.value = res.data.items
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function handleRetry(row) {
  try {
    await ElMessageBox.confirm('确定重试该推送吗？', '确认重试', { type: 'info' })
  } catch {
    return
  }
  try {
    await retryPush(row.id)
    ElMessage.success('重试成功')
    fetchData()
  } catch { /* handled */ }
}

onMounted(() => fetchData())
</script>
