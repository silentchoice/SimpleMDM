<template>
  <div>
    <div class="page-header"><h2>审批中心</h2></div>

    <el-card shadow="hover">
      <el-tabs v-model="activeTab" @tab-change="fetchData">
        <el-tab-pane label="待我审批" name="pending_my" />
        <el-tab-pane label="我的申请" name="my_submitted" />
        <el-tab-pane label="全部" name="all" />
      </el-tabs>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="id" label="编号" width="70" />
        <el-table-column prop="personnel_name" label="人员" width="100" />
        <el-table-column prop="workflow_type" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.workflow_type === 'create' ? 'success' : 'warning'" size="small">
              {{ row.workflow_type === 'create' ? '新增' : '变更' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitter_name" label="提交人" width="100" />
        <el-table-column prop="approver_name" label="审批人" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submit_time" label="提交时间" min-width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="$router.push(`/approvals/${row.id}`)">查看详情</el-button>
            <template v-if="row.status === 'pending'">
              <el-button
                v-if="userStore.isApprover() && activeTab === 'pending_my'"
                size="small" text type="success"
                @click="quickApprove(row)"
              >通过</el-button>
              <el-button
                v-if="userStore.isApprover() && activeTab === 'pending_my'"
                size="small" text type="danger"
                @click="quickReject(row)"
              >驳回</el-button>
              <el-button
                v-if="userStore.isOperator() && activeTab === 'my_submitted'"
                size="small" text type="warning"
                @click="handleWithdraw(row)"
              >撤回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listApprovals, approve, reject, withdraw } from '../../api/approval'
import { useUserStore } from '../../stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const activeTab = ref('pending_my')
const tableData = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

function statusType(s) {
  const map = { pending: 'warning', approved: 'success', rejected: 'danger', withdrawn: 'info' }
  return map[s] || 'info'
}

function statusLabel(s) {
  const map = { pending: '待审批', approved: '已通过', rejected: '已驳回', withdrawn: '已撤回' }
  return map[s] || s
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listApprovals({
      list_type: activeTab.value,
      page: page.value,
      page_size: pageSize.value,
    })
    tableData.value = res.data.items
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function quickApprove(row) {
  try {
    await ElMessageBox.prompt('请输入审批意见', '审批通过', { confirmButtonText: '通过', cancelButtonText: '取消' })
      .then(async ({ value }) => {
        await approve(row.id, value || '同意')
        ElMessage.success('审批已通过')
        fetchData()
      })
  } catch { /* cancelled */ }
}

async function quickReject(row) {
  try {
    await ElMessageBox.prompt('请输入驳回原因', '审批驳回', { confirmButtonText: '驳回', cancelButtonText: '取消' })
      .then(async ({ value }) => {
        if (!value) { ElMessage.warning('请填写驳回原因'); return }
        await reject(row.id, value)
        ElMessage.success('审批已驳回')
        fetchData()
      })
  } catch { /* cancelled */ }
}

async function handleWithdraw(row) {
  try {
    await ElMessageBox.confirm('确定撤回该申请吗？', '确认撤回', { type: 'warning' })
    await withdraw(row.id)
    ElMessage.success('申请已撤回')
    fetchData()
  } catch { /* cancelled */ }
}

onMounted(() => fetchData())
</script>
